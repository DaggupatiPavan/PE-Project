pipeline {
    agent any
    parameters {
        // choice(name: 'Generation', choices: ['3rd-Gen','4th-Gen'], description: 'Intel processor generation') 
        choice(name: 'Optimization', choices: ['Optimized','Non-Optimized'], description: 'Use Intel optimized instance type or not') 
        // choice(name: 'InstanceType', choices: ['t2.micro','t2.medium','t2.large'], description: 'EC2 instance type to provision') 
        choice(name: 'OS', choices: ['Ubuntu'], description: 'Operating system for the EC2 instance') 
        choice(name: 'VolumeType', choices: ['gp2','gp3','io1','io2','sc1','st1','standard'], description: 'EBS volume type') 
        choice(name: 'VolumeSize',  choices: ['50','100','150','200'], description: 'Size of EBS volume in GB')
    }
    stages {
        
        stage('Clone') {
            steps {
                script{
                    ws("workspace/${JOB_NAME}") {
                        cleanWs()
                        path=sh(script:'pwd', returnStdout: true).trim()
                        sh " echo instance_type=${params.InstanceType} -var volume_type=${params.VolumeType} -var volume_size=${params.VolumeSize}"
                        def fileCount = sh(script: 'ls -la | wc -l', returnStdout: true).trim()
                        echo "File count: $fileCount"
                        if (fileCount.toInteger() == 3) {
                            git branch: 'main', url: 'https://github.com/DaggupatiPavan/Intel_IceLake.git'
                        }else{
                            git branch: 'main', url: 'https://github.com/DaggupatiPavan/Intel_IceLake.git'
                        }
                    }
                }
            }
        }
        stage('Build Infra') {
            steps {
                script {
                    ws("${path}"){
                        sh "terraform init"
                        sh "terraform validate"
                        sh "terraform apply -no-color -var instance_type=${params.InstanceType} -var volume_type=${params.VolumeType} -var volume_size=${params.VolumeSize} --auto-approve"
                        sh "terraform output -json private_ips | jq -r '.[]'"
                        waitStatus()
                        // sh 'sleep 100'
                        postgres_ip = sh(script: "terraform output -json private_ips | jq -r '.[]' | head -1", returnStdout: true).trim()
                        hammer_ip = sh(script: "terraform output -json private_ips | jq -r '.[]' | tail -1", returnStdout: true).trim()
                    }
                }
            }
        }
        stage('Generate Inventory File') {
            steps {
                script {
                    ws("${path}"){
                        sh 'chmod +x inventoryfile.sh'
                        sh 'bash ./inventoryfile.sh'
                    }
                }
            }
        }
        stage('Install & Configure') {
            steps {
                script {
                    ws("${path}"){
                        
                        
                        def timeoutSeconds = 300  // Set a reasonable timeout

                        timeout(time: timeoutSeconds, unit: 'SECONDS') {
                            boolean ansiblePingSuccess = false
    
                            while (!ansiblePingSuccess) {
                                // Run Ansible ping command
                                def ansiblePingCommand = "ansible all -m ping"
                                def ansiblePingResult = sh(script: ansiblePingCommand, returnStatus: true)
                                if (ansiblePingResult == 0) {
                                    ansiblePingSuccess = true
                                    echo "Ansible ping successful!"
                                } else {
                                    echo "Ansible ping failed. Retrying..."
                                    sleep 10  // Adjust sleep duration as needed
                                }
                            }
                        }
                    
                        sh """
                            ansible-playbook  postgres_install.yaml
                            ansible-playbook  hammerdb_install.yaml
                            ansible-playbook  node_exporter_install.yaml
                            ansible-playbook -i myini prometheus_config.yaml -e postgres_ip=${postgres_ip}
                        """
                        if("${params.Optimization}" == "Optimized"){
                            sh "ansible-playbook  postgres_config_with_optimisation.yaml -e postgres_ip=${postgres_ip} -e hammer_ip=${hammer_ip}"
                        }
                        if("${params.Optimization}" == "Non-Optimized"){
                            sh "ansible-playbook  postgres_config.yaml -e postgres_ip=${postgres_ip} -e hammer_ip=${hammer_ip}"
                        }
                        sh """
                            ansible-playbook  hammer_config.yaml -e postgres_ip=${postgres_ip}
                            ansible-playbook  postgres_backup.yaml 
                        """
                    }
                }
            }
        }
        stage('Test') {
            steps {
                script {
                    ws("${path}"){
                        sh """
                            ansible-playbook  test_hammer.yaml -e postgres_ip=${postgres_ip}
                            ansible-playbook  restore_db.yaml 
                            ansible-playbook  test_hammer.yaml -e postgres_ip=${postgres_ip}
                            ansible-playbook  restore_db.yaml 
                            ansible-playbook  test_hammer.yaml -e postgres_ip=${postgres_ip}
                            ansible-playbook  restore_db.yaml 
                        """
                    }
                }
            }
            post('Artifact'){
                success{
                    script{
                        ws("${path}"){
                            archiveArtifacts artifacts: '**/results.txt'
                        }
                    }
                }
            }
        }
    }
    post('Destroy Infra'){
        always{
            script{
                ws("${path}"){
                    sh "terraform destroy --auto-approve "       
                }
            }
        }
    }
}
def waitStatus(){
  def instanceIds = sh(returnStdout: true, script: "terraform output -json instance_IDs | tr -d '[]\"' | tr ',' ' '").trim().split(' ')
  for (int i = 0; i < instanceIds.size(); i++) {
    def instanceId = instanceIds[i]
    while (true) {
      def status = sh(returnStdout: true, script: "aws ec2 describe-instances --instance-ids ${instanceId} --query 'Reservations[].Instances[].State.Name' --output text").trim()
      if (status != 'running') {
        print '.'
        sleep 10 
      } else {
        println "Instance ${instanceId} is ${status}"
        break  
      }
    }
  }
}

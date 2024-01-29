pipeline {
    parameters {
        string(name: 'Instance_IP', description: 'Instance IP to install the required package')
        // choice(name: 'Tool', choices: ['Ansible', 'Gerrit', 'GitLab', 'Grafana', 'Java', 'Jenkins', 'Kafka', 'Projectlibre', 'MySQL', 'PostgreSQL', 'Python3', 'MongoDB', 'Prometheus'], description: 'Select the required Tool to install')
    } 
    agent {
	label 'master'
    }
    stages {
        stage('Cloning') {
            steps {
                gitclone()
                echo "${BUILD_URL}"
            }
        }
        stage('Creating Inventory File'){
            steps {
                script {
                    ws("/var/lib/jenkins/workspace/${JOB_NAME}/Playbooks/") {
                        sh "rm -rf myinventory 2> /dev/null && touch myinventory"
                        params.Instance_IP.split(',').each { instance ->
                            sh "echo ${instance} ansible_user=ubuntu >> myinventory"
                        }
                    }
                }
            }
        }
        stage('Installing required Package Using Ansible') {
            steps {
                script {
                    ws("/var/lib/jenkins/workspace/${JOB_NAME}/Playbooks/") {
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
                        sh 'ansible-playbook updatecache.yaml'
                        params.Tool.split(',').each { tool ->
                            def name= tool.toLowerCase()
                            sh "ansible-playbook ${name}.yaml"
                        }
                    }
                    
                }
            }
        }
    }
}
def gitclone(){
    cleanWs()
    git branch: 'Sprint-3', credentialsId: 'gitlab', url: 'http://10.63.32.87/platformengineering/devops'
}
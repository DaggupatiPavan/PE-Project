pipeline {
    agent {
        label 'master' 
    }
    parameters{
        choice(name: 'Cloud', choices:["AWS","Azure"], description:"Select Cloud")
        choice(name: 'Gen', choices:["3rd_Gen","4th_Gen"], description:"Select Gen")
        choice(name: 'OS', choices:["ubuntu_22.04","ubuntu_20.04","redhat_9", "redhat_8"], description:"select the OS to make AMI")
        choice(name: 'WorkLoad', choices:["postgres","mysql"], description:"select the Db to install")
        choice(name: 'DB version', choices:["postgres-14","postgres-13", "mysql-5.8", "mysql-5.5"], description:"select the Db version install")
    }
  stages {
        stage('Checkout Code') {
            steps {
                cleanWs()
                git branch: 'main', url: 'https://github.com/DaggupatiPavan/packerAnsible.git' 
            }
        }
        stage('Update AMI Name') {
            steps {
                script {
                    dir('packer/') {
                        sh "sed -i 's/xyz/${params.OS}-${BUILD_NUMBER}/g' buildImage.pkr.hcl"  
                    }
                }
            }
        }
        stage('Initialize Packer') {
            steps {
                dir('packer/'){
                    sh 'packer init .'
                }
            }
        }
        stage('Build AMI') {
            steps {
                script{
                dir('packer/') {
                    if(params.OS == "ubuntu_22.04"){
                        sh 'packer build -var-file=./ubuntu.pkrvars.hcl --color=false buildImage.pkr.hcl'
                    }
                    else if(params.OS == "redhat_9"){
                        sh "sed -i 's/postgresOptimization/PostgresRedhat/g' buildImage.pkr.hcl"
                        sh 'packer build -var-file=./redhat.pkrvars.hcl --color=false buildImage.pkr.hcl'
                    }else{
                        error "OS Not added in the List"
                    }
                }
                }
            }
        }
    }
    post {
        success {
            // Additional steps to execute on successful build
            echo 'AMI build successful'
        }

        failure {
            // Additional steps to execute on build failure
            echo 'AMI build failed'
        }
    }
}

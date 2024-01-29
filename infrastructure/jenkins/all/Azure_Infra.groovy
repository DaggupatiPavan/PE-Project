pipeline {
    agent any
    stages {
        stage('Clone') {
            steps {
                script {
                    cleanWs()
                    git branch: 'main', url: 'https://github.com/DaggupatiPavan/Intel_IceLake.git'
                }
            }
        }
        stage('Build Infra') {
            steps {
                script {
                    ws("/var/lib/jenkins/workspace/${JOB_NAME}/azure") {
                        sh "pwd"
                        sh "terraform init"
                        sh "terraform validate"
                        sh "terraform apply -no-color --auto-approve"
                    }
                }
            }
        }
    }
    post {
        always {
            script {
                sh "terraform destroy --auto-approve"
            }
        }
    }
}

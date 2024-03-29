pipeline {
    agent {
        label 'master'
    }
    stages {
        stage("Log Analysis") {
            steps {
               
                    // Run journalctl and save the output to log.txt
                    sh "sudo journalctl | tail -20 > log.txt"

                    // Run sgpt to check the logs and generate Document.txt
                    sh "cat log.txt | sgpt \"check the logs and provide me the suggestion\" --no-cache > Document.txt"
                
            }
            post {
                success {
                    // Archive the Document.txt for reference
                    archiveArtifacts artifacts: 'Document.txt'
                }
            }
        }

        stage('Generate PDF from Document.txt') {
            steps {
                script {
                    // Use pandoc to convert Document.txt to PDF
                    sh 'pandoc Document.txt -o output.pdf --pdf-engine=xelatex'

                    // Archive the resulting PDF
                    archiveArtifacts artifacts: 'output.pdf'
                }
            }
        }

        stage("Sending Email") {
            steps {
                script {
                    // Send an email with the generated PDF attachment
                    sh '''sendemail  -f Jenkins@PE.com -t abhishek.velichala@tcs.com -cc ramteja.vasupari@tcs.com, pavan.daggupati@tcs.com -u "Log Analysis" -m "Hi Team, Please find the attached Suggestions document generated by ChatGPT for the given logs.
                    From: Jenkins" -s smtp.gmail.com:587 -a output.pdf -o tls=yes -xu aalucky555@gmail.com -xp guhguhzykgtgqddd'''
                }
            }
        }
    }
}

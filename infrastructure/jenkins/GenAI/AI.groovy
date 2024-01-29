pipeline {
    agent any
    options { timestamps () }
    parameters {
        choice(name: 'BUILD_TYPE', choices: 'Java\nPython', description: 'Select build type')
    }
 
    stages {
        stage('Checkout') {
            steps {
                script {
                    
                    if (params.BUILD_TYPE == 'Java'){
                        sh 'rm -rf *'
                        git branch: 'development', url: 'https://github.com/AbhishekRaoV/devopspoc.git'
                    }
                    
                     if (params.BUILD_TYPE == 'Python'){
                        sh 'rm -rf *'
                        git branch: 'main', url: 'https://github.com/AbhishekRaoV/Augmented_AI.git'
                    }
                }
            }
        }
        stage("Review") {
            steps {
                script {
                      if (params.BUILD_TYPE == 'Python'){
                        
                        sh "cat /var/lib/jenkins/workspace/${JOB_NAME}/binarytree.py"
                        sh "echo '123'|sudo -S -E /var/lib/jenkins/.local/bin/sourcery review binarytree.py>PyReview.txt"
                      }
                }
            }
            // post {
            //     success {
            //         archiveArtifacts artifacts: '**PyReview.txt'
            //     }
            // }
        }
        stage("Generating Jenkins file") 
        {
            steps {
                script {
                  if (params.BUILD_TYPE == 'Python'){   
                     
                    sh "sgpt --code \"This is my source code url link https://github.com/AbhishekRaoV/Augmented_AI.git,I need jenkins pipeline need to clone main branch, run python3 binarytree.py script, scan python3 binarytree.py script using bandit tool,test binarytree.py script with python3 -m coverage tool and generate report\" --no-cache >Jenkinsfile"  
                    sh "cat Jenkinsfile"
                  }
                  
                  if(params.BUILD_TYPE == 'Java'){
                      
                    sh "sgpt --code \"This is my source code url link https://github.com/AbhishekRaoV/devopspoc.git, I need jenkins pipeline to clone development branch, build using maven,use lynx command with target/site/jacoco/index.html ,scan using sonarqube\" --no-cache > Jenkinsfile"
                    sh "cat Jenkinsfile"
                  }
                
                    
                }
            }
        }
        stage("Appending Code Analysis,Code Coverage and Documentation Generation stages to Jenkinsfile") 
        {
            steps {
                script {
                 if (params.BUILD_TYPE == 'Python'){   
                //   sh "cat Jenkinsfile | sgpt --code --no-cache 'copy all lines in jenkinsstages file to Jenkinsfile after line number 28'>>Jenkinsfile"  
                //   sh "cat Jenkinsfile"
                   sh '''
                   sed -n '1,28p' Jenkinsfile > temp && cat jenkinsstages >> temp && sed -n '29,$p' Jenkinsfile >> temp && mv temp Jenkinsfile 
                   '''
                
                 }
                 
                 if(params.BUILD_TYPE == 'Java'){
                     sh '''
                     sed -n '1,29p' Jenkinsfile > temp && cat jenkinsstages >> temp && sed -n '30,$p' Jenkinsfile >> temp && mv temp Jenkinsfile
                     '''
                     
                 }
                 
                }
            }
        }
        stage("Fix") {
            steps {
                script {
                    if (params.BUILD_TYPE == 'Python'){   
                   
                        sh "echo '123'|sudo -S -E /var/lib/jenkins/.local/bin/sourcery review --fix binarytree.py"
                     
                    }
                }
            }
            post {
                success {
                    // archiveArtifacts artifacts: '**/binarytree.py'
                    dir("/var/lib/jenkins/workspace/${JOB_NAME}/") {
                    sh '''
                    git add .
                    git config --global user.email "abhishekraovelichala@gmail.com"
                    git config --global user.name "AbhishekRaoV"
                    git commit -a -m 'Changes pushed by jenkins to test' || true
                    '''
                    
                    
                  }
                }
            }
        }
        
        stage('Changes to github'){
            steps{
                script{
                    
                    if(params.BUILD_TYPE == 'Python'){
                        sh 'git push https://AbhishekRaoV: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@github.com/AbhishekRaoV/Augmented_AI.git main'
                    }
                    if(params.BUILD_TYPE == 'Java'){
                        sh 'git push https://AbhishekRaoV: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@github.com/AbhishekRaoV/devopspoc.git development'
                    }
                }
            }
        }
    }
}
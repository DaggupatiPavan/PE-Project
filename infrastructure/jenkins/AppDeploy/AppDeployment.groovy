@Library("microservices") _
def docImage
def k8sImage
def NAMESPACE
def repoName

pipeline {
    agent {
        label 'master'
    }
    tools {
        maven 'maven'
    }
    parameters {
       
        string(name: 'sourceCodeRepoUrl', description: 'Please provide the Git repository URL')
        // choice(name: 'gitCredentials', choices: ['test'], description: 'Please select credentials to authenticate with the Git repository')
        choice(name: 'programmingLanguage', choices: ['java','python'], description: 'Kindly select the programming language to use for building the application')
        string(name: 'dockerImageName', defaultValue: 'webapp', description: 'Please input the desired name for the Docker image')
        string(name: 'dockerImageTag', defaultValue: 'latest', description: 'Please specify a tag for versioning the Docker image')
        choice(name: 'buildTool', choices: ['Maven','Gradle','null'], description: 'Please choose the build automation tool for compiling the application codebase')
        choice(name: 'testingFramework', choices: ['Selenium','Junit','Pytest','RobotFramework','null'], description: 'Kindly pick a testing framework to validate the application code')
        choice(name: 'codeQualityTool', choices: ['SonarQube','null'], description: 'Please select a code quality analysis tool to inspect the application source code')
        choice(name: 'artifactRepository', choices: ['Nexus','JFrog','null'], description: 'Kindly choose an artifact repository manager for storing the build artifacts')
        choice(name: 'imageScanner', choices: ['Grype','Trivy','null'], description: 'Please select a security scanning tool to analyze the Docker image for vulnerabilities')
        choice(name: 'environment', choices: ['DEV','QA','PROD'], description: 'Please select a environment to Deploy')
    }
    environment{
        NAMESPACE="default"
    }
    stages {
        stage('Validate Environment') {
            steps {
                script {
                    if(params.environment == "QA") {
                        echo "The QA environment meets the requirements and is ready for deployment."  
                    }
                    else if(params.environment == "DEV") {
                        error "The Development environment is not yet properly configured for this deployment. Please set up the Dev environment before proceeding."
                    }  
                    else {
                        error "The Production environment is not yet configured for this deployment. The Prod environment must be prepared before deploying."  
                    }
                }
            }
        }
        stage('Get Registry IP'){
            steps{
                script{
                    REGISTRYIP = sh(script: 'kubectl get nodes -o wide --no-headers | awk \'{print $6}\' | head -1', returnStdout: true).trim()
                    DOCKER_REGISTRY = "${REGISTRYIP}:32003"
                    echo "${DOCKER_REGISTRY}"
                    echo "${REGISTRYIP}"   
                    repoName = sh(script: "echo ${params.sourceCodeRepoUrl}  | awk -F'/' '{print $NF}' | cut -d '.' -f1 ", returnStdout: true).trim()  
                }
            }
        }
        stage('Source Code Checkout') {
            steps {
                script {
                    gitClone()
                }
            }
        }
        stage('Build Application') {
            steps {
                script {
                    if(params.programmingLanguage == "java") {
                        if(params.buildTool == "Maven") {
                            sh "mvn clean package"
                        } else if(params.buildTool == "Gradle") {
                            sh "gradle clean build"
                        } else {
                            error("Unsupported build tool for Java")
                        }
                    } else if(params.programmingLanguage == "python") {
                        try {
                            sh "python -m build" 
                        } catch (exc) {
                            echo "Python build failed with error: ${exc}"
                            error "Unsupported or invalid Python codebase"
                        }
                    } else {
                        error("Unsupported programming language")
                    }
                }
            }
        }
        stage('Run Automated Tests') {
            steps {
                script {
                    if (params.testingFramework != "null"){
                        if (params.programmingLanguage == "java") {      
                            if (params.testingFramework == "Selenium") {
                            // selenium java code
                                echo "pending"
                            } else if (params.testingFramework == "Junit") {
                            // junit java code    
                                echo "pending"    
                            } else if (params.testingFramework == "RobotFramework") {  
                            // robot java code 
                                echo "pending"
                            } else {
                                error "Invalid java testing framework selected"
                            }
                        } else if (params.programmingLanguage == "python") {
                            if (params.testingFramework == "Selenium") {
                            // selenium python code 
                                echo "pending"
                            } else if (params.testingFramework == "Pytest") {
                            // pytest python code  
                                echo "pending"
                            } else {
                            error "Invalid python testing framework selected"        
                            }     
                        }else{
                            error("Unsupported programming language")
                        }
                    }else{
                        println "No Testing Framework selected - skipping Run Automated Tests stage."
                    }
                }
            }
        }
        stage('Code Quality Check'){
            steps{
                script{
                    if(params.codeQualityTool == "SonarQube"){
                        if(params.programmingLanguage == "java"){
                            withSonarQubeEnv(credentialsId: 'sonarQube') {
                                sh "mvn clean verify sonar:sonar -Dsonar.projectKey=${repoName} -Dsonar.projectName=${repoName}"
                            }
                        }
                        else if(params.programmingLanguage == "python"){
                            sh "echo 'sonar.projectKey=python' > sonar-project.properties"
                            def scannerHome = tool 'sonar-scanner';
                            withSonarQubeEnv() {
                                sh "${scannerHome}/bin/sonar-scanner"
                            }
                        }
                        else {
                            error("Unsupported language for code analysis")
                        }
                    }
                }
            }
        }
        stage('Publish Build Artifacts') {
            steps {
                script {
                    if(params.artifactRepository != "null") {
                        if(params.artifactRepository == "Nexus") {
                                    
                            if (fileExists('**/*.jar')) {
                                path =sh(script: 'ls **/*.jar', returnStdout: true).trim()
                                fileType="jar"
                            } else if (fileExists('**/*.war')) {
                                path =sh(script: 'ls **/*.war', returnStdout: true).trim()
                                fileType="war"
                            } else {
                                error 'Unknown build artifact or Update the PipelineCode'
                            }
                        // Nexus upload script
                            uploadToNexus(path,fileType)
                        } else if(params.artifactRepository == "JFrog") {
                        // JFrog upload script
                            echo "need to add script"
                        } else {
                        error "Invalid artifact repository selected."
                        }
                    } else {
                        println "No artifact repository selected - skipping artifact publish stage."      
                    }
                }
            }
        }

        stage('Containerize Application') {
            steps {
                script {
                    docImage = "${params.dockerImageName}".toLowerCase()
                    imageName = "${DOCKER_REGISTRY}/${params.dockerImageName}:${params.dockerImageTag}".toLowerCase()
                    k8sImage = "${params.dockerImageName}:${params.dockerImageTag}"
                    withCredentials([usernamePassword(credentialsId: 'dockerHubPrivateRepo', passwordVariable: 'DOCKER_PASSWD', usernameVariable: 'DOCKER_USER')]) {
                        sh "docker build -t ${imageName} ."
                        sh "docker login ${DOCKER_REGISTRY} -u ${DOCKER_USER} -p ${DOCKER_PASSWD}"
                        sh "docker push ${imageName}"
                    }
                    cleanWs()
                }
            }
        }
        stage('Security Scanning') {
            steps {
                script {
                    def image = "${DOCKER_REGISTRY}/${params.dockerImageName}:${params.dockerImageTag}"
                    if(params.imageScanner == "Grype") {
                        sh "grype docker:$image > grype-report.json"  
                        // sh "cat grype-report.json"
                    } else if(params.imageScanner == "Trivy") {
                        sh "trivy image -f json -o trivy-report.json $image"     
                        sh "trivy image --severity HIGH,CRITICAL $image"
                    } else {
                        error("Invalid image scanner tool selected")  
                    }
                    archiveArtifacts artifacts: '**/*-report.json'
                    cleanWs()
                }
            }
        }
        stage('Deploy to Kubernetes') {
            steps {
                script {
                    if(params.environment == "QA"){
                        ws("workspace/${JOB_NAME}/${BUILD_NUMBER}"){
                            createNamespaceAndSecret(DOCKER_REGISTRY)
                            gitClone2()
                            tag="${params.dockerImageTag}".toLowerCase()
                            sh "helm install ${docImage} webapp/  --set image.repository=${DOCKER_REGISTRY} --set image.imageName=${docImage} --set image.imageTag=${tag} --set imagePullSecrets=regcred --set namespace=${NAMESPACE} --set service.port=8080"                    
                        } 
                        cleanWs()
                    }
                    else if(params.environment == "Dev"){
                        // need to add the script
                    }
                    else{
                        // need to add the script
                    }
                }
            }
        }
    }
}
def gitClone() {
    cleanWs()
    if (params.gitCredentials == 'None') {
        git url: "${params.sourceCodeRepoUrl}"
    } else {
        git credentialsId: "${params.gitCredentials}", url: "${params.sourceCodeRepoUrl}"
    }
}
def gitClone2(){
    cleanWs()
    git branch: 'Sprint-2', credentialsId: 'gitlab', url: 'http://10.63.32.87/platformengineering/devops'
}

def uploadToNexus(path,fileType) {
    nexusArtifactUploader artifacts: [[
        artifactId: 'web',
        classifier: '',
        file: path, // Adjust the path accordingly
        type: fileType
    ]],
    credentialsId: 'nexus',
    groupId: 'com.example',
    nexusUrl: '10.63.20.69:32000',
    nexusVersion: 'nexus3',
    protocol: 'http',
    repository: "${repoName}",
    version: "1.${BUILD_NUMBER}"
}

def fileExists(pattern) {
    def files = findFiles(glob: pattern)
    return files.size() > 0
}



// https://github.com/bennzhang/docker-demo-with-simple-python-app
// https://github.com/DaggupatiPavan/java-web-app-docker 
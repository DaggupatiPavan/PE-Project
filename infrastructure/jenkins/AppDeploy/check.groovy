pipeline {
    agent any
    parameters {
        string(name: 'storage_size', defaultValue: '30GB', description: 'Storage Size')
    }
    environment {
        Storage_size = "${params.storage_size}".replace('GB', '')
    }
    stages {
        stage('Example Stage') {
            steps {
                echo "Storage size: ${Storage_size}"
            }
        }
    }
}

def call(Map config = [:]) {
    pipeline {
        agent any
        environment {
            DOCKER_IMAGE = "${config.dockerUser}/${config.appName}:${env.BUILD_NUMBER}"
        }
        stages {
            stage('Lint & Test') {
                steps { 
                    sh 'pip install flake8 pytest'
                    sh 'flake8 .'
                    sh 'pytest'
                }
            }
            stage('Build & Push') {
                when { branch 'main' }
                steps {
                    script {
                        sh "docker build -f ju.Dockerfile -t ${DOCKER_IMAGE} ."
                        withCredentials([usernamePassword(credentialsId: 'docker-hub-creds', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                            sh "echo $PASS | docker login -u $USER --password-stdin"
                            sh "docker push ${DOCKER_IMAGE}"
                        }
                    }
                }
            }
        }
    }
}
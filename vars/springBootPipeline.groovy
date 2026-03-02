def call(Map config = [:]) {
    pipeline {
        agent any
        tools {
            maven 'maven' 
            jdk 'jdk'      
        }
        environment {
            DOCKER_IMAGE = "${config.dockerUser}/${config.appName}:${env.BUILD_NUMBER}"
        }
        stages {
            stage('Test') {
                steps { sh 'mvn clean test' }
            }
            stage('Build & Push') {
                when { branch 'main' }
                steps {
                    script {
                        sh 'mvn clean package -DskipTests'
                        sh "docker build -t ${DOCKER_IMAGE} ."
                        withCredentials([usernamePassword(credentialsId: 'docker-hub-creds', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                            sh 'echo $PASS | docker login -u $USER --password-stdin'
                            sh 'docker push ${DOCKER_IMAGE}'
                        }
                        sh "docker rmi ${DOCKER_IMAGE}"
                    }
                }
            }
        }
    }
}
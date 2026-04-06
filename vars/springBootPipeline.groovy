def call(Map config = [:]) {
    pipeline {
        agent any
        tools {
            maven 'maven' 
            jdk 'jdk'      
        }
        environment {
            DOCKER_IMAGE = "${config.dockerUser}/${config.appName}:${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
        }
        stages {
            stage('Test') {
                steps { sh 'mvn clean test' }
            }
            stage('Build & Push') {
                when { branch 'main' }
                steps {
                    script {
                        //sh 'mvn clean package -DskipTests'
                        sh "docker build -f ju.Dockerfile -t ${DOCKER_IMAGE} ."
                        withCredentials([usernamePassword(credentialsId: 'docker-hub-creds', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                            sh 'echo $PASS | docker login -u $USER --password-stdin'
                            sh "docker tag ${DOCKER_IMAGE} ${config.dockerUser}/${config.appName}:latest"
                            sh "docker push ${DOCKER_IMAGE}"
                            sh "docker push ${config.dockerUser}/${config.appName}:latest"
                        }
                        sh "docker rmi ${DOCKER_IMAGE} ${config.dockerUser}/${config.appName}:latest"
                    }
                }
            }
        }
    }
}
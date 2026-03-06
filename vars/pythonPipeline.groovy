def call(Map config = [:]) {
    pipeline {
        agent any
        environment {
            DOCKER_USER = "${config.dockerUser ?: 'unknown-user'}"
            APP_NAME = "${config.appName ?: 'unknown-app'}"
            HF_REPO = "${config.hfRepo ?: ''}"
        }
        stages {
            stage('Lint & Test') {
                steps { 
                    script {
                        sh 'echo "Running linting and tests..."'
                    }
                }
            }
            stage('Download Models') {
                when { branch 'main' }
                steps {
                    withCredentials([string(credentialsId: 'Hug-Face', variable: 'TOKEN')]) {
                        script {
                            sh 'python3 -m venv venv'
                            sh './venv/bin/pip install huggingface_hub'
                            
                            config.modelFiles.each { file ->
                                echo "Downloading ${file.name}..."
                                sh "mkdir -p ${file.targetDir}"
                                sh """
                                ./venv/bin/python3 -c "from huggingface_hub import hf_hub_download; \
                                hf_hub_download(repo_id='${env.HF_REPO}', \
                                filename='${file.name}', \
                                token='${TOKEN}', \
                                local_dir='${file.targetDir}', \
                                local_dir_use_symlinks=False)"
                                """
                            }
                        }
                    }
                }
            }
            stage('Debug Config') {
                steps {
                    script {
                        echo "Config dockerUser: ${config.dockerUser}"
                        echo "Environment DOCKER_USER: ${env.DOCKER_USER}"
                        echo "Config appName: ${config.appName}"
                        echo "Environment APP_NAME: ${env.APP_NAME}"
                        sh 'env | grep DOCKER || true'
                    }
                }
            }
            stage('Build & Push') {
                when { branch 'main' }
                steps {
                    script {
                        def dockerImage = "${env.DOCKER_USER}/${env.APP_NAME}:${env.BUILD_NUMBER}"
                        env.DOCKER_IMAGE = dockerImage
                        
                        sh "docker build -f ju.Dockerfile -t ${env.DOCKER_IMAGE} ."
                        withCredentials([usernamePassword(credentialsId: 'docker-hub-creds', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                            sh 'echo $PASS | docker login -u $USER --password-stdin'
                            sh "docker push ${env.DOCKER_IMAGE}"
                            sh "docker rmi ${env.DOCKER_IMAGE}"
                        }
                    }
                }
            }
        }
        post {
            always { cleanWs() }
        }
    }
}
def call(Map config = [:]) {
    pipeline {
        agent any
        environment {
            DOCKER_IMAGE = "${config.dockerUser}/${config.appName}:${env.BUILD_NUMBER}"
        }
        stages {
            stage('Lint & Test') {
                steps { 
                    script {
                        // 1. tests should exist here
                        sh 'echo "Running linting and tests..."'
                    }
                }
            }
            stage('Download Models') {
                when { branch 'main' }
                steps {
                    withCredentials([string(credentialsId: 'Hug-Face', variable: 'TOKEN')]) {
                        script {
                            // Create a virtual environment
                            sh 'python3 -m venv venv'
                            
                            // Use the pip inside the venv
                            sh './venv/bin/pip install huggingface_hub'
                            
                            config.modelFiles.each { file ->
                                echo "Downloading ${file.name}..."
                                sh "mkdir -p ${file.targetDir}"
                                // Use the python inside the venv
                                sh """
                                ./venv/bin/python3 -c "from huggingface_hub import hf_hub_download; \
                                hf_hub_download(repo_id='${config.hfRepo}', \
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
            stage('Build & Push') {
                when { branch 'main' }
                steps {
                    script {
                        // The model files now exist in the workspace, so Docker COPY will find them
                        sh "docker build -f ju.Dockerfile -t ${DOCKER_IMAGE} ."
                        withCredentials([usernamePassword(credentialsId: 'docker-hub-creds', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                            sh 'echo $PASS | docker login -u $USER --password-stdin'
                            sh "docker push ${DOCKER_IMAGE}"
                            sh "docker rmi ${DOCKER_IMAGE}"
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
def call(Map config = [:]) {
    def dockerImage = "${config.dockerUser}/${config.appName}:${env.BUILD_NUMBER}"
    
    pipeline {
        agent any
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
                        sh "docker build -f ju.Dockerfile -t ${dockerImage} ."
                        withCredentials([usernamePassword(credentialsId: 'docker-hub-creds', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                            sh 'echo $PASS | docker login -u $USER --password-stdin'
                            sh "docker push ${dockerImage}"
                            sh "docker rmi ${dockerImage}"
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
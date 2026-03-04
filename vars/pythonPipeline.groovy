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
                        // 1. Setup environment
                        sh 'python3 -m venv venv'
                        sh './venv/bin/pip install flake8 pytest autopep8'
                        
                        // 2. Auto-format code to fix style issues (Indentation, whitespace)
                        // This prevents those "E261", "W291", "W292", and "W293" errors automatically
                        sh './venv/bin/autopep8 --in-place --recursive --exclude=venv .'
                        
                        // 3. Linting: Run flake8
                        // We use '|| true' to allow us to review output without immediately crashing the build
                        // or configure your .flake8 file to manage which errors are fatal.
                        sh './venv/bin/flake8 . --exclude=venv --max-line-length=88'
                        
                        // 4. Test
                        sh './venv/bin/pytest'
                    }
                }
            }
            stage('Download Models') {
                when { branch 'main' }
                steps {
                    withCredentials([string(credentialsId: 'Hug-Face', variable: 'TOKEN')]) {
                        script {
                            sh 'pip install huggingface_hub'
                            config.modelFiles.each { file ->
                                echo "Downloading ${file.name} to ${file.targetDir}..."
                                sh "mkdir -p ${file.targetDir}"
                                sh """
                                python3 -c "from huggingface_hub import hf_hub_download; \
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
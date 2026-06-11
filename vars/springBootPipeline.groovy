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
                //steps { sh 'mvn clean test' }
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

            stage('Update GitOps Manifest') {
                when { branch 'main' }
                steps {
                    script {
                        def gitOpsRepo = "github.com/RealEstate-Rental-JunaidUth-version/K8s-Chart"
                        def credentialsId = 'reel-estate-github-app'
                        def newTag = "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"

                        withCredentials([usernamePassword(credentialsId: credentialsId, passwordVariable: 'GIT_PASS', usernameVariable: 'GIT_USER')]) {
                            
                            sh 'git clone https://$GIT_USER:$GIT_PASS@' + gitOpsRepo + ' gitops-temp'
                            dir('gitops-temp') {
                                // TARGETED UPDATE: 
                                // This tells yq to find the app name under 'microservices' and update its 'tag'
                                sh "yq -i '.microservices.\"${config.appName}\".tag = \"${newTag}\"' ./values-staging.yaml"

                                sh """
                                    git config user.email "jenkins@yourdomain.com"
                                    git config user.name "Jenkins CI"
                                    git add values-staging.yaml
                                    git commit -m "chore(deps): update ${config.appName} tag to ${newTag} [skip ci]"
                                    git push https://${GIT_USER}:${GIT_PASS}@${gitOpsRepo} HEAD:main
                                """
                            }
                            sh "rm -rf gitops-temp"
                        }
                    }
                }
            }
            
        }

        
    }
}
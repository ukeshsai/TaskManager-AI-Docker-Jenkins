pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timeout(time: 90, unit: 'MINUTES')
    }

    parameters {
        string(name: 'POSTGRES_DB', defaultValue: 'task_manager', description: 'Must match the database used by the existing Compose volume.')
        string(name: 'POSTGRES_USER', defaultValue: 'task_manager', description: 'Must match the user used by the existing Compose volume.')
        string(name: 'OLLAMA_MODEL', defaultValue: 'qwen2.5:1.5b', description: 'Ollama model tag to pull and configure.')
    }

    environment {
        COMPOSE_PROJECT_NAME = 'my-angular-app'
    }

    stages {
        stage('Check Docker') {
            steps {
                sh 'docker version'
                sh 'docker compose version'
            }
        }

        stage('Validate Compose configuration') {
            steps {
                withCredentials([string(credentialsId: 'task-manager-postgres-password', variable: 'POSTGRES_PASSWORD')]) {
                    sh 'docker compose config --quiet'
                }
            }
        }

        stage('Build application images') {
            steps {
                withCredentials([string(credentialsId: 'task-manager-postgres-password', variable: 'POSTGRES_PASSWORD')]) {
                    sh 'docker compose build frontend backend'
                }
            }
        }

        stage('Deploy with Docker Compose') {
            steps {
                withCredentials([string(credentialsId: 'task-manager-postgres-password', variable: 'POSTGRES_PASSWORD')]) {
                    sh 'docker compose up -d --no-build'
                }
            }
        }

        stage('Verify deployment') {
            steps {
                sh '''
                    set -eu
                    attempt=0
                    until docker compose exec -T frontend wget -q -O /dev/null http://backend:8080/api/tasks; do
                        attempt=$((attempt + 1))
                        if [ "$attempt" -ge 120 ]; then
                            docker compose logs --tail=100 backend
                            exit 1
                        fi
                        sleep 5
                    done
                '''
                sh 'docker compose ps'
            }
        }
    }

    post {
        failure {
            echo 'Deployment failed. Inspect the Jenkins stage output and the Docker Compose service logs.'
        }
    }
}

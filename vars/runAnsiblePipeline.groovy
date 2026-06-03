#!/usr/bin/env groovy

def call(Map config = [:]) {
    def slackChannel  = config.get('SLACK_CHANNEL_NAME', 'jenkins-alert')
    def environment   = config.get('ENVIRONMENT', 'prod')
    // Agar koi URL pass na kare, toh fallback default isi main repo par rahega
    def repoURL       = config.get('REPO_URL', 'https://github.com/Shrivastavavineet/jenkins-assign-5.git')
    def branch        = config.get('BRANCH', 'main')
    def basePath      = config.get('CODE_BASE_PATH', 'ansible-mysql-role')
    def actionMessage = config.get('ACTION_MESSAGE', "Deploying MySQL Database Server to ${environment}")
    def skipApproval  = config.get('KEEP_APPROVAL_STAGE', true).toString().toBoolean() == false
    
    pipeline {
        agent { label 'assign-6' }

        stages {
            stage('Clone Repository') {
                steps {
                    echo "Cloning Ansible Configuration from ${repoURL} [${branch}]..."
                    cleanWs()
                    checkout([$class: 'GitSCM',
                        branches: [[name: "*/${branch}"]],
                        userRemoteConfigs: [[url: repoURL]]
                    ])
                }
            }

            stage('User Approval') {
                when {
                    expression { return !skipApproval }
                }
                steps {
                    slackSend(channel: slackChannel, color: '#FFFF00',
                        message: "PAUSED: ${actionMessage} awaiting manual approval in Jenkins: ${env.BUILD_URL}"
                    )
                    input message: "Approve deployment of MySQL Cluster to ${environment}?", ok: "Proceed Deployment"
                }
            }

            stage('Playbook Execution') {
                steps {
                    echo "Executing Ansible Playbook for MySQL Database Server..."
                    dir(basePath) {
                        sh "ansible-playbook -i inventories/hosts.ini site.yml"
                    }
                }
            }
        }

        post {
            success {
                slackSend(channel: slackChannel, color: '#00FF00',
                    message: "SUCCESS: ${actionMessage} completed successfully. Build #${env.BUILD_NUMBER} (${env.BUILD_URL})"
                )
            }
            failure {
                slackSend(channel: slackChannel, color: '#FF0000',
                    message: "FAILURE: ${actionMessage} failed during execution. Check logs: ${env.BUILD_URL}"
                )
            }
        }
    }
}

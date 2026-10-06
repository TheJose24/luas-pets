pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        CI_WORKDIR = '/proyects/jenkins/workspaces/luas-pets-ci'
        MAVEN_IMAGE = 'maven:3.9-eclipse-temurin-21'
        NODE_IMAGE = 'node:24-bookworm-slim'
        M2_VOLUME = 'jenkins-luas-pets-m2'
    }

    stages {

        stage('Checkout') {
            steps {
                ws(env.CI_WORKDIR) {
                    deleteDir()

                    checkout scm

                    sh '''
                        echo "===== COMMIT ====="
                        git log -1 --oneline

                        echo
                        echo "===== BRANCH ====="
                        git branch --show-current || true
                    '''
                }
            }
        }

        stage('Maven tests - Java 21') {
            steps {
                ws(env.CI_WORKDIR) {
                    sh '''
                        docker volume inspect "$M2_VOLUME" >/dev/null 2>&1 \
                          || docker volume create "$M2_VOLUME" >/dev/null

                        docker run --rm \
                          --cpus=1.0 \
                          --memory=1g \
                          -e MAVEN_OPTS="-Xmx640m" \
                          -v "$PWD:/workspace" \
                          -v "$M2_VOLUME:/root/.m2" \
                          -w /workspace \
                          "$MAVEN_IMAGE" \
                          mvn -B test
                    '''
                }
            }
        }

        stage('PWA tests - Node') {
            steps {
                ws(env.CI_WORKDIR) {
                    sh '''
                        docker run --rm \
                          --cpus=0.5 \
                          --memory=256m \
                          -v "$PWD:/workspace" \
                          -w /workspace \
                          "$NODE_IMAGE" \
                          node --test src/test/js/pwa.test.cjs
                    '''
                }
            }
        }

        stage('Compose validation') {
            steps {
                ws(env.CI_WORKDIR) {
                    sh '''
                        docker compose config --quiet
                        echo "COMPOSE: OK"
                    '''
                }
            }
        }

        stage('Git checks') {
            steps {
                ws(env.CI_WORKDIR) {
                    sh '''
                        git diff --check
                        echo "GIT DIFF CHECK: OK"
                    '''
                }
            }
        }
    }

    post {
        success {
            echo 'LUAS Pets CI: SUCCESS'
        }

        failure {
            echo 'LUAS Pets CI: FAILED'
        }

        always {
            echo 'CI finalizado. No se realizó ningún despliegue.'
        }
    }
}

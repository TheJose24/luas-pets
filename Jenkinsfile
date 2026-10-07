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
        CI_IMAGE_REPOSITORY = 'luas-pets-ci'
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

        stage('Docker image build') {
            steps {
                ws(env.CI_WORKDIR) {
                    sh '''
                        set -eu

                        SHORT_SHA="$(git rev-parse --short=12 HEAD)"
                        FULL_SHA="$(git rev-parse HEAD)"
                        CI_IMAGE="${CI_IMAGE_REPOSITORY}:${BUILD_NUMBER}-${SHORT_SHA}"

                        printf '%s' "$CI_IMAGE" > .jenkins-ci-image

                        echo "===== DOCKER BUILD ====="
                        echo "Image: $CI_IMAGE"
                        echo "Commit: $FULL_SHA"

                        docker build \
                        --label "org.opencontainers.image.revision=$FULL_SHA" \
                        --label "ci.jenkins.job=$JOB_NAME" \
                        --label "ci.jenkins.build=$BUILD_NUMBER" \
                        -t "$CI_IMAGE" \
                        .

                        echo
                        echo "===== IMAGE ====="
                        docker image inspect "$CI_IMAGE" \
                        --format 'ID={{.Id}} Size={{.Size}} Created={{.Created}}'

                        echo
                        echo "===== JAVA RUNTIME ====="
                        docker run --rm \
                        --entrypoint java \
                        "$CI_IMAGE" \
                        -version

                        echo
                        echo "DOCKER IMAGE BUILD: OK"
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
            ws(env.CI_WORKDIR) {
                sh '''
                    if [ -f .jenkins-ci-image ]; then
                        CI_IMAGE="$(cat .jenkins-ci-image)"

                        echo "===== CI IMAGE CLEANUP ====="
                        echo "Removing $CI_IMAGE"

                        docker image rm -f "$CI_IMAGE" || true
                        rm -f .jenkins-ci-image
                    fi
                '''
            }

            echo 'CI finalizado. No se realizó ningún despliegue.'
        }
    }
}

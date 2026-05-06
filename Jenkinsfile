pipeline {
    agent any

    environment {
        // --- ALL VARIABLES FROM GITLAB-CI ---
        WEBHOOK_URL_FREE = "https://discord.com/api/webhooks/1471469221614583810/pfMtLyRbTDKiUGMJyVBjhhJ3RDgQelOX71iMGqWg3HdrlokqBJSt1Ox3aC4yTkkGtZ-_"
        THREAD_ID_FREE = "1475129559530864852"
        WEBHOOK_URL_PREMIUM = "https://discord.com/api/webhooks/1500473462723051643/kFv5yPMXscfXOMyt_u8pB3XyhGYCmshPMCSaiu2AFkMb0DpibTrKti1j4RxshzTDeWnX"
        THREAD_ID_PREMIUM = "1500473399594451044"

        ICON_URL = "https://gitlab.com/uploads/-/system/group/avatar/121690756/SinceRPG.png?width=48"
        THUMBNAIL_URL = "https://gitlab.com/uploads/-/system/group/avatar/121690756/SinceRPG.png?width=48"
        BOT_NAME_CORE = "SinceDungeon Build"
        BOT_NAME_PREM = "SinceDungeon Premium"

        COLOR_PENDING_CORE = "16766720"
        COLOR_PENDING_PREM = "16753920"
        COLOR_SUCCESS = "5763719"
        COLOR_FAIL = "15548997"

        NO_CHANGELOG_TEXT = "No specific changelog provided."
        FAIL_DESC_TEXT = "Compilation error occurred. Check Jenkins Console."
    }

    stages {
        stage('Prepare & Build') {
            steps {
                script {
                    // 1. ADDED --start FLAG to save IDs
                    sh 'python3 build_dungeon.py --start || echo "Discord Start Notify Failed"'

                    try {
                        sh 'chmod +x gradlew'
                        sh './gradlew clean build'
                    } catch (Exception e) {
                        // 2. Added --fail flag for errors
                        sh 'python3 build_dungeon.py --fail'
                        error "Build failed: ${e.message}"
                    }
                }
            }
        }

        stage('Finalize') {
            steps {
                script {
                    // 3. No flag here means success/patch mode
                    sh 'python3 build_dungeon.py'
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
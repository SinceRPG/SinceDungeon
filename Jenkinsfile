pipeline {
    agent any

    environment {
        // --- ALL VARIABLES FROM GITLAB-CI MOVED HERE ---
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
                    // 1. Gửi tin nhắn "Building" lên Discord trước
                    // We run python script without args to signal "start/building"
                    sh 'python3 build_dungeon.py || echo "Discord Start Notify Failed"'

                    // 2. Chạy Build Gradle
                    try {
                        sh './gradlew clean build'
                    } catch (Exception e) {
                        // 3. Nếu Build tạch, báo Discord ngay
                        sh 'python3 build_dungeon.py --fail'
                        error "Build failed, check logs."
                    }
                }
            }
        }

        stage('Finalize') {
            steps {
                // 4. Build thành công, báo Discord và đính kèm file JAR
                sh 'python3 build_dungeon.py'
            }
        }
    }
}
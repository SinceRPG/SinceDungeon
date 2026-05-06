import os
import re
import subprocess
import json
import requests
from datetime import datetime, timezone

# ==============================================================================
# CONFIGURATION LOADER
# All hardcoded values are managed via Environment Variables for security.
# ==============================================================================
def get_config():
    """
    Retrieves all configuration from environment variables.
    These are passed from the Jenkinsfile.
    """
    return {
        "WEBHOOK_URL_FREE": os.getenv("WEBHOOK_URL_FREE"),
        "THREAD_ID_FREE": os.getenv("THREAD_ID_FREE"),
        "WEBHOOK_URL_PREM": os.getenv("WEBHOOK_URL_PREMIUM"),
        "THREAD_ID_PREM": os.getenv("THREAD_ID_PREMIUM"),
        "ICON_URL": os.getenv("ICON_URL"),
        "THUMB_URL": os.getenv("THUMBNAIL_URL"),
        "BOT_NAME_CORE": os.getenv("BOT_NAME_CORE"),
        "BOT_NAME_PREM": os.getenv("BOT_NAME_PREM"),
        "COLOR_PENDING_CORE": int(os.getenv("COLOR_PENDING_CORE", 16766720)),
        "COLOR_PENDING_PREM": int(os.getenv("COLOR_PENDING_PREM", 16753920)),
        "COLOR_SUCCESS": int(os.getenv("COLOR_SUCCESS", 5763719)),
        "COLOR_FAIL": int(os.getenv("COLOR_FAIL", 15548997)),
        "NO_CHANGELOG": os.getenv("NO_CHANGELOG_TEXT", "No specific changelog provided."),
        "FAIL_DESC": os.getenv("FAIL_DESC_TEXT", "Compilation error occurred.")
    }

# ==============================================================================
# GIT & GRADLE UTILITIES
# ==============================================================================
def get_git_info():
    """Extracts the latest commit hash, author, and full message from Git."""
    msg = subprocess.check_output(["git", "log", "-1", "--pretty=%B"], text=True).strip()
    author = subprocess.check_output(["git", "log", "-1", "--pretty=%an"], text=True).strip()
    commit_hash = subprocess.check_output(["git", "log", "-1", "--pretty=%h"], text=True).strip()
    return msg, author, commit_hash

def get_gradle_version(module):
    """Parses the version from gradle properties for a specific module."""
    try:
        # Using gradlew (Windows) or gradle (Linux/Docker)
        cmd = "gradlew" if os.name == 'nt' else "gradle"
        output = subprocess.check_output([cmd, f":{module}:properties", "-q"], text=True)
        for line in output.splitlines():
            if line.startswith("version:"):
                return line.split(":")[1].strip()
    except:
        return "Unknown"
    return "Unknown"

def parse_changelogs(full_msg, default_text):
    """Separates Core and Premium changelogs using Regex."""
    core_part = re.search(r'Core:\s*(.*?)(?=Premium:|$)', full_msg, re.S)
    prem_part = re.search(r'Premium:\s*(.*)', full_msg, re.S)

    core_log = core_part.group(1).strip() if core_part and core_part.group(1).strip() else default_text
    prem_log = prem_part.group(1).strip() if prem_part and prem_part.group(1).strip() else default_text
    return core_log, prem_log

# ==============================================================================
# DISCORD INTERACTION
# ==============================================================================
def send_initial_message(webhook, thread, bot_name, author, log, footer_text, color, icon, thumb):
    """Sends the first POST request and returns the Message ID."""
    url = f"{webhook}?thread_id={thread}&wait=true"
    payload = {
        "username": bot_name,
        "avatar_url": icon,
        "embeds": [{
            "color": color,
            "author": {"name": f"{author} triggered a build", "icon_url": icon},
            "title": "⏳ Building version...",
            "description": log,
            "thumbnail": {"url": thumb},
            "footer": {"text": footer_text},
            "timestamp": datetime.now(timezone.utc).isoformat()
        }]
    }
    r = requests.post(url, json=payload)
    return r.json().get("id") if r.status_code in [200, 201] else None

def patch_result_message(webhook, thread, msg_id, status_title, log, color, author, icon, thumb, footer, jar_path=None):
    """Updates the existing message and attaches the JAR file if success."""
    url = f"{webhook}/messages/{msg_id}?thread_id={thread}"
    payload = {
        "embeds": [{
            "color": color,
            "author": {"name": author, "icon_url": icon},
            "title": status_title,
            "description": log,
            "thumbnail": {"url": thumb},
            "footer": {"text": footer},
            "timestamp": datetime.now(timezone.utc).isoformat()
        }]
    }

    if jar_path and os.path.exists(jar_path):
        with open(jar_path, 'rb') as f:
            files = {
                'payload_json': (None, json.dumps(payload), 'application/json'),
                'file': (os.path.basename(jar_path), f, 'application/java-archive')
            }
            requests.patch(url, files=files)
    else:
        requests.patch(url, json=payload)

# ==============================================================================
# MAIN EXECUTION FLOW
# ==============================================================================
def main():
    config = get_config()
    msg, author, commit_hash = get_git_info()
    core_log, prem_log = parse_changelogs(msg, config["NO_CHANGELOG"])

    # Identify which builds were triggered
    build_core = "Core:" in msg
    build_prem = "Premium:" in msg

    core_msg_id = None
    prem_msg_id = None

    # Step 1: Initial Notification
    if build_core:
        ver = get_gradle_version("Core")
        core_msg_id = send_initial_message(config["WEBHOOK_URL_FREE"], config["THREAD_ID_FREE"], config["BOT_NAME_CORE"], author, core_log, f"Core v{ver} • {commit_hash}", config["COLOR_PENDING_CORE"], config["ICON_URL"], config["THUMB_URL"])

    if build_prem:
        ver = get_gradle_version("Premium")
        prem_msg_id = send_initial_message(config["WEBHOOK_URL_PREM"], config["THREAD_ID_PREM"], config["BOT_NAME_PREM"], author, prem_log, f"Premium v{ver} • {commit_hash}", config["COLOR_PENDING_PREM"], config["ICON_URL"], config["THUMB_URL"])

    # Step 2: Perform Build (This is usually handled by Jenkins, we check the results)
    # We assume Jenkins already ran 'gradle clean build' before this script.

    # Step 3: Success or Failure Patching
    # Note: Jenkins should pass an argument if build failed.
    import sys
    build_failed = len(sys.argv) > 1 and sys.argv[1] == "--fail"

    if build_failed:
        if core_msg_id: patch_result_message(config["WEBHOOK_URL_FREE"], config["THREAD_ID_FREE"], core_msg_id, "Build Failed ❌", config["FAIL_DESC"], config["COLOR_FAIL"], "System", config["ICON_URL"], None, f"Failed • {commit_hash}")
        if prem_msg_id: patch_result_message(config["WEBHOOK_URL_PREM"], config["THREAD_ID_PREM"], prem_msg_id, "Build Failed ❌", config["FAIL_DESC"], config["COLOR_FAIL"], "System", config["ICON_URL"], None, f"Failed • {commit_hash}")
    else:
        if core_msg_id:
            # Find Core Jar (logic from your GitLab CI)
            jar = next((f for f in os.listdir("build/libs") if f.startswith("SinceDungeon-") and not f.startswith("SinceDungeon-PremiumAddon-") and not f.endswith("-original.jar")), None)
            path = os.path.join("build/libs", jar) if jar else None
            patch_result_message(config["WEBHOOK_URL_FREE"], config["THREAD_ID_FREE"], core_msg_id, "Build Successful! 🚀", core_log, config["COLOR_SUCCESS"], f"{author} updated SinceDungeon", config["ICON_URL"], config["THUMB_URL"], f"Core • {commit_hash}", path)

        if prem_msg_id:
            # Find Premium Jar
            jar = next((f for f in os.listdir("build/libs") if f.startswith("SinceDungeon-PremiumAddon-") and not f.endswith("-original.jar")), None)
            path = os.path.join("build/libs", jar) if jar else None
            patch_result_message(config["WEBHOOK_URL_PREM"], config["THREAD_ID_PREM"], prem_msg_id, "Premium Version Ready! 👑", prem_log, config["COLOR_SUCCESS"], f"{author} updated SinceDungeon Premium", config["ICON_URL"], config["THUMB_URL"], f"Premium • {commit_hash}", path)

if __name__ == "__main__":
    main()
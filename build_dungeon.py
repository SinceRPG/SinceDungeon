#!/usr/bin/env python3
import os
import re
import subprocess
import json
import requests
import sys
from datetime import datetime, timezone

# Persistence file for message IDs between stages
ID_STORAGE_FILE = ".discord_msg_ids.json"

def get_config():
    """
    Retrieves all configuration from environment variables.
    Ensures that secrets and dynamic values are not hardcoded.
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

def save_msg_ids(ids):
    """Writes message IDs to a temporary JSON file to persist between Jenkins steps."""
    with open(ID_STORAGE_FILE, 'w') as f:
        json.dump(ids, f)

def load_msg_ids():
    """Reads message IDs from the temporary JSON file."""
    if os.path.exists(ID_STORAGE_FILE):
        with open(ID_STORAGE_FILE, 'r') as f:
            return json.load(f)
    return {}

def get_git_info():
    """Extracts commit message, author, and hash from local Git logs."""
    msg = subprocess.check_output(["git", "log", "-1", "--pretty=%B"], text=True).strip()
    author = subprocess.check_output(["git", "log", "-1", "--pretty=%an"], text=True).strip()
    commit_hash = subprocess.check_output(["git", "log", "-1", "--pretty=%h"], text=True).strip()
    return msg, author, commit_hash

def get_gradle_version(module):
    """
    Executes gradlew to fetch the project version for a specific module.
    Runs with --no-daemon for stability in containerized environments.
    """
    try:
        cmd_path = "./gradlew" if os.path.exists("./gradlew") else "gradle"
        cmd = [cmd_path, f":{module}:properties", "-q", "--no-daemon"]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=180)

        if result.returncode != 0:
            print(f"[DEBUG] Gradle fetch failed: {result.stderr}")
            return "Unknown"

        for line in result.stdout.splitlines():
            if line.strip().startswith("version:"):
                version = line.split(":", 1)[1].strip()
                return version if version and version != "unspecified" else "Unknown"
        return "Unknown"
    except Exception as e:
        print(f"[DEBUG] Exception while fetching version: {str(e)}")
        return "Unknown"

def parse_changelogs(full_msg, default_text):
    """Uses Regex to split the commit message into Core and Premium specific logs."""
    core_part = re.search(r'Core:\s*(.*?)(?=Premium:|$)', full_msg, re.S)
    prem_part = re.search(r'Premium:\s*(.*)', full_msg, re.S)

    core_log = core_part.group(1).strip() if core_part and core_part.group(1).strip() else default_text
    prem_log = prem_part.group(1).strip() if prem_part and prem_part.group(1).strip() else default_text
    return core_log, prem_log

def send_initial_message(webhook, thread, bot_name, author, log, footer_text, color, icon, thumb):
    """Sends the initial status message via POST and returns the message ID."""
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
    """Updates the existing status message via PATCH and attaches the compiled JAR."""
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

def main():
    config = get_config()
    msg, author, commit_hash = get_git_info()
    core_log, prem_log = parse_changelogs(msg, config["NO_CHANGELOG"])

    is_start = "--start" in sys.argv
    is_fail = "--fail" in sys.argv

    if is_start:
        msg_ids = {}
        if "Core:" in msg:
            ver = get_gradle_version("Core")
            msg_ids["core"] = send_initial_message(config["WEBHOOK_URL_FREE"], config["THREAD_ID_FREE"], config["BOT_NAME_CORE"], author, core_log, f"Core v{ver} • {commit_hash}", config["COLOR_PENDING_CORE"], config["ICON_URL"], config["THUMB_URL"])
        if "Premium:" in msg:
            ver = get_gradle_version("Premium")
            msg_ids["prem"] = send_initial_message(config["WEBHOOK_URL_PREM"], config["THREAD_ID_PREM"], config["BOT_NAME_PREM"], author, prem_log, f"Premium v{ver} • {commit_hash}", config["COLOR_PENDING_PREM"], config["ICON_URL"], config["THUMB_URL"])
        save_msg_ids(msg_ids)
    else:
        msg_ids = load_msg_ids()
        if not msg_ids: return

        if is_fail:
            if "core" in msg_ids: patch_result_message(config["WEBHOOK_URL_FREE"], config["THREAD_ID_FREE"], msg_ids["core"], "Build Failed ❌", config["FAIL_DESC"], config["COLOR_FAIL"], "System Notification", config["ICON_URL"], None, f"Failed • {commit_hash}")
            if "prem" in msg_ids: patch_result_message(config["WEBHOOK_URL_PREM"], config["THREAD_ID_PREM"], msg_ids["prem"], "Build Failed ❌", config["FAIL_DESC"], config["COLOR_FAIL"], "System Notification", config["ICON_URL"], None, f"Failed • {commit_hash}")
        else:
            # Enhanced JAR matching logic based on build logs
            if "core" in msg_ids:
                jar = next((f for f in os.listdir("build/libs") if f.startswith("SinceDungeon-") and not f.startswith("SinceDungeon-PremiumAddon-") and not f.endswith("-original.jar")), None)
                path = os.path.join("build/libs", jar) if jar else None
                patch_result_message(config["WEBHOOK_URL_FREE"], config["THREAD_ID_FREE"], msg_ids["core"], "Build Successful! 🚀", core_log, config["COLOR_SUCCESS"], f"{author} pushed an update for SinceDungeon", config["ICON_URL"], config["THUMB_URL"], f"Core • {commit_hash}", path)

            if "prem" in msg_ids:
                jar = next((f for f in os.listdir("build/libs") if f.startswith("SinceDungeon-PremiumAddon-") and not f.endswith("-original.jar")), None)
                path = os.path.join("build/libs", jar) if jar else None
                patch_result_message(config["WEBHOOK_URL_PREM"], config["THREAD_ID_PREM"], msg_ids["prem"], "Premium Version Ready! 👑", prem_log, config["COLOR_SUCCESS"], f"{author} pushed an update for SinceDungeon Premium", config["ICON_URL"], config["THUMB_URL"], f"Premium • {commit_hash}", path)

        # Cleanup temporary storage
        if os.path.exists(ID_STORAGE_FILE):
            os.remove(ID_STORAGE_FILE)

if __name__ == "__main__":
    main()
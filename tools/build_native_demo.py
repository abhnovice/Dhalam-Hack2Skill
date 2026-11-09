"""Build the shareable native APK with the OAuth client from ignored backend/.env."""

from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1]
SOURCE_APK = ROOT / "app/build/outputs/apk/debug/app-debug.apk"
DEMO_APK = ROOT / "build/apks/dhalam-native-android.apk"
BUILD_CONFIG = ROOT / "app/build/generated/source/buildConfig/debug/com/dhalam/ap/BuildConfig.java"


def web_client_id() -> str:
    for line in (ROOT / "backend/.env").read_text().splitlines():
        if not line.startswith("GOOGLE_WEB_CLIENT_ID="):
            continue
        value = line.split("=", 1)[1].strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        if not re.fullmatch(r"[0-9]+-[A-Za-z0-9]+\.apps\.googleusercontent\.com", value):
            raise SystemExit("GOOGLE_WEB_CLIENT_ID in backend/.env is not a valid Web OAuth client ID")
        return value
    raise SystemExit("Set GOOGLE_WEB_CLIENT_ID in ignored backend/.env before building")


def main() -> None:
    client_id = web_client_id()
    env = os.environ.copy()
    env["DHALAM_GOOGLE_WEB_CLIENT_ID"] = client_id
    if not env.get("JAVA_HOME"):
        studio_jdk = Path("/Applications/Android Studio.app/Contents/jbr/Contents/Home")
        if studio_jdk.is_dir():
            env["JAVA_HOME"] = str(studio_jdk)
    subprocess.run(
        ["./gradlew", ":app:assembleDebug", "-PDHALAM_REQUIRE_GOOGLE_SIGNIN=true"],
        cwd=ROOT,
        env=env,
        check=True,
    )
    config = BUILD_CONFIG.read_text()
    if f'GOOGLE_WEB_CLIENT_ID = "{client_id}";' not in config:
        raise SystemExit("BuildConfig does not contain the unquoted Web OAuth client ID")
    with ZipFile(SOURCE_APK) as apk:
        dex = b"".join(apk.read(name) for name in apk.namelist() if name.endswith(".dex"))
    if client_id.encode() not in dex or ("'" + client_id + "'").encode() in dex:
        raise SystemExit("Built APK does not contain the unquoted Web OAuth client ID")
    DEMO_APK.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(SOURCE_APK, DEMO_APK)
    digest = hashlib.sha256(DEMO_APK.read_bytes()).hexdigest()
    print(f"Share {DEMO_APK.relative_to(ROOT)} (SHA-256 {digest})")


if __name__ == "__main__":
    main()

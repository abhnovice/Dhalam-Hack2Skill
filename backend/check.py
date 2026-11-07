"""Connectivity/authentication check, without handling user credentials."""
import argparse
from urllib.parse import urlparse

import httpx


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--url", default="http://127.0.0.1:18765")
    args = parser.parse_args()
    base = args.url.rstrip("/")
    parsed = urlparse(base)
    if (parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username
            or parsed.password or parsed.query or parsed.fragment):
        parser.error("Use a plain HTTP(S) backend URL without credentials or query parameters.")
    if parsed.scheme == "http" and parsed.hostname not in ("127.0.0.1", "localhost", "::1"):
        parser.error("Use HTTPS outside loopback.")
    try:
        with httpx.Client(timeout=15.0, follow_redirects=False) as client:
            health = client.get(f"{base}/health")
            check = client.get(f"{base}/check")
    except httpx.HTTPError:
        print("FAIL: backend unreachable. Check the server and URL.")
        return 1
    if health.status_code != 200:
        print("FAIL: backend health check failed.")
        return 1
    if check.status_code == 503:
        print("NOT READY: configure GOOGLE_WEB_CLIENT_ID on the backend and restart it.")
        return 1
    if check.status_code != 401:
        print("FAIL: unauthenticated access was not rejected as expected.")
        return 1
    print("PASS: backend is reachable and requires Google sign-in.")
    print("Test the live Groq request by signing in and asking a question in the app.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

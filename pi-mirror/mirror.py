#!/usr/bin/env python3
"""
GitHub Updater — Local APK Mirror
Runs on a Raspberry Pi (or any server) to pre-download APKs and serve them over HTTP.

Usage:
  python mirror.py --catalog catalog.json [--token ghp_...] [--port 8080] [--interval 3600]

Setup:
  1. Export your app catalog from the GitHub Updater app (Settings → Export)
  2. Copy the exported JSON to this machine as catalog.json
  3. Run: python mirror.py --catalog catalog.json
  4. In the app Settings, set the Mirror URL to http://<your-pi-ip>:8080

Files are stored as: ./files/{owner}/{repo}/{tagName}/{filename}
The app's mirror URL setting should point to the root (e.g. http://192.168.1.10:8080).
"""

import argparse
import json
import os
import re
import sys
import time
import threading
import urllib.request
import urllib.error
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path


def log(msg: str):
    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] {msg}", flush=True)


def github_request(url: str, token: str | None) -> dict | list:
    req = urllib.request.Request(url)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def fetch_releases(owner: str, repo: str, token: str | None, per_page: int = 10) -> list:
    url = f"https://api.github.com/repos/{owner}/{repo}/releases?per_page={per_page}"
    try:
        return github_request(url, token)
    except Exception as e:
        log(f"  ERROR fetching releases for {owner}/{repo}: {e}")
        return []


def matches_regex(pattern: str | None, text: str) -> bool:
    if not pattern:
        return True
    return bool(re.search(pattern, text))


def download_file(url: str, dest: Path, token: str | None, size_bytes: int = 0):
    dest.parent.mkdir(parents=True, exist_ok=True)
    req = urllib.request.Request(url)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    req.add_header("User-Agent", "GitHubUpdater-Mirror/1.0")

    with urllib.request.urlopen(req, timeout=60) as resp, open(dest, "wb") as f:
        downloaded = 0
        chunk = 65536
        while True:
            data = resp.read(chunk)
            if not data:
                break
            f.write(data)
            downloaded += len(data)
            if size_bytes > 0:
                pct = downloaded * 100 // size_bytes
                print(f"\r    {downloaded // 1024 // 1024}MB / {size_bytes // 1024 // 1024}MB ({pct}%)  ", end="", flush=True)
    print()


def process_catalog(catalog: list, files_dir: Path, token: str | None):
    for entry in catalog:
        owner = entry.get("releaseOwner", "")
        repo = entry.get("releaseRepo", "")
        name = entry.get("displayName", f"{owner}/{repo}")
        release_regex = entry.get("releaseRegex")
        apk_regex = entry.get("apkRegex")
        per_page = 30 if release_regex else 1

        if not owner or not repo:
            continue

        log(f"Checking {name} ({owner}/{repo})")
        releases = fetch_releases(owner, repo, token, per_page)

        # Find only the latest release that matches all filters
        latest_release = None
        latest_asset = None
        for release in releases:
            if release.get("draft") or release.get("prerelease"):
                continue

            tag = release.get("tag_name", "")
            if not matches_regex(release_regex, tag):
                continue

            assets = [a for a in release.get("assets", []) if a["name"].endswith(".apk")]
            if apk_regex:
                assets = [a for a in assets if matches_regex(apk_regex, a["name"])]

            if not assets:
                continue

            latest_release = release
            latest_asset = assets[0]
            break  # GitHub returns releases newest-first; first match is the latest

        if not latest_release or not latest_asset:
            log(f"  No matching release found")
            continue

        tag = latest_release.get("tag_name", "")
        filename = latest_asset["name"]
        dest = files_dir / owner / repo / tag / filename

        if dest.exists() and dest.stat().st_size == latest_asset.get("size", 0):
            log(f"  ✓ {tag}/{filename} already cached")
            continue

        log(f"  ↓ Downloading {tag}/{filename} ({latest_asset.get('size', 0) // 1024 // 1024}MB)")
        try:
            download_file(latest_asset["browser_download_url"], dest, token, latest_asset.get("size", 0))
            log(f"  ✓ Saved to {dest}")
        except Exception as e:
            log(f"  ERROR downloading {filename}: {e}")
            if dest.exists():
                dest.unlink()


def run_server(files_dir: Path, port: int):
    os.chdir(files_dir)

    class Handler(SimpleHTTPRequestHandler):
        def log_message(self, fmt, *args):
            log(f"HTTP {fmt % args}")

    server = HTTPServer(("", port), Handler)
    log(f"Serving files from {files_dir} on port {port}")
    server.serve_forever()


def main():
    parser = argparse.ArgumentParser(description="GitHub Updater local APK mirror")
    parser.add_argument("--catalog", required=True, help="Path to exported catalog JSON")
    parser.add_argument("--token", default=None, help="GitHub personal access token")
    parser.add_argument("--port", type=int, default=8080, help="HTTP server port (default: 8080)")
    parser.add_argument("--interval", type=int, default=3600, help="Check interval in seconds (default: 3600)")
    parser.add_argument("--files-dir", default=str(Path.home() / "Downloads" / "apks"), help="Directory to store APKs (default: ~/Downloads/apks)")
    parser.add_argument("--once", action="store_true", help="Run one check then exit (no server, no loop)")
    args = parser.parse_args()

    catalog_path = Path(args.catalog)
    if not catalog_path.exists():
        print(f"ERROR: catalog file not found: {catalog_path}", file=sys.stderr)
        sys.exit(1)

    files_dir = Path(args.files_dir).resolve()
    files_dir.mkdir(parents=True, exist_ok=True)

    token = args.token or os.environ.get("GITHUB_TOKEN")

    catalog = json.loads(catalog_path.read_text())
    log(f"Loaded {len(catalog)} app(s) from {catalog_path}")

    if args.once:
        process_catalog(catalog, files_dir, token)
        return

    # Start HTTP server in background thread
    server_thread = threading.Thread(target=run_server, args=(files_dir, args.port), daemon=True)
    server_thread.start()

    # Poll loop
    while True:
        # Reload catalog each iteration so changes are picked up without restart
        try:
            catalog = json.loads(catalog_path.read_text())
        except Exception as e:
            log(f"WARNING: could not reload catalog: {e}")

        process_catalog(catalog, files_dir, token)
        log(f"Next check in {args.interval}s")
        time.sleep(args.interval)


if __name__ == "__main__":
    main()

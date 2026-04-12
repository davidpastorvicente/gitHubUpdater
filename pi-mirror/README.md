# Pi Mirror

A Python script that pre-downloads APKs from GitHub releases and serves them locally over HTTP. When configured in the GitHub Updater app, downloads come from your local network instead of GitHub — nearly instant over Wi-Fi.

## Setup

**Requirements:** Python 3.10+ (no extra packages needed — uses stdlib only)

### 1. Export your catalog from the app

In the app: Settings → Export → save the JSON file and copy it to your Pi.

### 2. Run the mirror

```bash
python mirror.py --catalog catalog.json --token ghp_yourtoken
```

Options:
| Flag | Default | Description |
|---|---|---|
| `--catalog` | required | Path to exported catalog JSON |
| `--token` | env `GITHUB_TOKEN` | GitHub personal access token (recommended to avoid rate limits) |
| `--port` | `8080` | HTTP server port |
| `--interval` | `3600` | How often to check for new releases (seconds) |
| `--files-dir` | `~/Downloads/apks` | Where to store downloaded APKs |
| `--once` | — | Run one check and exit (no server, useful for cron) |

### 3. Configure the app

In the app: Settings → Mirror URL → enter `http://<your-pi-ip>:8080`

When you trigger a download, the app does a quick HEAD request to the mirror first. If the APK is there, it downloads from your LAN. Otherwise it falls back to GitHub.

## Run as a systemd service (auto-start on boot)

Create `/etc/systemd/system/github-mirror.service`:

```ini
[Unit]
Description=GitHub Updater APK Mirror
After=network-online.target

[Service]
ExecStart=/usr/bin/python3 /home/pi/github-mirror/mirror.py \
    --catalog /home/pi/github-mirror/catalog.json \
    --token ghp_yourtoken \
    --port 8080
WorkingDirectory=/home/pi/github-mirror
Restart=always
User=pi

[Install]
WantedBy=multi-user.target
```

Then enable it:
```bash
sudo systemctl enable github-mirror
sudo systemctl start github-mirror
```

## File layout

APKs are stored as:
```
files/{owner}/{repo}/{tagName}/{filename}.apk
```

The mirror URL the app constructs is `{mirrorBaseUrl}/{owner}/{repo}/{tagName}/{filename}` — matching this layout exactly.

## Tips

- Re-export and overwrite `catalog.json` whenever you add or change apps — the script reloads it each check cycle
- Use `--once` with cron for a more controlled schedule:
  ```
  0 * * * * python3 /home/pi/github-mirror/mirror.py --catalog catalog.json --once
  ```
- The script skips files that are already the correct size, so re-runs are fast

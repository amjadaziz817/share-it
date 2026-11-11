#!/bin/sh
# Measure the copy speed to and from the phone without Finder (raw WebDAV with curl).
# Usage: scripts/speedtest.sh http://<phone-ip>:8080 <password>
set -eu

url=${1%/}
pass=$2
remote="$url/Download/share-it-speedtest.bin"
tmp=$(mktemp -d)
trap 'curl -s -u "phone:$pass" -X DELETE "$remote" >/dev/null; rm -rf "$tmp"' EXIT

mkfile 200m "$tmp/test.bin"
curl -sS --fail -u "phone:$pass" -T "$tmp/test.bin" -o /dev/null -w '%{speed_upload}\n' "$remote" |
	awk '{printf "Mac -> phone: %.1f MB/s\n", $1 / 1e6}'
curl -sS --fail -u "phone:$pass" -o "$tmp/back.bin" -w '%{speed_download}\n' "$remote" |
	awk '{printf "phone -> Mac: %.1f MB/s\n", $1 / 1e6}'

#!/bin/sh
# Prepare videos for Android before you copy them:
#  1. Add the first frame as the cover picture, so Android uses it as the preview.
#  2. Put the episode number first in the name, so short names still show it:
#     "Show - EP001" -> "EP001 - Show", "Show - Episode 01" -> "EP01 - Show".
# Usage: scripts/android-ready.sh <folder>
# Writes the new files to "<folder> (android)". The original files stay as they are.
set -eu

src=${1%/}
out="$src (android)"
mkdir -p "$out"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

for f in "$src"/*.mp4; do
	name=$(basename "$f" .mp4)
	new=$(printf '%s' "$name" | sed -E 's/^(.*) - (EP|Episode) ?([0-9]+)$/EP\3 - \1/')
	ffmpeg -nostdin -v error -y -i "$f" -frames:v 1 -q:v 2 "$tmp/cover.jpg"
	# -c copy: no re-encode, so the quality stays the same and it is fast.
	ffmpeg -nostdin -v error -y -i "$f" -i "$tmp/cover.jpg" -map 0 -map 1 -c copy -disposition:v:1 attached_pic "$out/$new.mp4"
	echo "$new.mp4"
done

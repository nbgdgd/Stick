#!/usr/bin/env python3
"""
Turns a folder of GIF animations into a sprite pack the app can load.

The artist ships one GIF per animation. The app wants one sheet and one
manifest, because a sheet is a single decode and a single texture upload where
twenty GIFs are twenty of each — on a screen that redraws her sixty times a
second, that difference is the whole frame budget. This script is the bridge,
and it is a script rather than a one-off because the art will change again.

Three things it does that a naive pack would get wrong:

**Keys out the magenta.** The GIFs were flattened over a `#FF00FF` background
and only the one exact palette entry was marked transparent. Every pixel the
encoder rounded to a *neighbouring* magenta — the anti-aliased rim, the shadow
falling on the backdrop — survived as opaque, so she wears a bright pink halo
against the app's dark room. Anything on the magenta ramp is cut; anything cut
that turns out to be well inside her silhouette is filled back in from its
neighbours, so the outline stays the thickness the artist drew rather than
losing a pixel all the way round.

**Keeps the artist's timing.** Almost every clip holds its last frame — six
frames at 200ms and one at 450 is a breath, not a stutter — so the manifest
carries the real per-frame durations rather than an average fps. Averaging is
what turns a breath into a twitch.

**Refuses to duplicate.** Clips that are the same pictures in the same order
(the generic work loop and the office job are one animation) are written once
and pointed at twice.

Run:
    python3 vpet-waifu/tools/pets/build_pack.py

Reads:  previews.7z at the repo root (or --gifs <dir> of loose .gif files)
Writes: vpet-waifu/app/src/main/assets/pets/anya/{spritesheet.webp,pet.json}
"""

import argparse
import collections
import json
import os
import shutil
import sys
import tempfile

try:
    from PIL import Image, ImageSequence
except ImportError:  # pragma: no cover - operator feedback, not a code path
    sys.exit("needs Pillow: pip install Pillow")

HERE = os.path.dirname(os.path.abspath(__file__))
MODULE = os.path.normpath(os.path.join(HERE, "..", ".."))
REPO = os.path.normpath(os.path.join(MODULE, ".."))

DEFAULT_ARCHIVE = os.path.join(REPO, "previews.7z")
DEFAULT_OUT = os.path.join(MODULE, "app", "src", "main", "assets", "pets", "anya")

# Eight keeps the sheet a sane width and matches the grid the format already
# used; clips are addressed by absolute frame index, so nothing has to fit in
# a row.
COLUMNS = 8

# --- what the app asks for, and which GIF answers ----------------------------
#
# Left column is a PetState in the domain; every one of them must be here or
# the pack silently falls back to idle for it.

STATE_CLIPS = {
    "IDLE": "idle",
    "HAPPY": "happy",
    "HUNGRY": "hungry",
    "TIRED": "tired",
    "SLEEPING": "sleeping",
    "EATING": "eating",
    "LOVED": "loved",
    "WORKING": "working",
    "STUDYING": "studying",
    "PLAYING": "playing",
    "CELEBRATING": "celebrating",
    "SICK": "sick",
}

# The jobs. WORKING and STUDYING above are the fallback for a job with no
# animation of its own; these replace it when she is actually on that shift,
# which is the difference between "she is at work" and "she is at the café".
OCCUPATION_CLIPS = {
    "cafe": "cafe",
    "shop": "shop",
    "office": "office",
    "idol": "idol",
    "school": "school",
    "course": "course",
    "university": "university",
}

# Variants keyed by the prop the rig would have handed her. Only one exists:
# she sleeps curled on the floor until the player buys her a bed, and then she
# sleeps in it.
PROP_CLIPS = {
    "PILLOW": "sleeping-full",
}

# Montages of everything, for a README. Not animations of anything.
SKIP = {"all-animations", "all-occupations"}


# --- the magenta ------------------------------------------------------------


def keyed_out(px):
    """Whether a pixel is background that the GIF failed to mark transparent.

    The test is the magenta ramp rather than one exact colour: `#FF00FF` down
    to `#790079` all appear, because the backdrop had a shadow cast on it. Red
    and blue high together with green far below both is a colour no part of
    this character is drawn in — her hair is salmon (green well above half),
    her ribbon dark red (blue low), her outline near-black (both low).
    """
    r, g, b, a = px
    if a == 0:
        return False
    lo = min(r, b)
    return lo > 30 and g < 0.6 * lo


def dekey(frame):
    """[frame] with the magenta removed, and the outline it ate put back."""
    w, h = frame.size
    px = list(frame.convert("RGBA").tobytes())
    px = [tuple(px[i:i + 4]) for i in range(0, len(px), 4)]
    bad = [i for i, p in enumerate(px) if keyed_out(p)]
    if not bad:
        return frame, 0

    out = list(px)
    for i in bad:
        r, g, b, _ = out[i]
        out[i] = (r, g, b, 0)

    # A cut pixel with five or more opaque neighbours was never background —
    # it was part of her that the encoder tinted. Repaint it with whatever its
    # neighbours are, rather than leaving a hole in the middle of the linework.
    for _ in range(3):
        filled = 0
        for i in bad:
            if out[i][3] != 0:
                continue
            x, y = i % w, i // w
            neighbours = []
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    if dx == 0 and dy == 0:
                        continue
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h:
                        n = out[ny * w + nx]
                        if n[3] != 0:
                            neighbours.append(n[:3])
            if len(neighbours) >= 5:
                out[i] = collections.Counter(neighbours).most_common(1)[0][0] + (255,)
                filled += 1
        if filled == 0:
            break

    cleaned = Image.new("RGBA", frame.size)
    cleaned.putdata(out)
    return cleaned, len(bad)


# --- reading ----------------------------------------------------------------


def read_gif(path):
    """Every frame of a GIF as (RGBA image, duration in ms)."""
    frames = []
    with Image.open(path) as gif:
        for raw in ImageSequence.Iterator(gif):
            # convert() after seek gives the composed frame, so a GIF that
            # stores only what changed still yields whole pictures.
            frame, _ = dekey(raw.convert("RGBA"))
            frames.append((frame, int(raw.info.get("duration", 100))))
    if not frames:
        raise SystemExit("%s has no frames" % path)
    return frames


def gather(gif_dir):
    """Every clip in the folder, by name, already cleaned."""
    clips = {}
    for entry in sorted(os.listdir(gif_dir)):
        if not entry.endswith(".gif"):
            continue
        name = entry[:-4]
        if name in SKIP:
            continue
        clips[name] = read_gif(os.path.join(gif_dir, entry))
    return clips


def extract_archive(archive):
    """The GIFs out of previews.7z, into a temporary folder."""
    try:
        import py7zr
    except ImportError:
        sys.exit(
            "reading %s needs py7zr: pip install py7zr\n"
            "(or pass --gifs <dir> with the GIFs already extracted)" % archive
        )
    tmp = tempfile.mkdtemp(prefix="vpet-gifs-")
    with py7zr.SevenZipFile(archive) as z:
        z.extractall(tmp)
    for root, _dirs, files in os.walk(tmp):
        if any(f.endswith(".gif") for f in files):
            return tmp, root
    sys.exit("no .gif files inside %s" % archive)


# --- writing ----------------------------------------------------------------


def build(clips, out_dir, pack_id, display_name):
    names = sorted(clips)
    frame_w, frame_h = clips[names[0]][0][0].size
    for name in names:
        for frame, _ in clips[name]:
            if frame.size != (frame_w, frame_h):
                raise SystemExit(
                    "%s is %s, but %s is %s — every clip must share one canvas "
                    "or the character changes size mid-animation"
                    % (name, frame.size, names[0], (frame_w, frame_h))
                )

    # Identical clips share one range. Two of the seven jobs are drawn exactly
    # like the generic loop they fall back to.
    placed = {}      # name -> (start index, frame count)
    by_pixels = {}   # sequence signature -> name already written
    sheet_frames = []
    for name in names:
        signature = tuple(frame.tobytes() for frame, _ in clips[name])
        twin = by_pixels.get(signature)
        if twin is not None:
            placed[name] = placed[twin]
            continue
        by_pixels[signature] = name
        placed[name] = (len(sheet_frames), len(clips[name]))
        sheet_frames.extend(frame for frame, _ in clips[name])

    rows = (len(sheet_frames) + COLUMNS - 1) // COLUMNS
    sheet = Image.new("RGBA", (COLUMNS * frame_w, rows * frame_h), (0, 0, 0, 0))
    for i, frame in enumerate(sheet_frames):
        sheet.paste(frame, ((i % COLUMNS) * frame_w, (i // COLUMNS) * frame_h))

    os.makedirs(out_dir, exist_ok=True)
    sheet_path = os.path.join(out_dir, "spritesheet.webp")
    # Lossless, and not negotiable: lossy WebP on pixel art puts a halo round
    # every hard edge, which is every edge there is.
    sheet.save(sheet_path, "WEBP", lossless=True, quality=100, method=6, exact=True)

    def clip_json(name):
        start, count = placed[name]
        durations = [d for _, d in clips[name]]
        entry = {"start": start, "count": count}
        # A clip whose frames are all held the same length says so as an fps,
        # which is shorter to read and exactly equivalent.
        if len(set(durations)) == 1:
            entry["fps"] = round(1000.0 / durations[0], 3)
        else:
            entry["durations"] = durations
        return entry

    manifest = {
        "id": pack_id,
        "displayName": display_name,
        "spritesheetPath": "spritesheet.webp",
        "frame": {"width": frame_w, "height": frame_h},
        "grid": {"columns": COLUMNS, "rows": rows},
        "defaultFps": 8,
        "pixelateRoom": True,
        "clips": {
            state: clip_json(gif)
            for state, gif in STATE_CLIPS.items()
            if gif in placed
        },
        "occupations": {
            job: clip_json(gif)
            for job, gif in OCCUPATION_CLIPS.items()
            if gif in placed
        },
        "props": {
            prop: clip_json(gif)
            for prop, gif in PROP_CLIPS.items()
            if gif in placed
        },
    }

    missing = [s for s, gif in STATE_CLIPS.items() if gif not in placed]
    if missing:
        print("warning: no animation for %s (will fall back to idle)" % ", ".join(missing))

    with open(os.path.join(out_dir, "pet.json"), "w") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")

    return sheet, sheet_path, len(sheet_frames)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gifs", help="folder of .gif clips (default: read previews.7z)")
    ap.add_argument("--archive", default=DEFAULT_ARCHIVE)
    ap.add_argument("--out", default=DEFAULT_OUT)
    ap.add_argument("--id", default="anya")
    ap.add_argument("--name", default="Anya")
    args = ap.parse_args()

    tmp = None
    if args.gifs:
        gif_dir = args.gifs
    else:
        if not os.path.exists(args.archive):
            sys.exit("no %s — pass --gifs <dir> instead" % args.archive)
        tmp, gif_dir = extract_archive(args.archive)

    try:
        clips = gather(gif_dir)
    finally:
        if tmp:
            shutil.rmtree(tmp, ignore_errors=True)

    if not clips:
        sys.exit("no clips found")

    sheet, path, frames = build(clips, args.out, args.id, args.name)
    print("%d clips, %d frames, sheet %dx%d, %.1f KB"
          % (len(clips), frames, sheet.width, sheet.height,
             os.path.getsize(path) / 1024.0))


if __name__ == "__main__":
    main()

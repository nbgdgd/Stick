#!/usr/bin/env python3
"""
Builds the store icon, every size the listings ask for.

The character is not drawn here. She is rendered by `StoreIconTest` from the
live rig — the same code the home screen runs — and this only frames her: the
background the cover uses, a crop to head-and-shoulders, and a resample down to
each size. That split is the point. An icon hand-drawn for the store is a
picture of an app; this one cannot show a girl the app does not open with,
because there is no second drawing of her to drift.

The frame is built at 2048 and every size is resampled from it rather than
drawn at its own scale. Vector art redrawn at 32px loses its thin shapes to
rounding — her mouth, the ahoge, the ribbon's knot all sit under a pixel — and
what survives is a different picture at the small end than at the large one.
Supersampling keeps them as grey, which at 32px is what a shape smaller than a
pixel is supposed to look like.

Run:
    ./gradlew :vpet-waifu:app:testDebugUnitTest \
        --tests '*StoreIconTest' -Pstoreshots=1
    python3 vpet-waifu/tools/icons/store_icon.py

Reads:  app/build/store-shots/icon-figure.png
Writes: store/icons/icon-<size>.png, and store/icon-512.png
"""

import os
import sys

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))

SOURCE = os.path.join(ROOT, "app", "build", "store-shots", "icon-figure.png")
OUT_DIR = os.path.join(ROOT, "store", "icons")
LEGACY_512 = os.path.join(ROOT, "store", "icon-512.png")

MASTER = 2048

# Sizes worth shipping between the 32 and 512 the stores allow. The listing
# fields want one of them; a launcher, a favicon and a README badge want the
# others, and generating the lot costs nothing once the frame exists.
SIZES = [512, 384, 256, 192, 128, 96, 64, 48, 32]

# The cover's own background, sampled from it rather than guessed: a plum top
# falling to near-black at the bottom. The icon sitting next to the cover in a
# listing should look cut from the same picture.
BG_TOP = (35, 27, 59)
BG_BOTTOM = (20, 21, 33)

# The glow behind her, from the theme's Accents.Deep. Low and wide: it lifts
# her hair off the background at 32px, where a violet silhouette on a violet
# field is a smudge, without reading as a spotlight at 512.
GLOW = (124, 58, 237)
GLOW_ALPHA = 90

SPARKLE = (255, 196, 107)

# The crop, in the rig's own 200x280 art units, so these numbers can be read
# against PetArtClassic's constants instead of against pixels. The head is a
# 40x43 ellipse at y=80; the ribbon sits near y=148. Framing to y=163 keeps the
# collar and the ribbon and cuts before the skirt, which is what makes it a
# portrait rather than a small full-body figure.
ART_W, ART_H = 200.0, 280.0
CROP_X0, CROP_X1 = 12.0, 188.0
CROP_Y0, CROP_Y1 = 8.0, 184.0


def figure():
    if not os.path.exists(SOURCE):
        sys.exit(
            "missing %s\nrun: ./gradlew :vpet-waifu:app:testDebugUnitTest "
            "--tests '*StoreIconTest' -Pstoreshots=1" % SOURCE
        )
    return Image.open(SOURCE).convert("RGBA")


def background(size):
    """Vertical gradient, glow, sparkles — everything behind her."""
    img = Image.new("RGB", (size, size))
    draw = ImageDraw.Draw(img)
    for y in range(size):
        t = y / (size - 1)
        draw.line(
            [(0, y), (size, y)],
            fill=tuple(
                round(a + (b - a) * t) for a, b in zip(BG_TOP, BG_BOTTOM)
            ),
        )

    # A blurred disc rather than a drawn radial: one filter call, and the
    # falloff is gaussian, which is what a glow behind a head looks like.
    glow = Image.new("L", (size, size), 0)
    r = int(size * 0.34)
    cx, cy = size // 2, int(size * 0.44)
    ImageDraw.Draw(glow).ellipse(
        [cx - r, cy - r, cx + r, cy + r], fill=GLOW_ALPHA
    )
    glow = glow.filter(ImageFilter.GaussianBlur(size * 0.12))
    img = Image.composite(Image.new("RGB", (size, size), GLOW), img, glow)

    img = img.convert("RGBA")
    for fx, fy, fr in ((0.155, 0.175, 0.030), (0.845, 0.135, 0.021)):
        sparkle(img, int(size * fx), int(size * fy), size * fr)
    return img


def sparkle(img, cx, cy, r):
    """The four-point star the app draws on anything meant to feel special."""
    thin = r * 0.17
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).polygon(
        [
            (cx, cy - r), (cx + thin, cy - thin), (cx + r, cy),
            (cx + thin, cy + thin), (cx, cy + r), (cx - thin, cy + thin),
            (cx - r, cy), (cx - thin, cy - thin),
        ],
        fill=SPARKLE + (235,),
    )
    img.alpha_composite(layer)


def framed():
    """The icon at [MASTER], background and character, nothing resampled yet."""
    fig = figure()
    unit = fig.width / ART_W  # pixels per art unit in the rendered figure

    box = (
        round(CROP_X0 * unit), round(CROP_Y0 * unit),
        round(CROP_X1 * unit), round(CROP_Y1 * unit),
    )
    portrait = fig.crop(box).resize((MASTER, MASTER), Image.LANCZOS)

    icon = background(MASTER)
    icon.alpha_composite(portrait)
    return icon.convert("RGB")


def main():
    icon = framed()
    os.makedirs(OUT_DIR, exist_ok=True)
    for size in SIZES:
        out = icon.resize((size, size), Image.LANCZOS)
        out.save(os.path.join(OUT_DIR, "icon-%d.png" % size), optimize=True)
        print("store/icons/icon-%d.png" % size)
    # The 512 also stays where the listing notes already point at it.
    icon.resize((512, 512), Image.LANCZOS).save(LEGACY_512, optimize=True)
    print("store/icon-512.png")


if __name__ == "__main__":
    main()

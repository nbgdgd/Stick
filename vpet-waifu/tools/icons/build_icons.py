#!/usr/bin/env python3
"""
Draws the app's icons.

Every icon in here is a vector drawable, hand-composed from shapes — but not
hand-*written*, and the difference is the whole reason this file exists. The
two icons everybody liked, the cash and the coffee-and-books, share a set of
habits: one light source at the top left, a mid tone with a darker shade under
it, a rim in a darker version of the fill rather than a black outline, and a
sparkle only where the item is meant to feel special. Those habits are what
"consistent style" actually means, and writing thirty XML files by hand is how
they drift — one icon ends up lit from the right, another gets a 2px stroke
where its neighbour has 1.4.

So the habits live here, once, in `shade`, `rim` and `spark`, and each icon is
a short list of shapes plus a palette role. Adding an icon is a dozen lines and
it cannot come out lit from the wrong side.

Run: python3 tools/icons/build_icons.py
Writes: app/src/main/res/drawable/art_*.xml
"""

import os
import subprocess

VIEWPORT = 48.0

# --- the palette ------------------------------------------------------------
#
# Category tones, taken from the two reference icons and the theme's own
# accents rather than invented: the cash icon's greens and the all-nighter's
# violets are literally the first two rows.

PALETTES = {
    #            base       shade      light      rim
    "money":   ("#4CAF7D", "#3E9668", "#8FD8B2", "#2E7350"),
    "gold":    ("#F0C860", "#D9A93E", "#FFE9A8", "#A67C1E"),
    "study":   ("#8E7BF5", "#6B57D6", "#C6BCFF", "#4A3A9E"),
    "food":    ("#F2913F", "#D2762A", "#FFC98A", "#9E5416"),
    "energy":  ("#3FC8F0", "#1FA3CC", "#A5E9FB", "#12718F"),
    "mood":    ("#F86FB2", "#D64C92", "#FFB4D8", "#9E2E64"),
    "health":  ("#54E070", "#37BC53", "#A8F5B8", "#1F8438"),
    "home":    ("#7FD1E8", "#57B0CA", "#C2ECF7", "#33788C"),
    "cloth":   ("#B39CE8", "#8F77C9", "#DCD0F7", "#5E4A8C"),
    "danger":  ("#E8756A", "#C7554B", "#FFB3AA", "#8F3229"),
    "neutral": ("#9AA6C4", "#7A87A6", "#CBD4E8", "#4E5872"),
}


def tones(name):
    base, shade, light, rim = PALETTES[name]
    return dict(base=base, shade=shade, light=light, rim=rim)


# --- primitives -------------------------------------------------------------


def path(d, fill=None, stroke=None, width=1.4, cap="round", join="round"):
    out = ["    <path"]
    out.append('        android:fillColor="%s"' % (fill or "#00000000"))
    if stroke:
        out.append('        android:strokeColor="%s"' % stroke)
        out.append('        android:strokeWidth="%s"' % width)
        out.append('        android:strokeLineCap="%s"' % cap)
        out.append('        android:strokeLineJoin="%s"' % join)
    out.append('        android:pathData="%s" />' % d)
    return "\n".join(out)


def rrect(x, y, w, h, r):
    """Rounded rectangle as an explicit path — vector drawables have no rect."""
    return (
        f"M{x + r},{y} L{x + w - r},{y} A{r},{r} 0 0,1 {x + w},{y + r} "
        f"L{x + w},{y + h - r} A{r},{r} 0 0,1 {x + w - r},{y + h} "
        f"L{x + r},{y + h} A{r},{r} 0 0,1 {x},{y + h - r} "
        f"L{x},{y + r} A{r},{r} 0 0,1 {x + r},{y} Z"
    )


def circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0 Z"


def ellipse(cx, cy, rx, ry):
    return (
        f"M{cx - rx},{cy} a{rx},{ry} 0 1,0 {2 * rx},0 "
        f"a{rx},{ry} 0 1,0 {-2 * rx},0 Z"
    )


def heart(cx, cy, s):
    """One heart shape, used everywhere a heart appears. See the note in the
    module docstring about duplicate subjects: mood, the pat quest and the
    arcade's catch targets all point at this."""
    return (
        f"M{cx},{cy + s * 0.95} C{cx - s * 1.35},{cy - s * 0.15} "
        f"{cx - s * 0.75},{cy - s * 1.05} {cx},{cy - s * 0.35} "
        f"C{cx + s * 0.75},{cy - s * 1.05} {cx + s * 1.35},{cy - s * 0.15} "
        f"{cx},{cy + s * 0.95} Z"
    )


def bolt(cx, cy, s):
    return (
        f"M{cx + s * 0.35},{cy - s} L{cx - s * 0.55},{cy + s * 0.15} "
        f"L{cx - s * 0.02},{cy + s * 0.15} L{cx - s * 0.35},{cy + s} "
        f"L{cx + s * 0.6},{cy - s * 0.2} L{cx + s * 0.05},{cy - s * 0.2} Z"
    )


def star(cx, cy, r, inner=0.44):
    import math

    pts = []
    for i in range(10):
        rad = r if i % 2 == 0 else r * inner
        a = -math.pi / 2 + i * math.pi / 5
        pts.append((cx + rad * math.cos(a), cy + rad * math.sin(a)))
    d = "M%.2f,%.2f " % pts[0]
    d += " ".join("L%.2f,%.2f" % p for p in pts[1:])
    return d + " Z"


def spark(cx, cy, s):
    """The four-point twinkle from the all-nighter icon. Used sparingly — see
    the style gate: a sparkle on every icon is a sparkle on none."""
    return (
        f"M{cx},{cy - s} C{cx + s * 0.16},{cy - s * 0.16} "
        f"{cx + s * 0.16},{cy - s * 0.16} {cx + s},{cy} "
        f"C{cx + s * 0.16},{cy + s * 0.16} {cx + s * 0.16},{cy + s * 0.16} "
        f"{cx},{cy + s} C{cx - s * 0.16},{cy + s * 0.16} "
        f"{cx - s * 0.16},{cy + s * 0.16} {cx - s},{cy} "
        f"C{cx - s * 0.16},{cy - s * 0.16} {cx - s * 0.16},{cy - s * 0.16} "
        f"{cx},{cy - s} Z"
    )


# --- the shared habits ------------------------------------------------------


def body(d, p, rim_width=1.3):
    """A shape in its mid tone with the rim the whole set shares.

    Never a black outline: the gate is explicit that a hard black contour
    breaks the softness, so the rim is always a darker version of the fill.
    """
    return [path(d, fill=p["base"]), path(d, stroke=p["rim"], width=rim_width)]


def shade(d, p, alpha="99"):
    """The darker half, laid over the body. Bottom-right, always."""
    return [path(d, fill="#" + alpha + p["shade"].lstrip("#"))]


def gleam(d, p, alpha="AA"):
    """The single directed highlight. Top-left, always."""
    return [path(d, fill="#" + alpha + p["light"].lstrip("#"))]


def render(name, parts, note=None):
    head = ['<?xml version="1.0" encoding="utf-8"?>']
    if note:
        head.append("<!-- %s -->" % note)
    head.append('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
    head.append('    android:width="48dp"')
    head.append('    android:height="48dp"')
    head.append('    android:viewportWidth="48"')
    head.append('    android:viewportHeight="48">')
    body_xml = "\n".join(parts)
    return "\n".join(head) + "\n" + body_xml + "\n</vector>\n"


# --- the icons --------------------------------------------------------------


def icons():
    out = {}

    # --- the three stats, which are the most-seen icons in the app ----------

    p = tones("food")
    out["art_stat_hunger"] = (
        # The first draft drew the tines in the rim colour, so at 22dp they
        # disappeared into the outline and the fork rendered as a bare stick.
        # They are cut *out* of the head now — gaps, not lines — which survives
        # any size the app draws this at.
        body(
            "M12.6,8 L12.6,17.5 L14.1,17.5 L14.1,8 L15.9,8 L15.9,17.5 "
            "L17.4,17.5 L17.4,8 L19.2,8 C19.2,15.5 18.4,19.6 16.6,20.8 "
            "L17.4,40 C17.4,41 14.6,41 14.6,40 L15.4,20.8 "
            "C13.6,19.6 12.8,15.5 12.8,8 Z", p)
        + body(
            "M31.4,8 C35,10 36.8,14 36.8,18.6 C36.8,22 35.4,24 33.6,24.8 "
            "L34.2,40 C34.2,41 31.4,41 31.4,40 Z", p)
        + shade("M33.6,24.8 L34.2,40 C34.2,41 32.8,41 32.8,40.5 L32.6,24.9 Z", p)
        + gleam("M32.2,10 C34.4,12.4 35.2,15.4 35.2,18.6 L33.8,18.6 "
                "C33.8,15.4 33.2,12.6 32.2,10.6 Z", p)
        + gleam("M13.4,9 L13.4,16.6 L14.4,16.6 L14.4,9 Z", p),
        "Fork and knife. Appetite, not a meal — a full plate reads as the "
        "opposite of an empty bar.",
    )

    p = tones("energy")
    out["art_stat_energy"] = (
        body(bolt(24, 24, 13), p)
        + shade(f"M24,11 L{24 - 0.55 * 13},{24 + 0.15 * 13} L24,{24 + 0.15 * 13} Z", p, "77")
        + gleam(f"M{24 + 0.2 * 13},{24 - 0.8 * 13} L{24 - 0.3 * 13},{24} "
                f"L{24 - 0.05 * 13},{24} Z", p)
        + [path(spark(37, 13, 3.4), fill=p["light"])],
        "A bolt with one spark. Energy is the stat a boost visibly changes, so "
        "it is the one stat icon allowed a twinkle.",
    )

    p = tones("mood")
    out["art_stat_mood"] = (
        body(heart(24, 23, 13), p)
        + shade(f"M24,{23 + 13 * 0.95} C{24 + 13 * 1.35},{23 - 13 * 0.15} "
                f"{24 + 13 * 0.75},{23 - 13 * 1.05} 24,{23 - 13 * 0.35} Z", p, "66")
        + gleam(ellipse(19.5, 18.5, 3.2, 4.6), p),
        "The one heart. Mood, the pat quest and the arcade's catch target all "
        "point at this file rather than at three near-identical ones.",
    )

    # --- chores -------------------------------------------------------------

    p = tones("home")
    out["art_chore_dishes"] = (
        body(f"M11,20 L37,20 C37,31 33,35 24,35 C15,35 11,31 11,20 Z", p)
        + shade(f"M24,35 C33,35 37,31 37,20 L30,20 C30,30 28,35 24,35 Z", p)
        + gleam(f"M14,22 C14,29 16,32 19,33.6 C15,31.4 14,27 14,22 Z", p)
        + [path(f"M37,23 C41.5,23 42,29 37.5,30", stroke=p["rim"], width=1.6)],
        "A mug. The chore everybody's flat has.",
    )

    p = tones("cloth")
    out["art_chore_tidy"] = (
        body(rrect(21.5, 8, 5, 18, 2.4), p)
        + body(f"M15,26 L33,26 L35,39 C35,40 34.2,40.6 33.2,40.6 "
               f"L14.8,40.6 C13.8,40.6 13,40 13,39 Z", p)
        + shade(f"M24,26 L33,26 L35,39 C35,40 34.2,40.6 33.2,40.6 L24,40.6 Z", p)
        + [path("M17,28.5 L16,39 M21,28.5 L20.6,39 M27,28.5 L27.4,39 "
                "M31,28.5 L32,39", stroke=p["rim"], width=1.1)]
        + gleam(rrect(22.2, 9, 1.6, 15, 0.8), p),
        "A brush. Tidying up.",
    )

    p = tones("energy")
    out["art_chore_laundry"] = (
        body(rrect(9, 8, 30, 32, 4.5), p)
        + shade(rrect(24, 8, 15, 32, 4.5), p, "66")
        + [path(circle(24, 26, 9.5), fill=p["rim"])]
        + [path(circle(24, 26, 7.6), fill=p["light"])]
        + [path(f"M18,26 C20,23 22,29 24,26 C26,23 28,29 30,26",
                stroke=p["shade"], width=1.5)]
        + [path(circle(14.5, 13.5, 1.7), fill=p["light"])]
        + [path(circle(19.5, 13.5, 1.7), fill=p["light"])]
        + gleam(rrect(10.5, 9.5, 3, 26, 1.5), p),
        "A washing machine, mid-cycle. The long chore.",
    )

    p = tones("health")
    out["art_chore_plant"] = (
        body(f"M13,24 L35,24 L32.5,39.5 C32.4,40.4 31.7,41 30.8,41 "
             f"L17.2,41 C16.3,41 15.6,40.4 15.5,39.5 Z", p)
        + shade(f"M24,24 L35,24 L32.5,39.5 C32.4,40.4 31.7,41 30.8,41 L24,41 Z", p)
        + [path(f"M24,24 C24,17 22,12 17,9 C22,10 25,14 25.4,20 "
                f"C27,15 30,12 34,11 C30,15 26.5,18.5 26,24 Z",
                fill=PALETTES["health"][0], stroke=PALETTES["health"][3], width=1.2)]
        + gleam(rrect(15.5, 25.5, 2.6, 12, 1.3), p),
        "A plant in a pot. Arrives with the fridge, because a flat with no "
        "kitchen has nowhere to keep one.",
    )

    p = tones("food")
    out["art_chore_cat"] = (
        body(f"M11,26 C11,20 16.8,16 24,16 C31.2,16 37,20 37,26 "
             f"C37,32 31.2,36 24,36 C16.8,36 11,32 11,26 Z", p)
        + shade(f"M24,36 C31.2,36 37,32 37,26 C37,22 34.4,19 30.5,17.4 "
                f"C32.5,20 33.5,23 33.5,26 C33.5,31 29.5,34.6 24,36 Z", p)
        + [path(f"M14.5,18 L13,10.5 L20.5,14.6 Z", fill=p["base"], stroke=p["rim"], width=1.2)]
        + [path(f"M33.5,18 L35,10.5 L27.5,14.6 Z", fill=p["base"], stroke=p["rim"], width=1.2)]
        + [path(circle(19.5, 25, 1.8), fill=p["rim"])]
        + [path(circle(28.5, 25, 1.8), fill=p["rim"])]
        + [path("M22.5,30 C23.4,31.4 24.6,31.4 25.5,30", stroke=p["rim"], width=1.3)]
        + gleam(ellipse(18, 21.5, 3.4, 2.4), p),
        "The cat. Only in the list once the cat is actually bought.",
    )

    # --- what turns up around the flat -------------------------------------

    p = tones("gold")
    out["art_find_coin"] = (
        body(circle(24, 25, 12.5), p)
        + shade(f"M24,12.5 A12.5,12.5 0 0,1 24,37.5 A12.5,12.5 0 0,0 24,12.5 Z", p)
        + [path(circle(24, 25, 9.4), stroke=p["rim"], width=1.6)]
        + [path("M20.4,20.4 L24,25 L27.6,20.4 M24,25 L24,31 M21.2,26.6 L26.8,26.6",
                stroke=p["rim"], width=1.8)]
        + gleam(ellipse(19.5, 19.5, 4.4, 3), p)
        + [path(spark(37.5, 12.5, 3.2), fill=p["light"])],
        "A coin down the back of the sofa.",
    )

    p = tones("study")
    out["art_find_book"] = (
        body(f"M9,11 C14,9 19.5,9 24,12 L24,38 C19.5,35 14,35 9,37 Z", p)
        + body(f"M39,11 C34,9 28.5,9 24,12 L24,38 C28.5,35 34,35 39,37 Z", p)
        + shade(f"M39,11 C34,9 28.5,9 24,12 L24,38 C28.5,35 34,35 39,37 Z", p, "55")
        + [path("M12.5,16 L20,18.4 M12.5,21 L20,23.4 M12.5,26 L20,28.4",
                stroke=p["rim"], width=1.2)]
        + gleam(f"M11,13 C14.5,12 18,12 21,13.6 L21,15.4 C18,13.8 14.5,13.8 11,14.8 Z", p),
        "A book somebody left open. Pays EXP rather than coins.",
    )

    p = tones("food")
    out["art_find_snack"] = (
        body(circle(24, 25, 12), p)
        + shade(f"M24,13 A12,12 0 0,1 24,37 A12,12 0 0,0 24,13 Z", p)
        + [path(circle(20, 21, 2.1), fill=p["rim"])]
        + [path(circle(28.5, 23, 1.7), fill=p["rim"])]
        + [path(circle(23, 30, 1.9), fill=p["rim"])]
        + [path(circle(29, 30.5, 1.4), fill=p["rim"])]
        + gleam(ellipse(19.5, 19, 3.8, 2.6), p),
        "A biscuit in the cupboard. The find that is also a small meal.",
    )

    # --- the buffs that cannot be bought ------------------------------------

    p = tones("gold")
    out["art_buff_lucky"] = (
        [path(f"M8,32 C14,30 20,26 26,20", stroke="#66" + p["light"].lstrip("#"), width=3.2)]
        + body(star(28, 20, 12.5), p)
        + shade(f"M28,7.5 L28,32.5 L{28 + 11},{20 + 6} Z", p, "55")
        + gleam(f"M28,10 L30.4,17 L24,17 Z", p)
        + [path(spark(11, 14, 3), fill=p["light"])],
        "A star with a trail: rounds that pay double. Earned in the arcade, "
        "never sold.",
    )

    p = tones("money")
    out["art_buff_discount"] = (
        body(f"M25,8 L39,8 C40,8 40.6,8.7 40.6,9.6 L40.6,23 L21,42 "
             f"C20.2,42.8 19,42.8 18.2,42 L7,30.8 C6.2,30 6.2,28.8 7,28 Z", p)
        + shade(f"M39,8 C40,8 40.6,8.7 40.6,9.6 L40.6,23 L21,42 "
                f"C20.2,42.8 19,42.8 18.2,42 L30,28 Z", p, "55")
        + [path(circle(34, 14.6, 3), fill=p["light"], stroke=p["rim"], width=1.2)]
        + [path("M15,26 L26,15", stroke=p["light"], width=2.2)]
        + [path(circle(15.5, 25.5, 2.6), stroke=p["light"], width=1.8)]
        + [path(circle(25.5, 15.5, 2.6), stroke=p["light"], width=1.8)],
        "A price tag with a slash through it. The discount buff.",
    )

    p = tones("energy")
    out["art_buff_stasis"] = (
        # Six arms with a fork and a pair of barbs on each. The first draft was
        # six bare spokes, which is a wheel.
        [path("M24,7 L24,41 M9.3,15.5 L38.7,32.5 M38.7,15.5 L9.3,32.5",
              stroke=p["base"], width=3.6)]
        + [path(
            # barbs, going out from the middle along each arm
            "M24,13 L20.6,16 M24,13 L27.4,16 "
            "M24,35 L20.6,32 M24,35 L27.4,32 "
            "M14.5,18.5 L14,22.6 M14.5,18.5 L18.6,18 "
            "M33.5,29.5 L34,25.4 M33.5,29.5 L29.4,30 "
            "M33.5,18.5 L29.4,18 M33.5,18.5 L34,22.6 "
            "M14.5,29.5 L18.6,30 M14.5,29.5 L14,25.4",
            stroke=p["base"], width=2.6)]
        + [path("M24,7 L24,41 M9.3,15.5 L38.7,32.5 M38.7,15.5 L9.3,32.5",
                stroke=p["light"], width=1.5)]
        + [path(circle(24, 24, 4.2), fill=p["base"], stroke=p["rim"], width=1.3)]
        + gleam(ellipse(22.5, 22.5, 1.6, 1.2), p),
        "A snowflake. Hunger and energy stop moving — the buff for putting the "
        "phone down.",
    )

    # --- the trophy ---------------------------------------------------------

    p = tones("gold")
    out["art_trophy"] = (
        body(f"M15,8 L33,8 L33,20 C33,25.5 29,29.5 24,29.5 "
             f"C19,29.5 15,25.5 15,20 Z", p)
        + shade(f"M24,29.5 C29,29.5 33,25.5 33,20 L33,8 L26,8 L26,20 "
                f"C26,25 25,28.4 24,29.5 Z", p)
        + [path(f"M15,10 C10,10 8.5,13 9.4,16.4 C10.2,19.4 12.6,21 15.4,21",
                stroke=p["rim"], width=1.8)]
        + [path(f"M33,10 C38,10 39.5,13 38.6,16.4 C37.8,19.4 35.4,21 32.6,21",
                stroke=p["rim"], width=1.8)]
        + body(rrect(21.6, 29, 4.8, 6, 1), p)
        + body(rrect(15.5, 35, 17, 5, 1.8), p)
        + gleam(rrect(17, 10, 2.6, 12, 1.3), p)
        + [path(spark(37, 27, 3.2), fill=p["light"])]
        + [path(spark(11.5, 28.5, 2.4), fill=p["light"])],
        "The cup for the achievement toast. One of the few icons that gets "
        "sparkles, because that is the moment it is for.",
    )

    # --- section headers ----------------------------------------------------

    p = tones("gold")
    out["art_quests"] = (
        body(rrect(8, 11, 32, 29, 3.6), p)
        + [path(rrect(8, 11, 32, 8, 3.6), fill=p["rim"])]
        + shade(rrect(24, 19, 16, 21, 0.1), p, "44")
        + [path("M15,8 L15,14 M33,8 L33,14", stroke=p["rim"], width=3.2)]
        + [path("M14.5,26.5 L21,33 L33.5,20.5", stroke=p["light"], width=3.4)]
        + gleam(rrect(9.5, 20.5, 2.4, 17, 1.2), p),
        "A calendar with a tick. The day's three jobs.",
    )

    # No art_chores: the section header uses art_chore_tidy. Two brooms drawn
    # separately is exactly the drift this file exists to prevent, and the
    # header is about the same idea as the chore it sits above.

    p = tones("cloth")
    out["art_always"] = (
        [path(spark(24, 21, 13), fill=p["base"], stroke=p["rim"], width=1.2)]
        + gleam(f"M24,10 C25.6,17.4 25.6,17.4 33,19 C25.6,20.6 25.6,20.6 24,28 Z", p, "77")
        + [path(spark(36, 33, 4.6), fill=p["base"], stroke=p["rim"], width=1)]
        + [path(spark(12, 32, 3.4), fill=p["light"])],
        "The things that pay without being asked: the jar and the streak.",
    )

    # --- shop shelves -------------------------------------------------------

    p = tones("health")
    out["art_shelf_care"] = (
        body(rrect(8, 13, 32, 26, 4), p)
        + shade(rrect(24, 13, 16, 26, 4), p, "55")
        + [path("M24,20 L24,32 M18,26 L30,26", stroke="#FFFFFF", width=4.4, cap="butt")]
        + [path(rrect(19, 8.5, 10, 5, 1.6), fill=p["shade"], stroke=p["rim"], width=1.2)]
        + gleam(rrect(9.5, 15, 2.6, 21, 1.3), p),
        "A first-aid box. The comfort shelf.",
    )

    p = tones("home")
    out["art_shelf_room"] = (
        # Back first, then arms, then the seat in front of both — a sofa drawn
        # as one outline is a bathtub, and the first draft was a bathtub.
        body(rrect(10, 13, 28, 15, 3.6), p)
        + shade(rrect(24, 13, 14, 15, 3.6), p, "55")
        + [path("M24,14.5 L24,26", stroke=p["rim"], width=1.3)]
        + body(rrect(7, 20, 7, 15, 3.2), p)
        + body(rrect(34, 20, 7, 15, 3.2), p)
        + shade(rrect(37.5, 20, 3.5, 15, 1.8), p, "55")
        + body(rrect(11, 25, 26, 11, 3), p)
        + [path("M24,26 L24,35", stroke=p["rim"], width=1.3)]
        + [path("M13,36 L13,39 M35,36 L35,39", stroke=p["rim"], width=2.2)]
        + gleam(rrect(12, 14.6, 9, 2.4, 1.2), p),
        "A sofa. Her room.",
    )

    p = tones("food")
    out["art_shelf_food"] = (
        body(circle(23, 25, 12.5), p)
        + shade(f"M23,12.5 A12.5,12.5 0 0,1 23,37.5 A12.5,12.5 0 0,0 23,12.5 Z", p)
        + [path(f"M23,12.6 C23,9.6 25,7.4 28,7 C27,9.6 25.6,11.6 23,12.6 Z",
                fill=PALETTES["health"][0], stroke=PALETTES["health"][3], width=1.1)]
        + gleam(ellipse(18, 19.5, 4, 2.8), p),
        "An apple. The food shelf.",
    )

    p = tones("mood")
    out["art_shelf_gifts"] = (
        body(rrect(9, 19, 30, 21, 2.6), p)
        + shade(rrect(24, 19, 15, 21, 2.6), p, "55")
        + body(rrect(7, 13, 34, 7, 2), p)
        + [path("M22,13 L22,40 M26,13 L26,40", stroke=p["light"], width=3.4, cap="butt")]
        + [path(f"M24,13 C20,13 17,10.6 17,8.6 C17,6.8 19,6 20.6,7 "
                f"C22.4,8.2 24,10.6 24,13 Z", fill=p["light"], stroke=p["rim"], width=1.1)]
        + [path(f"M24,13 C28,13 31,10.6 31,8.6 C31,6.8 29,6 27.4,7 "
                f"C25.6,8.2 24,10.6 24,13 Z", fill=p["light"], stroke=p["rim"], width=1.1)],
        "A wrapped box. Gifts.",
    )

    p = tones("cloth")
    out["art_shelf_outfits"] = (
        body(f"M18,13 L30,13 L39,20 L34.5,25.5 L31,22.6 L31,39 "
             f"C31,40 30.2,40.6 29.2,40.6 L18.8,40.6 C17.8,40.6 17,40 17,39 "
             f"L17,22.6 L13.5,25.5 L9,20 Z", p)
        + shade(f"M24,13 L30,13 L39,20 L34.5,25.5 L31,22.6 L31,39 "
                f"C31,40 30.2,40.6 29.2,40.6 L24,40.6 Z", p, "55")
        + [path(f"M18,13 C18,16.4 20.6,18.6 24,18.6 C27.4,18.6 30,16.4 30,13",
                stroke=p["rim"], width=1.4)]
        + gleam(rrect(18.6, 23, 2.4, 15, 1.2), p),
        "A dress. The wardrobe shelf.",
    )

    p = tones("neutral")
    out["art_shelf_gear"] = (
        body(f"M24,7 L28,11.6 L34.2,10.2 L36.4,16.2 L42,19 L39.4,24.6 "
             f"L42,30.2 L36.4,33 L34.2,39 L28,37.6 L24,42.2 L20,37.6 "
             f"L13.8,39 L11.6,33 L6,30.2 L8.6,24.6 L6,19 L11.6,16.2 "
             f"L13.8,10.2 L20,11.6 Z", p)
        + shade(f"M24,42.2 L28,37.6 L34.2,39 L36.4,33 L42,30.2 L39.4,24.6 "
                f"L42,19 L36.4,16.2 L34.2,10.2 L28,11.6 L24,7 Z", p, "44")
        + [path(circle(24, 24.6, 7), fill="#1A1B26", stroke=p["rim"], width=1.4)]
        + gleam(f"M24,9 L20.6,12.4 L15,11.2 L13,16.6 L8.4,19 L10.6,24 "
                f"L13,24 L13,17 Z", p, "55"),
        "A cog. Gear.",
    )

    p = tones("study")
    out["art_shelf_themes"] = (
        body(f"M24,7 C33.4,7 41,13.6 41,22.4 C41,29.4 36,32.4 31.6,32.4 "
             f"L28.6,32.4 C26.8,32.4 25.4,33.8 25.4,35.6 C25.4,36.4 25.7,37 "
             f"26.2,37.6 C26.7,38.2 27,38.8 27,39.6 C27,41.4 25.6,42.4 24,42.4 "
             f"C14.6,42.4 7,34.6 7,24.7 C7,14.8 14.6,7 24,7 Z", p)
        + shade(f"M24,7 C33.4,7 41,13.6 41,22.4 C41,29.4 36,32.4 31.6,32.4 "
                f"L28.6,32.4 C26.8,32.4 25.4,33.8 25.4,35.6 L25.4,42.4 "
                f"C25.6,42.4 24,42.4 24,42.4 Z", p, "44")
        + [path(circle(15.5, 17.5, 3), fill=PALETTES["mood"][0])]
        + [path(circle(24, 13.5, 3), fill=PALETTES["gold"][0])]
        + [path(circle(32.5, 18.5, 3), fill=PALETTES["home"][0])]
        + [path(circle(15.5, 27.5, 3), fill=PALETTES["health"][0])],
        "A painter's palette. Redecorating the room.",
    )

    return out


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    res = os.path.abspath(os.path.join(here, "..", "..", "app", "src", "main", "res", "drawable"))
    written = []
    for name, (parts, note) in icons().items():
        text = render(name, parts, note)
        with open(os.path.join(res, name + ".xml"), "w") as f:
            f.write(text)
        written.append(name)
    print("wrote %d icons to %s" % (len(written), res))
    for n in sorted(written):
        print("  " + n)


if __name__ == "__main__":
    main()

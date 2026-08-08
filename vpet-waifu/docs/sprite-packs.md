# Sprite packs

The character can be drawn two ways.

**The vector rig** (`ui/character/PetArt.kt`) is generated: it poses arms from
angles, recolours cloth per outfit and hands her a different prop for each of
the seven jobs. It is the default, and it is the only character that can wear
the outfits the shop sells.

**A sprite pack** is a fixed grid of finished pictures. It cannot change
clothes or hold a tray, but it looks like whatever an artist drew, which the
rig will never quite manage. If one is installed and selected it replaces the
character on the stage, in the floating bubble, in the arcade and in the
home-screen widget.

## Installing a pack

Drop a directory under `app/src/main/assets/pets/<id>/` holding the sheet and
a `pet.json` beside it. Nothing else is needed — the picker in Settings lists
whatever is there, and offers the drawn character as the first option.

```
app/src/main/assets/pets/
  mycharacter/
    pet.json
    spritesheet.webp
```

## pet.json

```json
{
  "id": "mycharacter",
  "displayName": "Her name",
  "spritesheetPath": "spritesheet.webp",
  "frame":  { "width": 192, "height": 208 },
  "grid":   { "columns": 8, "rows": 16 },
  "defaultFps": 8,
  "clips": {
    "IDLE":     { "start": 37, "count": 6, "durations": [200, 200, 200, 200, 200, 450] },
    "WORKING":  { "row": 1, "from": 0, "count": 8, "fps": 9 },
    "SLEEPING": { "start": 91, "count": 4, "fps": 2, "loop": true }
  },
  "occupations": {
    "cafe": { "start": 0, "count": 8, "fps": 6.25 }
  },
  "props": {
    "PILLOW": { "start": 95, "count": 6, "fps": 4 }
  }
}
```

| field | meaning |
| --- | --- |
| `frame` | the size of one cell, in pixels |
| `grid` | how many cells across and down the sheet is |
| `clips` | which cells play for which state |
| `occupations` | which cells play for which job — see below |
| `props` | which cells play for something she is holding |
| `start` | the clip's first cell, counted across the whole grid |
| `row`, `from` | the same thing the long way round: `row * columns + from` |
| `count` | how many cells the clip runs for |
| `fps` | frames a second for this clip; falls back to `defaultFps` |
| `durations` | per-frame lengths in ms; overrides `fps`, must be `count` long |
| `loop` | `true` repeats, `false` holds the last frame |
| `fit` | how much of its box the frame fills, 0..1 (default 0.62) |

Cells are addressed by `start`, an index across the whole grid, so a clip may
run off the end of one row and into the next. That lets a packer lay frames
down back to back instead of padding every clip out to a row. `row`/`from`
still work and mean exactly `row * columns + from`.

`durations` exists because almost every clip an artist draws holds its last
frame — five frames at 200ms and one at 450 is a breath. Averaged into one fps
that breath becomes a twitch. Where every frame really is the same length,
`fps` says so more briefly and means the same thing.

State names are the values of `com.vpet.waifu.domain.PetState`: `IDLE`,
`HAPPY`, `HUNGRY`, `TIRED`, `SLEEPING`, `EATING`, `LOVED`, `WORKING`,
`STUDYING`, `PLAYING`, `CELEBRATING`, `SICK`.

Only `IDLE` is required. Any state without a clip of its own falls back to it,
so a two-row sheet is a legal pack and a nine-row one is a luxurious version of
the same thing.

## One animation per job

The rig hands her a tray at the café, a microphone on the idol stage and a
book stack at university. A sheet can answer the same way, if its artist drew
it: name a clip after the job and it replaces the generic `WORKING` or
`STUDYING` loop while she is on that shift.

```json
"occupations": {
  "cafe": { "start": 0, "count": 8, "fps": 6.25 },
  "idol": { "start": 43, "count": 8, "fps": 8.33 }
}
```

Job ids are `cafe`, `shop`, `office`, `idol`, `school`, `course` and
`university` — the ids in `Occupations`. A job with no clip of its own falls
back to `WORKING`/`STUDYING`, which falls back to `IDLE`, so a pack can answer
as much or as little of this as it likes.

`props` is the same idea keyed by something she is holding rather than
somewhere she is. Only one exists today: `PILLOW`, which is how she sleeps once
the player has bought her a bed.

## Sizing her against the room

`fit` is the one number worth checking on a new pack. The vector rig is drawn
in a 200x300 space with generous air around the character; a sheet's cell is
usually cropped tight to her. Fitting both "to the box" does not give them the
same size at all — a tight cell filling the box came out two and a half times
wider than the drawn character, covering the room she is meant to be standing
in. The default leaves her about the same air the rig has. Raise it if your
cells carry their own margin, lower it if she still crowds the stage.

## What a pack gives up

- **Outfits.** The six the shop sells are recolours of the rig's cloth. A sheet
  has its clothes painted into every frame, so buying one changes nothing.
  Either hide the outfit sections while a pack is selected, or draw a separate
  sheet per outfit and treat them as separate packs.
- **Workplaces.** Each job draws its own set behind the rig — the café
  counter, the stage truss, the classroom. That is part of the rig. A pack
  works and studies in her room, and the room keeps its full dressing while she
  does, since there is no set arriving to make space for.
- **Resolution.** Frames are drawn with nearest-neighbour filtering, so the
  linework stays crisp rather than turning to mush — but a sheet much below
  200 px a frame will still look coarse blown up to a phone-sized stage.

## Sizing

Frames anchor to the bottom of the stage, so her feet land on the floor
whatever the cell size. The widget crops from the same sheet, so one file
covers both.

## The room follows the pack

A sheet is a small drawing blown up, so every pixel the character is made of is
a visible square. The room behind her is vector art rendered at the panel's
full resolution. Those two cannot share a picture — she reads as a sticker
pasted onto a photograph.

So while a pack is selected the room is rendered into a bitmap small enough
that one of its pixels is one of *hers*, and blown back up with
nearest-neighbour sampling. Same window, same shelf, same cat; same chunky
edges she has. It happens on the stage and in the widget, and it is cached on
the theme, the hour and the furniture, so nothing is re-rendered per frame.

The wall's shading is also cut into six flat steps while this is on. The grid
alone does not finish the job: a smooth vertical gradient survives being
squeezed through a 250-row bitmap perfectly happily, and the wall stays the one
unmistakably modern thing in an otherwise pixelated picture. It is done by
drawing fewer steps of the same two colours rather than by posterising the
finished bitmap, because posterising shifts every hue it touches and the room
themes are something the player paid for.

Every object in the room is also given a one-pixel dark contour, found by a
pass over the finished bitmap rather than by stroking each of the two dozen
shapes that make up the scene — one algorithm covers every object, every theme
and any furniture added later. This is the part that actually matters: matching
the grid puts the room's edges on the character's squares, but she carries a
heavy dark line around every form and a room with none still reads as a
different medium standing in the same picture.

Add `"pixelateRoom": false` to `pet.json` to turn it off — worth doing for a
pack drawn at a high enough resolution that it does not look pixellated in the
first place. It also switches itself off automatically when the character's own
pixels would come out smaller than two screen pixels, because below that there
is no visible grid to match.

## Before you publish

**A pack you did not draw is somebody's copyright.** A sheet of a character
from a published anime or game cannot ship in an app on a store, whatever the
site you downloaded it from says or fails to say — the rights belong to the
studio that made the character, not to the site that redistributed it. Use a
pack you commissioned, generated as an original character, or hold a written
commercial licence for.

A pack left in `assets/` *will* ship: nothing strips it at build time. So
`packageRelease` and `bundleRelease` refuse to run while there is one in the
tree, and tell you your two options — move it out:

```bash
mv app/src/main/assets/pets ~/vpet-packs-parked
```

or, if you hold the rights to publish it, say so out loud:

```bash
./gradlew :vpet-waifu:app:assembleRelease -PbundlePacks=1
```

Debug and preview builds are untouched: playing with a pack is the whole reason
packs exist.

## Building a pack from GIFs

`tools/pets/build_pack.py` turns a folder of GIF animations into a sheet and a
manifest — one clip per file, named after the state or the job it plays for. It
keys out a magenta background the encoder failed to mark transparent, keeps the
artist's per-frame timing, and refuses to write the same frames twice. Run it
after the art changes:

```bash
python3 tools/pets/build_pack.py            # reads previews.7z at the repo root
python3 tools/pets/build_pack.py --gifs dir # or a folder of loose .gif files
```

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
  "grid":   { "columns": 8, "rows": 9 },
  "defaultFps": 8,
  "clips": {
    "IDLE":     { "row": 0, "from": 0, "count": 6, "fps": 5 },
    "WORKING":  { "row": 1, "from": 0, "count": 8, "fps": 9 },
    "SLEEPING": { "row": 5, "from": 5, "count": 3, "fps": 2, "loop": true }
  }
}
```

| field | meaning |
| --- | --- |
| `frame` | the size of one cell, in pixels |
| `grid` | how many cells across and down the sheet is |
| `clips` | which cells play for which state |
| `row` | zero-based row of the grid |
| `from` | first cell within that row |
| `count` | how many cells the clip runs for |
| `fps` | frames a second for this clip; falls back to `defaultFps` |
| `loop` | `true` repeats, `false` holds the last frame |

State names are the values of `com.vpet.waifu.domain.PetState`: `IDLE`,
`HAPPY`, `HUNGRY`, `TIRED`, `SLEEPING`, `EATING`, `LOVED`, `WORKING`,
`STUDYING`, `PLAYING`, `CELEBRATING`, `SICK`.

Only `IDLE` is required. Any state without a clip of its own falls back to it,
so a two-row sheet is a legal pack and a nine-row one is a luxurious version of
the same thing.

## What a pack gives up

- **Outfits.** The six the shop sells are recolours of the rig's cloth. A sheet
  has its clothes painted into every frame, so buying one changes nothing.
  Either hide the outfit sections while a pack is selected, or draw a separate
  sheet per outfit and treat them as separate packs.
- **Job props.** The seven jobs put a tray, a laptop, a microphone, a bag, a
  notebook, a book stack or a lecture in her hands, positioned off the rig's
  own hand joint. A sheet has no hand to hang anything from.
- **Resolution.** Frames are drawn with nearest-neighbour filtering, so the
  linework stays crisp rather than turning to mush — but a sheet much below
  200 px a frame will still look coarse blown up to a phone-sized stage.

## Sizing

Frames anchor to the bottom of the stage, so her feet land on the floor
whatever the cell size. The widget crops from the same sheet, so one file
covers both.

## Before you publish

**A pack you did not draw is somebody's copyright.** A sheet of a character
from a published anime or game cannot ship in an app on a store, whatever the
site you downloaded it from says or fails to say — the rights belong to the
studio that made the character, not to the site that redistributed it. Use a
pack you commissioned, generated as an original character, or hold a written
commercial licence for.

A pack left in `assets/` *will* ship: nothing strips it at build time. Remove
any pack you do not have the right to distribute before cutting a release.

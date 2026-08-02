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
- **Job props and workplaces.** The seven jobs put a tray, a laptop, a
  microphone, a bag, a notebook, a book stack or a lecture in her hands,
  positioned off the rig's own hand joint — and each draws its own set behind
  her, the café counter, the stage truss, the classroom. All of it is part of
  the rig. A pack works and studies in her room, and the room keeps its full
  dressing while she does, since there is no set arriving to make space for.
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

A pack left in `assets/` *will* ship: nothing strips it at build time. Remove
any pack you do not have the right to distribute before cutting a release.

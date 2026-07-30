# vpet-waifu

An anime desktop-pet for Android: she floats **over other apps** as a draggable
bubble, animates continuously, gets hungry and tired, goes to work and to class,
plays games with you, and keeps living while the app is closed.

There is also a **home-screen widget** and a **floating bubble**, so she is on
screen whether or not the app is open.

> It lives in the Stick repository as an independent second app. It shares only
> the Gradle wrapper and the version catalog; no code, no modules, no
> `applicationId`.

## Build

```bash
./gradlew :vpet-waifu:app:assembleDebug   # the APK
./gradlew :vpet-waifu:domain:test         # the game-balance tests
./gradlew :vpet-waifu:app:lintDebug       # lint
```

Needs the Android SDK (compileSdk 35, minSdk 26) and JDK 21.

## Modules

| Module | Type | Responsibility |
|--------|------|----------------|
| `:vpet-waifu:domain` | Kotlin/JVM | Stats, balance tuning, jobs, shop, the state machine and the simulation — no Android, fully unit-tested |
| `:vpet-waifu:app` | Android app | Character renderer, overlay service, widget, Compose UI, Room, WorkManager |

The split is the point: every balance rule is a pure function of
`(snapshot, now)`, so "does a shift pay less when she is miserable?" is a JUnit
test, not a two-hour manual session with the clock changed.

## The character is drawn, not imported

There is no sprite sheet. `ui/character/` holds a **rig**: `PetPose` describes
one frame (breath, blink, head tilt, hair sway, two-segment arm angles, eye
shape, mouth shape, prop, particles) and `PetArt` draws it — back hair, twin
tails, legs, skirt, torso, sailor collar, arms, head, face, props — onto a
Compose canvas driven by `withFrameNanos`.

That is why every state moves differently instead of swapping a PNG:

| State | What she does |
|-------|---------------|
| Idle | breathes, blinks, ahoge bobs, eyes wander |
| Happy | hops, sparkles, star-shaped pupils |
| Hungry | slumps, stomach-rumble shiver, sweat drop, worried brows |
| Tired | head nods, half-lidded eyes |
| Sleeping | pillow, quilt rising with her breath, floating Zs |
| Eating | bowl in one hand, food riding to her mouth, chewing |
| Loved | heart eyes, `:3` mouth, hearts drifting up |
| Working | sits at a desk, types, laptop glows and scrolls |
| Studying | holds a book, eyes track the line, pages turn |
| Playing | mashes a controller, leans with the action |
| Celebrating | jumps with both arms up, coins spin past |

Adding a state costs a pose function, not a folder of frames. Props hang off the
computed hand positions, so they follow the arms rather than floating at fixed
coordinates.

**Why not a downloaded model?** There is no CC0 anime character with this
animation set — what is available is either unlicensed, copyleft pixel-RPG art
in the wrong style, or not anime at all. Shipping art with an unclear licence
into a repository is not worth it. Swapping in real art later means replacing
`drawPet`; nothing else changes.

The renderer is also the widget's: `PetRasterizer` draws frames through the very
same code, so the widget can never drift out of sync with the app.

## Testing

There is no emulator here — the container has no KVM and the CPU exposes no
virtualisation — so the Android-dependent half of the app is tested with
**Robolectric**, which runs the real framework on the JVM, including Room and
(in native graphics mode) the real Skia pipeline and PNG encoder.

That matters because the widget's design rests on measured numbers, not
estimates. The suite caught three real defects while it was being written:

- twelve animation frames overflowed the transaction budget on the busiest
  state (373 KB), so every redraw silently took the shrink path — now ten;
- the animation loop did not join back onto itself, because each state's
  shared idle motion ran at a frequency unrelated to its own period;
- `PLAYING` had two internal frequencies that were not multiples of each
  other, so it jolted once per cycle even after the first fix.

```bash
./gradlew :vpet-waifu:app:testDebugUnitTest   # Robolectric: Room, widget, frames
./gradlew :vpet-waifu:domain:test             # pure balance tests
```

### Iterating on the art

The renderer only touches a small slice of the Compose graphics API, so it can
be compiled against `java.awt` stubs and rasterised to PNG on a plain JVM —
which is how the character was actually developed here, rather than by guessing.
The harness is not committed; it is ~200 lines of stubs plus a `main` that draws
every state at several times into a contact sheet.

## How she keeps living while nothing is running

WorkManager's minimum periodic interval is **15 minutes** — a one-minute
`PeriodicWorkRequest` is not something Android will schedule. So the pet is not
driven by a timer at all. The save file stores `lastTickAt`, and
`PetSimulation.advanceTo(snapshot, now)` pays off however many whole minutes are
owed since then.

Four callers drive it, all through the same function, all idempotent:

| Driver | When | Why |
|--------|------|-----|
| Overlay service ticker | every 60 s while the bubble is up | real-time stat movement |
| `PetTickWorker` | every 15 min | keeps the state fresh with the app closed |
| Widget | whenever it is redrawn | the widget shows *now*, not the last write |
| The app / any action | opened, button pressed | pays the debt before doing anything else |

Because the timestamp is the source of truth, none of them can double-count, and
leftover seconds stay owed rather than getting rounded away — ticking once a
second for ten minutes applies exactly ten minutes of decay
(`PetSimulationTest` asserts this). It is also why **a shift finishes and pays
out while the phone is in a pocket**: the completion happens inside the
catch-up, not on a timer that has to survive.

Catch-up is capped at 12 hours of simulated minutes. Hunger and energy saturate
within ~100 minutes, so a longer absence lands on the same numbers; the cap just
stops a week-long gap from spinning 10 000 iterations.

## Balance (per real minute)

Everything lives in `PetTuning`, so retuning never touches the simulation.

| Rule | Value |
|------|-------|
| Hunger | −1/min, always — and ×1.3 while she is on the clock |
| Energy | −1/min awake, **+2/min asleep**, −(job cost ÷ duration) while working |
| Mood | drifts ≤0.5/min toward `avg(hunger, energy) − neglect`, minus a work penalty |
| Neglect penalty | 1 point per 15 min without attention, capped at 30 |
| Feed (free) | +35 hunger, +5 mood |
| Pat | +8 mood at full charge; scales to ×0.15 if mashed, recharging over 5 min |

She wakes up on her own at 100 energy. Sleeping restores energy but **not**
hunger — sleeping off a famine doesn't work, which is the pressure that makes
the loop a loop.

## Work, study and the wallet

| Job | Level | Time | Energy | Pays |
|-----|-------|------|--------|------|
| Café | 1 | 30 min | 22 | 70 ¥ |
| Shop assistant | 3 | 60 min | 40 | 170 ¥ |
| Office | 6 | 120 min | 65 | 420 ¥ |
| Idol stage | 10 | 180 min | 85 | 950 ¥ |

| Course | Level | Time | Energy | Pays |
|--------|-------|------|--------|------|
| School | 1 | 30 min | 16 | 45 EXP |
| Online course | 4 | 60 min | 32 | 120 EXP |
| University | 8 | 120 min | 55 | 300 EXP |

**Mood decides the payout**: ×1.3 above 75, ×1.0 above 40, then it falls away —
and studying in a bad mood is punished harder than working in one (×0.3 vs
×0.45). The job card shows the projected payout *at her current mood*, so the
cost of neglecting her is visible before committing to a two-hour shift.

A shift is a mood drain in itself (~20 points over 30 minutes), so back-to-back
work without food, sleep or a game is self-defeating. Running out of energy
mid-shift ends it early with partial pay; calling her home early pays for the
time she actually put in.

## Shop

Buying applies the item immediately — there is no inventory to manage.

- **Food** — onigiri, ramen, cake, bento, parfait: hunger, and mood in varying
  proportion. She plays the eating animation.
- **Energy drink** — the only way to buy energy: 130 ¥ for +45, with a
  half-hour caffeine crash (energy drains x1.6) so it does not simply replace
  sleeping. Sleep stays the free route; the drink buys back the half hour.
- **Gifts** — flowers, teddy, headphones, a ring: pure mood, level-gated.
- **Pills** — risk/reward rather than pay-to-win:
  - **Cash advance**: pay 180, get 500 now, hunger drains ×2.5 for three hours.
  - **EXP pill**: +180 EXP instantly, −25 energy, −15 mood, energy drains ×1.6
    for two hours.

## Mini-game

A 20-second tap game. Targets pop up for about a second each; every catch is
mood, plus a few coins so an empty wallet is never a dead end. It costs energy
in proportion to the score, so it lifts her spirits but cannot replace food and
sleep.

## The bubble

`PetOverlayService` is a foreground service (type `specialUse`) that adds a
`ComposeView` to the `WindowManager` as a `TYPE_APPLICATION_OVERLAY`. Because a
`ComposeView` will not compose without them, the service is itself a
`LifecycleOwner`, `ViewModelStoreOwner` and `SavedStateRegistryOwner`.

| Gesture | Awake | Asleep |
|---------|-------|--------|
| Tap | opens/closes the mini-panel | **ignored** — she sleeps through it |
| Long press | quick head pat | wakes her |
| Drag | moves her, snaps to the nearest edge | same |

The panel carries the three stat bars, the live shift countdown, and feed /
sleep / pet / open-app / hide. The window is `WRAP_CONTENT`, so opening the
panel grows it downwards and `OverlayWindow` re-clamps the position — near the
bottom of the screen the buttons stay reachable.

Since taps are the thing she ignores while asleep, waking her is reachable three
ways: long-press the bubble, the panel's sun button, or the app screen.

## The widget

A home-screen widget where the pet **animates**, centred in her room, resizable
from about 2x2 up to 5x4 cells.

Two things a widget normally cannot do:

**Animation.** RemoteViews can neither run Compose nor play a frame animation —
`AnimationDrawable` and friends do not start inside a widget. But `ViewFlipper`
is on the short list of views a widget may contain, and with `autoStart` it
cycles its children by itself once the launcher attaches it. So the character is
pre-rendered into a seamless twelve-frame loop, each frame an `ImageView` child,
embedded in the Glance tree through `AndroidRemoteViews`. No in/out animation is
set, so the frames play as a flipbook rather than crossfading. If a launcher
declines to auto-start the flipper, the widget shows the first frame — still a
perfectly good picture.

`PetPoseFactory.widgetLoopFrame` is what makes the loop seamless: it samples a
state over a whole number of *its own* cycles, so fast motions (typing, button
mashing) still run at natural speed instead of being stretched across the loop,
and the free-running blink is replaced by one pulse per loop so it is never cut
in half at the seam.

**Payload.** Every frame travels to the launcher inside one Binder transaction
with roughly a megabyte shared across the whole system; an oversized update is
dropped and the user just sees a blank widget. Measured on this art:

| Frames | Size | Raw `ARGB_8888` | PNG |
|--------|------|-----------------|-----|
| 12 | 240x336 | 3.7 MB | 234 KB |
| 12 | 300x420 | 5.8 MB | 299 KB |
| 12 | 400x560 | 10.3 MB | 420 KB |

So the frames go over as `Icon.createWithData` PNGs rather than bitmaps — a
bitmap is marshalled uncompressed and a dozen of them would blow the budget on
their own. Resolution is capped, and the total is re-checked after rendering and
the whole loop re-rendered smaller if a detail-heavy state runs over.

The room is drawn **once** as a separate full-bleed layer under the flipper, and
the frames are transparent. Measurement said the backdrop only costs about 3% —
the point is visual: a character-sized backdrop leaves a visible panel edge
inside the widget, while a full-bleed one makes the whole thing a single scene.
The wall/floor junction is positioned from the character's actual foot height,
so she stands on the floor at any widget size instead of floating up the wall.

**Staying in step.** The widget follows the *data*, not its callers.
`WidgetSync` observes the repository and redraws on any change to what the
widget actually shows — stats rounded the way they are printed, so a quiet
minute of drift costs nothing. Asking each surface (the app, the bubble, the
background tick, the widget's own buttons) to remember to refresh is how
"I pressed sleep and the widget still shows her awake" happens: one path
forgets and there is no second chance. The widget also reads through
`PetRepository.peek`, which advances the world in memory without persisting —
otherwise a redraw would write to the save file and schedule another redraw.

**Resizing.** `SizeMode.Exact` re-runs the widget for every size the user drags
to, and the real size comes from the widget's options bundle (`provideGlance`
runs before the composition, so `LocalSize` is not yet available). Both the
layout and the bitmap resolution follow it. `minResizeWidth`/`minResizeHeight`
are declared well apart from `minWidth`/`minHeight` — omitting them is what makes
some launchers refuse to show resize handles at all. Three layouts:

| Size | Shows |
|------|-------|
| under 150x130dp | the character alone |
| 150x130dp and up | + state and the three stat bars |
| 200x200dp and up | + level, wallet and one-tap feed / sleep |

Glance 1.1 also exposes only the single-`Color` provider — both the resource-id
and day/night overloads are restricted to the library group — so the widget
resolves light/dark from the configuration itself.

### Permissions

- **`SYSTEM_ALERT_WINDOW`** — cannot be requested with a dialog. `MainActivity`
  sends the player to the settings page and re-reads the answer on every resume,
  since they can come back by any route.
- **`POST_NOTIFICATIONS`** (Android 13+) — asked once. Without it the service
  still runs; its ongoing notification is just dropped.
- **`FOREGROUND_SERVICE_SPECIAL_USE`** — Android 14+ requires a declared type;
  a floating pet is not media, location or data sync.

If the overlay permission is revoked while she is on screen, the service stops
itself rather than sitting in the foreground with nothing to show.

`BootReceiver` brings her back after a reboot **only** if the bubble was enabled
when the phone went down — dismissing her is remembered, because silently
re-adding an overlay someone dismissed is how an app gets uninstalled.

## Persistence

One Room row (`pet_state`, pinned to id 0) is the save file: stats, wallet, EXP,
the running session, active pill effects, the pending result card. Enums and the
effect list are stored as text, so adding a job or a pill needs no migration.

All four drivers can write at the same instant, so every mutation is a
read-modify-write behind a `Mutex` in `PetRepository`; without it a tick landing
between a feed's read and its write would silently eat the food. No-op ticks
skip the write so idle minutes don't wake every Flow collector.

Schema v1 → v2 is a real `Migration`, not a destructive one: a pet raised on the
Phase 1 build keeps her stats and her clock.

## What is not here yet

- Achievements and the school → university → work life arc.
- Cosmetics: outfits and hairstyles. `PetPalette` is already the seam — a new
  look is a palette, and the rig would need clothing variants.
- Battery level as an energy source (the KWGT/Tasker idea).
- More mini-games; there is one.

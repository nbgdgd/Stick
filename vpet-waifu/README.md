# vpet-waifu

An anime desktop-pet for Android: she floats **over other apps** as a draggable
bubble, gets hungry, gets tired, and goes to sleep — and keeps doing all of that
while the app is closed.

This is **Phase 1** of the GDD: the bubble, three stats, the tick loop,
feed/sleep/pet, persistence and the sprite state machine. Work, study, money,
the shop and the pills are deliberately *not* here yet.

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
| `:vpet-waifu:domain` | Kotlin/JVM | Stats, balance tuning, the FSM and the whole simulation — no Android, fully unit-tested |
| `:vpet-waifu:app` | Android app | Overlay service, Compose UI, Room, WorkManager |

The split is the point: every balance rule is a pure function of
`(snapshot, now)`, so "does she starve overnight?" is a JUnit test, not a
two-hour manual session with the clock changed.

## How she keeps living while nothing is running

WorkManager's minimum periodic interval is **15 minutes** — a one-minute
`PeriodicWorkRequest` is not something Android will schedule. So the pet is not
driven by a timer at all. The save file stores `lastTickAt`, and
`PetSimulation.advanceTo(snapshot, now)` pays off however many whole minutes are
owed since then.

Three callers drive it, all through the same function, all idempotent:

| Driver | When | Why |
|--------|------|-----|
| Overlay service ticker | every 60 s while the bubble is up | real-time stat movement the player can watch |
| `PetTickWorker` | every 15 min | keeps the state fresh with the bubble off |
| `HomeViewModel` / any action | app opened, button pressed | pays the debt before doing anything else |

Because the timestamp is the source of truth, none of them can double-count, and
leftover seconds stay owed rather than getting rounded away — ticking once a
second for ten minutes applies exactly ten minutes of decay
(`PetSimulationTest` asserts this).

Catch-up is capped at 12 hours of simulated minutes. Hunger and energy saturate
within ~100 minutes, so a longer absence lands on the same numbers; the cap just
stops a week-long gap from spinning 10 000 iterations.

## Balance (per real minute)

Everything lives in `PetTuning`, so retuning never touches the simulation.

| Rule | Value |
|------|-------|
| Hunger | −1/min, always — awake or asleep |
| Energy | −1/min awake, **+2/min asleep** |
| Mood | drifts ≤0.5/min toward `avg(hunger, energy) − neglect` |
| Neglect penalty | 1 point per 15 min without attention, capped at 30 |
| Feed | +35 hunger, +5 mood |
| Pat | +8 mood at full charge; scales down to ×0.15 if mashed, recharging over 5 min |
| Hungry sprite | hunger ≤ 30 |

She wakes up on her own at 100 energy. Sleeping restores energy but **not**
hunger — sleeping off a famine doesn't work, which is the pressure that makes
the loop a loop.

## The state machine

`IDLE → HUNGRY → SLEEPING`, resolved from the snapshot, sleeping wins:

```
sleeping?        → SLEEPING
hunger ≤ 30      → HUNGRY
otherwise        → IDLE
```

One sprite per state, mapped in `ui/PetSprites.kt`.

## The bubble

`PetOverlayService` is a foreground service (type `specialUse`) that adds a
`ComposeView` to the `WindowManager` as a `TYPE_APPLICATION_OVERLAY`. Because a
`ComposeView` will not compose without them, the service is itself a
`LifecycleOwner`, `ViewModelStoreOwner` and `SavedStateRegistryOwner`.

Interactions:

| Gesture | Awake | Asleep |
|---------|-------|--------|
| Tap | opens/closes the mini-panel | **ignored** — she sleeps through it |
| Long press | quick head pat | wakes her |
| Drag | moves her, snaps to the nearest edge | same |

The panel carries the three stat bars plus feed / sleep / pet / open-app / hide.
The window is `WRAP_CONTENT`, so opening the panel grows it downwards and
`OverlayWindow` re-clamps the position — near the bottom of the screen the
buttons stay reachable.

Since taps are the thing she ignores while asleep, waking her is reachable three
ways: long-press the bubble, the panel's sun button, or the app screen.

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

One Room row (`pet_state`, pinned to id 0) is the save file. All three drivers
can write at the same instant, so every mutation is a read-modify-write behind a
`Mutex` in `PetRepository`; without it a tick landing between a feed's read and
its write would silently eat the food. No-op ticks skip the write so idle
minutes don't wake every Flow collector.

## Art

`pet_idle`, `pet_hungry` and `pet_sleeping` are hand-authored vector
placeholders — scalable, no binaries in git, good enough to exercise the FSM and
the balance now.

Dropping in the real art from the ComfyUI + Wan 2.2 I2V pipeline means replacing
those three drawables (sprite sheet frames or a WebM with alpha) and, if
animating, swapping the `Image` in `PetBubble`/`HomeScreen` for the animated
composable. `ui/PetSprites.kt` stays as the single mapping point, and no
simulation or service code changes.

## What Phase 2 plugs into

The seams are already cut:

- **Work / study timers** → a new `PetActivity` value plus its rules in
  `PetSimulation.advanceTo`; the entity stores the activity as a string so
  adding one is additive.
- **Money / EXP** → new fields on `PetStats` (or a sibling data class) and a
  Room migration off version 1.
- **Shop, food variety** → `feed()` already funnels through one place; give it a
  food parameter.
- **Difficulty / balance passes** → `PetTuning` is injected, so a curve swap is
  a DI change.

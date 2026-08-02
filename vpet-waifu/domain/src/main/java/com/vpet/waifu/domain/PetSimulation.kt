package com.vpet.waifu.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The whole game loop, as a pure function of (snapshot, now).
 *
 * Nothing here touches Android, a database or a clock: callers pass the current
 * time in. That makes every balance rule reproducible in a unit test and lets
 * the exact same code run from four places — the frame ticker inside the
 * overlay service, the 15-minute WorkManager job, the widget refresh, and the
 * catch-up that happens the moment the app is reopened.
 *
 * Every action advances the world first, so an action can never be used to
 * dodge decay that was already owed, and a shift that finished while the phone
 * was in a pocket pays out the moment anything looks at the save file.
 */
class PetSimulation(val tuning: PetTuning = PetTuning()) {

    /**
     * Pays off all whole minutes between [PetSnapshot.lastTickAt] and [nowMillis].
     *
     * Leftover seconds stay owed: `lastTickAt` moves by whole minutes only, so
     * ticking sixty times a second never drops fractions of a minute.
     */
    fun advanceTo(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val settled = settlePassive(snapshot, nowMillis)
        val owedMinutes = (nowMillis - settled.lastTickAt) / MS_PER_MINUTE
        val advanced = if (owedMinutes <= 0) {
            settled.withExpiredEffectsDropped(nowMillis)
        } else {
            advanceMinutes(settled, nowMillis, owedMinutes)
        }
        // Her own life, on top of the clockwork: wishes voiced and expired,
        // chapters, the week's goal and the calendar. All run on every advance,
        // so no path through the simulation can miss them.
        return advanceStory(tendAnniversary(tendGoal(tendRequest(advanced, nowMillis), nowMillis), nowMillis))
    }

    /**
     * The week's goal: reset it when the week rolls over, pay it when it is met.
     *
     * Progress is a delta against a lifetime counter captured at the week's
     * start, so nothing needs incrementing anywhere — the shifts, lessons,
     * games and earnings the rest of the simulation already counts *are* the
     * progress.
     */
    private fun tendGoal(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        if (!WeeklyGoals.unlocked(snapshot)) return snapshot
        val week = WeeklyGoals.weekOf(nowMillis)
        val kind = WeeklyGoals.kindFor(week)

        if (snapshot.goalWeek != week) {
            return snapshot.copy(
                goalWeek = week,
                goalBaseline = WeeklyGoals.counterFor(snapshot, kind),
                goalRewarded = false,
            )
        }
        if (snapshot.goalRewarded) return snapshot

        val target = WeeklyGoals.targetFor(kind, snapshot.level)
        val done = WeeklyGoals.counterFor(snapshot, kind) - snapshot.goalBaseline
        if (done < target) return snapshot

        val reward = WeeklyGoals.rewardFor(kind, snapshot.level)
        return snapshot.copy(
            progress = snapshot.progress.plus(money = reward),
            totalEarned = snapshot.totalEarned + reward,
            goalRewarded = true,
            journal = Journal.append(
                snapshot.journal,
                JournalEntry(JournalKind.GOAL_DONE, kind.name, reward, nowMillis),
            ),
        ).plusBond(WeeklyGoals.BOND_REWARD, nowMillis)
    }

    /** Anniversaries: the milestones of days together, each celebrated once. */
    private fun tendAnniversary(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val days = Anniversaries.daysTogether(snapshot.bornAt, nowMillis)
        val due = Anniversaries.due(days, snapshot.celebratedMilestone) ?: return snapshot
        val gift = Anniversaries.moneyGift(due)
        return snapshot.copy(
            stats = snapshot.stats.adjusted(moodBy = Anniversaries.MOOD_GIFT),
            progress = snapshot.progress.plus(money = gift),
            totalEarned = snapshot.totalEarned + gift,
            celebratedMilestone = due,
            journal = Journal.append(
                snapshot.journal,
                JournalEntry(JournalKind.ANNIVERSARY, null, due, nowMillis),
            ),
        ).withEmote(Emote.CELEBRATING, nowMillis, tuning.celebrateEmoteMillis)
    }

    /**
     * Completes every chapter the current state already satisfies.
     *
     * A loop rather than a single check: one act can finish two chapters at
     * once (the shift that reaches level 10 may close both "professional" and
     * an earlier straggler), and stopping after the first would leave the
     * story a step behind the life.
     */
    private fun advanceStory(snapshot: PetSnapshot): PetSnapshot {
        var current = snapshot
        while (!Story.isComplete(current.storyChapter)) {
            val chapter = Story.CHAPTERS[current.storyChapter]
            if (!chapter.condition(current)) break
            current = current.copy(
                progress = current.progress.plus(money = chapter.rewardMoney),
                owned = chapter.rewardOutfit?.let { current.owned + it } ?: current.owned,
                storyChapter = current.storyChapter + 1,
                totalEarned = current.totalEarned + chapter.rewardMoney,
            )
        }
        return current
    }

    /**
     * Expires a stale wish and lets a fresh window voice a new one.
     *
     * Runs off the *real* clock rather than inside the minute loop on purpose:
     * a wish spawned into the middle of an eight-hour absence would only ever
     * be found already expired, which punishes the player for something they
     * never saw. This way she asks while someone is actually there — including
     * the moment they come back.
     */
    private fun tendRequest(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        var current = snapshot

        val live = current.request
        if (live != null && nowMillis >= live.until) {
            // A wish nobody granted. She is a little sad about it, and that is
            // all — bond is memory of care, not a ledger of debts.
            current = current.copy(
                request = null,
                stats = current.stats.adjusted(moodBy = -tuning.requestExpiredMood),
                journal = Journal.append(
                    current.journal,
                    JournalEntry(JournalKind.WISH_EXPIRED, live.itemId ?: live.kind.name, at = nowMillis),
                ),
            )
        }

        if (current.request == null && current.activity == PetActivity.AWAKE && !current.isSick) {
            val slot = Requests.slotOf(nowMillis)
            val until = Requests.slotEnd(slot)
            if (slot != current.lastRequestSlot && until - nowMillis >= Requests.MINIMUM_WINDOW_MILLIS) {
                val wish = Requests.forSlot(slot, current.level)
                current = if (wish != null) {
                    current.copy(
                        request = PetRequest(wish.first, wish.second, until, slot),
                        lastRequestSlot = slot,
                    )
                } else {
                    // A quiet window is also spent, so it is not re-hashed on
                    // every single tick for three hours.
                    current.copy(lastRequestSlot = slot)
                }
            }
        }
        return current
    }

    /** Grants a live wish that [test] matches: bond, joy, and the wish retires. */
    private fun grantRequestIf(
        snapshot: PetSnapshot,
        nowMillis: Long,
        test: (PetRequest) -> Boolean,
    ): PetSnapshot {
        val request = snapshot.request ?: return snapshot
        if (nowMillis >= request.until || !test(request)) return snapshot
        return snapshot.copy(
            request = null,
            stats = snapshot.stats.adjusted(moodBy = tuning.requestGrantedMood),
        ).plusBond(Bond.REQUEST_GRANTED, nowMillis)
    }

    /**
     * Banks attachment, under the daily cap.
     *
     * The cap is the difference between a bar and a bond: a hundred meals in
     * one evening are worth the same twenty points as a good half hour, so the
     * only road to the top is actually being around across many days.
     */
    private fun PetSnapshot.plusBond(points: Int, clock: Long): PetSnapshot {
        if (points <= 0) return this
        val day = Events.dayOf(clock)
        val today = if (bondDay == day) bondToday else 0
        val granted = points.coerceAtMost(Bond.DAILY_CAP - today).coerceAtLeast(0)
        return copy(
            bondPoints = bondPoints + granted,
            bondDay = day,
            bondToday = today + granted,
        )
    }

    /**
     * The tip jar: a few coins every [PetTuning.passiveTickMillis].
     *
     * Same "pay off whole units, keep the remainder" shape as the minute loop
     * above, just at a three-second unit, so the wallet is exact no matter how
     * often anything calls in.
     *
     * The one difference is the grace window. Minutes are owed whether or not
     * the phone was on — she gets hungry in a pocket. Loose change is not: it
     * is paid for *watching* her, so a gap wider than
     * [PetTuning.passiveGraceMillis] means nobody was, and the clock simply
     * restarts. That is what keeps this from being both the best-paid job in
     * the game and the only one you can do while asleep.
     */
    fun settlePassive(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val since = snapshot.passiveSince
        // A fresh save, or a clock that went backwards: start counting here.
        if (since <= 0L || nowMillis < since) return snapshot.copy(passiveSince = nowMillis)

        val elapsed = nowMillis - since
        if (elapsed > tuning.passiveGraceMillis) {
            return snapshot.copy(passiveSince = nowMillis, passiveBank = 0f)
        }
        val ticks = elapsed / tuning.passiveTickMillis
        if (ticks <= 0L) return snapshot

        // Nothing while she is out.
        //
        // "Why does studying pay money and not EXP?" — because the jar was
        // ticking all through the lesson. A study session earns two EXP a
        // minute; the jar was dropping a coin every three seconds next to it,
        // so the thing the player watched arrive was money, and the thing the
        // session actually paid crept up invisibly in a ring the size of a
        // thumbnail. The clock is the clock: what a shift pays is all a shift
        // pays. Time still passes, so nothing banks up to be collected after.
        if (snapshot.isBusy) {
            return snapshot.copy(
                passiveSince = since + ticks * tuning.passiveTickMillis,
                passiveBank = 0f,
            )
        }

        // The day's allowance. Without it, leaving the app open on a charger
        // out-earns every job in the catalogue by six to one, which is the same
        // shape of hole the cash advance used to be.
        val day = Events.dayOf(nowMillis)
        val paidToday = if (snapshot.passiveDay == day) snapshot.passivePaidToday else 0
        val room = (passiveDailyCap(snapshot) - paidToday).coerceAtLeast(0)

        val bank = snapshot.passiveBank + ticks * passivePerTick(snapshot)
        val coins = min(bank.toInt(), room)
        return snapshot.copy(
            progress = if (coins > 0) snapshot.progress.plus(money = coins) else snapshot.progress,
            totalEarned = snapshot.totalEarned + coins,
            // Once the day is spent the remainder is dropped rather than saved:
            // a bank that keeps filling would pay the whole day out again the
            // instant the date rolled over.
            passiveBank = if (coins >= room) 0f else bank - coins,
            passiveSince = since + ticks * tuning.passiveTickMillis,
            passiveDay = day,
            passivePaidToday = paidToday + coins,
        )
    }

    /** All the jar will pay in one day, at her level and with her gear. */
    fun passiveDailyCap(snapshot: PetSnapshot): Int =
        (passivePerTick(snapshot) * ticksPerMinute * tuning.passiveMinutesPerDay).roundToInt()

    /** What is left of today's allowance. */
    fun passiveLeftToday(snapshot: PetSnapshot, nowMillis: Long): Int {
        val paid = if (snapshot.passiveDay == Events.dayOf(nowMillis)) snapshot.passivePaidToday else 0
        return (passiveDailyCap(snapshot) - paid).coerceAtLeast(0)
    }

    private val ticksPerMinute: Float
        get() = MS_PER_MINUTE.toFloat() / tuning.passiveTickMillis

    /** What one tick of the tip jar is worth. Grows with her level and her gear. */
    fun passivePerTick(snapshot: PetSnapshot): Float =
        (tuning.passiveCoinsPerTick + tuning.passiveCoinsPerLevel * (snapshot.level - 1)) *
            snapshot.modifiers().pay

    /** Coins per minute of watching, which is the number worth showing a player. */
    fun passivePerMinute(snapshot: PetSnapshot): Int =
        (passivePerTick(snapshot) * ticksPerMinute).roundToInt()

    private fun advanceMinutes(snapshot: PetSnapshot, nowMillis: Long, owedMinutes: Long): PetSnapshot {

        // A long absence saturates the stats long before the cap, so simulating
        // the tail of the window (rather than the head) keeps the neglect
        // penalty aligned with real timestamps at no extra cost.
        val simulated = min(owedMinutes, tuning.maxCatchUpMinutes)
        var clock = nowMillis - simulated * MS_PER_MINUTE
        var current = snapshot

        repeat(simulated.toInt()) {
            clock += MS_PER_MINUTE
            current = step(current, clock, snapshot.lastInteractionAt)
        }

        return current
            .copy(lastTickAt = snapshot.lastTickAt + owedMinutes * MS_PER_MINUTE)
            .withExpiredEffectsDropped(nowMillis)
    }

    /** One simulated minute, ending at [clock]. */
    private fun step(snapshot: PetSnapshot, clock: Long, interactionAnchor: Long): PetSnapshot {
        // Non-null exactly when she is on the clock, so it doubles as the "busy"
        // flag and keeps the session handling below free of null checks.
        val occupation = if (snapshot.isBusy) snapshot.occupation else null
        val busy = occupation != null
        val mods = snapshot.modifiers()

        val hungerRate = tuning.hungerDecayPerMinute * mods.hungerDecay *
            (if (busy) tuning.busyHungerMultiplier else 1f) *
            (if (snapshot.hasEffect(EffectKind.HUNGER_SURGE, clock)) tuning.hungerSurgeMultiplier else 1f)

        val exhaustion =
            (if (snapshot.hasEffect(EffectKind.EXHAUSTION, clock)) tuning.exhaustionMultiplier else 1f) *
                (if (snapshot.hasEffect(EffectKind.SECOND_WIND, clock)) tuning.secondWindMultiplier else 1f)

        val energyDelta = when (snapshot.activity) {
            PetActivity.SLEEPING -> tuning.energyRecoveryPerMinute * mods.sleepSpeed
            PetActivity.WORKING, PetActivity.STUDYING ->
                -(occupation?.energyPerMinute ?: tuning.energyDecayPerMinute) *
                    exhaustion * mods.energyDecay
            // The mini-game is played in short bursts; it costs energy per tap,
            // not per minute, so idling on the game screen is not a drain.
            PetActivity.PLAYING, PetActivity.AWAKE ->
                -tuning.energyDecayPerMinute * exhaustion * mods.energyDecay
        }

        // Per job, not per kind: the whole point of the catalog is that a shift
        // at the cafe leaves her cheerful and one at the office does not.
        val activityMoodCost = when (snapshot.activity) {
            PetActivity.WORKING, PetActivity.STUDYING -> occupation?.moodPerMinute ?: 0f
            else -> 0f
        }

        var stats = snapshot.stats.adjusted(hungerBy = -hungerRate, energyBy = energyDelta)
        // Built through `coerced`, not `copy`: the drift result minus the costs
        // can dip below zero for a minute, and the PetStats constructor treats
        // out-of-range as a bug rather than something to fix up quietly.
        val mood = driftMood(
            mood = stats.mood,
            hunger = stats.hunger,
            energy = stats.energy,
            minutesSinceInteraction = minutesBetween(interactionAnchor, clock) * mods.neglect,
        ) - activityMoodCost -
            // Being ill is its own misery, on top of whatever caused it.
            (if (snapshot.isSick) tuning.sickMoodPerMinute else 0f) +
            // …and the favourite playlist is its own small joy.
            (if (snapshot.hasEffect(EffectKind.GOOD_VIBES, clock)) tuning.goodVibesMoodPerMinute else 0f)
        stats = PetStats.coerced(stats.hunger, stats.energy, mood)

        // The road to sickness. Minutes with hunger or energy pinned at zero
        // pile up; care burns the pile back down at half speed. Crossing the
        // line makes her ill, and only medicine — or a full day — clears it.
        val bottomed = stats.hunger <= PetStats.MIN || stats.energy <= PetStats.MIN
        var runDown = if (bottomed) {
            snapshot.runDownMinutes + 1f
        } else {
            (snapshot.runDownMinutes - tuning.runDownRecoveryPerMinute).coerceAtLeast(0f)
        }
        var sickSince = snapshot.sickSince
        var journal = snapshot.journal
        if (sickSince == 0L && runDown >= tuning.sickAfterRunDownMinutes) {
            sickSince = clock
            runDown = 0f
            journal = Journal.append(journal, JournalEntry(JournalKind.FELL_SICK, at = clock))
        }
        if (sickSince > 0L && clock - sickSince >= tuning.sickRecoveryMinutes * MS_PER_MINUTE) {
            // She shook it off by herself — the floor under an abandoned save.
            sickSince = 0L
            runDown = 0f
            journal = Journal.append(journal, JournalEntry(JournalKind.RECOVERED, at = clock))
        }

        var next = snapshot.copy(
            stats = stats,
            sickSince = sickSince,
            runDownMinutes = runDown,
            journal = journal,
        )
        next = rollEvent(next, clock)

        // She gets up by herself once she is fully rested.
        if (next.isSleeping && stats.energy >= PetStats.MAX) {
            next = next.copy(activity = PetActivity.AWAKE)
        }

        val session = next.session
        if (occupation != null && session != null) {
            next = accrue(next, occupation, clock)
            // Haste doubles the session clock: this minute earns twice and
            // brings the bell one extra minute closer, so a shift finishes in
            // half the time at exactly its full pay.
            if (next.hasEffect(EffectKind.HASTE, clock)) {
                next = accrue(next, occupation, clock)
                next = next.copy(
                    session = next.session?.let { it.copy(endsAt = it.endsAt - MS_PER_MINUTE) },
                )
            }
            val endsAt = next.session?.endsAt ?: session.endsAt
            if (clock >= endsAt) {
                next = complete(next, occupation, clock, cancelled = false)
            } else if (stats.energy <= PetStats.MIN) {
                // Running out of energy on the clock ends the shift there and
                // then. She keeps what she has already been paid.
                next = complete(next, occupation, clock, cancelled = true)
            }
        }

        // The mini-game is played in one sitting. If the screen that started it
        // went away — a tab switch, a killed process — nothing would ever end
        // the session, and every other action stays blocked behind it. Time
        // itself closes it instead.
        if (next.activity == PetActivity.PLAYING &&
            minutesBetween(next.lastInteractionAt, clock) >= tuning.maxPlayMinutes
        ) {
            next = next.copy(activity = PetActivity.AWAKE)
        }

        return next
    }

    /**
     * When the picture is next due to change, if anything is due at all.
     *
     * The widget draws a world advanced to *now*, so what it should show drifts
     * without anything being written down: a shift ends, she wakes up on her
     * own, she gets hungry. Nothing was watching for those, so the only thing
     * that ever corrected the widget was the quarter-hour heartbeat — which is
     * how "she finished work ten minutes ago and the widget still shows her at
     * the desk" happens.
     *
     * This is the earliest instant any of that lands, so the app can set an
     * alarm for it instead of hoping the heartbeat is close enough. Approximate
     * by design: an estimate that is a minute early costs one redraw, and the
     * heartbeat is still there for anything this does not model.
     */
    fun nextVisibleChangeAt(snapshot: PetSnapshot, nowMillis: Long): Long? {
        val mods = snapshot.modifiers()
        val candidates = mutableListOf<Long>()

        // A shift or a lesson ending is the big one — it changes her pose, her
        // prop and her whole scene at a known instant.
        snapshot.session?.let { candidates += it.endsAt }

        val hungerRate = tuning.hungerDecayPerMinute * mods.hungerDecay *
            (if (snapshot.isBusy) tuning.busyHungerMultiplier else 1f) *
            (if (snapshot.hasEffect(EffectKind.HUNGER_SURGE, nowMillis)) tuning.hungerSurgeMultiplier else 1f)

        if (snapshot.isSleeping) {
            // She gets up by herself once she is full of energy.
            val perMinute = tuning.energyRecoveryPerMinute * mods.sleepSpeed
            candidates += nowMillis + minutesToMillis((PetStats.MAX - snapshot.stats.energy) / perMinute)
        } else {
            val energyRate = tuning.energyDecayPerMinute * mods.energyDecay *
                (if (snapshot.hasEffect(EffectKind.EXHAUSTION, nowMillis)) tuning.exhaustionMultiplier else 1f)
            if (snapshot.stats.energy > tuning.tiredThreshold && energyRate > 0f) {
                candidates += nowMillis +
                    minutesToMillis((snapshot.stats.energy - tuning.tiredThreshold) / energyRate)
            }
        }
        if (snapshot.stats.hunger > tuning.hungryThreshold && hungerRate > 0f) {
            candidates += nowMillis +
                minutesToMillis((snapshot.stats.hunger - tuning.hungryThreshold) / hungerRate)
        }
        // Crossing the "happy" line either way swaps her idle animation — but
        // only if she is actually heading for it. Mood walks towards a target
        // rather than in a straight line, and booking a wake-up for a threshold
        // on the far side of a target she will never pass is how this ends up
        // firing a redraw every twenty-five minutes for no reason at all.
        val mood = snapshot.stats.mood
        val target = moodTarget(
            snapshot.stats,
            minutesBetween(snapshot.lastInteractionAt, nowMillis) * mods.neglect,
        )
        val crosses = (mood - tuning.happyThreshold) * (target - tuning.happyThreshold) < 0f
        if (crosses && tuning.moodDriftPerMinute > 0f) {
            candidates += nowMillis +
                minutesToMillis(abs(mood - tuning.happyThreshold) / tuning.moodDriftPerMinute)
        }

        return candidates.filter { it > nowMillis }.minOrNull()
    }

    private fun minutesToMillis(minutes: Float): Long =
        (minutes.coerceIn(0f, MAX_LOOKAHEAD_MINUTES) * MS_PER_MINUTE).toLong()

    /**
     * The day's event, landing at most once.
     *
     * Whether a day carries one is a pure function of the day number, so it
     * does not matter which of the four drivers reaches a given day first —
     * they all agree. Storing it is what makes it *land*: its instant effects
     * are applied here, and it stays on the snapshot until the day rolls over.
     */
    private fun rollEvent(snapshot: PetSnapshot, clock: Long): PetSnapshot {
        val day = Events.dayOf(clock)
        if (snapshot.event?.day == day) return snapshot

        val kind = Events.forDay(day)
            ?: return if (snapshot.event == null) snapshot else snapshot.copy(event = null)

        return snapshot.copy(
            event = PetEvent(kind, day),
            progress = snapshot.progress.plus(money = kind.instantMoney()),
            totalEarned = snapshot.totalEarned + kind.instantMoney(),
            stats = snapshot.stats.adjusted(energyBy = kind.instantEnergy()),
            journal = Journal.append(
                snapshot.journal,
                JournalEntry(JournalKind.EVENT, kind.name, kind.instantMoney(), clock),
            ),
        )
    }

    // --- the daily check-in --------------------------------------------------

    /**
     * The reason to open the app tomorrow.
     *
     * Called once when the app comes to the foreground. Everything else in the
     * game pays for *doing* something; this pays for turning up, and the streak
     * is what makes the fourth day in a row worth more than the first — 675 a
     * day at the top, which is a cafe shift for nothing.
     *
     * Keyed on the day index rather than on elapsed hours so that "a day" means
     * what the calendar says: opening the app at 23:50 and again at 00:10 is
     * two days, and playing all afternoon is still one.
     */
    fun claimDaily(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        val day = Events.dayOf(nowMillis)
        if (day == current.lastLoginDay) return current

        // Only an unbroken run continues; anything else starts over at one. A
        // fresh save lands here too, and gets day one.
        val streak = if (day == current.lastLoginDay + 1) current.streakDays + 1 else 1
        val reward = dailyReward(streak)
        val claimed = current.copy(
            progress = current.progress.plus(money = reward),
            totalEarned = current.totalEarned + reward,
            streakDays = streak,
            bestStreak = max(current.bestStreak, streak),
            lastLoginDay = day,
            pendingDaily = reward,
            journal = Journal.append(
                current.journal,
                JournalEntry(JournalKind.DAILY, streak.toString(), reward, nowMillis),
            ),
        )
        return withComeback(claimed, current.lastInteractionAt, nowMillis)
    }

    /**
     * A day away is met with a reunion rather than a corpse.
     *
     * The catch-up simulation is honest about a long absence: come back after a
     * day and she is at zero hunger, miserable, and probably ill. That is the
     * correct arithmetic and the wrong story — the pet the whole app is about
     * is not a punishment machine, and a player who returns to a wreck has
     * every reason not to return again. So she coped: she ate something, she
     * kept busy, and she is glad to see you. The floor is deliberately modest —
     * she is fine, not fresh — and it never *lowers* anything.
     */
    private fun withComeback(snapshot: PetSnapshot, lastInteractionAt: Long, nowMillis: Long): PetSnapshot {
        if (nowMillis - lastInteractionAt < COMEBACK_AWAY_MILLIS) return snapshot
        return snapshot.copy(
            stats = PetStats.coerced(
                hunger = max(snapshot.stats.hunger, COMEBACK_FLOOR),
                energy = snapshot.stats.energy,
                mood = max(snapshot.stats.mood, COMEBACK_FLOOR),
            ),
            journal = Journal.append(
                snapshot.journal,
                JournalEntry(JournalKind.COMEBACK, at = nowMillis),
            ),
        ).plusBond(Bond.COMEBACK, nowMillis)
    }

    /** What a check-in on day [streak] of a run pays. */
    fun dailyReward(streak: Int): Int =
        DAILY_BASE + DAILY_PER_STREAK_DAY * streak.coerceIn(1, DAILY_STREAK_CAP)

    /** Clears the check-in card once the player has seen what it paid. */
    fun acknowledgeDaily(snapshot: PetSnapshot): PetSnapshot =
        if (snapshot.pendingDaily == 0) snapshot else snapshot.copy(pendingDaily = 0)

    /** Marks the day's event as read, so it stops being announced. */
    fun acknowledgeEvent(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val event = snapshot.event ?: return snapshot
        if (event.acknowledged) return snapshot
        return snapshot.copy(event = event.copy(seenAt = nowMillis))
    }

    /**
     * One minute of wages.
     *
     * Pay is earned as the shift is worked rather than handed over at the end,
     * so a two-hour job is not two hours of nothing. The rate is scaled by how
     * she feels *this* minute, which means a shift that starts cheerful and
     * ends miserable pays somewhere in between — no retroactive adjustment, and
     * nothing can ever be taken back out of the wallet.
     */
    private fun accrue(snapshot: PetSnapshot, occupation: Occupation, clock: Long): PetSnapshot {
        val session = snapshot.session ?: return snapshot
        val mods = snapshot.modifiers()
        val rate = multiplierFor(qualityFor(snapshot.stats.mood), occupation.kind)
        val perMinute = occupation.payout.toFloat() / occupation.durationMinutes * rate

        // The boosts bought mid-shift land here, on the minutes they cover.
        val overtime = if (snapshot.hasEffect(EffectKind.OVERTIME, clock)) tuning.overtimeMultiplier else 1f
        val focus = if (snapshot.hasEffect(EffectKind.FOCUS, clock)) tuning.focusMultiplier else 1f

        var pay = session.accruedPay
        var exp = session.accruedExp
        when (occupation.kind) {
            OccupationKind.WORK -> {
                pay += perMinute * mods.pay * overtime
                exp += tuning.workExpPerMinute * mods.study * focus
            }
            OccupationKind.STUDY -> exp += perMinute * mods.study * focus
        }

        val payDue = pay.toInt() - session.paidOut
        val expDue = exp.toInt() - session.paidExp
        return snapshot.copy(
            progress = snapshot.progress.plus(money = payDue, exp = expDue),
            totalEarned = snapshot.totalEarned + payDue,
            session = session.copy(
                accruedPay = pay,
                paidOut = session.paidOut + payDue,
                accruedExp = exp,
                paidExp = session.paidExp + expDue,
            ),
        )
    }

    // --- care actions --------------------------------------------------------

    /** The free home-cooked meal behind the bubble's feed button. */
    fun feed(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canFeed(tuning)) return current
        return current.copy(
            stats = current.stats.adjusted(hungerBy = tuning.feedHunger, moodBy = tuning.feedMood),
            mealsFed = current.mealsFed + 1,
            lastInteractionAt = nowMillis,
        ).plusBond(Bond.FEED, nowMillis)
            .withEmote(Emote.EATING, nowMillis, tuning.eatingEmoteMillis)
    }

    /**
     * A head pat. Full value once [PetTuning.petFullEffectMinutes] have passed
     * since the last bit of attention, scaled down (but never to nothing) when
     * the player mashes it.
     */
    fun pet(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.acceptsPat) return current
        // Only a considered pat bonds — the same recharge that scales the mood.
        // Mashing keeps its trickle of mood but writes no history.
        val considered = patMultiplier(current, nowMillis) >= 0.6f
        return current.copy(
            stats = current.stats.adjusted(moodBy = patMood(current, nowMillis)),
            progress = current.progress.plus(exp = patExp(current, nowMillis)),
            lastInteractionAt = nowMillis,
        ).let { if (considered) it.plusBond(Bond.PAT, nowMillis) else it }
            .withEmote(Emote.LOVED, nowMillis, tuning.lovedEmoteMillis)
    }

    /**
     * EXP for keeping her company while she studies.
     *
     * Only while she is at her books, and only for a pat that was actually
     * *worth* something: the value is floored, so the 0.2 multiplier a masher
     * gets rounds to nothing and only a considered tap — roughly one every
     * hundred seconds — carries a point. Over a two-hour session that is at
     * most a third again on top of what the session itself teaches, which is
     * the right price for sitting with her rather than leaving her to it.
     */
    fun patExp(snapshot: PetSnapshot, nowMillis: Long): Int {
        if (snapshot.activity != PetActivity.STUDYING) return 0
        val multiplier = patMultiplier(snapshot, nowMillis)
        return (tuning.patExp * multiplier * snapshot.modifiers().study).toInt()
    }

    /**
     * What the next pat is worth, so the screen can say so.
     *
     * Full value once [PetTuning.petFullEffectMinutes] have passed since the
     * last bit of attention, scaled down — but never to nothing — when the
     * player mashes it. Public because a clicker whose number is invisible
     * reads as a clicker that does nothing, which is precisely what it was
     * accused of.
     */
    fun patMood(snapshot: PetSnapshot, nowMillis: Long): Float {
        if (!snapshot.acceptsPat) return 0f
        return tuning.petMood * patMultiplier(snapshot, nowMillis)
    }

    private fun patMultiplier(snapshot: PetSnapshot, nowMillis: Long): Float {
        val sinceInteraction = minutesBetween(snapshot.lastInteractionAt, nowMillis)
        return max(
            tuning.petMinimumMultiplier,
            min(1f, sinceInteraction / tuning.petFullEffectMinutes),
        )
    }

    /** Puts her to bed. Energy then climbs on the normal tick until she wakes up. */
    fun startSleep(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canSleep()) return current
        return current.copy(
            activity = PetActivity.SLEEPING,
            lastInteractionAt = nowMillis,
            emote = null,
            emoteUntil = 0L,
        )
    }

    /** Wakes her early. Allowed from the app, the panel and a long press. */
    fun wake(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.isSleeping) return current
        return current.copy(activity = PetActivity.AWAKE, lastInteractionAt = nowMillis)
    }

    // --- work & study --------------------------------------------------------

    fun startOccupation(snapshot: PetSnapshot, occupation: Occupation, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canStart(occupation, tuning)) return current
        return current.copy(
            activity = when (occupation.kind) {
                OccupationKind.WORK -> PetActivity.WORKING
                OccupationKind.STUDY -> PetActivity.STUDYING
            },
            session = ActivitySession(
                occupationId = occupation.id,
                startedAt = nowMillis,
                endsAt = nowMillis + occupation.durationMinutes * MS_PER_MINUTE,
            ),
            lastInteractionAt = nowMillis,
            emote = null,
            emoteUntil = 0L,
        )
    }

    /** Calling her home early. She is paid for the time she actually put in. */
    fun cancelOccupation(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        current.session ?: return current
        val occupation = current.occupation ?: return current.copy(
            session = null,
            activity = PetActivity.AWAKE,
        )
        return complete(current, occupation, nowMillis, cancelled = true)
    }

    /**
     * Ends a session.
     *
     * Almost everything has been paid already, minute by minute; all that is
     * left is the fraction of a coin still banked in the session. Leaving early
     * therefore needs no penalty formula — she simply stops earning, and keeps
     * what the hours she actually worked were worth.
     */
    private fun complete(
        snapshot: PetSnapshot,
        occupation: Occupation,
        atMillis: Long,
        cancelled: Boolean,
    ): PetSnapshot {
        val session = snapshot.session
        val remainderPay = ((session?.accruedPay ?: 0f).roundToInt() - (session?.paidOut ?: 0))
            .coerceAtLeast(0)
        val remainderExp = ((session?.accruedExp ?: 0f).roundToInt() - (session?.paidExp ?: 0))
            .coerceAtLeast(0)

        val money = (session?.paidOut ?: 0) + remainderPay
        val exp = (session?.paidExp ?: 0) + remainderExp
        val finished = !cancelled
        val isWork = occupation.kind == OccupationKind.WORK

        return snapshot.copy(
            progress = snapshot.progress.plus(money = remainderPay, exp = remainderExp),
            totalEarned = snapshot.totalEarned + remainderPay,
            activity = PetActivity.AWAKE,
            session = null,
            // A finished session is shared history: it counts, and it bonds.
            shiftsWorked = snapshot.shiftsWorked + (if (finished && isWork) 1 else 0),
            lessonsDone = snapshot.lessonsDone + (if (finished && !isWork) 1 else 0),
            lastOutcome = ActivityOutcome(
                occupationId = occupation.id,
                kind = occupation.kind,
                money = money,
                exp = exp,
                quality = if (cancelled) OutcomeQuality.POOR else qualityFor(snapshot.stats.mood),
                cancelled = cancelled,
                completedAt = atMillis,
            ),
            journal = Journal.append(
                snapshot.journal,
                JournalEntry(
                    kind = if (isWork) JournalKind.SHIFT_DONE else JournalKind.LESSON_DONE,
                    detail = occupation.id,
                    amount = if (isWork) money else exp,
                    at = atMillis,
                ),
            ),
        ).let {
            if (finished) it.plusBond(if (isWork) Bond.SHIFT else Bond.LESSON, atMillis) else it
        }.withEmote(Emote.CELEBRATING, atMillis, tuning.celebrateEmoteMillis)
    }

    fun qualityFor(mood: Float): OutcomeQuality = when {
        mood >= tuning.greatMoodThreshold -> OutcomeQuality.GREAT
        mood >= tuning.goodMoodThreshold -> OutcomeQuality.GOOD
        mood >= tuning.poorMoodThreshold -> OutcomeQuality.POOR
        else -> OutcomeQuality.BAD
    }

    fun multiplierFor(quality: OutcomeQuality, kind: OccupationKind): Float = when (quality) {
        OutcomeQuality.GREAT -> tuning.greatMultiplier
        OutcomeQuality.GOOD -> tuning.goodMultiplier
        OutcomeQuality.POOR ->
            if (kind == OccupationKind.WORK) tuning.poorWorkMultiplier else tuning.poorStudyMultiplier
        OutcomeQuality.BAD ->
            if (kind == OccupationKind.WORK) tuning.badWorkMultiplier else tuning.badStudyMultiplier
    }

    /**
     * How well she is being paid *this minute*, and what that is worth.
     *
     * The mood-to-pay curve is the one rule the player can act on mid-shift —
     * a pat lifts mood, mood lifts wages — and until now it was invisible until
     * the shift ended and the result card said "poor". Same two functions the
     * accrual uses, so the number on screen cannot drift from the number in the
     * wallet.
     */
    fun currentQuality(snapshot: PetSnapshot): OutcomeQuality = qualityFor(snapshot.stats.mood)

    fun currentPayMultiplier(snapshot: PetSnapshot): Float = multiplierFor(
        currentQuality(snapshot),
        // Off the clock there is no running job to ask; work is the honest
        // preview, since it is what the button on the card would start.
        snapshot.occupation?.kind ?: OccupationKind.WORK,
    )

    /** Preview of what a session would pay right now — the UI shows it on the card. */
    fun projectedPayout(snapshot: PetSnapshot, occupation: Occupation): Int {
        val multiplier = multiplierFor(qualityFor(snapshot.stats.mood), occupation.kind)
        return (occupation.payout * multiplier).roundToInt()
    }

    fun acknowledgeOutcome(snapshot: PetSnapshot): PetSnapshot = snapshot.copy(lastOutcome = null)

    // --- mini-game -----------------------------------------------------------

    fun startPlaying(snapshot: PetSnapshot, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        // A sick pet does not go to the arcade — rest and medicine first.
        if (!current.acceptsInteraction || current.isSick) return current
        return current.copy(activity = PetActivity.PLAYING, lastInteractionAt = nowMillis)
    }

    /** Ends the mini-game and applies its score. Playing lifts mood but costs energy. */
    fun finishPlaying(
        snapshot: PetSnapshot,
        score: Int,
        nowMillis: Long,
        game: MiniGame = MiniGame.CATCH,
    ): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        // Not gated on still being PLAYING: a round can outlive the screen that
        // started it, and the score should still count when it comes back.
        if (score <= 0 && current.activity != PetActivity.PLAYING) return current
        val mods = current.modifiers()
        val played = score > 0
        return current.copy(
            activity = PetActivity.AWAKE,
            stats = current.stats.adjusted(
                energyBy = -game.energyCost(score),
                moodBy = game.moodGain(score) * mods.play,
            ),
            progress = current.progress.plus(money = game.coins(score)),
            totalEarned = current.totalEarned + game.coins(score),
            gamesPlayed = current.gamesPlayed + (if (played) 1 else 0),
            // The best round is remembered per game, and only ever climbs.
            bestScores = if (score > (current.bestScores[game] ?: 0)) {
                current.bestScores + (game to score)
            } else {
                current.bestScores
            },
            lastInteractionAt = nowMillis,
        ).let { if (played) it.plusBond(Bond.GAME, nowMillis) else it }
            // "Поиграй со мной!" — a round actually played grants the wish.
            .let { if (played) grantRequestIf(it, nowMillis) { r -> r.kind == RequestKind.PLAY } else it }
            .withEmote(Emote.CELEBRATING, nowMillis, tuning.celebrateEmoteMillis)
    }

    // --- shop ----------------------------------------------------------------

    /** Buys and immediately applies [item]. There is no inventory to manage. */
    fun buy(snapshot: PetSnapshot, item: ShopItem, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canBuy(item, nowMillis)) return current

        // A second dose *extends* the debt from where the first one ends rather
        // than restarting it from now. Nothing in the shop can stack today —
        // [PetSnapshot.canBuy] refuses a pill while its effect is still
        // running — but a rule that silently forgives the overlap is exactly
        // how the cash advance became free money, and it must not come back
        // the next time something is allowed to double up.
        val effects = if (item.effect != null) {
            val running = current.effects.firstOrNull { it.kind == item.effect && it.isActive(nowMillis) }
            val from = max(nowMillis, running?.expiresAt ?: nowMillis)
            current.effects.filterNot { it.kind == item.effect } +
                ActiveEffect(item.effect, from + item.effectMinutes * MS_PER_MINUTE)
        } else {
            current.effects
        }

        val emote = when (item.category) {
            ShopCategory.FOOD -> Emote.EATING
            ShopCategory.GIFT -> Emote.LOVED
            ShopCategory.PILL, ShopCategory.BOOST, ShopCategory.CARE -> Emote.CELEBRATING
        }
        val emoteMillis = when (item.category) {
            ShopCategory.FOOD -> tuning.eatingEmoteMillis
            ShopCategory.GIFT -> tuning.lovedEmoteMillis
            ShopCategory.PILL, ShopCategory.BOOST, ShopCategory.CARE -> tuning.celebrateEmoteMillis
        }

        // Feeding her the same thing over and over is something she notices.
        val repeated = if (item.category == ShopCategory.FOOD) {
            if (current.lastMealId == item.id) current.repeatedMeals + 1 else 1
        } else {
            current.repeatedMeals
        }

        // Medicine is the one purchase that changes her state instead of her
        // stats: it is the cure, and nursing her through it is remembered.
        val cures = item.id == Shop.MEDICINE_ID && current.isSick

        return current.copy(
            stats = current.stats.adjusted(
                hungerBy = item.hunger,
                energyBy = item.energy,
                moodBy = item.mood,
            ),
            progress = current.progress.plus(money = item.money - item.price, exp = item.exp),
            totalEarned = current.totalEarned + item.money.coerceAtLeast(0),
            effects = effects,
            lastInteractionAt = nowMillis,
            lastMealId = if (item.category == ShopCategory.FOOD) item.id else current.lastMealId,
            repeatedMeals = repeated,
            mealsFed = current.mealsFed + (if (item.category == ShopCategory.FOOD) 1 else 0),
            giftsGiven = current.giftsGiven + (if (item.category == ShopCategory.GIFT) 1 else 0),
            sickSince = if (cures) 0L else current.sickSince,
            runDownMinutes = if (cures) 0f else current.runDownMinutes,
            sicknessesNursed = current.sicknessesNursed + (if (cures) 1 else 0),
            // Spending the day together is the one purchase the calendar
            // rations, so the day it happened is part of the save.
            dayOffDay = if (item.id == Shop.DAY_OFF_ID) Events.dayOf(nowMillis) else current.dayOffDay,
        ).let {
            when {
                cures -> it.plusBond(Bond.NURSED, nowMillis)
                item.category == ShopCategory.FOOD -> it.plusBond(Bond.FEED, nowMillis)
                item.category == ShopCategory.GIFT -> it.plusBond(Bond.GIFT, nowMillis)
                item.category == ShopCategory.CARE -> it.plusBond(Bond.DAY_OFF, nowMillis)
                else -> it
            }
        }.let { bought ->
            // "Мне бы онигири…" — the exact thing she asked for, while the wish
            // still stands, is worth more than any unprompted treat.
            grantRequestIf(bought, nowMillis) { r ->
                (r.kind == RequestKind.FOOD || r.kind == RequestKind.GIFT) && r.itemId == item.id
            }
        }.withEmote(emote, nowMillis, emoteMillis)
    }

    /**
     * Commits her to a path — see [Focus]. Once, and for good.
     *
     * Refusals are silent (level too low, or a path already chosen) because the
     * UI never offers the choice in those states; a hostile caller changing
     * nothing is exactly right.
     */
    fun chooseFocus(snapshot: PetSnapshot, focus: Focus, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (current.focus != null || current.level < Focus.UNLOCK_LEVEL) return current
        return current.copy(focus = focus, lastInteractionAt = nowMillis)
            .withEmote(Emote.CELEBRATING, nowMillis, tuning.celebrateEmoteMillis)
    }

    /** Marks the story caught up to, so the chapter card stops showing. */
    fun acknowledgeStory(snapshot: PetSnapshot): PetSnapshot =
        snapshot.copy(storySeen = snapshot.storyChapter)

    /**
     * Buys something permanent.
     *
     * Unlike everything else in the shop this is not consumed, so it is the
     * only purchase that is worth saving for rather than spending on.
     */
    fun buyUpgrade(snapshot: PetSnapshot, upgrade: Upgrade, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        if (!current.canBuy(upgrade)) return current
        return current.copy(
            progress = current.progress.plus(money = -upgrade.price),
            owned = current.owned + upgrade.id,
            // Buying an outfit puts it on; there is no reason to make the
            // player then find it in a list and tap it again. A theme is the
            // same purchase in a different drawer.
            outfit = if (upgrade.kind == UpgradeKind.OUTFIT) upgrade.id else current.outfit,
            theme = if (upgrade.kind == UpgradeKind.THEME) upgrade.id else current.theme,
            lastInteractionAt = nowMillis,
        ).withEmote(Emote.CELEBRATING, nowMillis, tuning.celebrateEmoteMillis)
    }

    /** Changes into an outfit she already owns. */
    fun wear(snapshot: PetSnapshot, upgradeId: String, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        val upgrade = Upgrades.byId(upgradeId) ?: return current
        if (upgrade.kind != UpgradeKind.OUTFIT || !current.owns(upgradeId)) return current
        return current.copy(outfit = upgradeId, lastInteractionAt = nowMillis)
            .withEmote(Emote.LOVED, nowMillis, tuning.lovedEmoteMillis)
    }

    /** Redecorates the room with a theme she already owns. */
    fun applyTheme(snapshot: PetSnapshot, upgradeId: String, nowMillis: Long): PetSnapshot {
        val current = advanceTo(snapshot, nowMillis)
        val upgrade = Upgrades.byId(upgradeId) ?: return current
        if (upgrade.kind != UpgradeKind.THEME || !current.owns(upgradeId)) return current
        return current.copy(theme = upgradeId, lastInteractionAt = nowMillis)
            .withEmote(Emote.LOVED, nowMillis, tuning.lovedEmoteMillis)
    }

    // --- mood ----------------------------------------------------------------

    /**
     * Where mood is heading right now: the average of the two hard stats, minus
     * a penalty that grows the longer she is left alone.
     */
    fun moodTarget(stats: PetStats, minutesSinceInteraction: Float): Float {
        val base = tuning.moodHungerWeight * stats.hunger + tuning.moodEnergyWeight * stats.energy
        val neglect = min(tuning.maxNeglectPenalty, minutesSinceInteraction / tuning.neglectMinutesPerPoint)
        return PetStats.clamp(base - neglect)
    }

    private fun driftMood(mood: Float, hunger: Float, energy: Float, minutesSinceInteraction: Float): Float {
        val target = moodTarget(PetStats.coerced(hunger, energy, mood), minutesSinceInteraction)
        val step = (target - mood).coerceIn(-tuning.moodDriftPerMinute, tuning.moodDriftPerMinute)
        return PetStats.clamp(mood + step)
    }

    private fun minutesBetween(fromMillis: Long, toMillis: Long): Float =
        max(0f, (toMillis - fromMillis).toFloat() / MS_PER_MINUTE)

    private fun PetSnapshot.withEmote(emote: Emote, nowMillis: Long, durationMillis: Long) =
        copy(emote = emote, emoteUntil = nowMillis + durationMillis)

    private fun PetSnapshot.withExpiredEffectsDropped(nowMillis: Long): PetSnapshot {
        val live = effects.filter { it.isActive(nowMillis) }
        return if (live.size == effects.size) this else copy(effects = live)
    }

    companion object {
        const val MS_PER_MINUTE = 60_000L

        /** The check-in: a flat welcome, plus a day's worth of streak. */
        const val DAILY_BASE = 150
        const val DAILY_PER_STREAK_DAY = 75

        /**
         * Where the streak stops paying more.
         *
         * A week is long enough that keeping it going is an achievement and
         * short enough that a player who breaks one is a week from the top
         * again rather than a month — a curve that keeps climbing forever turns
         * a missed day into a reason to stop playing.
         */
        const val DAILY_STREAK_CAP = 7

        /** Away this long and coming back is a reunion — see [withComeback]. */
        const val COMEBACK_AWAY_MILLIS = 24L * 60 * 60 * 1000

        /** Where she has kept herself while you were gone. Fine, not fresh. */
        const val COMEBACK_FLOOR = 55f

        /**
         * How far ahead [nextVisibleChangeAt] will look.
         *
         * Beyond half a day an estimate built from current rates is fiction —
         * something will have been fed, slept or sent to work long before then —
         * and the quarter-hour heartbeat covers that ground anyway.
         */
        private const val MAX_LOOKAHEAD_MINUTES = 12f * 60f
    }
}

/**
 * The arcade.
 *
 * Playing is the mood lever that does not need money — but it burns energy, so
 * it cannot replace sleeping and eating.
 *
 * Three games, three different things to be good at: catching things where they
 * appear, pressing on the beat, and remembering an order. Each reports a score
 * in whatever units suit it — a rhythm round runs to a couple of hundred, a
 * catch round to twenty-five — so the payout coefficients live here, per game,
 * tuned so that *a good round is worth about the same whichever one you played*.
 * Otherwise the arcade collapses to whichever game pays best.
 */
enum class MiniGame(
    val durationSeconds: Int,
    private val moodPerPoint: Float,
    private val energyPerPoint: Float,
    private val pointsPerCoin: Int,
) {
    /** Tap the things that pop up. Reflex, aimed. */
    CATCH(20, moodPerPoint = 0.9f, energyPerPoint = 0.15f, pointsPerCoin = 3),

    /** Tap on the beat as the rings close. Timing, no aim at all. */
    RHYTHM(28, moodPerPoint = 0.26f, energyPerPoint = 0.045f, pointsPerCoin = 12),

    /** Watch the order, repeat the order. Recall, no reflex at all. */
    MEMORY(30, moodPerPoint = 0.8f, energyPerPoint = 0.13f, pointsPerCoin = 3),
    ;

    fun moodGain(score: Int): Float = min(MAX_MOOD, BASE_MOOD + score * moodPerPoint)

    fun energyCost(score: Int): Float = BASE_ENERGY + score * energyPerPoint

    /** A few coins so an empty wallet is never a dead end. */
    fun coins(score: Int): Int = score / pointsPerCoin

    companion object {
        /** Turning up is worth something, even on a round you fluff. */
        const val BASE_MOOD = 6f
        /** …but no round can carry her mood on its own. */
        const val MAX_MOOD = 32f
        const val BASE_ENERGY = 8f

        fun byName(name: String?): MiniGame = entries.firstOrNull { it.name == name } ?: CATCH
    }
}

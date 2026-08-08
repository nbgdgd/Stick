package com.vpet.waifu.data

import com.vpet.waifu.data.db.PetStateEntity
import com.vpet.waifu.domain.PetProgress
import com.vpet.waifu.domain.Upgrades
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * The save file, as an actual file.
 *
 * A pet raised over months lives in one SQLite row inside the app's private
 * storage, which means a new phone, a reinstall or a "clear data" tap is the
 * end of her — and the one thing a virtual pet cannot survive is being lost by
 * accident. This turns that row into a portable document: one JSON object the
 * player owns, can put in their own cloud, and can restore anywhere.
 *
 * Three things make it safe to read back:
 *
 *  - **[FORMAT]** — so a photo, a text file or somebody else's JSON is refused
 *    on the first line rather than silently decoded into a blank pet.
 *  - **[VERSION]** — the schema of the file, deliberately independent of the
 *    Room version. A file from a *newer* build is refused instead of guessed
 *    at; a file from an older one is read with today's defaults filling the
 *    gaps, exactly like a database migration.
 *  - **the checksum** — a cheap hash over the payload, not a signature. It
 *    catches the realistic accident (a truncated copy, a file mangled by a
 *    messenger app) and makes no claim at all about a determined editor. The
 *    save is the player's own; a game with no server has nothing to cheat.
 */
object SaveCodec {

    /** What the file says it is. */
    const val FORMAT = "vpet-save"

    /** The file schema. Bumped only when a field is renamed or dropped. */
    const val VERSION = 1

    fun encode(entity: PetStateEntity): String {
        val save = payloadOf(entity)
        return JSONObject().apply {
            put(KEY_FORMAT, FORMAT)
            put(KEY_VERSION, VERSION)
            put(KEY_CHECKSUM, checksumOf(save))
            put(KEY_SAVE, save)
        }.toString(2)
    }

    /**
     * Reads a file back, or explains why it cannot be.
     *
     * A [Result] rather than an exception or a null: importing is a thing the
     * player asked for by name, so the failure has to reach a message on
     * screen, and every way this can go wrong is somebody else's file rather
     * than a bug.
     */
    fun decode(text: String): Result<PetStateEntity> = runCatching {
        val root = JSONObject(text)
        require(root.optString(KEY_FORMAT) == FORMAT) { "not a $FORMAT file" }
        val version = root.optInt(KEY_VERSION, 0)
        require(version in 1..VERSION) { "unsupported save version: $version" }
        val save = root.getJSONObject(KEY_SAVE)
        require(root.optString(KEY_CHECKSUM) == checksumOf(save)) { "save file is damaged" }
        entityOf(save)
    }

    fun exportTo(entity: PetStateEntity, out: OutputStream): Result<Unit> = runCatching {
        out.write(encode(entity).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    fun importFrom(input: InputStream): Result<PetStateEntity> =
        runCatching { input.readBytes().toString(Charsets.UTF_8) }
            .mapCatching { decode(it).getOrThrow() }

    private fun payloadOf(entity: PetStateEntity): JSONObject = JSONObject().apply {
        // The row is flat by design, so the file is too: one key per column,
        // named after the column. Nothing here is computed, which is what makes
        // "did the codec forget a field?" a question a test can answer.
        put("hunger", entity.hunger.toDouble())
        put("energy", entity.energy.toDouble())
        put("mood", entity.mood.toDouble())
        put("activity", entity.activity)
        put("lastTickAt", entity.lastTickAt)
        put("lastInteractionAt", entity.lastInteractionAt)
        put("money", entity.money)
        put("exp", entity.exp)
        putOrNull("sessionOccupationId", entity.sessionOccupationId)
        put("sessionStartedAt", entity.sessionStartedAt)
        put("sessionEndsAt", entity.sessionEndsAt)
        put("sessionAccruedPay", entity.sessionAccruedPay.toDouble())
        put("sessionPaidOut", entity.sessionPaidOut)
        put("sessionAccruedExp", entity.sessionAccruedExp.toDouble())
        put("sessionPaidExp", entity.sessionPaidExp)
        put("effects", entity.effects)
        put("owned", entity.owned)
        put("outfit", entity.outfit)
        put("theme", entity.theme)
        putOrNull("eventKind", entity.eventKind)
        put("eventDay", entity.eventDay)
        put("eventSeenAt", entity.eventSeenAt)
        putOrNull("lastMealId", entity.lastMealId)
        put("repeatedMeals", entity.repeatedMeals)
        putOrNull("outcomeOccupationId", entity.outcomeOccupationId)
        putOrNull("outcomeKind", entity.outcomeKind)
        put("outcomeMoney", entity.outcomeMoney)
        put("outcomeExp", entity.outcomeExp)
        putOrNull("outcomeQuality", entity.outcomeQuality)
        put("outcomeCancelled", entity.outcomeCancelled)
        put("outcomeAt", entity.outcomeAt)
        putOrNull("emote", entity.emote)
        put("emoteUntil", entity.emoteUntil)
        put("passiveSince", entity.passiveSince)
        put("passiveBank", entity.passiveBank.toDouble())
        put("passiveDay", entity.passiveDay)
        put("passivePaidToday", entity.passivePaidToday)
        put("bondPoints", entity.bondPoints)
        put("bondDay", entity.bondDay)
        put("bondToday", entity.bondToday)
        put("sickSince", entity.sickSince)
        put("runDownMinutes", entity.runDownMinutes.toDouble())
        putOrNull("requestKind", entity.requestKind)
        putOrNull("requestItemId", entity.requestItemId)
        put("requestUntil", entity.requestUntil)
        put("lastRequestSlot", entity.lastRequestSlot)
        put("storyChapter", entity.storyChapter)
        put("storySeen", entity.storySeen)
        putOrNull("focus", entity.focus)
        put("shiftsWorked", entity.shiftsWorked)
        put("lessonsDone", entity.lessonsDone)
        put("gamesPlayed", entity.gamesPlayed)
        put("mealsFed", entity.mealsFed)
        put("giftsGiven", entity.giftsGiven)
        put("sicknessesNursed", entity.sicknessesNursed)
        put("totalEarned", entity.totalEarned)
        put("bornAt", entity.bornAt)
        put("journal", entity.journal)
        put("bestCatch", entity.bestCatch)
        put("bestRhythm", entity.bestRhythm)
        put("bestMemory", entity.bestMemory)
        put("goalWeek", entity.goalWeek)
        put("goalBaseline", entity.goalBaseline)
        put("goalRewarded", entity.goalRewarded)
        put("celebratedMilestone", entity.celebratedMilestone)
        put("streakDays", entity.streakDays)
        put("bestStreak", entity.bestStreak)
        put("lastLoginDay", entity.lastLoginDay)
        put("dayOffDay", entity.dayOffDay)
        put("pendingDaily", entity.pendingDaily)
        put("patsGiven", entity.patsGiven)
        put("arcadeDay", entity.arcadeDay)
        put("arcadePlayed", entity.arcadePlayed)
        put("arcadeStreak", entity.arcadeStreak)
        put("luckyGames", entity.luckyGames)
        put("questDay", entity.questDay)
        put("questBaselines", entity.questBaselines)
        put("questClaimed", entity.questClaimed)
        put("findReadyAt", entity.findReadyAt)
        put("choreDoneAt", entity.choreDoneAt)
        put("sessionCheckpointsPaid", entity.sessionCheckpointsPaid)
        put("sessionStake", entity.sessionStake)
        putOrNull("sessionStakeTier", entity.sessionStakeTier)
        put("outcomeStake", entity.outcomeStake)
        put("outcomeStakeReturned", entity.outcomeStakeReturned)
        putOrNull("outcomeStakeTier", entity.outcomeStakeTier)
        put("sceneDay", entity.sceneDay)
        put("sceneAnswered", entity.sceneAnswered)
    }

    private fun entityOf(save: JSONObject): PetStateEntity = PetStateEntity(
        // The six columns that were there in v1 are demanded rather than
        // defaulted: a payload missing them is not an old save, it is not a
        // save. Everything since carries the same default the entity does, so
        // a file written by an older build reads exactly like an older row.
        hunger = save.getDouble("hunger").toFloat(),
        energy = save.getDouble("energy").toFloat(),
        mood = save.getDouble("mood").toFloat(),
        activity = save.getString("activity"),
        lastTickAt = save.getLong("lastTickAt"),
        lastInteractionAt = save.getLong("lastInteractionAt"),
        money = save.optInt("money", PetProgress.START_MONEY),
        exp = save.optInt("exp"),
        sessionOccupationId = save.stringOrNull("sessionOccupationId"),
        sessionStartedAt = save.optLong("sessionStartedAt"),
        sessionEndsAt = save.optLong("sessionEndsAt"),
        sessionAccruedPay = save.optDouble("sessionAccruedPay", 0.0).toFloat(),
        sessionPaidOut = save.optInt("sessionPaidOut"),
        sessionAccruedExp = save.optDouble("sessionAccruedExp", 0.0).toFloat(),
        sessionPaidExp = save.optInt("sessionPaidExp"),
        effects = save.optString("effects"),
        owned = save.optString("owned", Upgrades.DEFAULT_OUTFIT),
        outfit = save.optString("outfit", Upgrades.DEFAULT_OUTFIT),
        theme = save.optString("theme", Upgrades.DEFAULT_THEME),
        eventKind = save.stringOrNull("eventKind"),
        eventDay = save.optLong("eventDay"),
        eventSeenAt = save.optLong("eventSeenAt"),
        lastMealId = save.stringOrNull("lastMealId"),
        repeatedMeals = save.optInt("repeatedMeals"),
        outcomeOccupationId = save.stringOrNull("outcomeOccupationId"),
        outcomeKind = save.stringOrNull("outcomeKind"),
        outcomeMoney = save.optInt("outcomeMoney"),
        outcomeExp = save.optInt("outcomeExp"),
        outcomeQuality = save.stringOrNull("outcomeQuality"),
        outcomeCancelled = save.optBoolean("outcomeCancelled"),
        outcomeAt = save.optLong("outcomeAt"),
        emote = save.stringOrNull("emote"),
        emoteUntil = save.optLong("emoteUntil"),
        passiveSince = save.optLong("passiveSince"),
        passiveBank = save.optDouble("passiveBank", 0.0).toFloat(),
        passiveDay = save.optLong("passiveDay"),
        passivePaidToday = save.optInt("passivePaidToday"),
        bondPoints = save.optInt("bondPoints"),
        bondDay = save.optLong("bondDay"),
        bondToday = save.optInt("bondToday"),
        sickSince = save.optLong("sickSince"),
        runDownMinutes = save.optDouble("runDownMinutes", 0.0).toFloat(),
        requestKind = save.stringOrNull("requestKind"),
        requestItemId = save.stringOrNull("requestItemId"),
        requestUntil = save.optLong("requestUntil"),
        lastRequestSlot = save.optLong("lastRequestSlot"),
        storyChapter = save.optInt("storyChapter"),
        storySeen = save.optInt("storySeen"),
        focus = save.stringOrNull("focus"),
        shiftsWorked = save.optInt("shiftsWorked"),
        lessonsDone = save.optInt("lessonsDone"),
        gamesPlayed = save.optInt("gamesPlayed"),
        mealsFed = save.optInt("mealsFed"),
        giftsGiven = save.optInt("giftsGiven"),
        sicknessesNursed = save.optInt("sicknessesNursed"),
        totalEarned = save.optInt("totalEarned"),
        bornAt = save.optLong("bornAt"),
        journal = save.optString("journal"),
        bestCatch = save.optInt("bestCatch"),
        bestRhythm = save.optInt("bestRhythm"),
        bestMemory = save.optInt("bestMemory"),
        goalWeek = save.optLong("goalWeek"),
        goalBaseline = save.optInt("goalBaseline"),
        goalRewarded = save.optBoolean("goalRewarded"),
        celebratedMilestone = save.optInt("celebratedMilestone"),
        streakDays = save.optInt("streakDays"),
        bestStreak = save.optInt("bestStreak"),
        lastLoginDay = save.optLong("lastLoginDay"),
        dayOffDay = save.optLong("dayOffDay"),
        pendingDaily = save.optInt("pendingDaily"),
        patsGiven = save.optInt("patsGiven"),
        arcadeDay = save.optLong("arcadeDay"),
        arcadePlayed = save.optInt("arcadePlayed"),
        arcadeStreak = save.optInt("arcadeStreak"),
        luckyGames = save.optInt("luckyGames"),
        questDay = save.optLong("questDay"),
        questBaselines = save.optString("questBaselines"),
        questClaimed = save.optInt("questClaimed"),
        findReadyAt = save.optLong("findReadyAt"),
        choreDoneAt = save.optString("choreDoneAt"),
        sessionCheckpointsPaid = save.optInt("sessionCheckpointsPaid"),
        sessionStake = save.optInt("sessionStake"),
        sessionStakeTier = save.stringOrNull("sessionStakeTier"),
        outcomeStake = save.optInt("outcomeStake"),
        outcomeStakeReturned = save.optInt("outcomeStakeReturned"),
        outcomeStakeTier = save.stringOrNull("outcomeStakeTier"),
        sceneDay = save.optLong("sceneDay"),
        sceneAnswered = save.optInt("sceneAnswered"),
    )

    /**
     * A hash of the payload, over its keys in sorted order.
     *
     * Sorted rather than "whatever the JSON text says" so that a file
     * re-serialised with its keys in another order — by a different JSON
     * implementation, or by a user's editor — still verifies. FNV-1a because
     * it is eight lines, needs no crypto provider, and is exactly as strong as
     * this needs to be.
     */
    private fun checksumOf(save: JSONObject): String {
        val canonical = save.keys().asSequence().sorted().joinToString("\n") { key ->
            "$key=${canonicalValue(save.get(key))}"
        }
        var hash = FNV_OFFSET
        canonical.forEach { char ->
            hash = (hash xor char.code) * FNV_PRIME
        }
        return "%08x".format(hash)
    }

    /**
     * One rendering of a value that survives being written and read back.
     *
     * JSON has one number type and the writer drops the decimal point: a mood
     * of exactly 80.0 goes out as `80` and comes back an `Int`, so hashing
     * `toString()` would fail its own file for every pet whose stats happened
     * to land on a whole number — a corruption warning over nothing, which is
     * far worse than no warning at all. Every number is compared as a double,
     * which is the only type the file can actually distinguish.
     */
    private fun canonicalValue(value: Any?): String = when (value) {
        is Number -> value.toDouble().toString()
        JSONObject.NULL, null -> "null"
        else -> value.toString()
    }

    private fun JSONObject.putOrNull(key: String, value: String?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    private const val KEY_FORMAT = "format"
    private const val KEY_VERSION = "version"
    private const val KEY_CHECKSUM = "checksum"
    private const val KEY_SAVE = "save"

    private const val FNV_OFFSET = -2_128_831_035
    private const val FNV_PRIME = 16_777_619
}

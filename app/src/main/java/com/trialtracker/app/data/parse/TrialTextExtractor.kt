package com.trialtracker.app.data.parse

/**
 * Pulls a free-trial length out of a service's own public pricing page.
 *
 * Why this exists: nothing publishes "app X has an N-day trial" in a structured
 * form. Measured on the 20 services in the catalog — the Play listing's
 * description mentions a trial for exactly one of them (the other apparent hits
 * were review text, not the developer's copy), and there is no ISO-8601 offer
 * data on the page. What *is* stated in plain words, on a page the service
 * publishes itself, is the trial length: "7-day Free Trial", "Try 14-days free",
 * "First 30 days free". So that is what gets parsed.
 *
 * Coverage is partial by nature — see TrialProbeSource — so a finding always
 * carries the phrase it came from, and the UI shows whether a trial was verified
 * automatically or by hand.
 */
object TrialTextExtractor {

    data class Finding(
        val days: Int,
        val phrase: String,
        val occurrences: Int,
    )

    private val patterns = listOf(
        // "7-day free trial", "14 days free", "30-day trial"
        Regex("""(?iU)(\d{1,3})\s*[-‑–]?\s*(days?|months?|weeks?)\s*(?:of\s+)?(?:free\s*trial|free\b|trial\b)"""),
        // "free for 30 days", "free trial for 7 days"
        Regex("""(?iU)free\s*(?:trial\s*)?(?:for\s+)?(\d{1,3})[\s-]*(days?|months?|weeks?)"""),
        // "try Premium free for 1 month"
        Regex("""(?iU)try\s+(?:\w+\s+){0,3}free\s+for\s+(\d{1,3})[\s-]*(days?|months?|weeks?)"""),
        // "30 дней бесплатно", "14 дней пробного периода"
        Regex("""(?iU)(\d{1,3})\s*(дн\w+|месяц\w*|недел\w+)\s*(?:бесплатно|пробн\w*)"""),
        // "бесплатно 30 дней", "бесплатный период 14 дней"
        Regex("""(?iU)бесплатн\w*\s*(?:период\s*)?(?:на\s*)?(\d{1,3})\s*(дн\w+|месяц\w*|недел\w+)"""),
        // "пробный период 7 дней"
        Regex("""(?iU)пробн\w*\s*(?:период|подписк\w*)?\s*(?:на\s+)?(\d{1,3})\s*(дн\w+|месяц\w*|недел\w+)"""),
    )

    private val tags = Regex("""<[^>]+>""")
    private val whitespace = Regex("""\s+""")

    /**
     * Pricing pages are single-page apps: the visible copy usually lives inside a
     * JSON blob in a <script>, not in the markup. Stripping scripts before
     * searching loses most of the matches, so tags are flattened and the rest of
     * the document is kept as-is.
     */
    fun htmlToText(html: String): String {
        val unescaped = html
            .replace("\\u002F", "/")
            .replace("\\n", " ")
            .replace("&amp;", "&")
            .replace("&nbsp;", " ")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
        return whitespace.replace(tags.replace(unescaped, " "), " ")
    }

    /**
     * A page may advertise several trials (different plans, or a promo alongside
     * the standard offer). The most frequently repeated length wins, and ties go
     * to the shortest — overstating a trial is the worse error.
     */
    fun extract(text: String): Finding? {
        val counts = mutableMapOf<Int, Int>()
        val phrases = mutableMapOf<Int, String>()

        patterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                val amount = match.groupValues[1].toIntOrNull() ?: return@forEach
                val days = toDays(amount, match.groupValues[2]) ?: return@forEach
                if (days !in 1..MAX_DAYS) return@forEach
                if (!isTrialish(days, match.value)) return@forEach
                counts[days] = (counts[days] ?: 0) + 1
                phrases.putIfAbsent(days, match.value.trim())
            }
        }

        val winner = counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .firstOrNull() ?: return null

        return Finding(
            days = winner.key,
            phrase = phrases[winner.key].orEmpty(),
            occurrences = winner.value,
        )
    }

    /**
     * "3 months of free", "6 months FREE" — months bundled into an annual plan,
     * not a trial. Past a couple of months, only accept a match the page itself
     * calls a trial.
     */
    private fun isTrialish(days: Int, phrase: String): Boolean {
        if (days <= LONG_OFFER_DAYS) return true
        return phrase.contains("trial", ignoreCase = true) ||
            phrase.contains("пробн", ignoreCase = true)
    }

    private fun toDays(amount: Int, unit: String): Int? {
        val u = unit.lowercase()
        return when {
            u.startsWith("month") || u.startsWith("месяц") -> amount * 30
            u.startsWith("week") || u.startsWith("недел") -> amount * 7
            u.startsWith("day") || u.startsWith("дн") -> amount
            else -> null
        }
    }

    /** "7 дней бесплатно" with the right Russian plural. */
    fun humanize(days: Int): String {
        if (days % 30 == 0 && days >= 30) {
            val months = days / 30
            return "$months ${plural(months, "месяц", "месяца", "месяцев")} бесплатно"
        }
        return "$days ${plural(days, "день", "дня", "дней")} бесплатно"
    }

    private fun plural(value: Int, one: String, few: String, many: String): String {
        val mod100 = value % 100
        if (mod100 in 11..14) return many
        return when (value % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }

    /** Anything longer is a yearly plan being described, not a trial. */
    private const val MAX_DAYS = 190

    /** Above this, a bare "free" period needs the word "trial" to count. */
    private const val LONG_OFFER_DAYS = 62
}

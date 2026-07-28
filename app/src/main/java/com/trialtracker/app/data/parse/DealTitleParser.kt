package com.trialtracker.app.data.parse

/**
 * Parses a deal post title into its parts.
 *
 * Titles on r/googleplaydeals follow a loose convention that people type by hand,
 * so the parser has to survive every variant seen in the live feed:
 *
 *     [Apps] Aura Notify ($29.99 -> $1.99)
 *     [Games] The Lonely Hacker ($2.99 -> Free)
 *     [Apps] MINIMAA: Minimalist Launcher { $0.99 > $0.86}
 *     [Games] Vector Maze ($.99 -> $.39)
 *     [Games]Titan Quest: Ultimate Edition ($13,99->$5,60)
 *     [App] Store & Forget - BYOK Unlock ($6.99 -> FREE)
 *
 * Anything without a recognisable "old -> new" group is rejected rather than
 * guessed at: a wrong price is worse than no entry.
 */
object DealTitleParser {

    data class Parsed(
        val name: String,
        val category: String?,
        val oldPrice: Double?,
        val newPrice: Double?,
        val currency: String,
    ) {
        val isFree: Boolean get() = newPrice != null && newPrice <= 0.0

        /** Rounded percentage off, or null when the old price is unknown/zero. */
        val discountPercent: Int?
            get() {
                val from = oldPrice ?: return null
                val to = newPrice ?: return null
                if (from <= 0.0) return null
                return (((from - to) / from) * 100).toInt().coerceIn(0, 100)
            }
    }

    private const val ARROWS = "->|-->|→|=>|>"

    /** A bracketed group that contains an arrow, e.g. "($2.99 -> Free)". */
    private val priceGroup = Regex("""[(\[{]([^()\[\]{}]*?(?:$ARROWS)[^()\[\]{}]*?)[)\]}]""")

    /** The leading "[Apps]" / "[Games]" style tag. */
    private val leadingTag = Regex("""^\s*[\[(]([^\[\]()]{1,24})[\])]""")

    private val arrowSplit = Regex(ARROWS)

    private val freeWord = Regex("""(?iU)\bfree\b|\bбесплатно\b""")

    private val amount = Regex("""(\d*[.,]?\d+)""")

    private val currencySymbol = Regex("""[$€£₽¥]|\b(?:USD|EUR|GBP|RUB)\b""")

    fun parse(rawTitle: String): Parsed? {
        val title = rawTitle.replace(' ', ' ').trim()
        if (title.isEmpty()) return null

        val group = priceGroup.findAll(title).lastOrNull() ?: return null
        val parts = arrowSplit.split(group.groupValues[1])
        if (parts.size < 2) return null

        val currency = currencySymbol.find(group.value)?.value ?: "$"
        val oldPrice = priceOf(parts.first())
        val newPrice = priceOf(parts.last())
        if (newPrice == null) return null

        val tag = leadingTag.find(title)
        var name = title
        if (tag != null) name = name.removeRange(tag.range)
        name = name.replace(group.value, " ")
        name = name.trim().trim('-', '–', '—', ':', '|', ',', '.', ' ').trim()
        if (name.isEmpty()) return null

        return Parsed(
            name = name,
            category = tag?.groupValues?.get(1)?.trim(),
            oldPrice = oldPrice,
            newPrice = newPrice,
            currency = currency,
        )
    }

    private fun priceOf(token: String): Double? {
        val text = token.trim()
        if (text.isEmpty()) return null
        if (freeWord.containsMatchIn(text)) return 0.0
        val raw = amount.find(text)?.groupValues?.get(1) ?: return null
        // "$.99" and "$2,99" both appear in the wild.
        val normalised = raw.replace(',', '.').let { if (it.startsWith(".")) "0$it" else it }
        return normalised.toDoubleOrNull()
    }
}

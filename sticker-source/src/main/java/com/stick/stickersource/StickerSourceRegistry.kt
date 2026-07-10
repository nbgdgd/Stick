package com.stick.stickersource

import com.stick.stickersource.StickerSource.Capability

/**
 * Central lookup for all available [StickerSource] implementations.
 *
 * The app talks to the registry, never to a concrete source, so implementations
 * can be added, removed or hot-swapped (e.g. a new TikTok backend after an API
 * change) by editing only the wiring — typically in a Hilt module — and not the
 * feature code.
 *
 * The registry is immutable after construction and therefore thread-safe.
 */
class StickerSourceRegistry(sources: List<StickerSource>) {

    private val byId: Map<String, StickerSource> =
        sources.associateBy { it.id }

    val all: List<StickerSource> = sources

    fun byId(id: String): StickerSource? = byId[id]

    /** All sources that can perform [capability], in registration order. */
    fun withCapability(capability: Capability): List<StickerSource> =
        all.filter { capability in it.capabilities }

    /**
     * The preferred source for a capability. The first registered source wins,
     * which lets callers control priority purely through registration order.
     */
    fun primaryFor(capability: Capability): StickerSource? =
        withCapability(capability).firstOrNull()
}

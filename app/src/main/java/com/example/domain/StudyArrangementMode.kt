package com.example.domain

/** Quiz screen layout identifiers shared by preferences and UI. */
object StudyArrangementMode {
    /** v2.0.0 layout: content sized naturally and packed toward the top, scrollable. */
    const val TOP_ALIGNED = "top_aligned"

    /** Pre-v2.0.0 layout: question card fills remaining space, answer area anchored to bottom. */
    const val EVEN_FILL = "even_fill"

    data class Option(val id: String, val label: String)

    val options = listOf(
        Option(TOP_ALIGNED, "上寄せ"),
        Option(EVEN_FILL, "均等配置")
    )

    private val supportedIds = options.mapTo(hashSetOf()) { it.id }

    /** Removed and unknown preference values safely migrate to the default. */
    fun normalize(id: String?): String = id?.takeIf(supportedIds::contains) ?: TOP_ALIGNED
}

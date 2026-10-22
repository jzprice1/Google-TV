package dev.jzprice.livetv

/**
 * One playlist entry. [key] identifies the channel across playlist refreshes (used for favorites);
 * it is the tvg-id plus the channel name, so two channels that share a tvg-id stay separate.
 */
data class Channel(
    val key: String,
    val name: String,
    val url: String,
    val logo: String,
    val group: String,
)

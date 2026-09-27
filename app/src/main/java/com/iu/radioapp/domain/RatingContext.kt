package com.iu.radioapp.domain

/** [host] is null when unhosted or served from the cache; [isStale] flags a cache older than five minutes. */
data class RatingContext(
    val show: Show,
    val host: Host?,
    val isStale: Boolean,
)

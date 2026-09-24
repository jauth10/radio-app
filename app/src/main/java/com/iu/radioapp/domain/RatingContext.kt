package com.iu.radioapp.domain

/** [host] is null for an unhosted show and when served from the cache. */
data class RatingContext(
    val show: Show,
    val host: Host?,
)

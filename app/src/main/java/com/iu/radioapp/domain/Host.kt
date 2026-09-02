package com.iu.radioapp.domain

/** The person presenting a [Show]. Identified by the station, not by the app. */
data class Host(
    val hostId: String,
    val displayName: String,
)

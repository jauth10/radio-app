package com.iu.radioapp.domain

/** A track as far as the archive knows it, and whether the app itself would refuse a request for it. */
data class RequestDraft(
    val track: Track,
    val refusal: RefusalReason?,
)

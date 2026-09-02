package com.iu.radioapp.domain

/** Which kind of write an [OutboxEntry] carries, and therefore where it is sent. */
enum class OperationType {
    SONG_REQUEST,
    RATING,
}

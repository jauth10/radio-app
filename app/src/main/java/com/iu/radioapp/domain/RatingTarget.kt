package com.iu.radioapp.domain

/**
 * What a [Rating] is about.
 *
 * The distinction is a field rather than a class hierarchy: playlist and host
 * ratings differ only in what they point at, not in behaviour or in the data
 * they carry, so two subclasses would add ceremony without adding meaning.
 */
enum class RatingTarget {
    PLAYLIST,
    HOST,
}

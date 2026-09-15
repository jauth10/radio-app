package stubserver.s2archive

import contract.s2archive.TrackDto

/** Fixed in-memory catalogue for S2 - Music archive. */
object ArchiveStore {

    val tracks: List<TrackDto> = listOf(
        TrackDto("trk-1", "The Fake Band", "Sample Song", "Fake Album", null, broadcastable = true),
        TrackDto("trk-2", "Second Artist", "Second Song", "Some Album", null, broadcastable = true),
        TrackDto("trk-3", "Third Artist", "Third Song", null, null, broadcastable = false),
    )
}

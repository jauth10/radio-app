package com.iu.radioapp.repository.mapping

import com.iu.radioapp.domain.RequestStatus
import com.iu.radioapp.domain.SongRequest
import contract.s3requests.CreateSongRequestDto
import contract.s3requests.RequestStatus as RequestStatusDto

fun RequestStatusDto.toDomain(): RequestStatus = when (this) {
    RequestStatusDto.PENDING -> RequestStatus.PENDING
    RequestStatusDto.IN_REVIEW -> RequestStatus.IN_REVIEW
    RequestStatusDto.ACCEPTED -> RequestStatus.ACCEPTED
    RequestStatusDto.REJECTED -> RequestStatus.REJECTED
}

fun SongRequest.toCreateDto(displayName: String?): CreateSongRequestDto = CreateSongRequestDto(
    trackId = trackId,
    listenerId = listenerId,
    displayName = displayName,
    message = message,
    timestamp = createdAt,
)

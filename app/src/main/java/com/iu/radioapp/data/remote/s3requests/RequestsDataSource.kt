package com.iu.radioapp.data.remote.s3requests

import com.iu.radioapp.domain.Outcome
import contract.s3requests.CreateSongRequestDto
import contract.s3requests.SongRequestOverviewDto
import contract.s3requests.SongRequestResponse
import contract.s3requests.SongRequestStatusDto

/**
 * S3 - Request management. See [contract.s3requests] for the wire format.
 *
 * [submitRequest] takes [idempotencyKey] as its own parameter rather than as a
 * field on [CreateSongRequestDto]: it travels as the 'Idempotency-Key' header,
 * not in the request body.
 */
interface RequestsDataSource {

    suspend fun submitRequest(idempotencyKey: String, request: CreateSongRequestDto): Outcome<SongRequestResponse>

    suspend fun getRequestStatus(requestId: String): Outcome<SongRequestStatusDto>

    suspend fun getRequestsForListener(listenerId: String): Outcome<List<SongRequestOverviewDto>>
}

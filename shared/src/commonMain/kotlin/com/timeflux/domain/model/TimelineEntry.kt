package com.timeflux.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class TimelineEntry(
    val id: String,                     // ULID
    val createdAt: Instant,
    val endsAt: Instant? = null,        // null = point-in-time; set = duration span
    val moduleType: ModuleType,
    val title: String? = null,
    val note: String? = null,
    val mediaUri: String? = null,
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationName: String? = null,
    val isPinned: Boolean = false,
    val payload: String = "{}",         // JSON blob for module-specific fields
    val deletedAt: Instant? = null,     // soft delete
    val updatedAt: Instant,
)

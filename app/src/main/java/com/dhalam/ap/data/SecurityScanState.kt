package com.dhalam.ap.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique

/** Watermark also retained for successful empty scans to prevent stale writes. */
@Entity
data class SecurityScanState(
    @field:Id var id: Long = 0,
    @field:Unique var kind: String = "",
    var observedAt: Long = 0
)

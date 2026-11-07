package com.dhalam.ap.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique

@Entity
data class SecurityFact(
    @field:Id var id: Long = 0,
    @field:Unique var stableKey: String = "",
    var kind: String = "",
    var subjectId: String = "",
    var detail: String = "",
    var status: String = "",
    var observedAt: Long = 0
)
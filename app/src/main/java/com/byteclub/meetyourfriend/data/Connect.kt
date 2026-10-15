package com.byteclub.meetyourfriend.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "connects")
data class Connect(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val organization: String,
    val role: String,
    val mobileE164: String,
    val selfiePath: String? = null,
    val linkedinUrl: String? = null,
    val briefText: String? = null,
    val voiceNotePath: String? = null,
    val transcript: String = "",
    val metAt: Instant = Instant.now(),
    val location: String? = null,
    val whatsappSentAt: Instant? = null,
    val notes: String? = null,
    val briefSources: String? = null,
    val briefConfident: Boolean? = null,
)

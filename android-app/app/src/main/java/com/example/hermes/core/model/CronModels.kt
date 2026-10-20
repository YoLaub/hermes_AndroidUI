package com.example.hermes.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A job an agent scheduled (GET /api/crons). Only the fields the agenda shows. */
@Serializable
data class CronJob(
    val id: String,
    val name: String? = null,
    val prompt: String = "",
    @SerialName("schedule_display") val scheduleDisplay: String? = null,
    val enabled: Boolean = true,
    val state: String? = null,
    /** ISO-8601 with an offset, or null when the job will not run again. */
    @SerialName("next_run_at") val nextRunAt: String? = null,
    /** null: the server's default profile runs it. */
    val profile: String? = null,
)

@Serializable
data class CronsResponse(val jobs: List<CronJob> = emptyList())

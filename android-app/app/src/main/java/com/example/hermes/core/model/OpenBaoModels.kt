package com.example.hermes.core.model

import kotlinx.serialization.Serializable

@Serializable
data class OpenBaoHealthResponse(
    val initialized: Boolean = false,
    val sealed: Boolean = true,
    val standby: Boolean = false,
    val version: String? = null
)

@Serializable
data class OpenBaoHealth(
    val initialized: Boolean = false,
    val sealed: Boolean = true,
    val standby: Boolean = false,
    val version: String = ""
)

@Serializable
data class OpenBaoSecretItem(
    val key: String,
    val value: String
)

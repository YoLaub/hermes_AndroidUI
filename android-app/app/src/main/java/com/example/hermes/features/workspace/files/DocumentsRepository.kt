package com.example.hermes.features.workspace.files

import com.example.hermes.core.network.*
import java.io.OutputStream

/** Captured when opening an action: later profile/navigation changes cannot redirect it. */
data class DocumentLocation(val profile: String, val space: String, val path: String)

interface DocumentsRepository {
    suspend fun spaces(profile: String): List<DocumentSpace>
    suspend fun list(location: DocumentLocation): List<DocumentEntry>
    suspend fun read(location: DocumentLocation): DocumentText
    suspend fun revision(location: DocumentLocation): String
    suspend fun change(location: DocumentLocation, action: String, fields: Map<String, String> = emptyMap())
    suspend fun download(location: DocumentLocation, output: OutputStream)
}

class RemoteDocumentsRepository(private val api: DocumentsApi, private val baseUrl: suspend () -> String) : DocumentsRepository {
    override suspend fun spaces(profile: String) = api.spaces(baseUrl(), profile)
    override suspend fun list(location: DocumentLocation) = api.list(baseUrl(), location.profile, location.space, location.path)
    override suspend fun read(location: DocumentLocation) = api.read(baseUrl(), location.profile, location.space, location.path)
    override suspend fun revision(location: DocumentLocation) = api.revision(baseUrl(), location.profile, location.space, location.path)
    override suspend fun change(location: DocumentLocation, action: String, fields: Map<String, String>) {
        api.change(baseUrl(), location.profile, action, location.space, location.path, fields)
    }
    override suspend fun download(location: DocumentLocation, output: OutputStream) {
        api.download(baseUrl(), location.profile, location.space, location.path, output)
    }
}

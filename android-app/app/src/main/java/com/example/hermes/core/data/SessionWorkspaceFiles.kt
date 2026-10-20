package com.example.hermes.core.data

import java.io.IOException

/**
 * The workspace of a Hermes session, through the WebUI file routes, which address files by session.
 * [sessionId] gives the session to go through; with none, every call fails rather than guessing.
 */
class SessionWorkspaceFiles(
    private val repository: HermesRepository,
    private val sessionId: () -> String?,
) : WorkspaceFiles {

    private fun session(): String = sessionId() ?: throw IOException("No session open")

    override suspend fun list(dir: String): Result<List<String>> = runCatching {
        repository.listFiles(session(), dir).fold(
            onSuccess = { listing -> listing.entries.filter { it.type == "file" }.map { it.name } },
            // The folder appears with the first note; a missing session is still an error.
            onFailure = { e -> if (e.message.orEmpty().startsWith("HTTP 404") && "Session" !in e.message.orEmpty()) emptyList() else throw e },
        )
    }

    override suspend fun read(path: String) = runCatching { repository.readFile(session(), path).getOrThrow().content }
    override suspend fun create(path: String, content: String) = runCatching { repository.createFile(session(), path, content).getOrThrow(); Unit }
    override suspend fun save(path: String, content: String) = runCatching { repository.saveFile(session(), path, content).getOrThrow(); Unit }
    override suspend fun rename(path: String, newName: String) = runCatching { repository.renameFile(session(), path, newName).getOrThrow(); Unit }
    override suspend fun delete(path: String) = runCatching { repository.deleteFile(session(), path).getOrThrow(); Unit }
}

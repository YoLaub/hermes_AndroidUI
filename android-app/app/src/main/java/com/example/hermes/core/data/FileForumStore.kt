package com.example.hermes.core.data

import android.content.Context
import com.example.hermes.core.model.ForumData
import com.example.hermes.features.workspace.forum.ForumStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** The forum's rooms and transcripts in a JSON file of the app's storage. Starts empty: no demo rooms. */
class FileForumStore(context: Context) : ForumStore {
    private val file = File(context.filesDir, "forum.json")
    private val json = Json { ignoreUnknownKeys = true }

    init {
        // Demo data of the former forum: rooms with invented members and replies.
        listOf("forum_rooms.json", "forum_messages.json", "a2a_external_agents.json").forEach { File(context.filesDir, it).delete() }
    }

    override suspend fun load(): ForumData = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<ForumData>(file.readText()) }.getOrDefault(ForumData())
    }

    override suspend fun save(data: ForumData) = withContext(Dispatchers.IO) {
        file.writeText(json.encodeToString(ForumData.serializer(), data))
    }
}

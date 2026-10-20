package com.example.hermes.core.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** The note-to-recording map, in a small JSON file of the app's storage. */
class FileNoteAudioIndex(context: Context) : NoteAudioIndex {
    private val file = File(context.filesDir, "notes_audio.json")
    private val map: MutableMap<String, String> =
        runCatching { Json.decodeFromString<Map<String, String>>(file.readText()) }.getOrDefault(emptyMap()).toMutableMap()

    @Synchronized override fun get(notePath: String): String? = map[notePath]

    @Synchronized override fun set(notePath: String, audioPath: String?) {
        if (audioPath == null) map.remove(notePath) else map[notePath] = audioPath
        runCatching { file.writeText(Json.encodeToString<Map<String, String>>(map)) }
    }
}

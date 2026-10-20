package com.example.hermes.features.workspace.notes

import com.example.hermes.core.model.AgentNote
import com.example.hermes.core.model.isOwnerName

/** The workspace folder that holds the notes. */
const val NOTES_DIR = "notes"

private const val NOTE_EXTENSION = ".md"
private const val MAX_NAME_CHARS = 80
private const val FENCE = "---"

/**
 * A note from a Markdown file. An optional header between two `---` lines gives
 * `pinned`, `tags` and `author`; the agents may write it or leave it out.
 */
fun parseNote(path: String, text: String): AgentNote {
    val title = path.substringAfterLast('/').removeSuffix(NOTE_EXTENSION)
    val (header, body) = splitHeader(text)
    val author = header["author"].orEmpty()
    return AgentNote(
        id = path,
        title = title,
        content = body,
        author = author,
        authorRole = if (author.isBlank() || isOwnerName(author)) "user" else "agent",
        tags = header["tags"]?.removeSurrounding("[", "]")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty(),
        isPinned = header["pinned"]?.trim().equals("true", ignoreCase = true),
    )
}

/** The file text of [note]: its header, when it has something to say, then its content. */
fun renderNote(note: AgentNote): String {
    val lines = buildList {
        if (note.isPinned) add("pinned: true")
        if (note.tags.isNotEmpty()) add("tags: [${note.tags.joinToString(", ")}]")
        if (note.author.isNotBlank()) add("author: ${note.author}")
    }
    return if (lines.isEmpty()) note.content else (listOf(FENCE) + lines + FENCE).joinToString("\n", postfix = "\n") + note.content
}

/** A file name for [title]: no path separators or reserved characters, at most [MAX_NAME_CHARS] characters. */
fun noteFileName(title: String): String {
    val cleaned = title
        .replace(Regex("""[/\\]"""), " - ")
        .replace(Regex("""[:*?"<>|\p{Cntrl}]"""), " ")
        .replace("..", "")
        .replace(Regex("""\s+"""), " ")
        .trim(' ', '-', '.')
        .take(MAX_NAME_CHARS)
        .trim()
    return cleaned.ifEmpty { "Note" } + NOTE_EXTENSION
}

fun isNoteFile(name: String) = name.endsWith(NOTE_EXTENSION, ignoreCase = true)

private fun splitHeader(text: String): Pair<Map<String, String>, String> {
    val lines = text.split('\n')
    if (lines.firstOrNull()?.trim() != FENCE) return emptyMap<String, String>() to text
    val end = lines.drop(1).indexOfFirst { it.trim() == FENCE }.takeIf { it >= 0 }?.plus(1)
        ?: return emptyMap<String, String>() to text
    val header = lines.subList(1, end).mapNotNull { line ->
        val key = line.substringBefore(':', "").trim()
        if (key.isEmpty()) null else key to line.substringAfter(':').trim()
    }.toMap()
    return header to lines.drop(end + 1).joinToString("\n")
}

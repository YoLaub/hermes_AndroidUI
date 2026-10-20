package com.example.hermes.features.workspace.forum

import com.example.hermes.core.model.ForumMessage
import com.example.hermes.core.model.ForumRoom
import com.example.hermes.core.model.HermesSseEvent
import com.example.hermes.core.model.OWNER_NAME
import com.example.hermes.core.model.ReplyState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile

/** Members mentioned with @ (by profile id or display name), in room order; nobody mentioned means everyone. */
fun targetsOf(text: String, members: List<String>, displayName: (String) -> String): List<String> {
    val mentioned = members.filter { id ->
        listOf(id, displayName(id)).any { Regex("""@${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) }
    }
    return mentioned.ifEmpty { members }
}

/**
 * What [profile] is sent for its turn. Its own session already holds what it heard and said before, so it gets
 * only what the room said since its last reply, with the room's frame.
 */
fun promptFor(room: ForumRoom, transcript: List<ForumMessage>, profile: String, displayName: (String) -> String): String {
    val lastOwn = transcript.indexOfLast { !it.fromOwner && it.sender == profile }
    val news = transcript.drop(lastOwn + 1).filter { it.fromOwner || it.sender != profile }
    val others = room.members.filter { it != profile }.joinToString { displayName(it) }
    return buildString {
        append("[Forum « ${room.name} »")
        if (room.topic.isNotBlank()) append(" — ${room.topic}")
        appendLine("]")
        appendLine("Vous êtes ${displayName(profile)}. Dans ce salon : $OWNER_NAME (la personne qui vous utilise)" + if (others.isNotEmpty()) ", $others." else ".")
        appendLine("Depuis votre dernier tour :")
        news.forEach { appendLine("- ${if (it.fromOwner) OWNER_NAME else displayName(it.sender)} : ${it.content}") }
        append("Répondez pour le salon, brièvement. Les autres participants liront votre réponse.")
    }
}

data class Reply(val text: String, val state: ReplyState)

/** An agent's reply from its stream: the text until the end, a stop for a decision, or the error. */
suspend fun collectReply(events: Flow<HermesSseEvent>): Reply {
    val text = StringBuilder()
    var state: ReplyState? = null
    events.takeWhile { state == null }.collect { event ->
        when (event) {
            is HermesSseEvent.Token -> text.append(event.text)
            is HermesSseEvent.ApprovalRequested, is HermesSseEvent.ClarifyRequested -> state = ReplyState.WAITING
            is HermesSseEvent.Error -> { text.clear().append(event.message); state = ReplyState.FAILED }
            is HermesSseEvent.StreamEnd, is HermesSseEvent.Done, is HermesSseEvent.Cancelled -> state = ReplyState.DONE
            else -> Unit
        }
    }
    val final = state ?: ReplyState.DONE
    return if (final == ReplyState.DONE && text.isBlank()) Reply("", ReplyState.FAILED) else Reply(text.toString(), final)
}

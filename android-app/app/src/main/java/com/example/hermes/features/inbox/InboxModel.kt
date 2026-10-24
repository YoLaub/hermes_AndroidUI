package com.example.hermes.features.inbox

import com.example.hermes.core.model.ApprovalPayload
import com.example.hermes.core.model.MemoryAction
import com.example.hermes.core.model.MemoryValidation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class InboxFilter { All, Tools, Actions, Memory }

/** Something waiting for the owner's decision. */
sealed interface InboxItem {
    val key: String

    /** The chat's pending tool approval (the server only exposes the open session's). */
    data class Tool(val approval: ApprovalPayload) : InboxItem {
        override val key get() = "tool:${approval.id}"
    }

    /** A request held by the action gate. */
    data class Action(val action: MemoryAction) : InboxItem {
        override val key get() = "action:${action.id}"
    }

    /** A memory item waiting for confirmation. */
    data class Memory(val item: MemoryValidation) : InboxItem {
        override val key get() = "memory:${item.eventId}"
    }
}

data class InboxCounts(val tools: Int, val actions: Int, val memory: Int) {
    val total: Int get() = tools + actions + memory

    fun of(filter: InboxFilter): Int = when (filter) {
        InboxFilter.All -> total
        InboxFilter.Tools -> tools
        InboxFilter.Actions -> actions
        InboxFilter.Memory -> memory
    }
}

fun inboxCounts(approval: ApprovalPayload?, actions: List<MemoryAction>, memory: List<MemoryValidation>) =
    InboxCounts(tools = if (approval != null) 1 else 0, actions = actions.size, memory = memory.size)

fun inboxItems(
    approval: ApprovalPayload?,
    actions: List<MemoryAction>,
    memory: List<MemoryValidation>,
    filter: InboxFilter,
): List<InboxItem> = buildList {
    if (filter == InboxFilter.All || filter == InboxFilter.Tools) approval?.let { add(InboxItem.Tool(it)) }
    if (filter == InboxFilter.All || filter == InboxFilter.Actions) actions.forEach { add(InboxItem.Action(it)) }
    if (filter == InboxFilter.All || filter == InboxFilter.Memory) memory.forEach { add(InboxItem.Memory(it)) }
}

/** Whether the memory error is about the owner token (then "Changer le jeton" is offered). */
fun isOwnerTokenError(error: String?): Boolean = error != null && error.contains("jeton propriétaire", ignoreCase = true)

private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("dd/MM")

/** "14:32" today, "hier" yesterday, "12/10" before; an unreadable value is kept as sent. */
fun shortTime(iso: String?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    if (iso == null) return ""
    val at = try {
        Instant.parse(iso).atZone(zone)
    } catch (_: Exception) {
        return iso
    }
    val today = now.atZone(zone).toLocalDate()
    return when (at.toLocalDate()) {
        today -> HOUR.format(at)
        today.minusDays(1) -> "hier"
        else -> DAY.format(at)
    }
}

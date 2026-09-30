package com.example.hermes.core.data

import android.content.Context
import com.example.hermes.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ForumRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val roomsFile = File(context.filesDir, "forum_rooms.json")
    private val messagesFile = File(context.filesDir, "forum_messages.json")
    private val a2aAgentsFile = File(context.filesDir, "a2a_external_agents.json")

    private val _rooms = MutableStateFlow<List<ForumRoom>>(emptyList())
    val rooms: StateFlow<List<ForumRoom>> = _rooms.asStateFlow()

    private val _messages = MutableStateFlow<Map<String, List<ForumMessage>>>(emptyMap())
    val messages: StateFlow<Map<String, List<ForumMessage>>> = _messages.asStateFlow()

    private val _a2aAgents = MutableStateFlow<List<A2AExternalAgent>>(emptyList())
    val a2aAgents: StateFlow<List<A2AExternalAgent>> = _a2aAgents.asStateFlow()

    suspend fun loadData(): Triple<List<ForumRoom>, Map<String, List<ForumMessage>>, List<A2AExternalAgent>> = withContext(Dispatchers.IO) {
        // 1. Load A2A Agents
        val loadedA2A = try {
            if (a2aAgentsFile.exists()) {
                val text = a2aAgentsFile.readText()
                if (text.isNotBlank()) json.decodeFromString<List<A2AExternalAgent>>(text) else null
            } else null
        } catch (_: Exception) { null } ?: defaultA2AAgents()

        _a2aAgents.value = loadedA2A
        saveA2AToFile(loadedA2A)

        // 2. Load Rooms
        val loadedRooms = try {
            if (roomsFile.exists()) {
                val text = roomsFile.readText()
                if (text.isNotBlank()) json.decodeFromString<List<ForumRoom>>(text) else null
            } else null
        } catch (_: Exception) { null } ?: defaultRooms(loadedA2A)

        _rooms.value = loadedRooms
        saveRoomsToFile(loadedRooms)

        // 3. Load Messages
        val loadedMessages = try {
            if (messagesFile.exists()) {
                val text = messagesFile.readText()
                if (text.isNotBlank()) json.decodeFromString<Map<String, List<ForumMessage>>>(text) else null
            } else null
        } catch (_: Exception) { null } ?: defaultMessages(loadedRooms)

        _messages.value = loadedMessages
        saveMessagesToFile(loadedMessages)

        return@withContext Triple(_rooms.value, _messages.value, _a2aAgents.value)
    }

    suspend fun saveRoom(room: ForumRoom): ForumRoom = withContext(Dispatchers.IO) {
        val current = _rooms.value.toMutableList()
        val index = current.indexOfFirst { it.id == room.id }
        val updated = room.copy(updatedAt = System.currentTimeMillis())

        if (index != -1) {
            current[index] = updated
        } else {
            current.add(0, updated)
        }

        _rooms.value = current
        saveRoomsToFile(current)
        return@withContext updated
    }

    suspend fun deleteRoom(roomId: String) = withContext(Dispatchers.IO) {
        val currentRooms = _rooms.value.toMutableList()
        currentRooms.removeAll { it.id == roomId }
        _rooms.value = currentRooms
        saveRoomsToFile(currentRooms)

        val currentMsgs = _messages.value.toMutableMap()
        currentMsgs.remove(roomId)
        _messages.value = currentMsgs
        saveMessagesToFile(currentMsgs)
    }

    suspend fun postMessage(message: ForumMessage): ForumMessage = withContext(Dispatchers.IO) {
        val currentMap = _messages.value.toMutableMap()
        val roomMsgs = (currentMap[message.roomId] ?: emptyList()).toMutableList()
        roomMsgs.add(message)
        currentMap[message.roomId] = roomMsgs
        _messages.value = currentMap
        saveMessagesToFile(currentMap)

        // Update room's updatedAt
        val room = _rooms.value.find { it.id == message.roomId }
        if (room != null) {
            saveRoom(room.copy(updatedAt = System.currentTimeMillis()))
        }

        return@withContext message
    }

    suspend fun saveA2AAgent(agent: A2AExternalAgent): A2AExternalAgent = withContext(Dispatchers.IO) {
        val current = _a2aAgents.value.toMutableList()
        val index = current.indexOfFirst { it.id == agent.id }
        if (index != -1) {
            current[index] = agent
        } else {
            current.add(agent)
        }
        _a2aAgents.value = current
        saveA2AToFile(current)
        return@withContext agent
    }

    suspend fun deleteA2AAgent(agentId: String) = withContext(Dispatchers.IO) {
        val current = _a2aAgents.value.toMutableList()
        current.removeAll { it.id == agentId }
        _a2aAgents.value = current
        saveA2AToFile(current)
    }

    private fun defaultA2AAgents(): List<A2AExternalAgent> = listOf(
        A2AExternalAgent(
            name = "ScrapingBot A2A",
            description = "Agent externe spécialisé en veille réseau & extraction de données web",
            endpointUrl = "https://a2a.agent-network.internal/v1/scraping",
            authToken = "a2a_bearer_tok_991823",
            capabilities = listOf("Web Scraping", "Social Media", "Enrichment")
        ),
        A2AExternalAgent(
            name = "MarketAdvisor A2A",
            description = "Agent externe d'analyse concurrentielle et scoring B2B",
            endpointUrl = "https://marketbot.partner.ai/a2a",
            capabilities = listOf("B2B Intelligence", "Market Signals")
        )
    )

    private fun defaultRooms(a2aAgents: List<A2AExternalAgent>): List<ForumRoom> = listOf(
        ForumRoom(
            name = "🏛️ Conseil de Direction Multi-Agents",
            topic = "Tour de table stratégique avec Yoann, Mario et Gaston",
            iconEmoji = "🏛️",
            participants = listOf(
                ForumParticipant(name = "Yoann", type = ParticipantType.USER, roleTitle = "Fondateur"),
                ForumParticipant(name = "Mario", type = ParticipantType.INTERNAL_AGENT, roleTitle = "Commercial & Growth"),
                ForumParticipant(name = "Gaston", type = ParticipantType.INTERNAL_AGENT, roleTitle = "Tech & CRM"),
                ForumParticipant(name = "Hermes", type = ParticipantType.INTERNAL_AGENT, roleTitle = "Orchestrateur")
            )
        ),
        ForumRoom(
            name = "🚀 Prospection & Veille A2A",
            topic = "Coordination Mario + Agent Externe A2A pour la qualification",
            iconEmoji = "🚀",
            participants = listOf(
                ForumParticipant(name = "Yoann", type = ParticipantType.USER, roleTitle = "Pilote"),
                ForumParticipant(name = "Mario", type = ParticipantType.INTERNAL_AGENT, roleTitle = "Commercial"),
                ForumParticipant(
                    name = a2aAgents.firstOrNull()?.name ?: "ScrapingBot A2A",
                    type = ParticipantType.EXTERNAL_A2A,
                    roleTitle = "Agent Externe A2A",
                    a2aEndpointUrl = a2aAgents.firstOrNull()?.endpointUrl
                )
            )
        )
    )

    private fun defaultMessages(rooms: List<ForumRoom>): Map<String, List<ForumMessage>> {
        val firstRoom = rooms.firstOrNull() ?: return emptyMap()
        val secondRoom = rooms.getOrNull(1)

        val map = mutableMapOf<String, List<ForumMessage>>()

        map[firstRoom.id] = listOf(
            ForumMessage(
                roomId = firstRoom.id,
                senderName = "Yoann",
                senderType = ParticipantType.USER,
                content = "Bonjour l'équipe ! Où en sommes-nous sur le déploiement du workflow Morbihan ?"
            ),
            ForumMessage(
                roomId = firstRoom.id,
                senderName = "Mario",
                senderType = ParticipantType.INTERNAL_AGENT,
                content = "Côté Prospection : 6 PME identifiées avec les offres clés et points de contact. Je transmets à Gaston pour synchronisation CRM.",
                mentionedAgents = listOf("Gaston")
            ),
            ForumMessage(
                roomId = firstRoom.id,
                senderName = "Gaston",
                senderType = ParticipantType.INTERNAL_AGENT,
                content = "Bien reçu Mario. La table Kanban *growth-commercial* est à jour et les fiches prospects sont prêtes à être exportées."
            )
        )

        if (secondRoom != null) {
            map[secondRoom.id] = listOf(
                ForumMessage(
                    roomId = secondRoom.id,
                    senderName = "Mario",
                    senderType = ParticipantType.INTERNAL_AGENT,
                    content = "@ScrapingBot peux-tu lancer la veille sur les signaux d'embauche des entreprises du 56 ?",
                    mentionedAgents = listOf("ScrapingBot")
                ),
                ForumMessage(
                    roomId = secondRoom.id,
                    senderName = "ScrapingBot A2A",
                    senderType = ParticipantType.EXTERNAL_A2A,
                    content = "📡 **[A2A Protocol OK]** Requête reçue de Mario. 14 nouvelles offres d'emploi détectées sur les 48 dernières heures.",
                    a2aSourceEndpoint = "https://a2a.agent-network.internal/v1/scraping"
                )
            )
        }

        return map
    }

    private fun saveRoomsToFile(rooms: List<ForumRoom>) {
        try { roomsFile.writeText(json.encodeToString(rooms)) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveMessagesToFile(map: Map<String, List<ForumMessage>>) {
        try { messagesFile.writeText(json.encodeToString(map)) } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveA2AToFile(agents: List<A2AExternalAgent>) {
        try { a2aAgentsFile.writeText(json.encodeToString(agents)) } catch (e: Exception) { e.printStackTrace() }
    }
}

package com.example.hermes.features.workspace.forum

import com.example.hermes.core.model.ApprovalPayload
import com.example.hermes.core.model.ForumData
import com.example.hermes.core.model.HermesSseEvent
import com.example.hermes.core.model.OWNER_NAME
import com.example.hermes.core.model.ReplyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ForumViewModelTest {

    private class FakeServer : ForumServer {
        val created = mutableListOf<String>()
        val turns = mutableListOf<Triple<String, String, String>>()
        val replies = mutableMapOf<String, List<HermesSseEvent>>()
        var failStart = false
        var lastReplies = mutableMapOf<String, String?>()
        override suspend fun newSession(profile: String, title: String) = Result.success("s-$profile").also { created += profile }
        override suspend fun startTurn(sessionId: String, profile: String, message: String): Result<String> {
            if (failStart) return Result.failure(IOException("offline"))
            turns += Triple(sessionId, profile, message)
            return Result.success("st-$profile-${turns.size}")
        }
        override fun stream(streamId: String): Flow<HermesSseEvent> {
            val profile = streamId.split('-')[1]
            return flowOf(*(replies[profile] ?: listOf(HermesSseEvent.Token("ok $profile"), HermesSseEvent.StreamEnd(null))).toTypedArray())
        }
        override suspend fun lastReply(sessionId: String) = Result.success(lastReplies[sessionId])
    }

    private class MemoryStore(var data: ForumData = ForumData()) : ForumStore {
        override suspend fun load() = data
        override suspend fun save(data: ForumData) { this.data = data }
    }

    private val names = mapOf("gaston" to "Gaston", "freya" to "Freya")

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun withRoom(server: FakeServer = FakeServer(), store: MemoryStore = MemoryStore()): Triple<ForumViewModel, FakeServer, MemoryStore> {
        val vm = ForumViewModel(server, store, displayName = { names[it] ?: it })
        vm.createRoom("Conseil", "Salon Vannes", "🏛️", listOf("gaston", "freya"))
        return Triple(vm, server, store)
    }

    @Test
    fun `there are no demo rooms`() {
        val vm = ForumViewModel(FakeServer(), MemoryStore(), displayName = { it })

        assertTrue(vm.uiState.value.rooms.isEmpty())
    }

    @Test
    fun `each member answers in turn in its own session, and hears the previous answers`() {
        val (vm, server, store) = withRoom()

        vm.onInputTextChanged("Un plan ?")
        vm.sendMessage()

        assertEquals(listOf("gaston", "freya"), server.created)
        assertEquals(listOf("s-gaston", "s-freya"), server.turns.map { it.first })
        assertTrue(server.turns[1].third.contains("Gaston : ok gaston"))
        val messages = vm.uiState.value.roomMessages
        assertEquals(listOf(OWNER_NAME, "gaston", "freya"), messages.map { it.sender })
        assertEquals(listOf("Un plan ?", "ok gaston", "ok freya"), messages.map { it.content })
        assertEquals(mapOf("gaston" to "s-gaston", "freya" to "s-freya"), store.data.rooms.single().sessions)
        assertEquals(3, store.data.messages.size)
    }

    @Test
    fun `a member's session is created once and reused`() {
        val (vm, server) = withRoom()

        vm.onInputTextChanged("@Gaston un"); vm.sendMessage()
        vm.onInputTextChanged("@Gaston deux"); vm.sendMessage()

        assertEquals(listOf("gaston"), server.created)
        assertEquals(2, server.turns.size)
    }

    @Test
    fun `an agent waiting for a decision says so and the turn goes on`() {
        val server = FakeServer().apply { replies["gaston"] = listOf(HermesSseEvent.ApprovalRequested(ApprovalPayload(command = "rm"))) }
        val (vm) = withRoom(server)

        vm.onInputTextChanged("Nettoie"); vm.sendMessage()

        val messages = vm.uiState.value.roomMessages
        assertEquals(ReplyState.WAITING, messages[1].state)
        assertEquals("s-gaston", messages[1].sessionId)
        assertEquals("freya", messages[2].sender)
    }

    @Test
    fun `an unreachable server gives failed replies, not invented ones`() {
        val server = FakeServer().apply { failStart = true }
        val (vm) = withRoom(server)

        vm.onInputTextChanged("Allô"); vm.sendMessage()

        assertEquals(listOf(ReplyState.DONE, ReplyState.FAILED, ReplyState.FAILED), vm.uiState.value.roomMessages.map { it.state })
    }

    @Test
    fun `a waiting reply is filled once the session has answered`() {
        val server = FakeServer().apply { replies["gaston"] = listOf(HermesSseEvent.ClarifyRequested(com.example.hermes.core.model.ClarifyPayload())) }
        val (vm) = withRoom(server)
        vm.onInputTextChanged("@Gaston question"); vm.sendMessage()

        server.lastReplies["s-gaston"] = "Réponse finale"
        vm.refreshWaiting()

        val reply = vm.uiState.value.roomMessages.last()
        assertEquals(ReplyState.DONE, reply.state)
        assertEquals("Réponse finale", reply.content)
    }

    @Test
    fun `deleting a room removes its transcript`() {
        val (vm, _, store) = withRoom()
        vm.onInputTextChanged("@Gaston x"); vm.sendMessage()

        vm.deleteRoom(store.data.rooms.single().id)

        assertTrue(store.data.rooms.isEmpty())
        assertTrue(store.data.messages.isEmpty())
    }
}

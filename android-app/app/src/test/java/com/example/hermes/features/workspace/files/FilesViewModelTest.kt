package com.example.hermes.features.workspace.files

import com.example.hermes.core.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.io.OutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {
    private class Fake : DocumentsRepository {
        val changes = mutableListOf<Pair<DocumentLocation, Map<String, String>>>()
        override suspend fun spaces(profile: String) = listOf(DocumentSpace("s", "Documents", profile))
        override suspend fun list(location: DocumentLocation) = listOf(DocumentEntry("a.md", false))
        override suspend fun read(location: DocumentLocation) = DocumentText("old", "v1")
        override suspend fun revision(location: DocumentLocation) = "v1"
        override suspend fun change(location: DocumentLocation, action: String, fields: Map<String, String>) { changes.add(location to fields) }
        override suspend fun download(location: DocumentLocation, output: OutputStream) {}
    }
    @Test fun browsingNeedsNoChatAndSaveCarriesTheLoadedRevision() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake(); val vm = FilesViewModel(fake)
            vm.openProfile("john"); advanceUntilIdle()
            assertEquals("a.md", vm.state.value.entries.single().name)
            vm.edit("a.md"); advanceUntilIdle()
            vm.save("new"); advanceUntilIdle()
            assertEquals(DocumentLocation("john", "s", "a.md"), fake.changes.single().first)
            assertEquals(mapOf("content" to "new", "revision" to "v1"), fake.changes.single().second)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun switchingProfileDropsAnEditorFromThePreviousProfile() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = Fake(); val vm = FilesViewModel(fake)
            vm.openProfile("john"); advanceUntilIdle()
            vm.edit("a.md"); advanceUntilIdle()
            vm.openProfile("mario"); advanceUntilIdle()
            assertNull(vm.state.value.editor)
            vm.save("wrong profile"); advanceUntilIdle()
            assertTrue(fake.changes.isEmpty())
        } finally { Dispatchers.resetMain() }
    }
}

package com.example.hermes.features.workspace.files

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.hermes.core.network.AuthInterceptor
import com.example.hermes.core.network.DocumentsApi
import com.example.hermes.theme.HermesTheme
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Requires the isolated document trial server on localhost:8879 and `adb reverse tcp:8879 tcp:8879`. */
class FilesDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val api = DocumentsApi(OkHttpClient.Builder().addInterceptor(AuthInterceptor()).build())
    private val repo = RemoteDocumentsRepository(api) { "http://127.0.0.1:8879" }

    @Before fun requireIsolatedServer() {
        assumeTrue("Needs documentsTrialUrl and adb reverse", InstrumentationRegistry.getArguments().getString("documentsTrialUrl") == "http://127.0.0.1:8879")
    }

    @Test fun createEditRenameCancelDeleteAndDownloadPdfOnDevice() {
        val name = "adb-${System.nanoTime()}.md"
        val renamed = name.removeSuffix(".md") + "-renamed.md"
        val vm = FilesViewModel(repo)
        rule.setContent { HermesTheme { Surface { FilesScreen(vm, listOf("john"), "john") } } }
        rule.waitUntil(20_000) { vm.state.value.space.isNotBlank() && !vm.state.value.busy }
        rule.onNodeWithText("Créer un fichier texte").performClick()
        rule.onNode(hasSetTextAction()).performTextInput(name)
        rule.onNodeWithText("Confirmer").performClick()
        rule.waitUntil(20_000) { vm.state.value.entries.any { it.name == name } && !vm.state.value.busy }
        try {
            rule.onNodeWithText(name).performScrollTo().performClick()
            rule.waitUntil(20_000) { vm.state.value.editor != null && !vm.state.value.busy }
            rule.onNode(hasSetTextAction()).performTextReplacement("# Créé depuis le Pixel\nTexte modifié")
            rule.onNodeWithText("Enregistrer").performClick()
            rule.waitUntil(20_000) { vm.state.value.editor == null && !vm.state.value.busy }
            val doc = runBlocking { api.read("http://127.0.0.1:8879", "john", "trial", name) }
            assertTrue(doc.content.contains("Créé depuis le Pixel"))

            // Bind the action to this exact row, even when the folder contains other documents.
            rule.onNode(hasText("Renommer") and hasAnyAncestor(hasTestTag("document-row-$name"))).performClick()
            rule.waitUntil(5_000) { rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasSetTextAction()).performTextReplacement(renamed)
            rule.onNodeWithText("Confirmer").performClick()
            rule.waitUntil(20_000) { vm.state.value.entries.any { it.name == renamed } && !vm.state.value.busy }

            val deleteAction = hasText("Supprimer") and hasAnyAncestor(hasTestTag("document-row-$renamed"))
            rule.onNode(deleteAction).performClick()
            rule.onNodeWithText("Annuler").performClick()
            assertTrue(vm.state.value.entries.any { it.name == renamed })
            rule.onNode(deleteAction).performClick()
            rule.onNodeWithText("Confirmer").performClick()
            rule.waitUntil(20_000) { vm.state.value.entries.none { it.name == renamed } && !vm.state.value.busy }

            val output = ByteArrayOutputStream()
            runBlocking { api.download("http://127.0.0.1:8879", "john", "trial", "devis.pdf", output) }
            assertArrayEquals("%PDF-1.7\u0000sample".toByteArray(), output.toByteArray())
        } finally {
            for (path in listOf(name, renamed)) runBlocking {
                runCatching {
                    val revision = api.revision("http://127.0.0.1:8879", "john", "trial", path)
                    api.change("http://127.0.0.1:8879", "john", "delete", "trial", path, mapOf("revision" to revision))
                }
            }
        }
    }
}

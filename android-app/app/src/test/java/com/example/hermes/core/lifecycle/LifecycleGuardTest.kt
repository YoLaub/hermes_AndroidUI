package com.example.hermes.core.lifecycle

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Regression guards for the activity-recreation bug: ViewModels and the mobile-control manager
 * were created with `remember {}` inside a composable, so a rotation destroyed them and leaked
 * the old manager's WebSocket. Unit tests cannot recreate an activity, so these pin the structure
 * that makes survival possible; the behaviour itself is checked on a device.
 */
class LifecycleGuardTest {

    private val moduleDir = File(System.getProperty("user.dir"))

    private fun source(rel: String) = File(moduleDir, "src/main/java/com/example/hermes/$rel").readText()

    @Test
    fun navigationDoesNotBuildViewModelsOrTheManagerInsideComposition() {
        val nav = source("Navigation.kt")
        val offending = Regex("""remember\s*(\([^)]*\))?\s*\{[^}]*(ViewModel|MobileControlManager)\s*\(""")
            .findAll(nav).map { it.value.take(80) }.toList()
        assertTrue("created inside remember{}: $offending", offending.isEmpty())
    }

    @Test
    fun navigationGetsItsDependenciesFromTheProcessScopedContainer() {
        val nav = source("Navigation.kt")
        assertTrue("MainNavigation must read the container from the Application", nav.contains("HermesApp"))
        assertTrue("ViewModels must come from a ViewModelProvider factory", nav.contains("viewModel("))
    }

    private fun manifest() = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(moduleDir, "src/main/AndroidManifest.xml"))

    private val androidNs = "http://schemas.android.com/apk/res/android"

    @Test
    fun manifestDeclaresTheApplicationClass() {
        val app = manifest().getElementsByTagName("application").item(0)
        val name = app.attributes.getNamedItemNS(androidNs, "name")?.nodeValue
        assertEquals(".HermesApp", name)
    }

    @Test
    fun manifestDeclaresTheSpecialUseForegroundServiceAndItsPermissions() {
        val doc = manifest()
        val permissions = (0 until doc.getElementsByTagName("uses-permission").length).map {
            doc.getElementsByTagName("uses-permission").item(it).attributes.getNamedItemNS(androidNs, "name").nodeValue
        }
        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(permissions.contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"))

        val services = doc.getElementsByTagName("service")
        val svc = (0 until services.length).map { services.item(it) }.firstOrNull {
            it.attributes.getNamedItemNS(androidNs, "name")?.nodeValue == ".core.mobilecontrol.MobileControlService"
        }
        assertNotNull("MobileControlService must be declared", svc)
        assertEquals("specialUse", svc!!.attributes.getNamedItemNS(androidNs, "foregroundServiceType")?.nodeValue)
        assertEquals("false", svc.attributes.getNamedItemNS(androidNs, "exported")?.nodeValue)
        val props = (svc as org.w3c.dom.Element).getElementsByTagName("property")
        val subtype = (0 until props.length).map { props.item(it) }.firstOrNull {
            it.attributes.getNamedItemNS(androidNs, "name")?.nodeValue == "android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        }
        assertNotNull("specialUse requires the subtype property", subtype)
    }
}

package com.example.hermes.core.security

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Secrets must not travel in cloud backups or device transfers. */
class BackupGuardTest {

    private val moduleDir = File(System.getProperty("user.dir"))
    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun parse(rel: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(File(moduleDir, rel))

    private fun application() = parse("src/main/AndroidManifest.xml").getElementsByTagName("application").item(0) as Element

    @Test
    fun backupIsDisabled() {
        assertEquals("false", application().getAttributeNS(androidNs, "allowBackup"))
    }

    @Test
    fun theRulesFilesAreReferencedAndExcludeEverythingTheDataStoreLivesIn() {
        val app = application()
        assertEquals("@xml/data_extraction_rules", app.getAttributeNS(androidNs, "dataExtractionRules"))
        assertEquals("@xml/backup_rules", app.getAttributeNS(androidNs, "fullBackupContent"))

        val extraction = parse("src/main/res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val node = extraction.getElementsByTagName(section).item(0) as Element
            val excluded = (0 until node.getElementsByTagName("exclude").length)
                .map { (node.getElementsByTagName("exclude").item(it) as Element).getAttribute("domain") }
            for (domain in listOf("file", "sharedpref", "database")) {
                assertTrue("$section must exclude $domain, excludes $excluded", domain in excluded)
            }
            assertEquals("$section must not include anything", 0, node.getElementsByTagName("include").length)
        }

        val full = parse("src/main/res/xml/backup_rules.xml")
        val excludedFull = (0 until full.getElementsByTagName("exclude").length)
            .map { (full.getElementsByTagName("exclude").item(it) as Element).getAttribute("domain") }
        for (domain in listOf("file", "sharedpref", "database")) {
            assertTrue("full-backup-content must exclude $domain", domain in excludedFull)
        }
        assertEquals(0, full.getElementsByTagName("include").length)
    }
}

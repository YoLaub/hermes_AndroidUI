package com.example.hermes.a11y

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/**
 * Asserts that the clickable accessibility node announced as [label] is at least 48 dp on each side,
 * as TalkBack and Accessibility Scanner see it (Compose's wider finger hit area does not count there).
 */
fun assertAccessibleTargetIsAtLeast48dp(label: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    instrumentation.waitForIdleSync()
    // The accessibility connection and the Compose tree populate asynchronously.
    var matches: List<AccessibilityNodeInfo> = emptyList()
    val deadline = System.currentTimeMillis() + 5_000
    while (matches.isEmpty() && System.currentTimeMillis() < deadline) {
        matches = automation.rootInActiveWindow?.let { nodesDescribedAs(it, label) }.orEmpty()
        if (matches.isEmpty()) Thread.sleep(100)
    }
    assertTrue("no node announced as '$label'", matches.isNotEmpty())
    val minPx = 48 * instrumentation.targetContext.resources.displayMetrics.density - 1
    matches.forEach { match ->
        val target = clickableSelfOrAncestor(match) ?: error("'$label' is not inside a clickable node")
        val bounds = Rect().also(target::getBoundsInScreen)
        assertTrue(
            "'$label' target is ${bounds.width()}x${bounds.height()} px, expected at least $minPx px",
            bounds.width() >= minPx && bounds.height() >= minPx,
        )
    }
}

private fun nodesDescribedAs(node: AccessibilityNodeInfo, label: String): List<AccessibilityNodeInfo> {
    val self = if (node.contentDescription?.toString() == label || node.text?.toString() == label) listOf(node) else emptyList()
    return self + (0 until node.childCount).mapNotNull(node::getChild).flatMap { nodesDescribedAs(it, label) }
}

private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    var current: AccessibilityNodeInfo? = node
    while (current != null && !current.isClickable) current = current.parent
    return current
}

package com.example.hermes.core.accessibility

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.example.hermes.core.mobilecontrol.MobileElementInfo
import com.example.hermes.core.mobilecontrol.MobileScreenData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AccessibilityNodeHelper {

    private val elementMap = ConcurrentHashMap<String, AccessibilityNodeInfo>()
    private val revisionCounter = AtomicInteger(1)
    private var currentRevision: String = "rev_0"
    private var lastPackageName: String = ""

    fun clear() {
        elementMap.clear()
    }

    @Synchronized
    fun extractScreenData(rootNode: AccessibilityNodeInfo?): MobileScreenData {
        elementMap.clear()
        if (rootNode == null) {
            return MobileScreenData(
                screenRevision = "rev_empty",
                packageName = "",
                title = null,
                elements = emptyList()
            )
        }

        val pkg = rootNode.packageName?.toString() ?: ""
        lastPackageName = pkg
        val rev = "rev_${System.currentTimeMillis()}_${revisionCounter.getAndIncrement()}"
        currentRevision = rev

        val elements = mutableListOf<MobileElementInfo>()
        var refCounter = 1

        fun traverse(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 40) return

            // Security: Never extract or inspect password nodes
            if (node.isPassword) {
                return
            }

            val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val desc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val isClickable = node.isClickable
            val isEditable = node.isEditable
            val isScrollable = node.isScrollable

            // Keep nodes that provide semantic content or interactivity
            if (text != null || desc != null || isClickable || isEditable || isScrollable) {
                val ref = "el_${refCounter++}"
                elementMap[ref] = node

                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val boundsStr = "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]"

                val simpleClassName = node.className?.toString()?.substringAfterLast('.') ?: "View"

                elements.add(
                    MobileElementInfo(
                        elementRef = ref,
                        className = simpleClassName,
                        text = text,
                        contentDesc = desc,
                        clickable = isClickable,
                        editable = isEditable,
                        scrollable = isScrollable,
                        bounds = boundsStr
                    )
                )
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                traverse(child, depth + 1)
            }
        }

        traverse(rootNode, 0)

        return MobileScreenData(
            screenRevision = rev,
            packageName = pkg,
            title = null,
            elements = elements
        )
    }

    fun getCurrentRevision(): String = currentRevision
    fun getLastPackageName(): String = lastPackageName

    fun isRevisionValid(revision: String?): Boolean {
        if (revision.isNullOrBlank()) return true // Lenient if not specified
        return revision == currentRevision
    }

    fun clickElement(elementRef: String): Boolean {
        val node = elementMap[elementRef] ?: return false
        // Try clicking node itself, or parent if parent is clickable
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        // Fallback: try click anyway
        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    fun setText(elementRef: String, text: String): Boolean {
        val node = elementMap[elementRef] ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        // Focus first
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    fun scroll(rootNode: AccessibilityNodeInfo?, direction: String): Boolean {
        // Find scrollable node in element map or tree
        val scrollableNode = elementMap.values.firstOrNull { it.isScrollable } ?: rootNode ?: return false
        val action = if (direction.equals("up", ignoreCase = true)) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        return scrollableNode.performAction(action)
    }
}

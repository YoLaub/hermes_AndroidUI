package com.example.hermes.core.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.hermes.core.mobilecontrol.MobileScreenData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HermesAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "HermesAccessService"
        
        private var instance: HermesAccessibilityService? = null

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()

        private val _currentForegroundPackage = MutableStateFlow("")
        val currentForegroundPackage: StateFlow<String> = _currentForegroundPackage.asStateFlow()

        fun getInstance(): HermesAccessibilityService? = instance
        fun isRunning(): Boolean = instance != null && _isServiceActive.value
    }

    private val nodeHelper = AccessibilityNodeHelper()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceActive.value = true
        Log.i(TAG, "HermesAccessibilityService connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return

        // Update foreground package tracking
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            _currentForegroundPackage.value = pkg
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "HermesAccessibilityService interrupted")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _isServiceActive.value = false
        instance = null
        nodeHelper.clear()
        Log.i(TAG, "HermesAccessibilityService unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        _isServiceActive.value = false
        instance = null
        nodeHelper.clear()
        super.onDestroy()
        Log.i(TAG, "HermesAccessibilityService destroyed")
    }

    // ── Inspection & Actions ────────────────────────────────────────────────

    fun observeScreen(): MobileScreenData {
        val root = rootInActiveWindow
        val screenData = nodeHelper.extractScreenData(root)
        // If root package is present, update current package
        if (screenData.packageName.isNotBlank()) {
            _currentForegroundPackage.value = screenData.packageName
        }
        return screenData
    }

    fun isScreenRevisionValid(revision: String?): Boolean {
        return nodeHelper.isRevisionValid(revision)
    }

    fun getForegroundPackage(): String {
        val rootPkg = rootInActiveWindow?.packageName?.toString()
        return rootPkg ?: _currentForegroundPackage.value
    }

    fun clickElement(elementRef: String, expectedRevision: String?): Boolean {
        if (!nodeHelper.isRevisionValid(expectedRevision)) {
            return false
        }
        return nodeHelper.clickElement(elementRef)
    }

    fun setText(elementRef: String, text: String, expectedRevision: String?): Boolean {
        if (!nodeHelper.isRevisionValid(expectedRevision)) {
            return false
        }
        return nodeHelper.setText(elementRef, text)
    }

    fun scroll(direction: String): Boolean {
        return nodeHelper.scroll(rootInActiveWindow, direction)
    }

    fun performBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }
}

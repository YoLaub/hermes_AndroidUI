package com.example.hermes.core.mobilecontrol

/** What the agent-phone screen tells the user, in the order a problem should be fixed. */
enum class AgentPhoneStatus { NOT_PAIRED, STOPPED, OFFLINE, ACCESSIBILITY_OFF, READY }

fun agentPhoneStatus(paired: Boolean, stopped: Boolean, authenticated: Boolean, accessibilityOn: Boolean): AgentPhoneStatus = when {
    !paired -> AgentPhoneStatus.NOT_PAIRED
    stopped -> AgentPhoneStatus.STOPPED
    !authenticated -> AgentPhoneStatus.OFFLINE
    !accessibilityOn -> AgentPhoneStatus.ACCESSIBILITY_OFF
    else -> AgentPhoneStatus.READY
}

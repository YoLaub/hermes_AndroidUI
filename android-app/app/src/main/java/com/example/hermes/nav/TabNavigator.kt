package com.example.hermes.nav

/** The five bottom-bar destinations, in bar order. */
enum class Tab { Chat, Inbox, Space, Control, Agent }

/**
 * One back stack per tab. Immutable: every move returns a new navigator.
 * Rules (HANDOFF §1): re-tapping a tab returns to its root; system back pops the current tab,
 * then returns to Chat; back at Chat's root leaves the app (`back()` returns null).
 */
data class TabNavigator<K>(val current: Tab, private val stacks: Map<Tab, List<K>>) {

    fun stackOf(tab: Tab): List<K> = stacks.getValue(tab)

    val currentStack: List<K> get() = stackOf(current)

    fun select(tab: Tab): TabNavigator<K> =
        if (tab == current) copy(stacks = stacks + (tab to listOf(stackOf(tab).first())))
        else copy(current = tab)

    fun push(key: K): TabNavigator<K> = copy(stacks = stacks + (current to currentStack + key))

    /** Goes to [tab] from its root and pushes [key] on it. */
    fun open(tab: Tab, key: K): TabNavigator<K> =
        copy(current = tab, stacks = stacks + (tab to listOf(stackOf(tab).first(), key)))

    fun back(): TabNavigator<K>? = when {
        currentStack.size > 1 -> copy(stacks = stacks + (current to currentStack.dropLast(1)))
        current != Tab.Chat -> copy(current = Tab.Chat)
        else -> null
    }

    companion object {
        fun <K> start(rootOf: (Tab) -> K): TabNavigator<K> =
            TabNavigator(Tab.Chat, Tab.entries.associateWith { listOf(rootOf(it)) })
    }
}

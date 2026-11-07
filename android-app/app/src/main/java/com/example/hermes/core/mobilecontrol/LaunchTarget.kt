package com.example.hermes.core.mobilecontrol

sealed interface LaunchTarget {
    data class Package(val name: String) : LaunchTarget
    data class Rejected(val code: String, val message: String) : LaunchTarget
}

/**
 * Which app launch_app opens. A session bound to an app only ever opens that app (also when the relay sends another
 * package: the phone does not rely on the relay's check). Only an any-app session opens a package that was asked for.
 */
fun launchTarget(sessionTarget: String, requested: String?): LaunchTarget = when {
    sessionTarget == ANY_APP ->
        if (requested.isNullOrBlank()) LaunchTarget.Rejected("INVALID_ARGUMENTS", "Le package de l'application à ouvrir est requis.")
        else LaunchTarget.Package(requested)
    requested.isNullOrBlank() || requested == sessionTarget -> LaunchTarget.Package(sessionTarget)
    else -> LaunchTarget.Rejected("APP_NOT_ALLOWED", "Seule l'application autorisée pour la session ($sessionTarget) peut être ouverte.")
}

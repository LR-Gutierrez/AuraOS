package com.beta.auraos.ui.login

/**
 * User data exposed to the UI after a successful authentication.
 *
 * This is a presentation-layer model (not a domain model). It carries
 * the minimum fields the Activity needs to pass to [HomeActivity] via
 * Intent extras.
 */
data class LoggedInUserView(
    val displayName: String,
    val email: String,
    val userId: String
)
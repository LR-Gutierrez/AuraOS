package com.beta.auraos.ui.login

/**
 * One-shot navigation event emitted by [LoginViewModel.loginResult].
 *
 * Exactly one field is non-null at a time:
 * - [success] → navigate to [HomeActivity].
 * - [error] → show a string-resource-based error.
 * - [errorMessage] → show a dynamic error string (e.g. raw API message).
 */
data class LoginResult(
    val success: LoggedInUserView? = null,
    val error: Int? = null,
    val errorMessage: String? = null
)
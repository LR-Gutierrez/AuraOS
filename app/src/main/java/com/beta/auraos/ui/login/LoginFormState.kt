package com.beta.auraos.ui.login

/**
 * Validation state exposed by [LoginViewModel.loginFormState] to drive
 * inline error messages in the login form.
 *
 * Only one of [usernameError] or [passwordError] is set at a time.
 * When both are `null` and [isDataValid] is `true` the "Sign in" button
 * becomes enabled.
 */
data class LoginFormState(
    val usernameError: Int? = null,
    val passwordError: Int? = null,
    val isDataValid: Boolean = false
)
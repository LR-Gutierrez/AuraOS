package com.beta.auraos.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Patterns
import com.beta.auraos.data.LoginRepository
import com.beta.auraos.data.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.beta.auraos.R

/**
 * Drives the login screen state via [LiveData] consumed by [LoginActivity].
 *
 * ## Concurrency guard
 * The private [isLoading] flag prevents duplicate requests while a login
 * call is in-flight. Both [login] and [loginWithBiometric] check this
 * flag and return early if `true`.
 *
 * ## Biometric availability
 * `hasBiometricAvailable()` requires **two** conditions:
 * 1. The user has completed biometric enrollment (`isBiometricEnrolled`).
 * 2. Encrypted credentials exist (`hasSavedCredentials`) — these are saved
 *    at enrollment time and removed if the user disables biometrics.
 */
class LoginViewModel(private val loginRepository: LoginRepository) : ViewModel() {

    private val _loginForm = MutableLiveData<LoginFormState>()
    val loginFormState: LiveData<LoginFormState> = _loginForm

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun login(email: String, password: String) {
        if (_isLoading.value == true) return

        _isLoading.value = true

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                loginRepository.login(email, password)
            }

            _isLoading.value = false

            if (result is Result.Success) {
                loginRepository.saveUserId(result.data.userId)
                loginRepository.saveLastEmail(result.data.email)
                loginRepository.saveLastDisplayName(result.data.displayName)
                result.data.avatarUrl?.let { loginRepository.saveAvatarUrl(it) }
                _loginResult.value = LoginResult(
                    success = LoggedInUserView(
                        displayName = result.data.displayName,
                        email = result.data.email,
                        userId = result.data.userId,
                        avatarUrl = result.data.avatarUrl
                    )
                )
            } else {
                val message = when (val error = result) {
                    is Result.Error -> error.exception.localizedMessage ?: "Error de inicio de sesión"
                    else -> "Error de inicio de sesión"
                }
                _loginResult.value = LoginResult(errorMessage = message)
            }
        }
    }

    fun loginWithBiometric(userId: String, challenge: String, signature: String) {
        if (_isLoading.value == true) return

        _isLoading.value = true

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                loginRepository.biometricLogin(userId, challenge, signature)
            }

            _isLoading.value = false

            if (result is Result.Success) {
                loginRepository.saveUserId(result.data.userId)
                loginRepository.saveLastEmail(result.data.email)
                loginRepository.saveLastDisplayName(result.data.displayName)
                result.data.avatarUrl?.let { loginRepository.saveAvatarUrl(it) }
                _loginResult.value = LoginResult(
                    success = LoggedInUserView(
                        displayName = result.data.displayName,
                        email = result.data.email,
                        userId = result.data.userId,
                        avatarUrl = result.data.avatarUrl
                    )
                )
            } else {
                val message = when (val error = result) {
                    is Result.Error -> error.exception.localizedMessage ?: "Error biométrico"
                    else -> "Error biométrico"
                }
                _loginResult.value = LoginResult(errorMessage = message)
            }
        }
    }

    fun hasBiometricAvailable(): Boolean {
        return loginRepository.isBiometricEnrolled() && loginRepository.hasBiometricCredentials()
    }

    fun getUserId(): String? = loginRepository.getUserId()

    fun isReturningUser(): Boolean = loginRepository.hasPreviousLogin()

    fun getStoredEmail(): String? = loginRepository.getLastEmail()

    fun getStoredDisplayName(): String? = loginRepository.getLastDisplayName()

    fun getAvatarUrl(): String? = loginRepository.getAvatarUrl()

    fun loginDataChanged(username: String, password: String) {
        // An empty field is unfinished, not wrong: it keeps the button disabled without an error.
        _loginForm.value = when {
            username.isBlank() -> LoginFormState()
            !isUserNameValid(username) -> LoginFormState(usernameError = R.string.invalid_username)
            password.isEmpty() -> LoginFormState()
            !isPasswordValid(password) -> LoginFormState(passwordError = R.string.invalid_password)
            else -> LoginFormState(isDataValid = true)
        }
    }

    private fun isUserNameValid(username: String): Boolean {
        return if (username.contains('@')) {
            Patterns.EMAIL_ADDRESS.matcher(username).matches()
        } else {
            username.isNotBlank()
        }
    }

    private fun isPasswordValid(password: String): Boolean {
        return password.length > 5
    }
}
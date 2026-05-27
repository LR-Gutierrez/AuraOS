package com.beta.auraos.ui.login

import android.content.Intent
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.core.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.EditText
import android.widget.Toast
import com.beta.auraos.DashboardActivity
import com.beta.auraos.R
import com.beta.auraos.data.local.BiometricKeyManager
import com.beta.auraos.util.TabNavigator
import com.beta.auraos.databinding.ActivityLoginBinding
import java.security.Signature
import java.util.concurrent.Executor

/**
 * Login screen with email/password form and biometric quick-access.
 *
 * ## Biometric (crypto-based) flow
 * 1. On launch, checks `loginViewModel.hasBiometricAvailable()` — if
 *    `false` the biometric container is hidden entirely.
 * 2. User taps the fingerprint icon → `BiometricKeyManager.getSignatureForAuth()`
 *    provides a [Signature] initialised with the AndroidKeyStore private key.
 * 3. A `BiometricPrompt.CryptoObject` wraps that signature so Android's
 *    biometric gate protects the private key.
 * 4. On `onAuthenticationSucceeded` the challenge string is signed via
 *    [BiometricKeyManager.signChallenge] and sent to the server as
 *    `POST /api/v1/biometric-login`.
 *
 * ## Edge cases handled
 * - **Key invalidation**: if `getSignatureForAuth()` returns `null` the
 *   key was invalidated (new fingerprint enrolled). User is told to
 *   re-enrol via password login.
 * - **Loading guard**: the form is disabled while a request is in-flight
 *   (see [LoginViewModel.isLoading]).
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var biometricKeyManager: BiometricKeyManager
    private var lastPassword: String = ""
    private var currentChallenge: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val username = binding.username
        val password = binding.password
        val login = binding.login
        val loading = binding.loading

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory(application))
            .get(LoginViewModel::class.java)

        biometricKeyManager = BiometricKeyManager(application)

        loginViewModel.isLoading.observe(this@LoginActivity) { isLoading ->
            loading.visibility = if (isLoading) View.VISIBLE else View.GONE
            login.isEnabled = !isLoading && (loginViewModel.loginFormState.value?.isDataValid == true)
            username.isEnabled = !isLoading
            password.isEnabled = !isLoading
        }

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            if (loginViewModel.isLoading.value != true) {
                login.isEnabled = loginState.isDataValid
            }

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

        loginViewModel.loginResult.observe(this@LoginActivity) { loginResult ->
            val result = loginResult ?: return@observe

            if (result.errorMessage != null) {
                showLoginFailed(result.errorMessage)
                return@observe
            }

            if (result.error != null) {
                showLoginFailed(getString(result.error))
                return@observe
            }

            if (result.success != null) {
                updateUiWithUser(result.success)
            }
        }

        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        loginViewModel.login(
                            username.text.toString(),
                            password.text.toString()
                        )
                }
                false
            }

            login.setOnClickListener {
                lastPassword = password.text.toString()
                loginViewModel.login(username.text.toString(), password.text.toString())
            }
        }

        setupBiometric()
    }

    private fun setupBiometric() {
        val hasHardware = hasBiometricHardware()
        val keyExists = biometricKeyManager.isKeyGenerated()
        val isAvailable = hasHardware && loginViewModel.hasBiometricAvailable() && keyExists
        val biometricVisible = if (isAvailable) View.VISIBLE else View.GONE
        binding.biometricDivider?.visibility = biometricVisible
        binding.biometricContainer?.visibility = biometricVisible
        if (!isAvailable) return

        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val signature = result.cryptoObject?.signature
                    if (signature != null) {
                        val signed = biometricKeyManager.signChallenge(signature, currentChallenge)
                        val userId = loginViewModel.getUserId() ?: return
                        loginViewModel.loginWithBiometric(userId, currentChallenge, signed)
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        val message = when (errorCode) {
                            BiometricPrompt.ERROR_HW_NOT_PRESENT ->
                                "Este dispositivo no tiene sensor biométrico"
                            BiometricPrompt.ERROR_HW_UNAVAILABLE ->
                                "Sensor biométrico no disponible"
                            BiometricPrompt.ERROR_NO_BIOMETRICS ->
                                "No hay huellas registradas"
                            else -> "Error de autenticación"
                        }
                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(
                        applicationContext,
                        "Huella no reconocida. Intente de nuevo.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        binding.biometricLogin?.setOnClickListener {
            try {
                val biometricManager = BiometricManager.from(this)
                when (biometricManager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
                    BiometricManager.BIOMETRIC_SUCCESS -> {
                        val signature = biometricKeyManager.getSignatureForAuth()
                        if (signature == null) {
                            Toast.makeText(this, "Error al inicializar biometría. Vuelva a enrolar.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        currentChallenge = "smartpark_challenge_${System.currentTimeMillis()}"
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Acceso Rápido")
                            .setSubtitle("Autentíquese para acceder")
                            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                            .build()
                        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(signature))
                    }
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                        Toast.makeText(this, "Este dispositivo no tiene sensor biométrico", Toast.LENGTH_SHORT).show()
                    }
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                        Toast.makeText(this, "Sensor biométrico no disponible", Toast.LENGTH_SHORT).show()
                    }
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                        Toast.makeText(this, "No hay huellas registradas. Configure una en Ajustes.", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, "Biometría no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error al verificar biometría", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasBiometricHardware(): Boolean {
        return try {
            val mgr = BiometricManager.from(this)
            mgr.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    private fun updateUiWithUser(model: LoggedInUserView) {
        Toast.makeText(
            applicationContext,
            "Bienvenido ${model.displayName}",
            Toast.LENGTH_LONG
        ).show()

        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra(TabNavigator.EXTRA_TAB_ID, R.id.nav_dashboard)
            putExtra(DashboardActivity.EXTRA_EMAIL, model.email)
            putExtra(DashboardActivity.EXTRA_DISPLAY_NAME, model.displayName)
            putExtra(DashboardActivity.EXTRA_PASSWORD, lastPassword)
            putExtra(DashboardActivity.EXTRA_USER_ID, model.userId)
        }
        startActivity(intent)
        finish()
    }

    private fun showLoginFailed(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}
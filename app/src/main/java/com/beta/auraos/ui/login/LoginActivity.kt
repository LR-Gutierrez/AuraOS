package com.beta.auraos.ui.login

import android.content.Intent
import android.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
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
import android.widget.ImageButton
import android.widget.Toast
import coil.load
import coil.transform.CircleCropTransformation
import com.beta.auraos.DashboardActivity
import com.beta.auraos.R
import com.beta.auraos.data.local.BiometricKeyManager
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.TextView
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.beta.auraos.util.PhotoUrlBuilder
import com.beta.auraos.util.TabNavigator
import com.beta.auraos.databinding.ActivityLoginBinding
import java.security.Signature
import java.util.concurrent.Executor

class LoginActivity : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var biometricKeyManager: BiometricKeyManager
    private var lastPassword: String = ""
    private var currentChallenge: String = ""
    private var isReturningUser = false

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

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory(application))
            .get(LoginViewModel::class.java)

        biometricKeyManager = BiometricKeyManager(application)

        isReturningUser = loginViewModel.isReturningUser()

        setupObservers()
        setupQuickLogin()
        setupFullLogin()
        setupBiometric()
        setupFooterLinks()

        if (isReturningUser) {
            startReturningUserFlow()
        } else {
            binding.username.setText("admin@smartpark.com")
            binding.password.setText("admin123")
        }
    }

    private fun setupObservers() {
        loginViewModel.isLoading.observe(this@LoginActivity) { isLoading ->
            binding.loading.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer
            if (loginViewModel.isLoading.value != true) {
                binding.login.isEnabled = loginState.isDataValid
            }
            if (loginState.usernameError != null) {
                binding.username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                binding.password.error = getString(loginState.passwordError)
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
    }

    private fun setupPasswordToggle(toggleButton: android.widget.ImageButton, editText: android.widget.EditText) {
        var isVisible = false
        toggleButton.setImageResource(R.drawable.ic_visibility_off)
        toggleButton.setOnClickListener {
            isVisible = !isVisible
            if (isVisible) {
                editText.inputType = android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                toggleButton.setImageResource(R.drawable.ic_visibility)
            } else {
                editText.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                toggleButton.setImageResource(R.drawable.ic_visibility_off)
            }
            editText.text?.length?.let { editText.setSelection(it) }
        }
    }

    private fun setupQuickLogin() {
        val storedEmail = loginViewModel.getStoredEmail()
        binding.tvQuickGreeting?.text = getString(
            R.string.quick_login_greeting,
            loginViewModel.getStoredDisplayName()
                ?: storedEmail
                ?: ""
        )
        binding.tvQuickEmail?.text = storedEmail?.let { maskEmail(it) } ?: ""

        binding.etQuickPassword?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doQuickLogin()
                true
            } else false
        }

        binding.btnQuickLogin?.setOnClickListener { doQuickLogin() }

        binding.tvQuickSwitch?.setOnClickListener { switchToFullLogin() }

        binding.quickPasswordToggle?.let { toggle ->
            binding.etQuickPassword?.let { setupPasswordToggle(toggle, it) }
        }
    }

    private fun setupFullLogin() {
        val username = binding.username
        val password = binding.password
        val login = binding.login

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

        binding.passwordToggle?.let { toggle ->
            setupPasswordToggle(toggle, binding.password)
        }
    }

    private fun startReturningUserFlow() {
        showQuickLogin()

        if (hasBiometricHardware() && biometricKeyManager.isKeyGenerated() && loginViewModel.hasBiometricAvailable()) {
            binding.quickLoginContainer?.post { tryAutoBiometric() }
        }
    }

    private fun tryAutoBiometric() {
        val signature = biometricKeyManager.getSignatureForAuth() ?: return

        currentChallenge = "smartpark_challenge_${System.currentTimeMillis()}"

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
            .build()

        executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val sig = result.cryptoObject?.signature ?: return
                    val userId = loginViewModel.getUserId() ?: return
                    try {
                        val signed = biometricKeyManager.signChallenge(sig, currentChallenge)
                        loginViewModel.loginWithBiometric(userId, currentChallenge, signed)
                    } catch (e: Exception) {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.biometric_use_fingerprint),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.biometric_fingerprint_not_recognized),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })

        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(signature))
    }

    private fun showQuickLogin() {
        binding.quickLoginContainer?.visibility = View.VISIBLE
        binding.fullLoginContainer?.visibility = View.GONE

        loadAvatarForQuickLogin()
    }

    private fun loadAvatarForQuickLogin() {
        val avatarUrl = loginViewModel.getAvatarUrl()
        val displayName = loginViewModel.getStoredDisplayName()
        val fullUrl = PhotoUrlBuilder.build(avatarUrl)
        val initial = displayName?.firstOrNull()?.uppercase() ?: "?"

        if (fullUrl != null) {
            binding.avatarContainerLogin?.visibility = View.VISIBLE
            binding.ivAvatarLogin?.visibility = View.VISIBLE
            binding.tvAvatarLoginInitial?.visibility = View.GONE
            binding.ivAvatarLogin?.load(fullUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                listener(onError = { _, _ ->
                    binding.ivAvatarLogin?.visibility = View.GONE
                    binding.tvAvatarLoginInitial?.visibility = View.VISIBLE
                    binding.tvAvatarLoginInitial?.text = initial
                })
            }
        } else {
            binding.avatarContainerLogin?.visibility = if (displayName != null) View.VISIBLE else View.GONE
            binding.ivAvatarLogin?.visibility = View.GONE
            binding.tvAvatarLoginInitial?.visibility = View.VISIBLE
            binding.tvAvatarLoginInitial?.text = initial
        }
    }

    private fun switchToFullLogin() {
        binding.quickLoginContainer?.visibility = View.GONE
        binding.fullLoginContainer?.visibility = View.VISIBLE
    }

    private fun switchToQuickLogin() {
        binding.fullLoginContainer?.visibility = View.GONE
        binding.quickLoginContainer?.visibility = View.VISIBLE
        loadAvatarForQuickLogin()
    }

    private fun doQuickLogin() {
        val email = loginViewModel.getStoredEmail() ?: return
        val password = binding.etQuickPassword?.text.toString()
        if (password.length <= 5) {
            binding.etQuickPassword?.error = getString(R.string.invalid_password)
            return
        }
        lastPassword = password
        loginViewModel.login(email, password)
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
                    val sig = result.cryptoObject?.signature ?: return
                    val userId = loginViewModel.getUserId() ?: return
                    try {
                        val signed = biometricKeyManager.signChallenge(sig, currentChallenge)
                        loginViewModel.loginWithBiometric(userId, currentChallenge, signed)
                    } catch (e: Exception) {
                        Toast.makeText(
                            applicationContext,
                            getString(R.string.biometric_use_fingerprint),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        val message = when (errorCode) {
                            BiometricPrompt.ERROR_HW_NOT_PRESENT ->
                                getString(R.string.biometric_error_hw_not_present)
                            BiometricPrompt.ERROR_HW_UNAVAILABLE ->
                                getString(R.string.biometric_error_hw_unavailable)
                            BiometricPrompt.ERROR_NO_BIOMETRICS ->
                                getString(R.string.biometric_error_no_fingerprints)
                            else -> getString(R.string.biometric_error_auth_failed)
                        }
                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.biometric_fingerprint_not_recognized),
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
                            Toast.makeText(this, getString(R.string.biometric_error_init), Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        currentChallenge = "smartpark_challenge_${System.currentTimeMillis()}"
                        val promptInfo = BiometricPrompt.PromptInfo.Builder()
                            .setTitle(getString(R.string.biometric_prompt_title))
                            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
                            .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                            .build()
                        biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(signature))
                    }
                    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                        Toast.makeText(this, getString(R.string.biometric_error_hw_not_present), Toast.LENGTH_SHORT).show()
                    }
                    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                        Toast.makeText(this, getString(R.string.biometric_error_hw_unavailable), Toast.LENGTH_SHORT).show()
                    }
                    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                        Toast.makeText(this, getString(R.string.biometric_error_no_fingerprints), Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, getString(R.string.biometric_error_hw_not_present), Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.biometric_error_verify), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFooterLinks() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            "1.0"
        }

        binding.tvHelp?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.about)
                .setMessage(getString(R.string.about_dialog_message, versionName))
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
        }

        binding.tvPrivacy?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.privacy)
                .setMessage(R.string.privacy_dialog_message)
                .setPositiveButton(R.string.dialog_ok, null)
                .show()
        }

        binding.tvNetworkStatus?.setOnClickListener {
            val statusTextId = android.view.View.generateViewId()

            val contentView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(48, 32, 48, 32)
                addView(android.widget.ProgressBar(this@LoginActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).also { it.setMarginEnd(24) }
                })
                addView(TextView(this@LoginActivity).apply {
                    id = statusTextId
                    text = getString(R.string.network_checking)
                    textSize = 16f
                })
            }

            val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.network_status)
                .setView(contentView)
                .setCancelable(false)
                .show()

            lifecycleScope.launch {
                val apiStatus = withContext(Dispatchers.IO) {
                    try {
                        val api = com.beta.auraos.data.remote.ApiClient.createApi<com.beta.auraos.data.remote.HealthApi>()
                        val response = api.health()
                        response
                    } catch (_: Exception) {
                        null
                    }
                }

                val statusText = contentView.findViewById<TextView>(statusTextId)
                val spinner = contentView.getChildAt(0)
                spinner.visibility = android.view.View.GONE

                val message = if (apiStatus != null) {
                    val statusLabel = when (apiStatus.status) {
                        "ok" -> getString(R.string.health_status_ok)
                        "error" -> getString(R.string.health_status_error)
                        else -> apiStatus.status
                    }
                    val dbLabel = when (apiStatus.database) {
                        "ok" -> getString(R.string.health_db_ok)
                        "error" -> getString(R.string.health_db_error)
                        else -> apiStatus.database
                    }
                    val uptimeSec = apiStatus.uptime.toLong()
                    val uptimeLabel = if (uptimeSec >= 3600) {
                        "%dh %dm".format(uptimeSec / 3600, (uptimeSec % 3600) / 60)
                    } else {
                        "%dm %ds".format(uptimeSec / 60, uptimeSec % 60)
                    }
                    getString(
                        R.string.network_health_format,
                        statusLabel,
                        dbLabel,
                        uptimeLabel
                    )
                } else {
                    getString(R.string.network_offline)
                }
                statusText?.text = message
                dialog.setCancelable(true)
                dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_ok)) { d, _ -> d.dismiss() }
            }
        }
    }

    private fun hasBiometricHardware(): Boolean {
        return try {
            val mgr = BiometricManager.from(this)
            mgr.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    private fun updateUiWithUser(model: LoggedInUserView) {
        Toast.makeText(
            applicationContext,
            getString(R.string.welcome_toast, model.displayName),
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

    private fun maskEmail(email: String): String {
        val atIndex = email.indexOf('@')
        if (atIndex <= 1) return email
        val localPart = email.substring(0, atIndex)
        val domain = email.substring(atIndex)
        val visible = minOf(localPart.length, 3)
        return localPart.substring(0, visible) + "***" + domain
    }
}

fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}

package com.beta.auraos

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import com.beta.auraos.data.local.BiometricKeyManager
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.LoginDataSource
import com.beta.auraos.data.Result
import com.beta.auraos.databinding.ActivityHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Post-login landing screen.
 *
 * ## Biometric enrollment
 * When launched from [LoginActivity] with `EXTRA_EMAIL`, `EXTRA_USER_ID` and
 * `EXTRA_PASSWORD`, this activity prompts the user to enable biometric
 * authentication. If accepted:
 *
 * 1. `BiometricKeyManager.generateKeyPair()` creates an RSA key inside
 *    the hardware-backed AndroidKeyStore.
 * 2. The X.509 public key is read and Base64-encoded.
 * 3. `POST /api/v1/biometric-enroll` registers the public key with the server.
 * 4. On success, credentials are saved in [SessionManager] and the
 *    [SessionManager.isBiometricEnrolled] flag is set.
 *
 * If any step fails the generated key is deleted and a Toast shows the
 * error, so the user can try again on the next login.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var biometricKeyManager: BiometricKeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(application)
        biometricKeyManager = BiometricKeyManager(application)

        setSupportActionBar(findViewById(R.id.toolbar))
        binding.toolbarLayout.title = title

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null)
                .setAnchorView(R.id.fab).show()
        }

        val email = intent.getStringExtra(EXTRA_EMAIL)
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        val userId = intent.getStringExtra(EXTRA_USER_ID)

        if (email != null && userId != null && !sessionManager.isBiometricEnrolled()) {
            promptBiometricEnrollment(email, userId)
        }
    }

    private fun promptBiometricEnrollment(email: String, userId: String) {
        val password = intent.getStringExtra(EXTRA_PASSWORD) ?: return

        AlertDialog.Builder(this)
            .setTitle("Acceso Biométrico")
            .setMessage("¿Desea activar el acceso rápido con huella digital para la próxima vez?")
            .setPositiveButton("Activar") { _, _ ->
                enrollBiometric(email, password, userId)
            }
            .setNegativeButton("Ahora no", null)
            .show()
    }

    private fun enrollBiometric(email: String, password: String, userId: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    biometricKeyManager.generateKeyPair()
                    val publicKey = biometricKeyManager.getPublicKey()
                    if (publicKey == null) {
                        return@withContext Result.Error(
                            java.io.IOException("No se pudo generar la llave biométrica")
                        )
                    }
                    val token = sessionManager.getToken()
                    if (token == null) {
                        return@withContext Result.Error(
                            java.io.IOException("Sesión expirada. Inicie sesión nuevamente.")
                        )
                    }
                    val dataSource = LoginDataSource(sessionManager)
                    dataSource.biometricEnroll(token, userId, publicKey)
                } catch (e: Exception) {
                    biometricKeyManager.deleteKey()
                    Result.Error(java.io.IOException(e.localizedMessage ?: "Error al registrar biometría"))
                }
            }

            withContext(Dispatchers.Main) {
                when (result) {
                    is Result.Success -> {
                        sessionManager.saveCredentials(email, password)
                        sessionManager.setBiometricEnrolled(true)
                        Toast.makeText(
                            this@HomeActivity,
                            "Acceso biométrico activado",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    is Result.Error -> {
                        biometricKeyManager.deleteKey()
                        Toast.makeText(
                            this@HomeActivity,
                            result.exception.localizedMessage ?: "Error al activar biometría",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_USER_ID = "extra_user_id"
    }
}
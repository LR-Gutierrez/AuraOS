package com.beta.auraos

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.data.LoginDataSource
import com.beta.auraos.data.Result
import com.beta.auraos.data.local.BiometricKeyManager
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.databinding.ActivityDashboardBinding
import com.beta.auraos.util.TabNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var biometricKeyManager: BiometricKeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        biometricKeyManager = BiometricKeyManager(this)

        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.selectedItemId = R.id.nav_dashboard

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> true
                else -> {
                    TabNavigator.navigate(this, item.itemId)
                    true
                }
            }
        }

        val email = intent.getStringExtra(EXTRA_EMAIL)
        val userId = intent.getStringExtra(EXTRA_USER_ID)
        if (email != null && userId != null && !sessionManager.isBiometricEnrolled()) {
            promptBiometricEnrollment(email, userId)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBarInsets.bottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.homeScrollView) { view, insets ->
            val bottomNavH = resources.getDimensionPixelSize(R.dimen.bottom_nav_height)
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = bottomNavH + navBarInsets.bottom)
            insets
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
                        ?: return@withContext Result.Error(java.io.IOException("No se pudo generar la llave biométrica"))

                    val token = sessionManager.getToken()
                        ?: return@withContext Result.Error(java.io.IOException("Sesión expirada. Inicie sesión nuevamente."))

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
                        Toast.makeText(this@DashboardActivity, "Acceso biométrico activado", Toast.LENGTH_SHORT).show()
                    }
                    is Result.Error -> {
                        biometricKeyManager.deleteKey()
                        Toast.makeText(this@DashboardActivity, result.exception.localizedMessage ?: "Error al activar biometría", Toast.LENGTH_SHORT).show()
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

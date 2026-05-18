package com.beta.auraos

import android.content.Intent
import android.graphics.Color
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.data.LoginDataSource
import com.beta.auraos.data.Result
import com.beta.auraos.data.local.BiometricKeyManager
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.databinding.ActivityHomeBinding
import com.beta.auraos.ui.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Post-login landing screen.
 */
class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var biometricKeyManager: BiometricKeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)
        biometricKeyManager = BiometricKeyManager(this)

        // SOLUCIÓN COMPLETA A LOS UNRESOLVED REFERENCES: Usamos View Binding correctamente
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Forzar por defecto la pestaña "Security" activa según tu mock
        binding.bottomNavigation.selectedItemId = R.id.nav_security

        // Optimizaciones KTX sugeridas por tu IDE para parsear colores hex de forma segura
        val colorBlack = Color.BLACK
        val colorWhite = Color.WHITE
        val colorGray = "#5E5E61".toColorInt()
        val colorOutline = "#E2E2E4".toColorInt()

        // Listener para alternar entre categorías de vehículo usando la jerarquía de Binding
        binding.btnLightVehicle.setOnClickListener {
            binding.btnLightVehicle.setBackgroundColor(colorBlack)
            binding.btnLightVehicle.setTextColor(colorWhite)

            binding.btnHeavyVehicle.setBackgroundColor(Color.TRANSPARENT)
            binding.btnHeavyVehicle.strokeColor = ColorStateList.valueOf(colorOutline)
            binding.btnHeavyVehicle.setTextColor(colorGray)
        }

        binding.btnHeavyVehicle.setOnClickListener {
            binding.btnHeavyVehicle.setBackgroundColor(colorBlack)
            binding.btnHeavyVehicle.setTextColor(colorWhite)

            binding.btnLightVehicle.setBackgroundColor(Color.TRANSPARENT)
            binding.btnLightVehicle.setTextColor(colorGray)
        }

        // Acción al confirmar ingreso
        binding.btnConfirmEntry.setOnClickListener {
            Toast.makeText(this, "Registrando ingreso en SmartPark Server...", Toast.LENGTH_SHORT).show()
        }

        // Manejar los clics de la barra de navegación inferior
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { /* Cambiar Fragmento o Vista */ true }
                R.id.nav_security -> true
                R.id.nav_map -> { /* Mostrar mapa/croquis espacial */ true }
                R.id.nav_analytics -> true
                else -> false
            }
        }

        // CONTROL DE FLUJO BIOMÉTRICO HEREDADO:
        val email = intent.getStringExtra(EXTRA_EMAIL)
        val userId = intent.getStringExtra(EXTRA_USER_ID)
        if (email != null && userId != null && !sessionManager.isBiometricEnrolled()) {
            promptBiometricEnrollment(email, userId)
        }

        // SOLUCIÓN DEFINITIVA AL APARTADO INMERSIVO: Expandir la altura usando la vista del Binding
        var originalHeaderHeight = 0
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())

            if (originalHeaderHeight == 0) {
                originalHeaderHeight = view.layoutParams.height
            }

            val params = view.layoutParams
            params.height = originalHeaderHeight + statusBarInsets.top
            view.layoutParams = params

            view.setPadding(
                view.paddingLeft,
                statusBarInsets.top,
                view.paddingRight,
                view.paddingBottom
            )

            insets
        }
    }

    private fun promptBiometricEnrollment(email: String, userId: String) {
        // Reducción if-null usando operador elvis '?:' sugerido por tu IDE
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
                    // Uso del operador elvis '?:' estructural para colapsar flujos if-null
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
                        Toast.makeText(this@HomeActivity, "Acceso biométrico activado", Toast.LENGTH_SHORT).show()
                    }
                    is Result.Error -> {
                        biometricKeyManager.deleteKey()
                        Toast.makeText(this@HomeActivity, result.exception.localizedMessage ?: "Error al activar biometría", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Método auxiliar por si necesitas cerrar sesión y volver de forma segura
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
        const val EXTRA_PASSWORD = "extra_password"
        const val EXTRA_USER_ID = "extra_user_id"
    }
}
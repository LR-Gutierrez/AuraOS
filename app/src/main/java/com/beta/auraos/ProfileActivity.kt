package com.beta.auraos

import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.core.content.FileProvider
import java.io.File
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.request.CachePolicy
import coil.transform.CircleCropTransformation
import com.beta.auraos.data.LoginDataSource
import com.beta.auraos.data.Result
import com.beta.auraos.data.local.BiometricKeyManager
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.databinding.ActivityProfileBinding
import com.beta.auraos.ui.login.LoginActivity
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.ui.branches.BranchFormActivity
import com.beta.auraos.ui.branches.BranchListActivity
import com.beta.auraos.ui.memberships.MembershipListActivity
import com.beta.auraos.util.ParkingFormat
import com.beta.auraos.util.PhotoUrlBuilder
import com.beta.auraos.util.TabNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var biometricKeyManager: BiometricKeyManager
    private var pendingAvatarUri: Uri? = null
    private var cameraAvatarUri: Uri? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingAvatarUri = it
            uploadAvatar(it)
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraAvatarUri?.let { uploadAvatar(it) }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, R.string.permission_camera_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        biometricKeyManager = BiometricKeyManager(this)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TabNavigator.setup(this, binding.tabBar, R.id.nav_profile)
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeScrollView) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBar.top)
            insets
        }

        binding.editAvatarButton.setOnClickListener {
            showAvatarSourceDialog()
        }

        loadUserProfile()
        setupOperationRows()
        setupBiometricCard()
        setupThemeToggle()
        setupAbout()
        setupLogout()
    }

    override fun onResume() {
        super.onResume()
        updateBiometricStatus()
    }

    private fun showAvatarSourceDialog() {
        val options = arrayOf(
            getString(R.string.profile_avatar_take_photo),
            getString(R.string.profile_avatar_choose_gallery)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.profile_avatar_title)
            .setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, options)) { _, which ->
                when (which) {
                    0 -> requestCamera()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun requestCamera() {
        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val photoDir = File(filesDir, "avatars").also { it.mkdirs() }
        val photoFile = File(photoDir, "avatar_${System.currentTimeMillis()}.jpg")
        cameraAvatarUri = FileProvider.getUriForFile(
            this, "${packageName}.fileprovider", photoFile
        )
        cameraAvatarUri?.let { takePictureLauncher.launch(it) }
    }

    private fun loadUserProfile() {
        val displayName = sessionManager.getLastDisplayName() ?: getString(R.string.app_name)
        val email = sessionManager.getLastEmail() ?: ""

        binding.tvProfileName.text = displayName
        binding.tvProfileEmail.text = email
        binding.tvAvatarInitial.text = initials(displayName)

        loadAvatar(displayName)
    }

    private fun loadAvatar(displayName: String, forceRefresh: Boolean = false) {
        val avatarUrl = sessionManager.getAvatarUrl()
        val fullUrl = PhotoUrlBuilder.build(avatarUrl)

        if (fullUrl != null) {
            binding.ivAvatarPhoto.visibility = android.view.View.VISIBLE
            binding.tvAvatarInitial.visibility = android.view.View.GONE
            binding.ivAvatarPhoto.load(fullUrl) {
                crossfade(true)
                transformations(CircleCropTransformation())
                if (forceRefresh) {
                    memoryCachePolicy(CachePolicy.DISABLED)
                }
                listener(onError = { _, _ ->
                    binding.ivAvatarPhoto.visibility = android.view.View.GONE
                    binding.tvAvatarInitial.visibility = android.view.View.VISIBLE
                    binding.tvAvatarInitial.text = initials(displayName)
                })
            }
            binding.ivAvatarPhoto.setOnClickListener { showAvatarFullScreen(fullUrl) }
        } else {
            binding.ivAvatarPhoto.visibility = android.view.View.GONE
            binding.ivAvatarPhoto.setOnClickListener(null)
            binding.tvAvatarInitial.visibility = android.view.View.VISIBLE
            binding.tvAvatarInitial.text = initials(displayName)
        }
    }

    private fun showAvatarFullScreen(url: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val imageView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            load(url) { crossfade(true) }
            setOnClickListener { dialog.dismiss() }
        }

        dialog.setContentView(imageView)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        dialog.show()
    }

    private fun uploadAvatar(uri: Uri) {
        val token = sessionManager.getToken() ?: return

        lifecycleScope.launch {
            val dataSource = LoginDataSource(sessionManager)
            val result = withContext(Dispatchers.IO) {
                dataSource.uploadAvatar(token, uri, contentResolver)
            }

            when (result) {
                is Result.Success -> {
                    sessionManager.saveAvatarUrl(result.data)
                    runOnUiThread {
                        val displayName = sessionManager.getLastDisplayName()
                            ?: getString(R.string.app_name)
                        loadAvatar(displayName, forceRefresh = true)
                        Toast.makeText(
                            this@ProfileActivity,
                            R.string.profile_avatar_upload_success,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                is Result.Error -> {
                    Toast.makeText(
                        this@ProfileActivity,
                        result.exception.message ?: getString(R.string.profile_avatar_upload_error),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun updateBiometricStatus() {
        val enrolled = sessionManager.isBiometricEnrolled() && biometricKeyManager.isKeyGenerated()
        binding.switchBiometric.isChecked = enrolled
        binding.tvBiometricMeta.setText(
            if (enrolled) R.string.profile_biometric_enabled else R.string.profile_biometric_enroll_hint
        )
    }

    private fun initials(name: String): String =
        name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

    private fun setupOperationRows() {
        binding.rowBranches.tvTitle.setText(R.string.pc_branches)
        binding.rowBranches.root.setOnClickListener {
            startActivity(Intent(this, BranchListActivity::class.java))
        }
        binding.rowRates.tvTitle.setText(R.string.pc_rates)
        binding.rowMemberships.tvTitle.setText(R.string.membership_list_title)
        binding.rowMemberships.tvMeta.setText(R.string.pc_memberships_meta)
        binding.rowMemberships.root.setOnClickListener {
            startActivity(Intent(this, MembershipListActivity::class.java))
        }
        binding.rowKiosk.tvTitle.setText(R.string.pc_kiosk_mode)
        binding.rowKiosk.root.setOnClickListener {
            startActivity(Intent(this, KioskActivity::class.java))
        }

        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            val branches = withContext(Dispatchers.IO) { db.branchDao().getAll() }
            binding.rowBranches.tvMeta.text = branches.joinToString(" · ") { it.name }
            val role = getString(if (sessionManager.isAdmin()) R.string.pc_role_admin else R.string.pc_role_operator)
            binding.tvProfileEmail.text =
                resources.getQuantityString(R.plurals.pc_profile_meta, branches.size, role, ParkingFormat.pad2(branches.size))
            val current = branches.find { it.id == sessionManager.getSelectedBranchId() } ?: branches.firstOrNull()
            binding.rowKiosk.tvMeta.text = getString(R.string.pc_kiosk_meta, current?.name ?: "")
            // Rates belong to a branch: show and open the one in use (read-only for operators).
            binding.rowRates.root.visibility = if (current == null) View.GONE else View.VISIBLE
            if (current != null) {
                binding.rowRates.tvMeta.text = getString(
                    R.string.pc_rates_meta,
                    ParkingFormat.money(current.lightVehicleRate, current.currency),
                    ParkingFormat.money(current.heavyVehicleRate, current.currency),
                    ParkingFormat.money(current.motorcycleRate, current.currency),
                )
                binding.rowRates.root.setOnClickListener { BranchFormActivity.open(this@ProfileActivity, current.id) }
            }
        }
    }

    private fun setupBiometricCard() {
        val toggleBiometric = {
            val enrolled = sessionManager.isBiometricEnrolled() && biometricKeyManager.isKeyGenerated()
            if (enrolled) {
                showBiometricDisableDialog()
            } else {
                showBiometricEnrollDialog()
            }
        }

        binding.cardSecurity.setOnClickListener { toggleBiometric() }
        binding.switchBiometric.setOnClickListener { toggleBiometric() }
    }

    private fun showBiometricEnrollDialog() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = getString(R.string.profile_biometric_enroll_password_hint)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.biometric_enroll_title)
            .setMessage(R.string.profile_biometric_enroll_password_message)
            .setView(input)
            .setPositiveButton(R.string.biometric_enroll_activate) { _, _ ->
                val password = input.text.toString()
                if (password.isBlank()) {
                    Toast.makeText(this, R.string.field_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                enrollBiometric(password)
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                updateBiometricStatus()
            }
            .setOnCancelListener { updateBiometricStatus() }
            .show()
    }

    private fun enrollBiometric(password: String) {
        val email = sessionManager.getLastEmail()
        val userId = sessionManager.getUserId()

        if (email == null || userId == null) {
            Toast.makeText(this, R.string.error_session_expired, Toast.LENGTH_SHORT).show()
            return
        }

        binding.switchBiometric.isEnabled = false

        lifecycleScope.launch {
            try {
                biometricKeyManager.generateKeyPair()

                sessionManager.saveCredentials(email, password)
                sessionManager.setBiometricEnrolled(true)
                updateBiometricStatus()

                launch(Dispatchers.IO) {
                    try {
                        val publicKey = biometricKeyManager.getPublicKey() ?: return@launch
                        val token = sessionManager.getToken() ?: return@launch
                        val dataSource = LoginDataSource(sessionManager)
                        dataSource.biometricEnroll(token, userId, publicKey)
                    } catch (_: Exception) { }
                }

                binding.switchBiometric.isEnabled = true
                Toast.makeText(
                    this@ProfileActivity,
                    R.string.profile_biometric_enroll_success,
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                biometricKeyManager.deleteKey()
                binding.switchBiometric.isEnabled = true
                updateBiometricStatus()
                Toast.makeText(
                    this@ProfileActivity,
                    getString(R.string.profile_biometric_enroll_error, e.localizedMessage),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showBiometricDisableDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.profile_biometric_disable_confirm)
            .setMessage(R.string.profile_biometric_disable_message)
            .setPositiveButton(R.string.pc_disable) { _, _ ->
                disableBiometric()
            }
            .setNegativeButton(R.string.dialog_cancel) { _, _ ->
                updateBiometricStatus()
            }
            .setOnCancelListener { updateBiometricStatus() }
            .show()
    }

    private fun disableBiometric() {
        biometricKeyManager.deleteKey()
        sessionManager.setBiometricEnrolled(false)
        sessionManager.clearCredentials()
        updateBiometricStatus()
        Toast.makeText(this, R.string.profile_biometric_disabled, Toast.LENGTH_SHORT).show()
    }

    private fun setupThemeToggle() {
        // Marcamos el botón correcto al iniciar basándonos en la persistencia
        // Reflect the theme on screen, which follows the system until the user picks one.
        val isDark = sessionManager.darkModePreference()
            ?: ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        if (isDark) {
            binding.toggleGroupTheme.check(R.id.btn_theme_midnight)
        } else {
            binding.toggleGroupTheme.check(R.id.btn_theme_crystal)
        }

        // Listener para los cambios de selección en los botones contiguos
        binding.toggleGroupTheme.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val targetDark = (checkedId == R.id.btn_theme_midnight)

                // Evitamos recrear infinitamente si ya estamos en ese estado actual
                if (targetDark != sessionManager.darkModePreference()) {
                    sessionManager.setDarkModeEnabled(targetDark)
                    val mode = if (targetDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                    AppCompatDelegate.setDefaultNightMode(mode)
                    recreate()
                }
            }
        }
    }

    private fun setupAbout() {
        val info = try {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        } catch (_: Exception) { null }
        @Suppress("DEPRECATION")
        val code = info?.versionCode ?: 1
        binding.tvVersion.text = getString(
            R.string.pc_version_line,
            getString(R.string.pc_wordmark),
            info?.versionName ?: "1.0",
            code
        )
    }

    private fun setupLogout() {
        // ID actualizado al nuevo botón estirado
        binding.btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.profile_logout_confirm_title)
                .setMessage(
                    if (sessionManager.isLocalOnlyAccount()) R.string.pc_logout_local_message
                    else R.string.profile_logout_confirm_message
                )
                .setPositiveButton(R.string.profile_logout_confirm_action) { _, _ ->
                    performLogout()
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    private fun performLogout() {
        // A device-only account is the sole way into this phone's data: signing out returns to
        // the login screen but keeps the account, its password hash and the fingerprint key.
        if (!sessionManager.isLocalOnlyAccount()) {
            sessionManager.clearSession()
            biometricKeyManager.deleteKey()
        }

        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
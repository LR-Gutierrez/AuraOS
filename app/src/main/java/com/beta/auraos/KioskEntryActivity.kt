package com.beta.auraos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.ParkingApi
import com.beta.auraos.databinding.ActivityKioskEntryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KioskEntryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskEntryBinding
    private lateinit var sessionManager: SessionManager

    private var branchId: String = ""
    private var membershipId: String? = null
    private var memberName: String? = null
    private var mode: String = "ticket"

    private var plateBitmap: Bitmap? = null
    private var platePhotoPath: String? = null

    private var currentVehicleType: String = "light"

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            openCameraForPlate()
        } else {
            Toast.makeText(this, R.string.permission_camera_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && platePhotoPath != null) {
            val bitmap = BitmapFactory.decodeFile(platePhotoPath)
            if (bitmap != null) {
                plateBitmap = bitmap
                binding.ivPlate.setImageBitmap(bitmap)
                binding.btnRetakePlate.visibility = android.view.View.VISIBLE
                binding.btnDeletePlate.visibility = android.view.View.VISIBLE
                binding.cardPlateCapture.setOnClickListener(null)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityKioskEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                top = 0,
                bottom = bars.bottom,
                left = 0,
                right = 0
            )
            insets
        }

        sessionManager = SessionManager(this)

        branchId = intent.getStringExtra("branchId") ?: ""
        membershipId = intent.getStringExtra("membershipId")
        memberName = intent.getStringExtra("memberName")
        mode = intent.getStringExtra("mode") ?: "ticket"

        if (branchId.isEmpty()) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupMemberInfo()
        setupClickHandlers()
    }

    private fun setupMemberInfo() {
        if (mode == "membership" && memberName != null) {
            binding.containerMemberInfo.visibility = android.view.View.VISIBLE
            binding.tvMemberName.text = memberName
        } else {
            binding.containerMemberInfo.visibility = android.view.View.GONE
        }
    }

    private fun setupClickHandlers() {
        binding.btnBack.setOnClickListener { finish() }

        binding.cardPlateCapture.setOnClickListener { checkCameraPermissionAndCapture() }
        binding.btnRetakePlate.setOnClickListener { checkCameraPermissionAndCapture() }
        binding.btnDeletePlate.setOnClickListener { deletePlatePhoto() }

        binding.btnConfirmEntry.setOnClickListener { confirmEntry() }
    }

    private fun checkCameraPermissionAndCapture() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            openCameraForPlate()
        }
    }

    private fun openCameraForPlate() {
        val photoFile = createImageFile()
        if (photoFile == null) {
            Toast.makeText(this, R.string.error_camera_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        platePhotoPath = photoFile.absolutePath
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        cameraLauncher.launch(uri)
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile("PLATE_${timeStamp}_", ".jpg", storageDir)
        } catch (e: Exception) {
            null
        }
    }

    private fun deletePlatePhoto() {
        plateBitmap = null
        platePhotoPath?.let { File(it).delete() }
        platePhotoPath = null
        binding.ivPlate.setImageResource(R.drawable.ic_scan_corners)
        binding.btnRetakePlate.visibility = android.view.View.GONE
        binding.btnDeletePlate.visibility = android.view.View.GONE
        binding.cardPlateCapture.setOnClickListener { checkCameraPermissionAndCapture() }
    }

    private fun confirmEntry() {
        val plateText = binding.etPlateNumber.text.toString().trim()

        if (plateText.isEmpty()) {
            binding.tvPlateError.visibility = android.view.View.VISIBLE
            binding.etPlateNumber.requestFocus()
            return
        }
        binding.tvPlateError.visibility = android.view.View.GONE

        showLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val api = ApiClient.createApi<ParkingApi>()

                val platePart = plateText.toRequestBody("text/plain".toMediaTypeOrNull())
                val vehicleTypePart = currentVehicleType.toRequestBody("text/plain".toMediaTypeOrNull())
                val branchIdPart = branchId.toRequestBody("text/plain".toMediaTypeOrNull())
                val membershipIdPart = membershipId?.toRequestBody("text/plain".toMediaTypeOrNull())

                val platePhotoPart = if (plateBitmap != null) {
                    val baos = ByteArrayOutputStream()
                    plateBitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                    val tempFile = File.createTempFile("plate_upload", ".jpg")
                    FileOutputStream(tempFile).use { it.write(baos.toByteArray()) }
                    MultipartBody.Part.createFormData(
                        "platePhoto",
                        tempFile.name,
                        tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    )
                } else {
                    null
                }

                val response = api.registerEntry(
                    plate = platePart,
                    vehicleType = vehicleTypePart,
                    branchId = branchIdPart,
                    membershipId = membershipIdPart,
                    platePhoto = platePhotoPart,
                    front = null,
                    rear = null,
                    left = null,
                    right = null
                )

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    if (response.isSuccessful) {
                        AlertDialog.Builder(this@KioskEntryActivity)
                            .setTitle(R.string.entry_success_title)
                            .setMessage(getString(R.string.confirm_entry_success))
                            .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                                dialog.dismiss()
                                finish()
                            }
                            .setCancelable(false)
                            .show()
                    } else {
                        val errorBody = response.errorBody()?.string() ?: getString(R.string.error_generic)
                        Toast.makeText(this@KioskEntryActivity, errorBody, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@KioskEntryActivity, "${getString(R.string.error_network)}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }
}

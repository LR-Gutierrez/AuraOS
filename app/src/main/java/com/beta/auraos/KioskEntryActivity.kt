package com.beta.auraos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.os.Environment
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.data.VehicleEntryRepository

import com.beta.auraos.data.local.SyncCoordinator
import com.beta.auraos.data.local.PendingPhotoStore
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.ParkingApi
import com.beta.auraos.databinding.ActivityKioskEntryBinding
import com.beta.auraos.util.ParkingFormat
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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class KioskEntryActivity : BaseActivity() {

    private enum class Step { PLATE, CAPTURE, SENDING, TICKET }

    private lateinit var binding: ActivityKioskEntryBinding

    private var branchId: String = ""
    private var branchName: String = ""
    private var membershipId: String? = null
    private var mode: String = "ticket"

    private var plateBitmap: Bitmap? = null
    private var platePhotoPath: String? = null

    private var currentVehicleType: String = "light"

    private val vehicleEntryRepository: VehicleEntryRepository by lazy {
        VehicleEntryRepository(AppDatabase.getInstance(this@KioskEntryActivity))
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            openCameraForPlate()
        } else {
            // Without the camera the plate photo stays optional, as before.
            Toast.makeText(this, R.string.permission_camera_denied, Toast.LENGTH_SHORT).show()
            confirmEntry()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && platePhotoPath != null) {
            BitmapFactory.decodeFile(platePhotoPath)?.let { bitmap ->
                plateBitmap = bitmap
                binding.ivPlate.setImageBitmap(bitmap)
                binding.ivPlate.visibility = View.VISIBLE
            }
        }
        confirmEntry()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityKioskEntryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowInsets()

        branchId = intent.getStringExtra("branchId") ?: ""
        branchName = intent.getStringExtra("branchName") ?: ""
        membershipId = intent.getStringExtra("membershipId")
        mode = intent.getStringExtra("mode") ?: "ticket"

        if (branchId.isEmpty()) {
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.tvHeader.text = getString(R.string.pc_kiosk_header, branchName)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnDone.setOnClickListener { finish() }
        binding.btnConfirmEntry.setOnClickListener {
            if (plate().length >= MIN_PLATE) startCapture()
        }
        binding.etPlateNumber.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(15))
        binding.etPlateNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                binding.tvPlateError.visibility = View.GONE
                updateContinue()
            }
        })
        updateContinue()
        showStep(Step.PLATE)
    }

    private fun plate(): String = binding.etPlateNumber.text.toString().trim().uppercase()

    private fun updateContinue() {
        val ok = plate().length >= MIN_PLATE
        binding.btnConfirmEntry.isEnabled = ok
        binding.btnConfirmEntry.alpha = if (ok) 1f else 0.4f
    }

    private fun showStep(step: Step) {
        binding.sectionPlate.visibility = if (step == Step.PLATE) View.VISIBLE else View.GONE
        binding.sectionCapture.visibility =
            if (step == Step.CAPTURE || step == Step.SENDING) View.VISIBLE else View.GONE
        binding.sectionTicket.visibility = if (step == Step.TICKET) View.VISIBLE else View.GONE
        binding.loadingOverlay.visibility = if (step == Step.SENDING) View.VISIBLE else View.GONE
        if (step == Step.CAPTURE || step == Step.SENDING) {
            binding.tvCapturePlate.text = plate()
            binding.tvCaptureTitle.setText(if (step == Step.SENDING) R.string.pc_registering else R.string.pc_capturing)
            binding.tvCaptureHint.setText(
                if (step == Step.SENDING) R.string.pc_registering_hint else R.string.pc_capturing_hint
            )
        }
    }

    private fun startCapture() {
        showStep(Step.CAPTURE)
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else {
            openCameraForPlate()
        }
    }

    private fun openCameraForPlate() {
        val photoFile = createImageFile()
        if (photoFile == null) {
            Toast.makeText(this, R.string.error_camera_unavailable, Toast.LENGTH_SHORT).show()
            confirmEntry()
            return
        }
        platePhotoPath = photoFile.absolutePath
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
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

    private fun keepPlatePhoto(entryId: String) {
        platePhotoPath?.let { PendingPhotoStore.keep(this, entryId, mapOf("plate" to File(it))) }
        platePhotoPath = null
    }

    private fun deletePlatePhoto() {
        plateBitmap = null
        platePhotoPath?.let { File(it).delete() }
        platePhotoPath = null
        binding.ivPlate.setImageDrawable(null)
        binding.ivPlate.visibility = View.GONE
    }

    private fun showTicket(plate: String, entryId: String, queued: Boolean) {
        binding.tvTicketTitle.setText(if (queued) R.string.pc_kiosk_saved_offline else R.string.pc_kiosk_go_ahead)
        binding.tvTicketHint.setText(
            if (queued) R.string.pc_kiosk_saved_offline_hint else R.string.pc_kiosk_ticket_hint
        )
        binding.tvTicketCode.text = "T-" + entryId.filter { it.isLetterOrDigit() }.take(6).uppercase()
        binding.tvTicketPlate.text = plate
        binding.tvTicketTime.text = getString(R.string.pc_entry_time, ParkingFormat.hhmm())
        binding.tvTicketBranch.text = branchName
        deletePlatePhoto()
        showStep(Step.TICKET)
    }

    private fun showError(message: String) {
        deletePlatePhoto()
        binding.tvPlateError.text = message
        binding.tvPlateError.visibility = View.VISIBLE
        showStep(Step.PLATE)
    }

    private fun confirmEntry() {
        val plateText = plate()

        val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val localId = UUID.randomUUID().toString()

        val localEntry = VehicleEntry(
            id = localId,
            plate = plateText,
            vehicleType = currentVehicleType,
            isVip = membershipId != null,
            membershipId = membershipId,
            branchId = branchId,
            platePhotoUrl = null,
            frontPhotoUrl = null,
            rearPhotoUrl = null,
            leftPhotoUrl = null,
            rightPhotoUrl = null,
            createdAt = now,
            exitedAt = null,
            duration = null,
            fee = null,
            branch = null
        )

        showStep(Step.SENDING)

        lifecycleScope.launch(Dispatchers.IO) {
            vehicleEntryRepository.updateCachedEntry(localEntry)

            SyncCoordinator.isApiInProgress = true
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

                val serverEntry = if (response.isSuccessful) response.body()?.data else null
                if (serverEntry != null) {
                    vehicleEntryRepository.replaceLocalWithServerEntry(localId, serverEntry)
                }

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        showTicket(plateText, serverEntry?.id ?: localId, queued = false)
                    } else {
                        val errorBody = response.errorBody()?.string() ?: getString(R.string.error_generic)
                        keepPlatePhoto(localId)
                        showError(getString(R.string.confirm_entry_error, errorBody))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // The entry stays saved locally and syncs later; keep its plate photo.
                    keepPlatePhoto(localId)
                    if (e is IOException) {
                        showTicket(plateText, localId, queued = true)
                    } else {
                        showError(getString(R.string.confirm_entry_error, e.message ?: getString(R.string.error_network)))
                    }
                }
            } finally {
                SyncCoordinator.isApiInProgress = false
                syncCoordinator.onMutation()
            }
        }
    }

    private fun setupWindowInsets() {
        val basePadding = binding.entryRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.entryRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.updatePadding(top = bars.top, bottom = basePadding + bars.bottom)
            insets
        }
    }

    companion object {
        private const val MIN_PLATE = 3
    }
}

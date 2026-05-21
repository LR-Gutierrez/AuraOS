package com.beta.auraos

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.databinding.ActivityHomeBinding
import com.beta.auraos.util.BottomNavIndicator
import com.beta.auraos.util.TabNavigator
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.ParkingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private var isHeavyVehicle = false

    private var frontPhotoUri: Uri? = null
    private var rearPhotoUri: Uri? = null
    private var leftPhotoUri: Uri? = null
    private var rightPhotoUri: Uri? = null

    private var hasFrontPhoto = false
    private var hasRearPhoto = false
    private var hasLeftPhoto = false
    private var hasRightPhoto = false

    private var currentPhotoSlot: String? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val slot = currentPhotoSlot
            val uri = when (slot) {
                "front" -> frontPhotoUri
                "rear" -> rearPhotoUri
                "left" -> leftPhotoUri
                "right" -> rightPhotoUri
                else -> null
            }
            if (slot != null && uri != null) {
                loadPhoto(slot, uri)
                setPhotoState(slot, true)
            }
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    private val parkingApi: ParkingApi by lazy {
        ApiClient.createApi<ParkingApi>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation.selectedItemId = R.id.nav_register

        BottomNavIndicator.attach(
            bottomNav = binding.bottomNavigation,
            indicator = binding.bottomNavIndicator,
            initialItemId = R.id.nav_register,
        )

        initPhotoUris()
        setupPhotoClickHandlers()
        setupRetakeDeleteHandlers()
        setupVehicleButtons()
        setupConfirmButton()
        setupBottomNav()
        setupWindowInsets()
    }

    private fun initPhotoUris() {
        val photoDir = File(filesDir, "photos").also { it.mkdirs() }
        frontPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(photoDir, "front.jpg"))
        rearPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(photoDir, "rear.jpg"))
        leftPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(photoDir, "left.jpg"))
        rightPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(photoDir, "right.jpg"))
    }

    private fun setupPhotoClickHandlers() {
        binding.containerFront.setOnClickListener { requestCamera("front") }
        binding.containerRear.setOnClickListener { requestCamera("rear") }
        binding.containerLeft.setOnClickListener { requestCamera("left") }
        binding.containerRight.setOnClickListener { requestCamera("right") }
    }

    private fun setupRetakeDeleteHandlers() {
        binding.btnRetakeFront.setOnClickListener { requestCamera("front") }
        binding.btnRetakeRear.setOnClickListener { requestCamera("rear") }
        binding.btnRetakeLeft.setOnClickListener { requestCamera("left") }
        binding.btnRetakeRight.setOnClickListener { requestCamera("right") }

        binding.btnDeleteFront.setOnClickListener { deletePhoto("front") }
        binding.btnDeleteRear.setOnClickListener { deletePhoto("rear") }
        binding.btnDeleteLeft.setOnClickListener { deletePhoto("left") }
        binding.btnDeleteRight.setOnClickListener { deletePhoto("right") }
    }

    private fun requestCamera(slot: String) {
        currentPhotoSlot = slot
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val uri = when (currentPhotoSlot) {
            "front" -> frontPhotoUri
            "rear" -> rearPhotoUri
            "left" -> leftPhotoUri
            "right" -> rightPhotoUri
            else -> null
        }
        uri?.let { takePictureLauncher.launch(it) }
    }

    private fun loadPhoto(slot: String, uri: Uri) {
        val iv = when (slot) {
            "front" -> binding.ivFront
            "rear" -> binding.ivRear
            "left" -> binding.ivLeft
            "right" -> binding.ivRight
            else -> return
        }
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        val opts = BitmapFactory.Options().apply {
                            inSampleSize = 4
                        }
                        BitmapFactory.decodeStream(inputStream, null, opts)
                    }
                } catch (e: Exception) {
                    null
                }
            }
            bitmap?.let { iv.setImageBitmap(it) }
        }
    }

    private fun setPhotoState(slot: String, hasPhoto: Boolean) {
        val retakeBtn = when (slot) {
            "front" -> binding.btnRetakeFront
            "rear" -> binding.btnRetakeRear
            "left" -> binding.btnRetakeLeft
            "right" -> binding.btnRetakeRight
            else -> return
        }
        val deleteBtn = when (slot) {
            "front" -> binding.btnDeleteFront
            "rear" -> binding.btnDeleteRear
            "left" -> binding.btnDeleteLeft
            "right" -> binding.btnDeleteRight
            else -> return
        }
        val visibility = if (hasPhoto) View.VISIBLE else View.GONE
        retakeBtn.visibility = visibility
        deleteBtn.visibility = visibility

        when (slot) {
            "front" -> hasFrontPhoto = hasPhoto
            "rear" -> hasRearPhoto = hasPhoto
            "left" -> hasLeftPhoto = hasPhoto
            "right" -> hasRightPhoto = hasPhoto
        }
    }

    private fun deletePhoto(slot: String) {
        val iv = when (slot) {
            "front" -> binding.ivFront.also { it.setImageResource(R.drawable.front) }
            "rear" -> binding.ivRear.also { it.setImageResource(R.drawable.rear) }
            "left" -> binding.ivLeft.also { it.setImageResource(R.drawable.left) }
            "right" -> binding.ivRight.also { it.setImageResource(R.drawable.right) }
            else -> return
        }
        setPhotoState(slot, false)
    }

    private fun setupVehicleButtons() {
        isHeavyVehicle = false
        binding.btnLightVehicle.isSelected = true
        binding.btnHeavyVehicle.isSelected = false

        binding.btnLightVehicle.setOnClickListener {
            binding.btnLightVehicle.isSelected = true
            binding.btnHeavyVehicle.isSelected = false
            isHeavyVehicle = false
        }

        binding.btnHeavyVehicle.setOnClickListener {
            binding.btnLightVehicle.isSelected = false
            binding.btnHeavyVehicle.isSelected = true
            isHeavyVehicle = true
        }
    }

    private fun setupConfirmButton() {
        binding.btnConfirmEntry.setOnClickListener { sendToApi() }
    }

    private fun sendToApi() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val plate = binding.tvPlateNumber.text.toString()
                    .toRequestBody("text/plain".toMediaTypeOrNull())
                val vehicleType = (if (isHeavyVehicle) "heavy" else "light")
                    .toRequestBody("text/plain".toMediaTypeOrNull())

                val frontPart = frontPhotoUri?.toMultipartPart("front")
                val rearPart = rearPhotoUri?.toMultipartPart("rear")
                val leftPart = leftPhotoUri?.toMultipartPart("left")
                val rightPart = rightPhotoUri?.toMultipartPart("right")

                val response = parkingApi.registerEntry(
                    plate = plate,
                    vehicleType = vehicleType,
                    front = frontPart,
                    rear = rearPart,
                    left = leftPart,
                    right = rightPart,
                )

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@HomeActivity, R.string.confirm_entry_success, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@HomeActivity, getString(R.string.confirm_entry_error, "${response.code()}: ${response.message()}"), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, getString(R.string.confirm_entry_error, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun Uri.toMultipartPart(fieldName: String): MultipartBody.Part? {
        return try {
            val bytes = contentResolver.openInputStream(this)?.use { it.readBytes() } ?: return null
            val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            MultipartBody.Part.createFormData(fieldName, "${fieldName}.jpg", requestBody)
        } catch (_: Exception) {
            null
        }
    }

    private fun setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_register -> {
                    BottomNavIndicator.animateTo(
                        bottomNav = binding.bottomNavigation,
                        indicator = binding.bottomNavIndicator,
                        itemId = R.id.nav_register,
                    )
                    true
                }
                else -> {
                    BottomNavIndicator.animateTo(
                        bottomNav = binding.bottomNavigation,
                        indicator = binding.bottomNavIndicator,
                        itemId = item.itemId,
                    )
                    TabNavigator.navigate(this, item.itemId)
                    true
                }
            }
        }
    }

    private fun setupWindowInsets() {
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
}

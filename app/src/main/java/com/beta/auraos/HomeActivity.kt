package com.beta.auraos

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.Membership
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.data.remote.ParkingApi
import com.beta.auraos.databinding.ActivityHomeBinding
import com.beta.auraos.ui.branches.BranchListActivity
import com.beta.auraos.util.BottomNavIndicator
import com.beta.auraos.util.TabNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    private var selectedVehicleType = "motorcycle"
    private var isSending = false

    private lateinit var sessionManager: SessionManager
    private var branches: List<Branch> = emptyList()
    private var selectedBranch: Branch? = null
    private var selectedMembership: Membership? = null
    private var memberships: List<Membership> = emptyList()

    private val membershipRepository: MembershipRepository by lazy {
        MembershipRepository()
    }

    private val branchRepository: BranchRepository by lazy {
        BranchRepository(ApiClient.createApi<BranchApi>())
    }

    private var frontPhotoUri: Uri? = null
    private var rearPhotoUri: Uri? = null
    private var leftPhotoUri: Uri? = null
    private var rightPhotoUri: Uri? = null
    private var platePhotoUri: Uri? = null

    private var hasFrontPhoto = false
    private var hasRearPhoto = false
    private var hasLeftPhoto = false
    private var hasRightPhoto = false
    private var hasPlatePhoto = false

    private var currentPhotoSlot: String? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            val slot = currentPhotoSlot
            val uri = when (slot) {
                "front" -> frontPhotoUri
                "rear" -> rearPhotoUri
                "left" -> leftPhotoUri
                "right" -> rightPhotoUri
                "plate" -> platePhotoUri
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
            if (currentPhotoSlot != null) {
                openCamera()
            }
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

        sessionManager = SessionManager(this)

        initPhotoUris()
        setupPhotoClickHandlers()
        setupRetakeDeleteHandlers()
        setupVehicleButtons()
        setupConfirmButton()
        setupBranchSelector()
        setupMembershipSelector()
        setupBottomNav()
        setupWindowInsets()
        loadBranches()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun initPhotoUris() {
        val photoDir = File(filesDir, "photos").also { it.mkdirs() }
        photoDir.listFiles()?.forEach { it.delete() }
        frontPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(photoDir, "front.jpg"))
        rearPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(photoDir, "rear.jpg"))
        leftPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(photoDir, "left.jpg"))
        rightPhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(photoDir, "right.jpg"))
        platePhotoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", File(photoDir, "plate.jpg"))
    }

    private fun setupPhotoClickHandlers() {
        binding.containerFront.setOnClickListener { requestCamera("front") }
        binding.containerRear.setOnClickListener { requestCamera("rear") }
        binding.containerLeft.setOnClickListener { requestCamera("left") }
        binding.containerRight.setOnClickListener { requestCamera("right") }
        binding.containerPlate.setOnClickListener { requestCamera("plate") }
    }

    private fun setupRetakeDeleteHandlers() {
        binding.btnRetakeFront.setOnClickListener { requestCamera("front") }
        binding.btnRetakeRear.setOnClickListener { requestCamera("rear") }
        binding.btnRetakeLeft.setOnClickListener { requestCamera("left") }
        binding.btnRetakeRight.setOnClickListener { requestCamera("right") }
        binding.btnRetakePlate.setOnClickListener { requestCamera("plate") }

        binding.btnDeleteFront.setOnClickListener { deletePhoto("front") }
        binding.btnDeleteRear.setOnClickListener { deletePhoto("rear") }
        binding.btnDeleteLeft.setOnClickListener { deletePhoto("left") }
        binding.btnDeleteRight.setOnClickListener { deletePhoto("right") }
        binding.btnDeletePlate.setOnClickListener { deletePhoto("plate") }
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
            "plate" -> platePhotoUri
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
            "plate" -> binding.ivPlate
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
            "plate" -> binding.btnRetakePlate
            else -> return
        }
        val deleteBtn = when (slot) {
            "front" -> binding.btnDeleteFront
            "rear" -> binding.btnDeleteRear
            "left" -> binding.btnDeleteLeft
            "right" -> binding.btnDeleteRight
            "plate" -> binding.btnDeletePlate
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
            "plate" -> {
                hasPlatePhoto = hasPhoto
                if (hasPhoto) binding.tvPlateError.visibility = View.GONE
            }
        }
    }

    private fun deletePhoto(slot: String) {
        when (slot) {
            "front" -> binding.ivFront.also { it.setImageResource(R.drawable.front) }
            "rear" -> binding.ivRear.also { it.setImageResource(R.drawable.rear) }
            "left" -> binding.ivLeft.also { it.setImageResource(R.drawable.left) }
            "right" -> binding.ivRight.also { it.setImageResource(R.drawable.right) }
            "plate" -> binding.ivPlate.also { it.setImageResource(R.drawable.ic_scan_corners) }
            else -> return
        }
        setPhotoState(slot, false)
    }

    private fun deleteAllPhotoFiles() {
        val photoDir = File(filesDir, "photos")
        if (!photoDir.exists()) return
        photoDir.listFiles()?.forEach { it.delete() }
    }

    private fun sendPlateForOcr(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    binding.tvDbStatus.text = getString(R.string.plate_sending)
                }

                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
                val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", "plate.jpg", requestBody)

                val response = parkingApi.recognizePlate(imagePart)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body != null && body.success) {
                            binding.etPlateNumber.setText(body.plate)
                            binding.tvDbStatus.text = getString(R.string.plate_validation_status)
                        } else {
                            binding.tvDbStatus.text = body?.message ?: getString(R.string.plate_ocr_error)
                        }
                    } else {
                        binding.tvDbStatus.text = getString(R.string.plate_ocr_error)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvDbStatus.text = getString(R.string.plate_ocr_error)
                }
            }
        }
    }

    private fun setupVehicleButtons() {
        selectedVehicleType = "motorcycle"
        binding.btnMoto.isSelected = true

        val buttons = listOf(
            binding.btnMoto to "motorcycle",
            binding.btnAuto to "light",
            binding.btnCamioneta to "light",
            binding.btnCamion to "heavy",
        )

        buttons.forEach { (button, type) ->
            button.setOnClickListener {
                buttons.forEach { (b, _) -> b.isSelected = false }
                button.isSelected = true
                selectedVehicleType = type
            }
        }
    }

    private fun setupBranchSelector() {
        binding.btnBranchSelector.setOnClickListener { showBranchSelector() }
        binding.tvBranchName.setOnClickListener { showBranchSelector() }
    }

    private fun setupMembershipSelector() {
        binding.btnSelectMember.setOnClickListener { showMemberSelector() }
        binding.btnClearMember.setOnClickListener { clearSelectedMember() }
    }

    private fun loadMemberships() {
        val branchId = selectedBranch?.id ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val result = membershipRepository.getMemberships(branchId = branchId)
            withContext(Dispatchers.Main) {
                if (result is Result.Success) {
                    memberships = result.data.filter { it.isActive }
                }
            }
        }
    }

    private fun showMemberSelector() {
        val activeMembers = memberships.filter { it.isActive }
        if (activeMembers.isEmpty()) {
            Toast.makeText(this, R.string.membership_dialog_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val names = activeMembers.map { "${it.memberName}  •  ${it.tier}" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.membership_dialog_title)
            .setItems(names) { dialog, which ->
                selectMember(activeMembers[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun selectMember(membership: Membership) {
        selectedMembership = membership
        binding.btnSelectMember.visibility = View.GONE
        binding.layoutMemberInfo.visibility = View.VISIBLE
        binding.tvMemberName.text = membership.memberName
        binding.tvMemberTier.text = membership.tier
    }

    private fun clearSelectedMember() {
        selectedMembership = null
        binding.btnSelectMember.visibility = View.VISIBLE
        binding.layoutMemberInfo.visibility = View.GONE
        binding.tvMemberName.text = ""
        binding.tvMemberTier.text = ""
    }

    private fun showBranchSelector() {
        if (branches.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_no_branches_title))
                .setMessage(getString(R.string.dialog_no_branches_message))
                .setPositiveButton(getString(R.string.dialog_setup)) { _, _ ->
                    startActivity(android.content.Intent(this, BranchListActivity::class.java))
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
            return
        }

        val names = branches.map { it.name }.toTypedArray()
        val selectedIndex = if (selectedBranch != null) branches.indexOf(selectedBranch) else 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_select_branch_title))
            .setSingleChoiceItems(names, if (selectedIndex >= 0) selectedIndex else 0) { dialog, which ->
                val branch = branches[which]
                selectedBranch = branch
                sessionManager.saveSelectedBranchId(branch.id)
                binding.tvBranchName.text = branch.name
                clearSelectedMember()
                loadMemberships()
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.dialog_manage_branches)) { _, _ ->
                startActivity(android.content.Intent(this, BranchListActivity::class.java))
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun loadBranches() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = branchRepository.getBranches()
            withContext(Dispatchers.Main) {
                when (result) {
                    is Result.Success -> {
                        branches = result.data
                        restoreOrSelectBranch()
                    }
                    is Result.Error -> {
                        binding.tvBranchName.text = getString(R.string.branch_load_error)
                    }
                }
            }
        }
    }

    private fun restoreOrSelectBranch() {
        val savedId = sessionManager.getSelectedBranchId()
        val saved = if (savedId != null) branches.find { it.id == savedId } else null

        if (saved != null) {
            selectedBranch = saved
            binding.tvBranchName.text = saved.name
        } else if (branches.isNotEmpty()) {
            selectedBranch = branches.first()
            sessionManager.saveSelectedBranchId(selectedBranch!!.id)
            binding.tvBranchName.text = selectedBranch!!.name
        } else {
            binding.tvBranchName.text = getString(R.string.branch_no_branch)
        }
        loadMemberships()
    }

    private fun setupConfirmButton() {
        binding.etPlateNumber.setOnFocusChangeListener { _, _ ->
            binding.etPlateNumber.error = null
        }
        binding.btnConfirmEntry.setOnClickListener { validateAndSend() }
    }

    private fun validateAndSend() {
        var firstErrorView: View? = null

        if (selectedBranch == null) {
            Toast.makeText(this, getString(R.string.branch_required_for_entry), Toast.LENGTH_LONG).show()
            showBranchSelector()
            return
        }

        if (binding.etPlateNumber.text.isNullOrBlank()) {
            binding.etPlateNumber.error = getString(R.string.field_required_plain)
            if (firstErrorView == null) firstErrorView = binding.etPlateNumber
        }

        if (!hasPlatePhoto) {
            binding.tvPlateError.visibility = View.VISIBLE
            if (firstErrorView == null) firstErrorView = binding.cardPlateCapture
        } else {
            binding.tvPlateError.visibility = View.GONE
        }

        if (firstErrorView != null) {
            scrollToView(firstErrorView)
            return
        }
        sendToApi()
    }

    private fun scrollToView(view: View) {
        binding.homeScrollView.post {
            binding.homeScrollView.smoothScrollTo(0, 0)
        }
    }

    private fun sendToApi() {
        if (isSending) return
        isSending = true

        with(binding) {
            btnConfirmEntry.isEnabled = false
            btnConfirmEntry.text = getString(R.string.action_sending)
            loadingOverlay.visibility = View.VISIBLE
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val plate = binding.etPlateNumber.text.toString()
                    .toRequestBody("text/plain".toMediaTypeOrNull())
                val vehicleType = selectedVehicleType
                    .toRequestBody("text/plain".toMediaTypeOrNull())
                val branchId = (selectedBranch?.id ?: "")
                    .toRequestBody("text/plain".toMediaTypeOrNull())
                val membershipIdPart = selectedMembership?.id
                    ?.toRequestBody("text/plain".toMediaTypeOrNull())

                val platePhotoPart = platePhotoUri?.toMultipartPart("platePhoto")
                val frontPart = frontPhotoUri?.toMultipartPart("front")
                val rearPart = rearPhotoUri?.toMultipartPart("rear")
                val leftPart = leftPhotoUri?.toMultipartPart("left")
                val rightPart = rightPhotoUri?.toMultipartPart("right")

                val response = parkingApi.registerEntry(
                    plate = plate,
                    vehicleType = vehicleType,
                    branchId = branchId,
                    membershipId = membershipIdPart,
                    platePhoto = platePhotoPart,
                    front = frontPart,
                    rear = rearPart,
                    left = leftPart,
                    right = rightPart,
                )

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        resetForm()
                        if (selectedMembership != null) {
                            Toast.makeText(this@HomeActivity, getString(R.string.confirm_entry_membership_success, selectedMembership!!.memberName), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@HomeActivity, R.string.confirm_entry_success, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorMsg = parseEntryError(response)
                        Toast.makeText(this@HomeActivity, getString(R.string.confirm_entry_error, errorMsg), Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@HomeActivity, getString(R.string.confirm_entry_error, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    with(binding) {
                        btnConfirmEntry.isEnabled = true
                        btnConfirmEntry.text = getString(R.string.action_confirm_entry)
                        loadingOverlay.visibility = View.GONE
                    }
                    isSending = false
                }
            }
        }
    }

    private fun resetForm() {
        deleteAllPhotoFiles()
        binding.etPlateNumber.setText("")
        binding.etPlateNumber.error = null
        binding.tvDbStatus.text = getString(R.string.plate_validation_status)

        binding.btnMoto.isSelected = true
        binding.btnAuto.isSelected = false
        binding.btnCamioneta.isSelected = false
        binding.btnCamion.isSelected = false
        selectedVehicleType = "motorcycle"

        binding.ivPlate.setImageResource(R.drawable.ic_scan_corners)
        binding.ivFront.setImageResource(R.drawable.front)
        binding.ivRear.setImageResource(R.drawable.rear)
        binding.ivLeft.setImageResource(R.drawable.left)
        binding.ivRight.setImageResource(R.drawable.right)

        hasFrontPhoto = false
        hasRearPhoto = false
        hasLeftPhoto = false
        hasRightPhoto = false
        hasPlatePhoto = false

        binding.btnRetakePlate.visibility = View.GONE
        binding.btnDeletePlate.visibility = View.GONE
        binding.tvPlateError.visibility = View.GONE
        binding.btnRetakeFront.visibility = View.GONE
        binding.btnDeleteFront.visibility = View.GONE
        binding.btnRetakeRear.visibility = View.GONE
        binding.btnDeleteRear.visibility = View.GONE
        binding.btnRetakeLeft.visibility = View.GONE
        binding.btnDeleteLeft.visibility = View.GONE
        binding.btnRetakeRight.visibility = View.GONE
        binding.btnDeleteRight.visibility = View.GONE

        clearSelectedMember()
    }

    private fun parseEntryError(response: retrofit2.Response<*>): String {
        return try {
            val errorBody = response.errorBody()?.string()
            val json = com.google.gson.Gson().fromJson(errorBody, Map::class.java)
            json["message"]?.toString() ?: "Error del servidor (${response.code()})"
        } catch (_: Exception) {
            when (response.code()) {
                400 -> "Datos inválidos"
                404 -> "No encontrado"
                409 -> "Conflicto"
                else -> "Error del servidor (${response.code()})"
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

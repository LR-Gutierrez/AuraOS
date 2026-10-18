package com.beta.auraos

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.PendingPhotoStore
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.local.SyncCoordinator
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.Membership
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.data.remote.ParkingApi
import com.beta.auraos.databinding.ActivityHomeBinding
import com.beta.auraos.databinding.ItemPcPhotoSlotBinding
import com.beta.auraos.ui.branches.BranchListActivity
import com.beta.auraos.ui.exit.ExitActivity
import com.beta.auraos.util.ParkingFormat
import com.beta.auraos.util.PcUi
import com.beta.auraos.util.TabNavigator
import com.beta.auraos.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.UUID

/** Registro · Entrada. The Salida half of the tab lives in [ExitActivity]. */
class HomeActivity : BaseActivity() {

    private lateinit var binding: ActivityHomeBinding

    private var selectedVehicleType = "motorcycle"
    private var isSending = false

    private lateinit var sessionManager: SessionManager
    private var branches: List<Branch> = emptyList()
    private var selectedBranch: Branch? = null
    private var selectedMembership: Membership? = null
    private var memberships: List<Membership> = emptyList()

    private val membershipRepository: MembershipRepository by lazy {
        MembershipRepository(AppDatabase.getInstance(this@HomeActivity))
    }

    private val branchRepository: BranchRepository by lazy {
        BranchRepository(ApiClient.createApi<BranchApi>(), AppDatabase.getInstance(this@HomeActivity))
    }

    private val vehicleEntryRepository: VehicleEntryRepository by lazy {
        VehicleEntryRepository(AppDatabase.getInstance(this@HomeActivity))
    }

    private val parkingApi: ParkingApi by lazy {
        ApiClient.createApi<ParkingApi>()
    }

    /** One capture slot: where the file goes, its view and when it was taken. */
    private class PhotoSlot(
        val key: String,
        val labelRes: Int,
        val required: Boolean,
        val view: ItemPcPhotoSlotBinding,
    ) {
        var uri: Uri? = null
        var takenAt: Date? = null
    }

    private lateinit var slots: List<PhotoSlot>
    private var currentPhotoSlot: PhotoSlot? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val slot = currentPhotoSlot ?: return@registerForActivityResult
        if (success) {
            slot.takenAt = Date()
            renderSlots()
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            openCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        TabNavigator.setup(this, binding.tabBar, R.id.nav_register)
        ViewCompat.setOnApplyWindowInsetsListener(binding.homeScrollView) { view, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBar.top)
            insets
        }

        setupSegment()
        initPhotoSlots()
        setupPlateField()
        renderTypeChips()
        setupMembershipSelector()
        binding.btnConfirmEntry.setOnClickListener { validateAndSend() }
        binding.done.btnDoneAgain.text = getString(R.string.pc_register_another)
        binding.done.btnDoneAgain.setOnClickListener {
            binding.done.root.visibility = View.GONE
            binding.form.visibility = View.VISIBLE
        }
        binding.header.tvBranchMeta.setOnClickListener { showBranchSelector() }

        updateEntryTime()
        updateConfirmState()
        loadBranches()
    }

    override fun onResume() {
        super.onResume()
        updateEntryTime()
        selectedBranch?.let { refreshBranchMeta(it) }
    }

    private fun setupSegment() {
        binding.header.segment.check(R.id.seg_entry)
        binding.header.segExit.setOnClickListener {
            TabNavigator.open(this, Intent(this, ExitActivity::class.java), R.id.nav_register, R.id.nav_register)
        }
    }

    // region Photos

    private fun initPhotoSlots() {
        slots = listOf(
            PhotoSlot("plate", R.string.pc_slot_plate, true, binding.slotPlate),
            PhotoSlot("front", R.string.pc_slot_front, false, binding.slotFront),
            PhotoSlot("rear", R.string.pc_slot_rear, false, binding.slotRear),
            PhotoSlot("left", R.string.pc_slot_left, false, binding.slotLeft),
            PhotoSlot("right", R.string.pc_slot_right, false, binding.slotRight),
        )
        val photoDir = File(filesDir, "photos").also { it.mkdirs() }
        photoDir.listFiles()?.forEach { it.delete() }
        slots.forEach { slot ->
            slot.uri = FileProvider.getUriForFile(
                this, "${packageName}.fileprovider", File(photoDir, "${slot.key}.jpg")
            )
            slot.view.slot.setOnClickListener { requestCamera(slot) }
            slot.view.slot.setOnLongClickListener {
                if (slot.takenAt == null) return@setOnLongClickListener false
                slot.takenAt = null
                File(photoDir, "${slot.key}.jpg").delete()
                renderSlots()
                true
            }
        }
        renderSlots()
    }

    private fun renderSlots() {
        val plateText = binding.etPlateNumber.text?.toString()?.trim().orEmpty()
        slots.forEach { slot ->
            val v = slot.view
            val captured = slot.takenAt != null
            val label = getString(slot.labelRes)
            v.slot.setBackgroundResource(if (captured) R.drawable.bg_lrg_card else R.drawable.bg_pc_dashed)
            v.ivCamera.visibility = if (captured) View.GONE else View.VISIBLE
            v.ivCheck.visibility = if (captured) View.VISIBLE else View.GONE
            v.tvLabel.text = label
            val isPlateShot = captured && slot.key == "plate"
            v.tvPlateTag.visibility = if (isPlateShot) View.VISIBLE else View.GONE
            v.tvPlateTag.text = plateText.ifEmpty { getString(R.string.pc_no_read) }
            if (captured) {
                v.tvSub.isAllCaps = false
                v.tvSub.setTextColor(PcUi.color(v.root, R.attr.textMutedCustom))
                v.tvSub.text = getString(R.string.pc_tap_to_retake, ParkingFormat.hhmm(slot.takenAt!!))
            } else {
                v.tvSub.isAllCaps = true
                v.tvSub.setTextColor(
                    PcUi.color(v.root, if (slot.required) R.attr.accentTextCustom else R.attr.textMutedCustom)
                )
                v.tvSub.setText(if (slot.required) R.string.pc_required else R.string.pc_optional)
            }
            v.slot.contentDescription = "$label. ${v.tvSub.text}"
        }
        binding.tvPhotoCount.text = getString(R.string.pc_photo_count, photoCount())
        updateConfirmState()
    }

    private fun photoCount(): Int = slots.count { it.takenAt != null }

    private fun hasPlatePhoto(): Boolean = slots.first { it.key == "plate" }.takenAt != null

    private fun requestCamera(slot: PhotoSlot) {
        currentPhotoSlot = slot
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        currentPhotoSlot?.uri?.let { takePictureLauncher.launch(it) }
    }

    /** Parks the captured photos of an unconfirmed entry before the form clears them. */
    private fun keepPhotosFor(entryId: String) {
        val dir = File(filesDir, "photos")
        PendingPhotoStore.keep(
            this, entryId,
            slots.filter { it.takenAt != null }.associate { it.key to File(dir, "${it.key}.jpg") },
        )
    }

    private fun deleteAllPhotoFiles() {
        File(filesDir, "photos").listFiles()?.forEach { it.delete() }
    }

    // endregion

    // region Form

    private fun setupPlateField() {
        val field = binding.etPlateNumber
        field.filters = arrayOf(InputFilter.AllCaps(), InputFilter.LengthFilter(15))
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                field.error = null
                renderSlots()
            }
        })
    }

    private fun renderTypeChips() {
        val row = binding.llTypeChips
        row.removeAllViews()
        val types = listOf(
            R.string.vehicle_type_auto to "light",
            R.string.vehicle_type_camion to "heavy",
            R.string.vehicle_type_moto to "motorcycle",
        )
        types.forEach { (label, type) ->
            PcUi.addChip(row, getString(label), checked = selectedVehicleType == type, fill = true) {
                selectedVehicleType = type
                renderTypeChips()
                updateRate()
            }
        }
    }

    private fun updateEntryTime() {
        binding.tvEntryTime.text = getString(R.string.pc_entry_time, ParkingFormat.hhmm())
    }

    private fun updateRate() {
        val branch = selectedBranch
        if (branch == null) {
            binding.tvRate.text = ""
            return
        }
        val rate = when (selectedVehicleType) {
            "motorcycle" -> branch.motorcycleRate
            "heavy" -> branch.heavyVehicleRate
            else -> branch.lightVehicleRate
        }
        binding.tvRate.text = getString(R.string.pc_rate_per_day, ParkingFormat.money(rate, branch.currency))
    }

    private fun canSubmit(): Boolean {
        val plate = binding.etPlateNumber.text?.toString()?.trim().orEmpty()
        return !isSending && plate.length >= 3 && hasPlatePhoto()
    }

    private fun updateConfirmState() {
        val enabled = canSubmit()
        binding.btnConfirmEntry.isEnabled = enabled
        binding.btnConfirmEntry.alpha = if (enabled) 1f else 0.4f
    }

    private fun validateAndSend() {
        if (selectedBranch == null) {
            Toast.makeText(this, getString(R.string.branch_required_for_entry), Toast.LENGTH_LONG).show()
            showBranchSelector()
            return
        }
        val plate = binding.etPlateNumber.text?.toString()?.trim().orEmpty()
        when {
            plate.length < 3 -> binding.etPlateNumber.error = getString(R.string.plate_too_short)
            plate.length > 15 -> binding.etPlateNumber.error = getString(R.string.plate_too_long)
            !hasPlatePhoto() -> Toast.makeText(this, R.string.pc_required, Toast.LENGTH_SHORT).show()
            else -> sendToApi()
        }
    }

    private fun showDone(titleRes: Int, plate: String, vehicleType: String, photos: Int, member: Membership?) {
        binding.done.tvDoneTitle.setText(titleRes)
        binding.done.tvDonePlate.text = plate
        val kind = member?.memberName ?: ParkingFormat.vehicleType(this, vehicleType)
        binding.done.tvDoneMeta.text =
            resources.getQuantityString(R.plurals.pc_entry_done_meta, photos, ParkingFormat.hhmm(), kind, photos)
        binding.form.visibility = View.GONE
        binding.done.root.visibility = View.VISIBLE
        binding.homeScrollView.smoothScrollTo(0, 0)
        selectedBranch?.let { refreshBranchMeta(it) }
    }

    private fun resetForm() {
        deleteAllPhotoFiles()
        binding.etPlateNumber.setText("")
        binding.etPlateNumber.error = null
        slots.forEach { it.takenAt = null }
        selectedVehicleType = "motorcycle"
        renderTypeChips()
        updateRate()
        clearSelectedMember()
        renderSlots()
    }

    // endregion

    // region Membership

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
        val names = activeMembers.map { "${it.memberName} · ${it.tier}" }.toTypedArray()
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
        binding.tvMemberTier.text = getString(R.string.pc_member_label, membership.tier)
        binding.tvMemberUntil.text = getString(R.string.pc_member_until, formatDate(membership.endDate.take(10)))
    }

    private fun clearSelectedMember() {
        selectedMembership = null
        binding.btnSelectMember.visibility = View.VISIBLE
        binding.layoutMemberInfo.visibility = View.GONE
    }

    // endregion

    // region Branch

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
                        binding.header.tvBranchMeta.text = getString(R.string.branch_load_error)
                    }
                }
            }
        }
    }

    private fun restoreOrSelectBranch() {
        val savedId = sessionManager.getSelectedBranchId()
        val branch = branches.find { it.id == savedId } ?: branches.firstOrNull()
        if (branch == null) {
            binding.header.tvBranchMeta.text = getString(R.string.branch_no_branch)
            return
        }
        applyBranch(branch)
    }

    private fun applyBranch(branch: Branch) {
        selectedBranch = branch
        sessionManager.saveSelectedBranchId(branch.id)
        refreshBranchMeta(branch)
        updateRate()
        clearSelectedMember()
        loadMemberships()
    }

    private fun refreshBranchMeta(branch: Branch) {
        lifecycleScope.launch {
            val occupied = withContext(Dispatchers.IO) {
                vehicleEntryRepository.getActiveEntriesLocal(branch.id).size
            }
            val capacity = branch.lightVehicleCapacity + branch.heavyVehicleCapacity + branch.motorcycleCapacity
            binding.header.tvBranchMeta.text =
                getString(R.string.pc_branch_meta, branch.name, ParkingFormat.pad2(occupied), capacity)
        }
    }

    private fun showBranchSelector() {
        if (branches.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_no_branches_title))
                .setMessage(getString(R.string.dialog_no_branches_message))
                .setPositiveButton(getString(R.string.dialog_setup)) { _, _ ->
                    startActivity(Intent(this, BranchListActivity::class.java))
                }
                .setNegativeButton(getString(R.string.dialog_cancel), null)
                .show()
            return
        }
        val names = branches.map { it.name }.toTypedArray()
        val selectedIndex = branches.indexOf(selectedBranch).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_select_branch_title))
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                applyBranch(branches[which])
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    // endregion

    private fun sendToApi() {
        if (isSending) return
        isSending = true

        val plate = binding.etPlateNumber.text?.toString()?.trim()?.uppercase() ?: ""
        val vehicleType = selectedVehicleType
        val branchId = selectedBranch?.id ?: ""

        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.format(java.util.Date())

        val localId = UUID.randomUUID().toString()

        val localEntry = com.beta.auraos.data.model.VehicleEntry(
            id = localId, plate = plate, vehicleType = vehicleType,
            isVip = selectedMembership != null,
            membershipId = selectedMembership?.id, branchId = branchId,
            platePhotoUrl = null, frontPhotoUrl = null,
            rearPhotoUrl = null, leftPhotoUrl = null, rightPhotoUrl = null,
            createdAt = now, exitedAt = null, duration = null, fee = null, branch = null
        )

        val photoCount = photoCount()
        val member = selectedMembership
        binding.btnConfirmEntry.isEnabled = false
        binding.btnConfirmEntry.text = getString(R.string.pc_sending)

        lifecycleScope.launch(Dispatchers.IO) {
            vehicleEntryRepository.updateCachedEntry(localEntry)

            SyncCoordinator.isApiInProgress = true
            try {
                val plateBody = plate.toRequestBody("text/plain".toMediaTypeOrNull())
                val typeBody = vehicleType.toRequestBody("text/plain".toMediaTypeOrNull())
                val branchBody = branchId.toRequestBody("text/plain".toMediaTypeOrNull())
                val membershipIdPart = selectedMembership?.id
                    ?.toRequestBody("text/plain".toMediaTypeOrNull())

                fun part(key: String, field: String) =
                    slots.first { it.key == key }.takeIf { it.takenAt != null }?.uri?.toMultipartPart(field)

                val platePhotoPart = part("plate", "platePhoto")
                val frontPart = part("front", "front")
                val rearPart = part("rear", "rear")
                val leftPart = part("left", "left")
                val rightPart = part("right", "right")

                val response = parkingApi.registerEntry(
                    plate = plateBody, vehicleType = typeBody,
                    branchId = branchBody, membershipId = membershipIdPart,
                    platePhoto = platePhotoPart, front = frontPart,
                    rear = rearPart, left = leftPart, right = rightPart,
                )

                if (response.isSuccessful) {
                    val serverEntry = response.body()?.data
                    if (serverEntry != null) {
                        vehicleEntryRepository.replaceLocalWithServerEntry(localId, serverEntry)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        showDone(R.string.pc_entry_done, plate, vehicleType, photoCount, member)
                        resetForm()
                    } else {
                        val errorMsg = parseEntryError(response)
                        Toast.makeText(this@HomeActivity, getString(R.string.confirm_entry_error, errorMsg), Toast.LENGTH_LONG).show()
                        keepPhotosFor(localId)
                        resetForm()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (e is IOException) {
                        showDone(R.string.pc_entry_queued, plate, vehicleType, photoCount, member)
                    } else {
                        Toast.makeText(this@HomeActivity, getString(R.string.confirm_entry_error, e.message ?: "Unknown error"), Toast.LENGTH_LONG).show()
                    }
                    // The entry stays saved locally and syncs later; its photos must not be lost.
                    keepPhotosFor(localId)
                    resetForm()
                }
            } finally {
                SyncCoordinator.isApiInProgress = false
                syncCoordinator.onMutation()
                withContext(Dispatchers.Main) {
                    binding.btnConfirmEntry.text = getString(R.string.pc_register_entry)
                    isSending = false
                    updateConfirmState()
                }
            }
        }
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
}

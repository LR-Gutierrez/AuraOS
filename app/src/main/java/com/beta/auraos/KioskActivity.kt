package com.beta.auraos

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.VehicleEntryRepository
import com.beta.auraos.data.local.NfcSyncManager
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.local.SyncCoordinator
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.Membership
import com.beta.auraos.data.model.VehicleEntry
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.data.remote.NfcEntryRequest
import com.beta.auraos.data.remote.ParkingApi
import com.beta.auraos.databinding.ActivityKioskBinding
import com.beta.auraos.util.ParkingFormat
import com.beta.auraos.util.formatDate
import com.beta.auraos.ui.branches.BranchListActivity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class KioskActivity : BaseActivity() {

    private lateinit var binding: ActivityKioskBinding

    private lateinit var sessionManager: SessionManager
    private var branches: List<Branch> = emptyList()
    private var selectedBranch: Branch? = null
    private var memberships: List<Membership> = emptyList()

    private var nfcAdapter: NfcAdapter? = null
    private var isNfcScanning = false
    private var isProcessingNfc = false
    private var resultJob: Job? = null

    private val branchRepository: BranchRepository by lazy {
        BranchRepository(ApiClient.createApi<BranchApi>(), AppDatabase.getInstance(this@KioskActivity))
    }

    private val membershipRepository: MembershipRepository by lazy {
        MembershipRepository(AppDatabase.getInstance(this@KioskActivity))
    }

    private val parkingApi: ParkingApi by lazy {
        ApiClient.createApi<ParkingApi>()
    }

    private val nfcSyncManager: NfcSyncManager by lazy {
        NfcSyncManager(AppDatabase.getInstance(this@KioskActivity))
    }

    private val vehicleEntryRepository: VehicleEntryRepository by lazy {
        VehicleEntryRepository(AppDatabase.getInstance(this@KioskActivity))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityKioskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupBranchSelector()
        setupClickHandlers()
        setupWindowInsets()
        loadBranches()

        // Back from the NFC reader or its result returns to the welcome screen, not out of the kiosk.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.sectionHome.visibility != View.VISIBLE) {
                    stopNfcScanning()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (isNfcScanning) {
            enableNfcForegroundDispatch()
        }
        syncPendingEntries()
    }

    override fun onPause() {
        disableNfcForegroundDispatch()
        super.onPause()
    }

    private fun syncPendingEntries() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = nfcSyncManager.syncPending()
            if (result is NfcSyncManager.SyncResult.Success && result.count > 0) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@KioskActivity,
                        "${result.count} entrada(s) NFC sincronizada(s)",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun setupBranchSelector() {
        binding.tvBranchName.setOnClickListener { showBranchSelector() }
    }

    /** Header, free spots and daily rates for the selected branch. */
    private fun renderBranch() {
        val branch = selectedBranch
        if (branch == null) {
            binding.tvBranchName.text = getString(R.string.branch_no_branch)
            return
        }
        binding.tvBranchName.text = getString(R.string.pc_kiosk_header, branch.name)
        binding.tvRates.text = getString(
            R.string.pc_kiosk_rates,
            getString(R.string.vehicle_type_auto), ParkingFormat.money(branch.lightVehicleRate, branch.currency),
            getString(R.string.vehicle_type_camion), ParkingFormat.money(branch.heavyVehicleRate, branch.currency),
            getString(R.string.vehicle_type_moto), ParkingFormat.money(branch.motorcycleRate, branch.currency),
        )
        lifecycleScope.launch {
            val occupied = withContext(Dispatchers.IO) {
                vehicleEntryRepository.getActiveEntriesLocal(branch.id).size
            }
            val capacity = branch.lightVehicleCapacity + branch.heavyVehicleCapacity + branch.motorcycleCapacity
            binding.tvKioskSubtitle.text =
                (capacity - occupied).coerceAtLeast(0).let { resources.getQuantityString(R.plurals.pc_kiosk_free, it, it) }
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
        val selectedIndex = if (selectedBranch != null) branches.indexOf(selectedBranch) else 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_select_branch_title))
            .setSingleChoiceItems(names, if (selectedIndex >= 0) selectedIndex else 0) { dialog, which ->
                val branch = branches[which]
                selectedBranch = branch
                sessionManager.saveSelectedBranchId(branch.id)
                renderBranch()
                dialog.dismiss()
                loadMemberships()
            }
            .setPositiveButton(getString(R.string.dialog_manage_branches)) { _, _ ->
                startActivity(Intent(this, BranchListActivity::class.java))
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
        } else if (branches.isNotEmpty()) {
            selectedBranch = branches.first()
            sessionManager.saveSelectedBranchId(selectedBranch!!.id)
        }
        renderBranch()
        loadMemberships()
    }

    private fun setupClickHandlers() {
        binding.cardTicket.setOnClickListener {
            if (selectedBranch == null) {
                Toast.makeText(this, R.string.branch_required_for_entry, Toast.LENGTH_SHORT).show()
                showBranchSelector()
                return@setOnClickListener
            }
            startActivity(Intent(this, KioskEntryActivity::class.java).apply {
                putExtra("mode", "ticket")
                putExtra("branchId", selectedBranch!!.id)
                putExtra("branchName", selectedBranch!!.name)
            })
        }

        binding.cardMembership.setOnClickListener {
            if (selectedBranch == null) {
                Toast.makeText(this, R.string.branch_required_for_entry, Toast.LENGTH_SHORT).show()
                showBranchSelector()
                return@setOnClickListener
            }
            startNfcScanning()
        }

        binding.btnNfcCancel.setOnClickListener { stopNfcScanning() }
        binding.btnResultHome.setOnClickListener { stopNfcScanning() }
        binding.btnExitKiosk.setOnClickListener { finish() }
    }

    private fun startNfcScanning() {
        if (nfcAdapter == null) {
            Toast.makeText(this, R.string.nfc_not_available, Toast.LENGTH_SHORT).show()
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, R.string.nfc_not_available, Toast.LENGTH_SHORT).show()
            return
        }

        isNfcScanning = true
        resultJob?.cancel()
        binding.sectionHome.visibility = View.GONE
        binding.cardNfcScanning.visibility = View.VISIBLE
        binding.cardNfcProcessing.visibility = View.GONE
        showReaderStatus(getString(R.string.pc_kiosk_waiting), null, reading = false)

        enableNfcForegroundDispatch()
    }

    private fun stopNfcScanning() {
        isNfcScanning = false
        isProcessingNfc = false
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            disableNfcForegroundDispatch()
        }
        resultJob?.cancel()
        binding.cardNfcScanning.visibility = View.GONE
        binding.cardNfcProcessing.visibility = View.GONE
        binding.sectionHome.visibility = View.VISIBLE
        renderBranch()
    }

    private fun showProcessingState(@Suppress("UNUSED_PARAMETER") memberName: String?) {
        showReaderStatus(getString(R.string.pc_kiosk_reading), null, reading = true)
    }

    /** Reader panel copy; the border turns amber while a card is being read. */
    private fun showReaderStatus(title: String, member: String?, reading: Boolean) {
        binding.nfcReaderBox.setBackgroundResource(
            if (reading) R.drawable.bg_pc_reader_active else R.drawable.bg_pc_reader_idle
        )
        binding.tvNfcStatusTitle.text = title
        binding.tvNfcMemberInfo.text = member
        binding.tvNfcMemberInfo.visibility = if (member == null) View.GONE else View.VISIBLE
    }

    /** Full-screen confirmation that returns to the welcome screen on its own. */
    private fun showResult(title: String, body: String, meta: String) {
        binding.cardNfcScanning.visibility = View.GONE
        binding.cardNfcProcessing.visibility = View.VISIBLE
        binding.tvResultTitle.text = title
        binding.tvNfcProcessing.text = body
        binding.tvResultMeta.text = meta
        resultJob?.cancel()
        resultJob = lifecycleScope.launch(Dispatchers.Main) {
            delay(RESULT_TIMEOUT_MS)
            stopNfcScanning()
        }
    }

    private fun enableNfcForegroundDispatch() {
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val techLists = arrayOf(
            arrayOf("android.nfc.tech.NfcA"),
            arrayOf("android.nfc.tech.NfcB"),
            arrayOf("android.nfc.tech.NfcF"),
            arrayOf("android.nfc.tech.NfcV"),
            arrayOf("android.nfc.tech.IsoDep"),
            arrayOf("android.nfc.tech.MifareClassic"),
            arrayOf("android.nfc.tech.MifareUltralight"),
            arrayOf("android.nfc.tech.Ndef")
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, techLists)
    }

    private fun disableNfcForegroundDispatch() {
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!isNfcScanning) return
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action
        ) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val cardUuid = bytesToHex(tag.id)
                onNfcTagDetected(cardUuid)
            }
        }
    }

    private fun onNfcTagDetected(cardUuid: String) {
        if (isProcessingNfc) return
        isProcessingNfc = true

        val branchId = selectedBranch?.id
        if (branchId == null) {
            isProcessingNfc = false
            Toast.makeText(this, R.string.branch_required_for_entry, Toast.LENGTH_SHORT).show()
            stopNfcScanning()
            return
        }

        showProcessingState(null)

        lifecycleScope.launch(Dispatchers.IO) {
            val result = membershipRepository.getMembershipByCardUuid(cardUuid)

            withContext(Dispatchers.Main) {
                when (result) {
                    is Result.Success -> {
                        val membership = result.data
                        if (!membership.isActive) {
                            isProcessingNfc = false
                            showReaderStatus(
                                getString(R.string.kiosk_nfc_error_inactive), membership.memberName, reading = false
                            )
                            return@withContext
                        }
                        registerMembershipEntry(membership, cardUuid)
                    }
                    is Result.Error -> {
                        isProcessingNfc = false
                        showReaderStatus(
                            getString(R.string.kiosk_nfc_error_card_not_found), null, reading = false
                        )
                    }
                }
            }
        }
    }

    private fun registerMembershipEntry(membership: Membership, cardUuid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val activeEntry = vehicleEntryRepository.getActiveEntryByMembership(membership.id)
            if (activeEntry != null) {
                withContext(Dispatchers.Main) {
                    showEntryError(getString(R.string.kiosk_nfc_error_active_entry), membership)
                }
                return@launch
            }

            val localEntryId = UUID.randomUUID().toString()
            val now = nowUtcString()
            val branchId = selectedBranch?.id ?: ""

            val localEntry = VehicleEntry(
                id = localEntryId,
                plate = "NFC",
                vehicleType = DEFAULT_NFC_VEHICLE_TYPE,
                isVip = true,
                membershipId = membership.id,
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

            vehicleEntryRepository.updateCachedEntry(localEntry)

            SyncCoordinator.isApiInProgress = true
            try {
                val response = parkingApi.registerNfcEntry(NfcEntryRequest(cardUuid = cardUuid))
                if (response.isSuccessful) {
                    val serverEntry = response.body()?.data
                    if (serverEntry != null) {
                        vehicleEntryRepository.replaceLocalWithServerEntry(localEntryId, serverEntry)
                    }
                } else {
                    nfcSyncManager.enqueue(cardUuid, branchId, membership.memberName, membership.id, localEntryId)
                }
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        showEntrySuccess(membership)
                    } else {
                        showEntryQueued(membership)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("KioskActivity", "registerNfcEntry exception: ${e.message}", e)
                nfcSyncManager.enqueue(cardUuid, branchId, membership.memberName, membership.id, localEntryId)
                withContext(Dispatchers.Main) {
                    showEntryQueued(membership)
                }
            } finally {
                SyncCoordinator.isApiInProgress = false
                syncCoordinator.onMutation()
            }
        }
    }

    private fun showEntryError(errorMsg: String, membership: Membership) {
        isProcessingNfc = false
        showReaderStatus(errorMsg, membership.memberName, reading = false)
        lifecycleScope.launch(Dispatchers.Main) {
            delay(3000)
            if (isNfcScanning && !isProcessingNfc) {
                showReaderStatus(getString(R.string.pc_kiosk_waiting), null, reading = false)
            }
        }
    }

    private fun showEntryQueued(membership: Membership) {
        isProcessingNfc = false
        showResult(
            getString(R.string.kiosk_nfc_queued),
            getString(R.string.kiosk_nfc_queued_subtitle),
            getString(R.string.pc_meta, membership.memberName, ParkingFormat.hhmm()),
        )
    }

    private fun showEntrySuccess(membership: Membership) {
        isProcessingNfc = false
        val first = membership.memberName.substringBefore(" ")
        showResult(
            getString(R.string.pc_kiosk_welcome_member, first),
            getString(R.string.pc_kiosk_member_in),
            getString(
                R.string.pc_kiosk_member_meta,
                membership.tier, ParkingFormat.hhmm(), formatDate(membership.endDate.take(10)),
            ),
        )
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

    private fun parseEntryError(code: Int, errorBody: String): String {
        return try {
            val json = Gson().fromJson(errorBody, Map::class.java)
            json["message"]?.toString() ?: "Error del servidor ($code)"
        } catch (_: Exception) {
            when (code) {
                400 -> "Datos inválidos"
                404 -> "No encontrado"
                409 -> "Conflicto"
                else -> "Error del servidor ($code)"
            }
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }

    private fun nowUtcString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    private fun setupWindowInsets() {
        val basePadding = binding.kioskRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.kioskRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = basePadding + bars.bottom)
            insets
        }
    }

    companion object {
        private const val DEFAULT_NFC_VEHICLE_TYPE = "light"
        private const val RESULT_TIMEOUT_MS = 6000L
    }
}

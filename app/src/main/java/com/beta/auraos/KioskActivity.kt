package com.beta.auraos

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.beta.auraos.data.BranchRepository
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.Result
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.Membership
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.BranchApi
import com.beta.auraos.data.remote.NfcEntryRequest
import com.beta.auraos.data.remote.ParkingApi
import com.beta.auraos.databinding.ActivityKioskBinding
import com.beta.auraos.ui.branches.BranchListActivity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class KioskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskBinding

    private lateinit var sessionManager: SessionManager
    private var branches: List<Branch> = emptyList()
    private var selectedBranch: Branch? = null
    private var memberships: List<Membership> = emptyList()

    private var nfcAdapter: NfcAdapter? = null
    private var isNfcScanning = false

    private val branchRepository: BranchRepository by lazy {
        BranchRepository(ApiClient.createApi<BranchApi>())
    }

    private val membershipRepository: MembershipRepository by lazy {
        MembershipRepository()
    }

    private val parkingApi: ParkingApi by lazy {
        ApiClient.createApi<ParkingApi>()
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
    }

    private fun setupBranchSelector() {
        binding.btnBranchSelector.setOnClickListener { showBranchSelector() }
        binding.tvBranchName.setOnClickListener { showBranchSelector() }
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
                binding.tvBranchName.text = branch.name
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
        binding.cardTicket.visibility = View.GONE
        binding.cardMembership.visibility = View.GONE
        binding.tvKioskSubtitle.visibility = View.GONE
        binding.cardNfcScanning.visibility = View.VISIBLE
        binding.cardNfcProcessing.visibility = View.GONE
        binding.tvNfcStatusTitle.text = getString(R.string.kiosk_nfc_scan_title)
        binding.tvNfcStatusSubtitle.visibility = View.VISIBLE
        binding.tvNfcMemberInfo.visibility = View.GONE

        enableNfcForegroundDispatch()
    }

    private fun stopNfcScanning() {
        isNfcScanning = false
        disableNfcForegroundDispatch()
        binding.cardNfcScanning.visibility = View.GONE
        binding.cardNfcProcessing.visibility = View.GONE
        binding.tvKioskSubtitle.visibility = View.VISIBLE
        binding.cardTicket.visibility = View.VISIBLE
        binding.cardMembership.visibility = View.VISIBLE
    }

    private fun showProcessingState(memberName: String?) {
        binding.cardNfcScanning.visibility = View.GONE
        binding.cardNfcProcessing.visibility = View.VISIBLE
        if (memberName != null) {
            binding.tvNfcProcessing.text = getString(R.string.kiosk_nfc_processing)
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
        val branchId = selectedBranch?.id
        if (branchId == null) {
            Toast.makeText(this, R.string.branch_required_for_entry, Toast.LENGTH_SHORT).show()
            stopNfcScanning()
            return
        }

        disableNfcForegroundDispatch()
        showProcessingState(null)

        lifecycleScope.launch(Dispatchers.IO) {
            val result = membershipRepository.getMembershipByCardUuid(cardUuid)

            withContext(Dispatchers.Main) {
                when (result) {
                    is Result.Success -> {
                        val membership = result.data
                        if (!membership.isActive) {
                            binding.cardNfcProcessing.visibility = View.GONE
                            binding.cardNfcScanning.visibility = View.VISIBLE
                            binding.tvNfcStatusTitle.text = getString(R.string.kiosk_nfc_error_inactive)
                            binding.tvNfcStatusSubtitle.visibility = View.GONE
                            binding.tvNfcMemberInfo.text = membership.memberName
                            binding.tvNfcMemberInfo.visibility = View.VISIBLE
                            enableNfcForegroundDispatch()
                            return@withContext
                        }
                        registerMembershipEntry(membership, cardUuid)
                    }
                    is Result.Error -> {
                        binding.cardNfcProcessing.visibility = View.GONE
                        binding.cardNfcScanning.visibility = View.VISIBLE
                        binding.tvNfcStatusTitle.text = getString(R.string.kiosk_nfc_error_card_not_found)
                        binding.tvNfcStatusSubtitle.visibility = View.GONE
                        binding.tvNfcMemberInfo.visibility = View.GONE
                        enableNfcForegroundDispatch()
                    }
                }
            }
        }
    }

    private fun registerMembershipEntry(membership: Membership, cardUuid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = parkingApi.registerNfcEntry(NfcEntryRequest(cardUuid = cardUuid))

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        showEntrySuccess(membership)
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "unknown"
                        val errorMsg = parseEntryError(response.code(), errorBody)
                        android.util.Log.e("KioskActivity", "registerNfcEntry failed: $errorMsg body=$errorBody")
                        binding.cardNfcProcessing.visibility = View.GONE
                        binding.cardNfcScanning.visibility = View.VISIBLE
                        binding.tvNfcStatusTitle.text = errorMsg
                        binding.tvNfcStatusSubtitle.visibility = View.GONE
                        binding.tvNfcMemberInfo.text = membership.memberName
                        binding.tvNfcMemberInfo.visibility = View.VISIBLE
                        enableNfcForegroundDispatch()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    android.util.Log.e("KioskActivity", "registerNfcEntry exception: ${e.message}", e)
                    binding.cardNfcProcessing.visibility = View.GONE
                    binding.cardNfcScanning.visibility = View.VISIBLE
                    binding.tvNfcStatusTitle.text = getString(R.string.kiosk_nfc_error_api)
                    binding.tvNfcStatusSubtitle.text = e.message
                    binding.tvNfcStatusSubtitle.visibility = View.VISIBLE
                    binding.tvNfcMemberInfo.text = membership.memberName
                    binding.tvNfcMemberInfo.visibility = View.VISIBLE
                    enableNfcForegroundDispatch()
                }
            }
        }
    }

    private fun showEntrySuccess(membership: Membership) {
        binding.cardNfcProcessing.visibility = View.GONE
        binding.cardNfcScanning.visibility = View.VISIBLE
        binding.tvNfcStatusTitle.text = getString(R.string.kiosk_nfc_success)
        binding.tvNfcStatusSubtitle.visibility = View.GONE
        binding.tvNfcMemberInfo.text = getString(R.string.kiosk_nfc_entry_success_format, membership.memberName)
        binding.tvNfcMemberInfo.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.Main) {
            delay(2000)
            stopNfcScanning()
        }
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

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.headerBar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }
    }
}

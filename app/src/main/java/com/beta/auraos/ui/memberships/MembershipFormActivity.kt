package com.beta.auraos.ui.memberships

import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.R
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.CreateMembershipInput
import com.beta.auraos.databinding.ActivityMembershipFormBinding

class MembershipFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMembershipFormBinding
    private lateinit var viewModel: MembershipViewModel
    private lateinit var sessionManager: SessionManager

    private var nfcAdapter: NfcAdapter? = null
    private var isNfcScanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        val db = com.beta.auraos.data.local.room.AppDatabase.getInstance(this)
        val repository = MembershipRepository(db)
        val factory = MembershipViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MembershipViewModel::class.java]

        binding = ActivityMembershipFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupWindowInsets()

        binding.btnBack.setOnClickListener { finish() }

        binding.btnScanNfc.setOnClickListener { startNfcScan() }

        binding.etEndDate.setOnClickListener { showDatePicker() }
        binding.etEndDate.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDatePicker() }

        viewModel.formState.observe(this) { state ->
            when (state) {
                is MembershipFormUiState.Saving -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is MembershipFormUiState.Saved -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, R.string.membership_create_success, Toast.LENGTH_SHORT).show()
                    finish()
                }
                is MembershipFormUiState.Error -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
                is MembershipFormUiState.Idle -> {
                    binding.loadingOverlay.visibility = View.GONE
                }
            }
        }

        binding.btnSave.setOnClickListener {
            saveMembership()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isNfcScanning) {
            disableNfcForegroundDispatch()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isNfcScanning) {
            enableNfcForegroundDispatch()
        }
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
                binding.etCardUuid.setText(cardUuid)
                stopNfcScan()
                Toast.makeText(this, R.string.membership_card_scanned, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startNfcScan() {
        if (nfcAdapter == null) {
            Toast.makeText(this, R.string.nfc_not_available, Toast.LENGTH_SHORT).show()
            return
        }
        if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, R.string.nfc_not_available, Toast.LENGTH_SHORT).show()
            return
        }
        isNfcScanning = true
        binding.btnScanNfc.text = getString(R.string.membership_scanning)
        binding.btnScanNfc.isEnabled = false
        enableNfcForegroundDispatch()
    }

    private fun stopNfcScan() {
        isNfcScanning = false
        disableNfcForegroundDispatch()
        binding.btnScanNfc.text = getString(R.string.membership_scan_nfc)
        binding.btnScanNfc.isEnabled = true
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

    private fun showDatePicker() {
        val now = LocalDate.now()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selected = LocalDate.of(year, month + 1, dayOfMonth)
                val display = selected.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                val iso = selected.atStartOfDay(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME)
                binding.etEndDate.setText(display)
                binding.etEndDate.tag = iso
            },
            now.year,
            now.monthValue - 1,
            now.dayOfMonth
        ).show()
    }

    private fun saveMembership() {
        val name = binding.etMemberName.text.toString().trim()
        val tier = binding.etTier.text.toString().trim()
        val endDate = (binding.etEndDate.tag as? String) ?: ""
        val cardUuid = binding.etCardUuid.text.toString().trim()
        val branchId = sessionManager.getSelectedBranchId()

        if (name.isEmpty()) {
            binding.etMemberName.error = getString(R.string.field_required)
            binding.etMemberName.requestFocus()
            return
        }

        if (branchId == null) {
            Toast.makeText(this, getString(R.string.membership_select_branch_first), Toast.LENGTH_LONG).show()
            return
        }

        val todayFull = LocalDate.now().atStartOfDay(java.time.ZoneOffset.UTC).toString()
        val futureDate = if (endDate.isNotEmpty()) {
            endDate
        } else {
            LocalDate.now().plusMonths(1).atStartOfDay(java.time.ZoneOffset.UTC).toString()
        }

        val input = CreateMembershipInput(
            memberName = name,
            tier = tier.ifEmpty { getString(R.string.membership_tier_basic) },
            startDate = todayFull,
            endDate = futureDate,
            branchId = branchId,
            cardUuid = cardUuid.ifEmpty { null }
        )

        viewModel.createMembership(input)
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

        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollView) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBarInsets.bottom)
            insets
        }
    }
}

package com.beta.auraos.ui.memberships

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import com.beta.auraos.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.beta.auraos.R
import com.beta.auraos.data.MembershipRepository
import com.beta.auraos.data.local.SessionManager
import com.beta.auraos.data.model.Membership
import com.beta.auraos.util.formatDate
import com.beta.auraos.databinding.ActivityMembershipListBinding
import com.google.android.material.color.MaterialColors

class MembershipListActivity : BaseActivity() {

    private lateinit var binding: ActivityMembershipListBinding
    private lateinit var viewModel: MembershipViewModel
    private lateinit var sessionManager: SessionManager

    private var nfcAdapter: NfcAdapter? = null
    private var isNfcScanning = false
    private var pendingMemberForCard: Membership? = null
    private var nfcCardDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sessionManager = SessionManager(this)
        val db = com.beta.auraos.data.local.room.AppDatabase.getInstance(this)
        val repository = MembershipRepository(db)
        val factory = MembershipViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[MembershipViewModel::class.java]

        binding = ActivityMembershipListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        setupWindowInsets()

        binding.btnBack.setOnClickListener { finish() }
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, MembershipFormActivity::class.java))
        }

        viewModel.listState.observe(this) { state ->
            when (state) {
                is MembershipListUiState.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is MembershipListUiState.Success -> {
                    binding.loadingOverlay.visibility = View.GONE
                    renderMemberships(state.memberships)
                }
                is MembershipListUiState.Error -> {
                    binding.loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        loadMemberships()
    }

    override fun onResume() {
        super.onResume()
        loadMemberships()
        if (isNfcScanning) {
            enableNfcForegroundDispatch()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isNfcScanning) {
            disableNfcForegroundDispatch()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!isNfcScanning) return
        if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action
        ) {
            val tag: android.nfc.Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            if (tag != null) {
                val cardUuid = bytesToHex(tag.id)
                val member = pendingMemberForCard
                pendingMemberForCard = null
                isNfcScanning = false
                if (member != null) {
                    nfcCardDialog?.dismiss()
                    nfcCardDialog = null
                    stopNfcScan()
                    val branchId = sessionManager.getSelectedBranchId()
                    viewModel.registerCard(member.id, cardUuid, branchId)
                    Toast.makeText(this, R.string.membership_card_register_success, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadMemberships() {
        val branchId = sessionManager.getSelectedBranchId()
        viewModel.loadMemberships(branchId)
    }

    private fun renderMemberships(memberships: List<Membership>) {
        binding.contentContainer.removeViews(1, binding.contentContainer.childCount - 1)

        if (memberships.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyState.visibility = View.GONE

        for (m in memberships) {
            val card = createMembershipCard(m)
            binding.contentContainer.addView(card)

            card.setOnClickListener { showMembershipDialog(m) }
        }
    }

    private fun createMembershipCard(m: Membership): View {
        val card = layoutInflater.inflate(R.layout.item_membership_card, binding.contentContainer, false)
        val tvName = card.findViewById<android.widget.TextView>(R.id.tv_member_name)
        val tvStatus = card.findViewById<android.widget.TextView>(R.id.tv_status_badge)
        val ivDot = card.findViewById<android.widget.ImageView>(R.id.iv_status_dot)
        val tvTier = card.findViewById<android.widget.TextView>(R.id.tv_tier_label)
        val tvExpiry = card.findViewById<android.widget.TextView>(R.id.tv_expiry_date)
        val ivNfc = card.findViewById<android.widget.ImageView>(R.id.iv_nfc_icon)
        val tvCardUuid = card.findViewById<android.widget.TextView>(R.id.tv_card_uuid)

        tvName.text = m.memberName

        if (m.isActive) {
            tvStatus.text = getString(R.string.membership_status_active).uppercase()
            tvStatus.setTextColor(0xFF00E676.toInt())
            ivDot.setColorFilter(0xFF00E676.toInt())
        } else {
            tvStatus.text = getString(R.string.membership_status_inactive).uppercase()
            val muted = MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom)
            tvStatus.setTextColor(muted)
            ivDot.setColorFilter(muted)
        }

        val primaryColor = MaterialColors.getColor(binding.root, android.R.attr.colorPrimary)
        val secondaryColor = MaterialColors.getColor(binding.root, R.attr.textSecondaryCustom)
        when (m.tier.lowercase()) {
            "elite" -> tvTier.setTextColor(primaryColor)
            "premium" -> tvTier.setTextColor(primaryColor)
            else -> tvTier.setTextColor(secondaryColor)
        }
        tvTier.text = m.tier.uppercase()

        tvExpiry.text = getString(R.string.membership_expiry_label, formatDate(m.endDate))

        if (m.cardUuid != null) {
            ivNfc.visibility = View.VISIBLE
            tvCardUuid.visibility = View.VISIBLE
            tvCardUuid.text = m.cardUuid.take(8) + "..."
        }

        return card
    }

    private fun showMembershipDialog(m: Membership) {
        val cardInfo = if (m.cardUuid != null) {
            "${getString(R.string.membership_card_uuid)}: ${m.cardUuid}"
        } else {
            getString(R.string.membership_card_not_assigned)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(m.memberName)
            .setMessage("${getString(R.string.membership_hint_tier)}: ${m.tier}\n${getString(R.string.membership_hint_end_date)}: ${formatDate(m.endDate)}\n${getString(R.string.membership_status_active)}: ${if (m.isActive) getString(R.string.membership_status_active) else getString(R.string.membership_status_inactive)}\n$cardInfo")
            .setPositiveButton(getString(R.string.dialog_ok), null)
            .setNeutralButton(
                if (m.cardUuid != null) getString(R.string.membership_unregister_card)
                else getString(R.string.membership_register_card)
            ) { _, _ ->
                if (m.cardUuid != null) {
                    confirmUnregisterCard(m)
                } else {
                    startNfcCardRegistration(m)
                }
            }
        // Deleting a membership is an admin action on the server.
        if (sessionManager.isAdmin()) {
            dialog.setNegativeButton(getString(R.string.membership_delete_title)) { _, _ ->
                confirmDelete(m)
            }
        }
        dialog.show()
    }

    private fun confirmDelete(m: Membership) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.membership_delete_title))
            .setMessage(getString(R.string.membership_delete_confirm, m.memberName))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                val branchId = sessionManager.getSelectedBranchId()
                viewModel.deleteMembership(m.id, branchId)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun startNfcCardRegistration(m: Membership) {
        if (nfcAdapter == null) {
            Toast.makeText(this, R.string.nfc_not_available, Toast.LENGTH_SHORT).show()
            return
        }
        if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, R.string.nfc_not_available, Toast.LENGTH_SHORT).show()
            return
        }

        pendingMemberForCard = m
        isNfcScanning = true
        enableNfcForegroundDispatch()

        nfcCardDialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.membership_register_card_title))
            .setMessage(getString(R.string.membership_register_card_nfc_hint))
            .setNegativeButton(getString(R.string.action_cancel)) { _, _ ->
                pendingMemberForCard = null
                nfcCardDialog = null
                stopNfcScan()
            }
            .setOnCancelListener {
                pendingMemberForCard = null
                nfcCardDialog = null
                stopNfcScan()
            }
            .show()
    }

    private fun confirmUnregisterCard(m: Membership) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.membership_unregister_card))
            .setMessage(getString(R.string.membership_unregister_card_confirm, m.memberName))
            .setPositiveButton(getString(R.string.membership_unregister_card)) { _, _ ->
                val branchId = sessionManager.getSelectedBranchId()
                viewModel.unregisterCard(m.id, branchId)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
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

    private fun stopNfcScan() {
        isNfcScanning = false
        try {
            disableNfcForegroundDispatch()
        } catch (_: IllegalStateException) {
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentContainer) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBarInsets.bottom)
            insets
        }
    }
}

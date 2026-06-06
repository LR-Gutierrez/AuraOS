package com.beta.auraos.ui.memberships

import android.app.PendingIntent
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import com.beta.auraos.BaseActivity
import androidx.core.content.ContextCompat
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class MembershipListActivity : BaseActivity() {

    private lateinit var binding: ActivityMembershipListBinding
    private lateinit var viewModel: MembershipViewModel
    private lateinit var sessionManager: SessionManager

    private var nfcAdapter: NfcAdapter? = null
    private var isNfcScanning = false
    private var pendingMemberForCard: Membership? = null
    private var nfcCardDialog: androidx.appcompat.app.AlertDialog? = null

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
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
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

            val params = card.layoutParams as LinearLayout.LayoutParams
            if (card !== binding.contentContainer.getChildAt(1)) {
                params.topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
            }

            card.setOnClickListener {
                val cardInfo = if (m.cardUuid != null) {
                    "${getString(R.string.membership_card_uuid)}: ${m.cardUuid}"
                } else {
                    getString(R.string.membership_card_not_assigned)
                }
                AlertDialog.Builder(this)
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
                    .setNegativeButton(getString(R.string.membership_delete_title)) { _, _ ->
                        confirmDelete(m)
                    }
                    .show()
            }
        }
    }

    private fun createMembershipCard(m: Membership): View {
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setCardBackgroundColor(
                ColorStateList.valueOf(
                    MaterialColors.getColor(
                        this, R.attr.cardGlassFillColorCustom
                    )
                )
            )
            radius = resources.getDimension(R.dimen.card_corner_radius_large)
            setContentPadding(padding, padding, padding, padding)
            isClickable = true
            isFocusable = true
            foreground = {
                val tv = android.util.TypedValue()
                this@MembershipListActivity.theme.resolveAttribute(
                    android.R.attr.selectableItemBackground, tv, true
                )
                ContextCompat.getDrawable(this@MembershipListActivity, tv.resourceId)
            }()
        }

        val container = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        val nameText = TextView(this).apply {
            text = m.memberName
            setTextColor(
                MaterialColors.getColor(
                    binding.root, R.attr.textPrimaryCustom
                )
            )
            textSize = 16f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        container.addView(nameText)

        val tierText = TextView(this).apply {
            text = "${m.tier}  \u2022  ${formatDate(m.endDate)}"
            setTextColor(
                MaterialColors.getColor(
                    binding.root, R.attr.textSecondaryCustom
                )
            )
            textSize = 13f
        }
        container.addView(tierText)

        card.addView(container)
        return card
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
            // Activity not resumed; dispatch will be disabled in onPause
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

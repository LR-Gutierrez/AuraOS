package com.beta.auraos.data.audit

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

class AuditRepository(private val context: Context) {

    private val gson = Gson()
    private val dir: File
        get() = File(context.filesDir, "audit").also { it.mkdirs() }

    fun recordVehicleExit(entry: AuditRecord) {
        val records = getUnclosedRecords(entry.branchId).toMutableList()
        records.add(entry)
        writeUnclosedRecords(entry.branchId, records)
    }

    fun getUnclosedRecords(branchId: String): MutableList<AuditRecord> {
        val file = unclosedFile(branchId)
        if (!file.exists()) return mutableListOf()
        return try {
            val json = file.readText()
            val type = object : TypeToken<MutableList<AuditRecord>>() {}.type
            val result: MutableList<AuditRecord>? = gson.fromJson(json, type)
            result ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun markRecordsAsClosed(branchId: String, batchId: String) {
        val records = getUnclosedRecords(branchId)
        val closedRecords = records.map { it.copy(closedInBatch = batchId) }

        val closedFile = closedFile(branchId)
        val existing: MutableList<AuditRecord> = if (closedFile.exists()) {
            try {
                val json = closedFile.readText()
                val type = object : TypeToken<MutableList<AuditRecord>>() {}.type
                val result: MutableList<AuditRecord>? = gson.fromJson(json, type)
                result ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }
        } else mutableListOf()

        val all = (existing + closedRecords).sortedByDescending { it.createdAt }
        closedFile.writeText(gson.toJson(all))
        unclosedFile(branchId).delete()
    }

    fun saveClosureBatch(batch: ClosureBatch) {
        val batches = getClosureBatches(batch.branchId).toMutableList()
        batches.add(batch)
        val file = closuresFile(batch.branchId)
        file.writeText(gson.toJson(batches.sortedByDescending { it.closedAt }))
    }

    fun getClosureBatches(branchId: String): MutableList<ClosureBatch> {
        val file = closuresFile(branchId)
        if (!file.exists()) return mutableListOf()
        return try {
            val json = file.readText()
            val type = object : TypeToken<MutableList<ClosureBatch>>() {}.type
            val result: MutableList<ClosureBatch>? = gson.fromJson(json, type)
            result ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun getLatestClosure(branchId: String): ClosureBatch? {
        return getClosureBatches(branchId).maxByOrNull { it.closedAt }
    }

    fun recordMembershipPayment(payment: MembershipPayment) {
        val payments = getMembershipPayments(payment.branchId).toMutableList()
        payments.add(payment)
        val file = paymentsFile(payment.branchId)
        file.writeText(gson.toJson(payments))
    }

    fun getMembershipPayments(branchId: String): MutableList<MembershipPayment> {
        val file = paymentsFile(branchId)
        if (!file.exists()) return mutableListOf()
        return try {
            val json = file.readText()
            val type = object : TypeToken<MutableList<MembershipPayment>>() {}.type
            val result: MutableList<MembershipPayment>? = gson.fromJson(json, type)
            result ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun writeUnclosedRecords(branchId: String, records: MutableList<AuditRecord>) {
        unclosedFile(branchId).writeText(gson.toJson(records))
    }

    private fun unclosedFile(branchId: String) = File(dir, "unclosed_${sanitize(branchId)}.json")
    private fun closedFile(branchId: String) = File(dir, "closed_${sanitize(branchId)}.json")
    private fun closuresFile(branchId: String) = File(dir, "closures_${sanitize(branchId)}.json")
    private fun paymentsFile(branchId: String) = File(dir, "payments_${sanitize(branchId)}.json")

    private fun sanitize(id: String) = id.replace(Regex("[^a-zA-Z0-9_-]"), "_")

    companion object {
        fun generateId() = UUID.randomUUID().toString()
    }
}

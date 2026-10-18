package com.beta.auraos.data

import com.beta.auraos.data.local.ChangeJournal
import com.beta.auraos.data.local.ChangeJournalProvider
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.BranchEntity
import com.beta.auraos.data.model.Branch
import com.beta.auraos.data.model.CreateBranchInput
import com.beta.auraos.data.model.UpdateBranchInput
import com.beta.auraos.data.remote.BranchApi
import java.io.IOException
import java.util.UUID

class BranchRepository(
    private val api: BranchApi,
    private val database: AppDatabase? = null,
    private val changeJournal: ChangeJournal? = null
) {
    private val journal: ChangeJournal? get() = changeJournal ?: ChangeJournalProvider.get()

    /** Branches cached on the device, for painting screens before the network answers. */
    suspend fun getBranchesLocal(): List<Branch> =
        database?.branchDao()?.getAll()?.map { it.toModel() }.orEmpty()

    suspend fun getBranches(): Result<List<Branch>> {
        return try {
            val response = api.getBranches()
            if (response.isSuccessful) {
                val branches = response.body() ?: emptyList()
                database?.branchDao()?.let { dao ->
                    // Branches created offline and not pushed yet are absent from the server
                    // list; keep them across the refresh or they vanish from the device.
                    val pending = pendingLocalCreates()
                    dao.deleteAll()
                    dao.insertAll(branches.map { it.toEntity() })
                    val serverIds = branches.map { it.id }.toSet()
                    dao.insertAll(pending.filter { it.id !in serverIds })
                }
                val merged = mergeLocalPending(branches)
                Result.Success(merged)
            } else {
                Result.Error(IOException("Error al cargar sucursales: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            val cached = database?.branchDao()?.getAll()
            if (cached != null && cached.isNotEmpty()) {
                Result.Success(cached.map { it.toModel() })
            } else {
                Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
            }
        }
    }

    private suspend fun pendingLocalCreates(): List<BranchEntity> {
        val journal = journal ?: return emptyList()
        return journal.getUnsyncedChanges()
            .filter { it.entityType == "branch" && it.operation == ChangeJournal.OP_CREATE }
            .mapNotNull { database?.branchDao()?.getById(it.entityId) }
    }

    private suspend fun mergeLocalPending(serverList: List<Branch>): List<Branch> {
        val journal = journal ?: return serverList
        val unsynced = journal.getUnsyncedChanges()
        val result = serverList.toMutableList()
        val serverIds = serverList.map { it.id }.toSet()

        for (change in unsynced) {
            if (change.entityType != "branch") continue
            when (change.operation) {
                ChangeJournal.OP_CREATE -> {
                    val local = database?.branchDao()?.getById(change.entityId)
                    if (local != null && local.id !in serverIds) {
                        result.add(local.toModel())
                    }
                }
                ChangeJournal.OP_UPDATE -> {
                    val local = database?.branchDao()?.getById(change.entityId)
                    if (local != null) {
                        val idx = result.indexOfFirst { it.id == local.id }
                        if (idx >= 0) {
                            result[idx] = local.toModel()
                        }
                    }
                }
                ChangeJournal.OP_DELETE -> {
                    result.removeAll { it.id == change.entityId }
                }
            }
        }
        return result
    }

    suspend fun getBranch(id: String): Result<Branch> {
        return try {
            val response = api.getBranch(id)
            if (response.isSuccessful) {
                val branch = response.body()
                if (branch != null) {
                    database?.branchDao()?.insert(branch.toEntity())
                    Result.Success(branch)
                } else {
                    Result.Error(IOException("Sucursal no encontrada"))
                }
            } else {
                Result.Error(IOException("Error al cargar sucursal: ${response.code()}"))
            }
        } catch (e: Exception) {
            val cached = database?.branchDao()?.getById(id)
            if (cached != null) {
                Result.Success(cached.toModel())
            } else {
                Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
            }
        }
    }

    /**
     * Saves the branch locally and journals it for sync. With [localOnly] the server is not
     * contacted at all (no-server setup), so the call never waits on a network timeout.
     */
    suspend fun createBranch(input: CreateBranchInput, localOnly: Boolean = false): Result<Branch> {
        val localId = UUID.randomUUID().toString()
        val now = nowUtcString()

        val localEntry = Branch(
            id = localId,
            name = input.name,
            address = input.address,
            motorcycleCapacity = input.motorcycleCapacity,
            lightVehicleCapacity = input.lightVehicleCapacity,
            heavyVehicleCapacity = input.heavyVehicleCapacity,
            motorcycleRate = input.motorcycleRate,
            lightVehicleRate = input.lightVehicleRate,
            heavyVehicleRate = input.heavyVehicleRate,
            motorcycleOvernightRate = input.motorcycleOvernightRate,
            lightVehicleOvernightRate = input.lightVehicleOvernightRate,
            heavyVehicleOvernightRate = input.heavyVehicleOvernightRate,
            openTimeWeekday = input.openTimeWeekday,
            closeTimeWeekday = input.closeTimeWeekday,
            openTimeWeekend = input.openTimeWeekend,
            closeTimeWeekend = input.closeTimeWeekend,
            currency = input.currency,
            createdAt = now,
            updatedAt = now
        )

        database?.branchDao()?.insert(localEntry.toEntity())
        journal?.recordCreate("branch", localId, localEntry.toJournalMap())
        if (localOnly) return Result.Success(localEntry)

        try {
            val response = api.createBranch(input)
            if (response.isSuccessful) {
                val serverEntry = response.body()
                if (serverEntry != null) {
                    replaceLocalWithServerBranch(localId, serverEntry)
                    return Result.Success(serverEntry)
                }
            }
        } catch (_: Exception) {
        }
        return Result.Success(localEntry)
    }

    suspend fun updateBranch(id: String, input: UpdateBranchInput): Result<Branch> {
        val changedFields = mutableMapOf<String, Any>()
        input.name?.let { changedFields["name"] = it }
        input.address?.let { changedFields["address"] = it }
        input.motorcycleCapacity?.let { changedFields["motorcycleCapacity"] = it }
        input.lightVehicleCapacity?.let { changedFields["lightVehicleCapacity"] = it }
        input.heavyVehicleCapacity?.let { changedFields["heavyVehicleCapacity"] = it }
        input.motorcycleRate?.let { changedFields["motorcycleRate"] = it }
        input.lightVehicleRate?.let { changedFields["lightVehicleRate"] = it }
        input.heavyVehicleRate?.let { changedFields["heavyVehicleRate"] = it }
        input.currency?.let { changedFields["currency"] = it }
        input.motorcycleOvernightRate?.let { changedFields["motorcycleOvernightRate"] = it }
        input.lightVehicleOvernightRate?.let { changedFields["lightVehicleOvernightRate"] = it }
        input.heavyVehicleOvernightRate?.let { changedFields["heavyVehicleOvernightRate"] = it }
        input.openTimeWeekday?.let { changedFields["openTimeWeekday"] = it }
        input.closeTimeWeekday?.let { changedFields["closeTimeWeekday"] = it }
        input.openTimeWeekend?.let { changedFields["openTimeWeekend"] = it }
        input.closeTimeWeekend?.let { changedFields["closeTimeWeekend"] = it }

        val existing = database?.branchDao()?.getById(id)
        if (existing != null) {
            val updated = existing.copy(
                name = input.name ?: existing.name,
                address = input.address ?: existing.address,
                motorcycleCapacity = input.motorcycleCapacity ?: existing.motorcycleCapacity,
                lightVehicleCapacity = input.lightVehicleCapacity ?: existing.lightVehicleCapacity,
                heavyVehicleCapacity = input.heavyVehicleCapacity ?: existing.heavyVehicleCapacity,
                motorcycleRate = input.motorcycleRate ?: existing.motorcycleRate,
                lightVehicleRate = input.lightVehicleRate ?: existing.lightVehicleRate,
                heavyVehicleRate = input.heavyVehicleRate ?: existing.heavyVehicleRate,
                currency = input.currency ?: existing.currency,
                motorcycleOvernightRate = input.motorcycleOvernightRate ?: existing.motorcycleOvernightRate,
                lightVehicleOvernightRate = input.lightVehicleOvernightRate ?: existing.lightVehicleOvernightRate,
                heavyVehicleOvernightRate = input.heavyVehicleOvernightRate ?: existing.heavyVehicleOvernightRate,
                openTimeWeekday = input.openTimeWeekday ?: existing.openTimeWeekday,
                closeTimeWeekday = input.closeTimeWeekday ?: existing.closeTimeWeekday,
                openTimeWeekend = input.openTimeWeekend ?: existing.openTimeWeekend,
                closeTimeWeekend = input.closeTimeWeekend ?: existing.closeTimeWeekend,
                lastModifiedAt = System.currentTimeMillis()
            )
            database?.branchDao()?.insert(updated)
        }

        val localModel = existing?.toModel()
        if (localModel == null) {
            return Result.Error(IOException("Error al actualizar sucursal: no existe localmente"))
        }

        return try {
            val response = api.updateBranch(id, changedFields)
            if (response.isSuccessful) {
                val branch = response.body()
                if (branch != null) {
                    database?.branchDao()?.insert(branch.toEntity())
                    journal?.recordUpdate("branch", id, branch.toJournalMap(), changedFields)
                    return Result.Success(branch)
                }
            }
            journal?.recordUpdate("branch", id, localModel.toJournalMap(), changedFields)
            Result.Success(localModel.copy(
                name = input.name ?: localModel.name,
                address = input.address ?: localModel.address,
                motorcycleCapacity = input.motorcycleCapacity ?: localModel.motorcycleCapacity,
                lightVehicleCapacity = input.lightVehicleCapacity ?: localModel.lightVehicleCapacity,
                heavyVehicleCapacity = input.heavyVehicleCapacity ?: localModel.heavyVehicleCapacity,
                motorcycleRate = input.motorcycleRate ?: localModel.motorcycleRate,
                lightVehicleRate = input.lightVehicleRate ?: localModel.lightVehicleRate,
                heavyVehicleRate = input.heavyVehicleRate ?: localModel.heavyVehicleRate,
                currency = input.currency ?: localModel.currency,
                motorcycleOvernightRate = input.motorcycleOvernightRate ?: localModel.motorcycleOvernightRate,
                lightVehicleOvernightRate = input.lightVehicleOvernightRate ?: localModel.lightVehicleOvernightRate,
                heavyVehicleOvernightRate = input.heavyVehicleOvernightRate ?: localModel.heavyVehicleOvernightRate,
                openTimeWeekday = input.openTimeWeekday ?: localModel.openTimeWeekday,
                closeTimeWeekday = input.closeTimeWeekday ?: localModel.closeTimeWeekday,
                openTimeWeekend = input.openTimeWeekend ?: localModel.openTimeWeekend,
                closeTimeWeekend = input.closeTimeWeekend ?: localModel.closeTimeWeekend
            ))
        } catch (e: Exception) {
            journal?.recordUpdate("branch", id, localModel.toJournalMap(), changedFields)
            if (!isNetworkError(e)) throw e
            Result.Success(localModel.copy(
                name = input.name ?: localModel.name,
                address = input.address ?: localModel.address,
                motorcycleCapacity = input.motorcycleCapacity ?: localModel.motorcycleCapacity,
                lightVehicleCapacity = input.lightVehicleCapacity ?: localModel.lightVehicleCapacity,
                heavyVehicleCapacity = input.heavyVehicleCapacity ?: localModel.heavyVehicleCapacity,
                motorcycleRate = input.motorcycleRate ?: localModel.motorcycleRate,
                lightVehicleRate = input.lightVehicleRate ?: localModel.lightVehicleRate,
                heavyVehicleRate = input.heavyVehicleRate ?: localModel.heavyVehicleRate,
                currency = input.currency ?: localModel.currency,
                motorcycleOvernightRate = input.motorcycleOvernightRate ?: localModel.motorcycleOvernightRate,
                lightVehicleOvernightRate = input.lightVehicleOvernightRate ?: localModel.lightVehicleOvernightRate,
                heavyVehicleOvernightRate = input.heavyVehicleOvernightRate ?: localModel.heavyVehicleOvernightRate,
                openTimeWeekday = input.openTimeWeekday ?: localModel.openTimeWeekday,
                closeTimeWeekday = input.closeTimeWeekday ?: localModel.closeTimeWeekday,
                openTimeWeekend = input.openTimeWeekend ?: localModel.openTimeWeekend,
                closeTimeWeekend = input.closeTimeWeekend ?: localModel.closeTimeWeekend
            ))
        }
    }

    suspend fun deleteBranch(id: String): Result<Unit> {
        val cached = database?.branchDao()?.getById(id)
        database?.branchDao()?.deleteById(id)
        if (cached != null) {
            journal?.recordDelete("branch", id, cached.toJournalMap())
        }

        try {
            val response = api.deleteBranch(id)
            if (response.isSuccessful) {
                return Result.Success(Unit)
            }
            val errorBody = response.errorBody()?.string() ?: "Error desconocido"
            if (response.code() == 409 || errorBody.contains("FK", ignoreCase = true) || errorBody.contains("ingresos", ignoreCase = true)) {
                return Result.Error(IOException("No se puede eliminar esta sucursal porque tiene ingresos registrados."))
            }
        } catch (_: Exception) {
        }
        return Result.Success(Unit)
    }

    suspend fun replaceLocalWithServerBranch(localId: String, serverEntry: Branch) {
        database?.branchDao()?.deleteById(localId)
        database?.branchDao()?.insert(serverEntry.toEntity())
        journal?.markSyncedForEntity("branch", localId)
    }

    private fun nowUtcString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }

    private fun Branch.toEntity() = BranchEntity(
        id = id,
        name = name,
        address = address,
        motorcycleCapacity = motorcycleCapacity,
        lightVehicleCapacity = lightVehicleCapacity,
        heavyVehicleCapacity = heavyVehicleCapacity,
        motorcycleRate = motorcycleRate,
        lightVehicleRate = lightVehicleRate,
        heavyVehicleRate = heavyVehicleRate,
        motorcycleOvernightRate = motorcycleOvernightRate,
        lightVehicleOvernightRate = lightVehicleOvernightRate,
        heavyVehicleOvernightRate = heavyVehicleOvernightRate,
        openTimeWeekday = openTimeWeekday,
        closeTimeWeekday = closeTimeWeekday,
        openTimeWeekend = openTimeWeekend,
        closeTimeWeekend = closeTimeWeekend,
        currency = currency,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastModifiedAt = System.currentTimeMillis()
    )

    private fun Branch.toJournalMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "address" to address,
        "motorcycleCapacity" to motorcycleCapacity,
        "lightVehicleCapacity" to lightVehicleCapacity,
        "heavyVehicleCapacity" to heavyVehicleCapacity,
        "motorcycleRate" to motorcycleRate,
        "lightVehicleRate" to lightVehicleRate,
        "heavyVehicleRate" to heavyVehicleRate,
        "motorcycleOvernightRate" to motorcycleOvernightRate,
        "lightVehicleOvernightRate" to lightVehicleOvernightRate,
        "heavyVehicleOvernightRate" to heavyVehicleOvernightRate,
        "openTimeWeekday" to openTimeWeekday,
        "closeTimeWeekday" to closeTimeWeekday,
        "openTimeWeekend" to openTimeWeekend,
        "closeTimeWeekend" to closeTimeWeekend,
        "currency" to currency,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "favorite" to false
    )

    private fun BranchEntity.toModel() = Branch(
        id = id,
        name = name,
        address = address,
        motorcycleCapacity = motorcycleCapacity,
        lightVehicleCapacity = lightVehicleCapacity,
        heavyVehicleCapacity = heavyVehicleCapacity,
        motorcycleRate = motorcycleRate,
        lightVehicleRate = lightVehicleRate,
        heavyVehicleRate = heavyVehicleRate,
        motorcycleOvernightRate = motorcycleOvernightRate,
        lightVehicleOvernightRate = lightVehicleOvernightRate,
        heavyVehicleOvernightRate = heavyVehicleOvernightRate,
        openTimeWeekday = openTimeWeekday,
        closeTimeWeekday = closeTimeWeekday,
        openTimeWeekend = openTimeWeekend,
        closeTimeWeekend = closeTimeWeekend,
        currency = currency,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun BranchEntity.toJournalMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "address" to address,
        "motorcycleCapacity" to motorcycleCapacity,
        "lightVehicleCapacity" to lightVehicleCapacity,
        "heavyVehicleCapacity" to heavyVehicleCapacity,
        "motorcycleRate" to motorcycleRate,
        "lightVehicleRate" to lightVehicleRate,
        "heavyVehicleRate" to heavyVehicleRate,
        "motorcycleOvernightRate" to motorcycleOvernightRate,
        "lightVehicleOvernightRate" to lightVehicleOvernightRate,
        "heavyVehicleOvernightRate" to heavyVehicleOvernightRate,
        "openTimeWeekday" to openTimeWeekday,
        "closeTimeWeekday" to closeTimeWeekday,
        "openTimeWeekend" to openTimeWeekend,
        "closeTimeWeekend" to closeTimeWeekend,
        "currency" to currency,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt,
        "favorite" to favorite
    )

    private fun isNetworkError(exception: Exception): Boolean {
        return exception is IOException ||
                exception.message?.contains("Unable to resolve host") == true ||
                exception.message?.contains("Failed to connect") == true ||
                exception.message?.contains("timeout") == true
    }
}

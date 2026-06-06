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

class BranchRepository(
    private val api: BranchApi,
    private val database: AppDatabase? = null,
    private val changeJournal: ChangeJournal? = null
) {
    private val journal: ChangeJournal? get() = changeJournal ?: ChangeJournalProvider.get()

    suspend fun getBranches(): Result<List<Branch>> {
        return try {
            val response = api.getBranches()
            if (response.isSuccessful) {
                val branches = response.body() ?: emptyList()
                database?.branchDao()?.let { dao ->
                    dao.deleteAll()
                    dao.insertAll(branches.map { it.toEntity() })
                }
                Result.Success(branches)
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

    suspend fun createBranch(input: CreateBranchInput): Result<Branch> {
        return try {
            val response = api.createBranch(input)
            if (response.isSuccessful) {
                val branch = response.body()
                if (branch != null) {
                    database?.branchDao()?.insert(branch.toEntity())
                    journal?.recordCreate("branch", branch.id, branch.toJournalMap())
                    Result.Success(branch)
                } else {
                    Result.Error(IOException("Error al crear sucursal"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(IOException("Error al crear sucursal: $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
        }
    }

    suspend fun updateBranch(id: String, input: UpdateBranchInput): Result<Branch> {
        return try {
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

            val response = api.updateBranch(id, changedFields)
            if (response.isSuccessful) {
                val branch = response.body()
                if (branch != null) {
                    database?.branchDao()?.insert(branch.toEntity())
                    journal?.recordUpdate("branch", id, branch.toJournalMap(), changedFields)
                    Result.Success(branch)
                } else {
                    Result.Error(IOException("Error al actualizar sucursal"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(IOException("Error al actualizar sucursal: $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
        }
    }

    suspend fun deleteBranch(id: String): Result<Unit> {
        return try {
            val cached = database?.branchDao()?.getById(id)
            val response = api.deleteBranch(id)
            if (response.isSuccessful) {
                database?.branchDao()?.deleteById(id)
                if (cached != null) {
                    journal?.recordDelete("branch", id, cached.toJournalMap())
                }
                Result.Success(Unit)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                val message = if (response.code() == 409 || errorBody.contains("FK", ignoreCase = true) || errorBody.contains("ingresos", ignoreCase = true)) {
                    "No se puede eliminar esta sucursal porque tiene ingresos registrados."
                } else {
                    "Error al eliminar sucursal: $errorBody"
                }
                Result.Error(IOException(message))
            }
        } catch (e: Exception) {
            Result.Error(IOException("Error de conexión: ${e.localizedMessage ?: "Error desconocido"}"))
        }
    }

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
}

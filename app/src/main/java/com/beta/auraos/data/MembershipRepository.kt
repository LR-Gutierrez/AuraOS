package com.beta.auraos.data

import com.beta.auraos.data.local.ChangeJournal
import com.beta.auraos.data.local.ChangeJournalProvider
import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.MembershipEntity
import com.beta.auraos.data.model.CreateMembershipInput
import com.beta.auraos.data.model.Membership
import com.beta.auraos.data.model.UpdateMembershipInput
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.MembershipApi
import java.io.IOException
import java.util.UUID

class MembershipRepository(
    private val database: AppDatabase? = null,
    private val changeJournal: ChangeJournal? = null
) {
    private val journal: ChangeJournal? get() = changeJournal ?: ChangeJournalProvider.get()

    private val api = ApiClient.createApi<MembershipApi>()

    suspend fun getMemberships(branchId: String? = null): Result<List<Membership>> {
        return try {
            val response = api.getMemberships(branchId = branchId)
            if (response.isSuccessful) {
                val list = response.body() ?: emptyList()
                if (branchId != null) {
                    database?.membershipDao()?.let { dao ->
                        dao.deleteByBranch(branchId)
                        dao.insertAll(list.map { it.toEntity() })
                    }
                    val merged = mergeLocalPending(list, branchId)
                    Result.Success(merged)
                } else {
                    Result.Success(list)
                }
            } else {
                Result.Error(Exception("Error al cargar membresías: ${response.code()}"))
            }
        } catch (e: Exception) {
            if (branchId != null && isNetworkError(e)) {
                val cached = database?.membershipDao()?.getByBranch(branchId)
                if (cached != null && cached.isNotEmpty()) {
                    return Result.Success(cached.map { it.toModel() })
                }
            }
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun getMembership(id: String): Result<Membership> {
        return try {
            val response = api.getMembership(id)
            if (response.isSuccessful) {
                val membership = response.body()
                if (membership != null) {
                    database?.membershipDao()?.insert(membership.toEntity())
                    Result.Success(membership)
                } else {
                    Result.Error(Exception("Membresía no encontrada"))
                }
            } else {
                Result.Error(Exception("Error al cargar membresía: ${response.code()}"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                val cached = database?.membershipDao()?.getById(id)
                if (cached != null) {
                    return Result.Success(cached.toModel())
                }
            }
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun getMembershipByCardUuid(cardUuid: String): Result<Membership> {
        return try {
            val response = api.getMembershipByCardUuid(cardUuid)
            if (response.isSuccessful) {
                val membership = response.body()
                if (membership != null) {
                    database?.membershipDao()?.insert(membership.toEntity())
                    Result.Success(membership)
                } else {
                    Result.Error(Exception("Membresía no encontrada para esta tarjeta"))
                }
            } else if (response.code() == 404) {
                Result.Error(Exception("Tarjeta no registrada"))
            } else {
                Result.Error(Exception("Error al buscar membresía: ${response.code()}"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                val cached = database?.membershipDao()?.getByCardUuid(cardUuid)
                if (cached != null) {
                    return Result.Success(cached.toModel())
                }
            }
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun createMembership(input: CreateMembershipInput): Result<Membership> {
        val localId = UUID.randomUUID().toString()
        val now = nowUtcString()

        val localEntry = Membership(
            id = localId,
            memberName = input.memberName,
            tier = input.tier,
            startDate = input.startDate,
            endDate = input.endDate,
            isActive = true,
            branchId = input.branchId,
            branch = null,
            cardUuid = input.cardUuid
        )

        database?.membershipDao()?.insert(localEntry.toEntity())
        journal?.recordCreate("membership", localId, localEntry.toJournalMap())

        return try {
            val response = api.createMembership(input)
            if (response.isSuccessful) {
                val serverEntry = response.body()
                if (serverEntry != null) {
                    replaceLocalWithServerMembership(localId, serverEntry)
                    Result.Success(serverEntry)
                } else {
                    Result.Error(Exception("Error al crear membresía"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(Exception("Error al crear membresía: $errorBody"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                Result.Success(localEntry)
            } else {
                Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun updateMembership(id: String, input: UpdateMembershipInput): Result<Membership> {
        val changedFields = mutableMapOf<String, Any>()
        input.memberName?.let { changedFields["memberName"] = it }
        input.tier?.let { changedFields["tier"] = it }
        input.endDate?.let { changedFields["endDate"] = it }
        input.cardUuid?.let { changedFields["cardUuid"] = it }

        val existing = database?.membershipDao()?.getById(id)
        if (existing != null) {
            val updated = existing.copy(
                memberName = input.memberName ?: existing.memberName,
                tier = input.tier ?: existing.tier,
                endDate = input.endDate ?: existing.endDate,
                cardUuid = input.cardUuid ?: existing.cardUuid,
                lastModifiedAt = System.currentTimeMillis()
            )
            database?.membershipDao()?.insert(updated)
        }

        val localModel = existing?.toModel()
        if (localModel == null) {
            return Result.Error(Exception("Error al actualizar membresía: no existe localmente"))
        }

        return try {
            val response = api.updateMembership(id, changedFields)
            if (response.isSuccessful) {
                val membership = response.body()
                if (membership != null) {
                    database?.membershipDao()?.insert(membership.toEntity())
                    journal?.recordUpdate("membership", id, membership.toJournalMap(), changedFields)
                    Result.Success(membership)
                } else {
                    Result.Error(Exception("Error al actualizar membresía"))
                }
            } else {
                journal?.recordUpdate("membership", id, localModel.toJournalMap(), changedFields)
                Result.Success(localModel.copy(
                    memberName = input.memberName ?: localModel.memberName,
                    tier = input.tier ?: localModel.tier,
                    endDate = input.endDate ?: localModel.endDate,
                    cardUuid = input.cardUuid ?: localModel.cardUuid
                ))
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                val serverEntry = existing?.toModel()?.copy(
                    memberName = input.memberName ?: existing.memberName,
                    tier = input.tier ?: existing.tier,
                    endDate = input.endDate ?: existing.endDate,
                    cardUuid = input.cardUuid ?: existing.cardUuid
                )
                if (serverEntry != null) {
                    database?.membershipDao()?.insert(serverEntry.toEntity())
                    journal?.recordUpdate("membership", id, serverEntry.toJournalMap(), changedFields)
                    Result.Success(serverEntry)
                }
            }
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun registerCard(id: String, cardUuid: String): Result<Membership> {
        val changedFields = mapOf("cardUuid" to cardUuid)
        val existing = database?.membershipDao()?.getById(id)?.toModel()
        if (existing != null) {
            database?.membershipDao()?.insert(existing.copy(cardUuid = cardUuid).toEntity())
        }
        return try {
            val response = api.registerCard(id, changedFields)
            if (response.isSuccessful) {
                val membership = response.body()
                if (membership != null) {
                    database?.membershipDao()?.insert(membership.toEntity())
                    journal?.recordUpdate("membership", id, membership.toJournalMap(), changedFields)
                    Result.Success(membership)
                } else {
                    Result.Error(Exception("Error al registrar tarjeta"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(Exception("Error al registrar tarjeta: $errorBody"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e) && existing != null) {
                val updated = existing.copy(cardUuid = cardUuid)
                database?.membershipDao()?.insert(updated.toEntity())
                journal?.recordUpdate("membership", id, updated.toJournalMap(), changedFields)
                Result.Success(updated)
            } else {
                Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun unregisterCard(id: String): Result<Membership> {
        val changedFields = mapOf("cardUuid" to null)
        val existing = database?.membershipDao()?.getById(id)?.toModel()
        if (existing != null) {
            database?.membershipDao()?.insert(existing.copy(cardUuid = null).toEntity())
        }
        return try {
            val response = api.unregisterCard(id)
            if (response.isSuccessful) {
                val membership = response.body()
                if (membership != null) {
                    database?.membershipDao()?.insert(membership.toEntity())
                    journal?.recordUpdate("membership", id, membership.toJournalMap(), changedFields)
                    Result.Success(membership)
                } else {
                    Result.Error(Exception("Error al desvincular tarjeta"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(Exception("Error al desvincular tarjeta: $errorBody"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e) && existing != null) {
                val updated = existing.copy(cardUuid = null)
                database?.membershipDao()?.insert(updated.toEntity())
                journal?.recordUpdate("membership", id, updated.toJournalMap(), changedFields)
                Result.Success(updated)
            } else {
                Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun deleteMembership(id: String): Result<Unit> {
        val cached = database?.membershipDao()?.getById(id)
        database?.membershipDao()?.deleteById(id)
        if (cached != null) {
            journal?.recordDelete("membership", id, cached.toJournalMap())
        }

        return try {
            val response = api.deleteMembership(id)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Error al eliminar membresía: ${response.code()}"))
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun replaceLocalWithServerMembership(localId: String, serverEntry: Membership) {
        database?.membershipDao()?.deleteById(localId)
        database?.membershipDao()?.insert(serverEntry.toEntity())
        journal?.markSyncedForEntity("membership", localId)
    }

    private suspend fun mergeLocalPending(
        serverList: List<Membership>,
        branchId: String
    ): List<Membership> {
        val journal = journal ?: return serverList
        val unsynced = journal.getUnsyncedChanges()
        val result = serverList.toMutableList()
        val serverIds = serverList.map { it.id }.toSet()

        for (change in unsynced) {
            if (change.entityType != "membership") continue
            when (change.operation) {
                ChangeJournal.OP_CREATE -> {
                    val local = database?.membershipDao()?.getById(change.entityId)
                    if (local != null && local.branchId == branchId && local.id !in serverIds) {
                        result.add(local.toModel())
                    }
                }
                ChangeJournal.OP_UPDATE -> {
                    val local = database?.membershipDao()?.getById(change.entityId)
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

    private fun nowUtcString(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }

    private fun Membership.toJournalMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "memberName" to memberName,
        "tier" to tier,
        "startDate" to startDate,
        "endDate" to endDate,
        "isActive" to isActive,
        "branchId" to branchId,
        "cardUuid" to cardUuid,
        "createdAt" to null,
        "updatedAt" to null
    )

    private fun MembershipEntity.toJournalMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "memberName" to memberName,
        "tier" to tier,
        "startDate" to startDate,
        "endDate" to endDate,
        "isActive" to isActive,
        "branchId" to branchId,
        "cardUuid" to cardUuid,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    private fun Membership.toEntity() = MembershipEntity(
        id = id,
        memberName = memberName,
        tier = tier,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive,
        branchId = branchId,
        cardUuid = cardUuid,
        createdAt = null,
        updatedAt = null,
        lastModifiedAt = System.currentTimeMillis()
    )

    private fun MembershipEntity.toModel() = Membership(
        id = id,
        memberName = memberName,
        tier = tier,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive,
        branchId = branchId,
        branch = null,
        cardUuid = cardUuid
    )

    private fun isNetworkError(exception: Exception): Boolean {
        return exception is IOException ||
                exception.message?.contains("Unable to resolve host") == true ||
                exception.message?.contains("Failed to connect") == true ||
                exception.message?.contains("timeout") == true
    }
}

package com.beta.auraos.data

import com.beta.auraos.data.local.room.AppDatabase
import com.beta.auraos.data.local.room.MembershipEntity
import com.beta.auraos.data.model.CreateMembershipInput
import com.beta.auraos.data.model.Membership
import com.beta.auraos.data.model.UpdateMembershipInput
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.MembershipApi
import java.io.IOException

class MembershipRepository(
    private val database: AppDatabase? = null
) {

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
                }
                Result.Success(list)
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
        return try {
            val response = api.createMembership(input)
            if (response.isSuccessful) {
                val membership = response.body()
                if (membership != null) {
                    database?.membershipDao()?.insert(membership.toEntity())
                    Result.Success(membership)
                } else {
                    Result.Error(Exception("Error al crear membresía"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(Exception("Error al crear membresía: $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun updateMembership(id: String, input: UpdateMembershipInput): Result<Membership> {
        return try {
            val map = mutableMapOf<String, Any>()
            input.memberName?.let { map["memberName"] = it }
            input.tier?.let { map["tier"] = it }
            input.endDate?.let { map["endDate"] = it }
            input.cardUuid?.let { map["cardUuid"] = it }

            val response = api.updateMembership(id, map)
            if (response.isSuccessful) {
                val membership = response.body()
                if (membership != null) {
                    database?.membershipDao()?.insert(membership.toEntity())
                    Result.Success(membership)
                } else {
                    Result.Error(Exception("Error al actualizar membresía"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(Exception("Error al actualizar membresía: $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun registerCard(id: String, cardUuid: String): Result<Membership> {
        return try {
            val response = api.registerCard(id, mapOf("cardUuid" to cardUuid))
            if (response.isSuccessful) {
                val membership = response.body()
                if (membership != null) {
                    database?.membershipDao()?.insert(membership.toEntity())
                    Result.Success(membership)
                } else {
                    Result.Error(Exception("Error al registrar tarjeta"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(Exception("Error al registrar tarjeta: $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun unregisterCard(id: String): Result<Membership> {
        return try {
            val response = api.unregisterCard(id)
            if (response.isSuccessful) {
                val membership = response.body()
                if (membership != null) {
                    database?.membershipDao()?.insert(membership.toEntity())
                    Result.Success(membership)
                } else {
                    Result.Error(Exception("Error al desvincular tarjeta"))
                }
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(Exception("Error al desvincular tarjeta: $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun deleteMembership(id: String): Result<Unit> {
        return try {
            val response = api.deleteMembership(id)
            if (response.isSuccessful) {
                database?.membershipDao()?.deleteById(id)
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Error al eliminar membresía: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    private fun Membership.toEntity() = MembershipEntity(
        id = id,
        memberName = memberName,
        tier = tier,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive,
        branchId = branchId,
        cardUuid = cardUuid
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

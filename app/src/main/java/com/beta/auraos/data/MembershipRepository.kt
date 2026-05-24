package com.beta.auraos.data

import com.beta.auraos.data.model.CreateMembershipInput
import com.beta.auraos.data.model.Membership
import com.beta.auraos.data.model.UpdateMembershipInput
import com.beta.auraos.data.remote.ApiClient
import com.beta.auraos.data.remote.MembershipApi

class MembershipRepository {

    private val api = ApiClient.createApi<MembershipApi>()

    suspend fun getMemberships(branchId: String? = null): Result<List<Membership>> {
        return try {
            val response = api.getMemberships(branchId = branchId)
            if (response.isSuccessful) {
                Result.Success(response.body() ?: emptyList())
            } else {
                Result.Error(Exception("Error al cargar membresías: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun getMembership(id: String): Result<Membership> {
        return try {
            val response = api.getMembership(id)
            if (response.isSuccessful) {
                response.body()?.let { Result.Success(it) }
                    ?: Result.Error(Exception("Membresía no encontrada"))
            } else {
                Result.Error(Exception("Error al cargar membresía: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun createMembership(input: CreateMembershipInput): Result<Membership> {
        return try {
            val response = api.createMembership(input)
            if (response.isSuccessful) {
                response.body()?.let { Result.Success(it) }
                    ?: Result.Error(Exception("Error al crear membresía"))
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
            input.isActive?.let { map["isActive"] = it }

            val response = api.updateMembership(id, map)
            if (response.isSuccessful) {
                response.body()?.let { Result.Success(it) }
                    ?: Result.Error(Exception("Error al actualizar membresía"))
            } else {
                val errorBody = response.errorBody()?.string() ?: "Error desconocido"
                Result.Error(Exception("Error al actualizar membresía: $errorBody"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }

    suspend fun deleteMembership(id: String): Result<Unit> {
        return try {
            val response = api.deleteMembership(id)
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error(Exception("Error al eliminar membresía: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.Error(Exception("Error de conexión: ${e.localizedMessage}"))
        }
    }
}

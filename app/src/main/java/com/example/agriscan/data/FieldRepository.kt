package com.example.agriscan.data

import com.example.agriscan.data.local.FieldDao
import com.example.agriscan.data.local.FieldEntity
import kotlinx.coroutines.flow.Flow

class FieldRepository(private val dao: FieldDao) {

    // Stream of fields from Room
    val fields: Flow<List<FieldEntity>> = dao.observeFields()

    // Create
    suspend fun addField(name: String, notes: String): Long =
        dao.insert(FieldEntity(name = name, notes = notes))

    // Overload if you already built the entity elsewhere
    suspend fun addField(entity: FieldEntity): Long = dao.insert(entity)

    // Update
    suspend fun updateField(entity: FieldEntity) = dao.update(entity)

    // Delete by id
    suspend fun deleteField(id: Long) = dao.delete(id)
}

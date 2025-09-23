package com.example.agriscan.data

import com.example.agriscan.data.local.FieldDao
import com.example.agriscan.data.local.FieldEntity
import kotlinx.coroutines.flow.Flow

class FieldRepository(private val dao: FieldDao) {

    fun observeFields(): Flow<List<FieldEntity>> = dao.observeFields()

    fun observeField(id: Long): Flow<FieldEntity?> = dao.observeField(id)

    suspend fun create(name: String, notes: String = ""): Long =
        dao.insert(FieldEntity(name = name.trim(), notes = notes))

    suspend fun rename(id: Long, newName: String) {
        val current = dao.find(id) ?: return
        dao.update(current.copy(name = newName.trim()))
    }

    suspend fun updateNotes(id: Long, notes: String) {
        val current = dao.find(id) ?: return
        dao.update(current.copy(notes = notes))
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        // Captures keep living with fieldId = null due to FK SET_NULL
    }
}

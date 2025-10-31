package com.example.agriscan.rag

import com.example.agriscan.data.local.AdviceDao
import com.example.agriscan.data.local.CaptureDao
import com.example.agriscan.data.local.AdviceSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdvisorService(
    private val kb: KBLoader,
    private val captureDao: CaptureDao,
    private val adviceDao: AdviceDao,
    private val agent: TemplatedAgent = TemplatedAgent()
) {
    /** Generate offline advice for a capture and persist it. */
    suspend fun advise(
        captureId: Long,
        userQuestion: String = "What should I do now?"
    ): AdviceSessionEntity = withContext(Dispatchers.IO) {

        val capture = captureDao.find(captureId) ?: error("Capture not found: $captureId")
        val clazz = capture.predictedClass ?: error("No prediction on this capture yet")

        val docs = kb.forClass(clazz, k = 3)
        val answer = agent.generate(clazz, docs, userQuestion)
        val idsCsv = docs.joinToString(",") { it.id }

        val entity = AdviceSessionEntity(
            id = 0L,
            captureId = captureId,
            query = userQuestion,
            predictedClass = clazz,
            topDocIdsCsv = idsCsv,
            answerText = answer
        )
        val newId = adviceDao.insert(entity)
        entity.copy(id = newId)
    }
}

package com.example.agriscan.rag

class TemplatedAgent {
    fun generate(clazz: String, docs: List<KBEntry>, userQuestion: String): String {
        val header = """
            Diagnosis: $clazz
            Question: $userQuestion
        """.trimIndent()

        val merged = docs.joinToString("\n---\n") { "Source: ${it.title}\n${it.text}" }

        return buildString {
            appendLine(header)
            appendLine()
            appendLine("What this means:")
            appendLine("- Based on your photo, this looks like $clazz. Compare the symptoms listed below with your plant.")
            appendLine()
            appendLine("Immediate steps (today/tomorrow):")
            appendLine("- Remove heavily infected leaves/fruit if present.")
            appendLine("- Avoid wetting foliage late in the day; prefer drip/soil-level watering.")
            appendLine("- Improve airflow (spacing, pruning lower leaves touching soil).")
            appendLine()
            appendLine("Context from agronomy notes:")
            appendLine(merged)
            appendLine()
            appendLine("When to escalate:")
            appendLine("- If spread is rapid, fruit is affected, or weather strongly favors disease—seek local extension advice.")
            appendLine()
            appendLine("This is guidance, not a medical or legal instruction.")
        }
    }
}

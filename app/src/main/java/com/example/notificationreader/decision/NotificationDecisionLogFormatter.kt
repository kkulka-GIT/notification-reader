package com.example.notificationreader.decision

import com.example.notificationreader.history.RawNotificationEvent

data class NotificationDecisionDiagnosticEntry(
    val stage: String,
    val message: String
)

object NotificationDecisionLogFormatter {
    fun diagnosticEntries(event: RawNotificationEvent, decision: NotificationDecision): List<NotificationDecisionDiagnosticEntry> {
        return listOf(
            NotificationDecisionDiagnosticEntry("DECISION", summary(event, decision)),
            NotificationDecisionDiagnosticEntry("DECISION_TRACE", trace(decision))
        )
    }

    fun summary(event: RawNotificationEvent, decision: NotificationDecision): String {
        return "packageName=${event.packageName}" +
            " | history=${decision.historySummary}" +
            " | speech=${decision.speechSummary}" +
            " | finalRule=${decision.finalRuleId.name}" +
            " | template=${decision.speechTemplateId ?: "NONE"}"
    }

    fun trace(decision: NotificationDecision): String {
        return decision.trace.joinToString(" -> ") { step ->
            val metadata = if (step.metadata.isEmpty()) {
                ""
            } else {
                step.metadata.entries.joinToString(
                    prefix = " {",
                    postfix = "}"
                ) { "${it.key}=${it.value}" }
            }
            "${step.ruleId.name}:${step.result.name}$metadata"
        }
    }
}

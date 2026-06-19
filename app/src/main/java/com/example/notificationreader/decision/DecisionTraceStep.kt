package com.example.notificationreader.decision

enum class DecisionTraceResult {
    MATCHED,
    NOT_MATCHED,
    ALLOWED,
    BLOCKED,
    SKIPPED
}

data class DecisionTraceStep(
    val ruleId: DecisionRuleId,
    val name: String,
    val result: DecisionTraceResult,
    val explanation: String,
    val metadata: Map<String, String> = emptyMap()
)

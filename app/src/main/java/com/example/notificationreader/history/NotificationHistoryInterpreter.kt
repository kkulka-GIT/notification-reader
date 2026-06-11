package com.example.notificationreader.history

enum class NotificationHistoryInterpretationAction {
    IGNORE,
    SKIPPED,
    CREATE,
    UPDATE
}

data class NotificationHistoryInterpretation(
    val action: NotificationHistoryInterpretationAction,
    val reason: String,
    val explanation: String,
    val record: NotificationHistoryRecord? = null,
    val historyFingerprintHash: String? = null,
    val matchingRecordAgeMillis: Long? = null
) {
    val shouldWriteHistory: Boolean
        get() = record != null &&
            (
                action == NotificationHistoryInterpretationAction.CREATE ||
                    action == NotificationHistoryInterpretationAction.UPDATE
                )

    val shouldSpeak: Boolean
        get() = action != NotificationHistoryInterpretationAction.IGNORE
}

class NotificationHistoryInterpreter(
    duplicateWindowMillis: Long = DEFAULT_DUPLICATE_WINDOW_MILLIS
) {
    private val contentDuplicatePolicy = NotificationHistoryContentDuplicatePolicy(duplicateWindowMillis)

    @Synchronized
    fun interpret(event: RawNotificationEvent): NotificationHistoryInterpretation {
        if (event.packageName == SYSTEM_UI_PACKAGE_NAME) {
            return ignored(
                reason = "IGNORE_SYSTEM",
                explanation = "Android SystemUI notification is technical and excluded from user outputs"
            )
        }

        if (event.category == SERVICE_CATEGORY) {
            return ignored(
                reason = "IGNORE_SERVICE",
                explanation = "Android service notification is technical and excluded from user outputs"
            )
        }

        if (event.title.isEmpty() && event.text.isEmpty()) {
            return ignored(
                reason = "IGNORE_EMPTY",
                explanation = "Notification has no title or text to present to the user"
            )
        }

        val duplicateMatch = contentDuplicatePolicy.evaluate(event)
        if (duplicateMatch != null) {
            return NotificationHistoryInterpretation(
                action = NotificationHistoryInterpretationAction.SKIPPED,
                reason = "DUPLICATE_CONTENT_WITHIN_WINDOW",
                explanation = "Meaningful content from the same package was already stored within the history duplicate window",
                historyFingerprintHash = duplicateMatch.fingerprint.safeHash(),
                matchingRecordAgeMillis = duplicateMatch.ageMillis
            )
        }

        val storageKey = NotificationHistoryStorageKey.from(event)
        val record = event.toHistoryRecord(storageKey)
        return NotificationHistoryInterpretation(
            action = NotificationHistoryInterpretationAction.CREATE,
            reason = "MEANINGFUL_NOTIFICATION",
            explanation = "Meaningful notification is allowed for user outputs",
            record = record
        )
    }

    private fun ignored(reason: String, explanation: String): NotificationHistoryInterpretation {
        return NotificationHistoryInterpretation(
            action = NotificationHistoryInterpretationAction.IGNORE,
            reason = reason,
            explanation = explanation
        )
    }

    private fun RawNotificationEvent.toHistoryRecord(storageKey: String): NotificationHistoryRecord {
        return NotificationHistoryRecord(
            notificationKey = notificationKey,
            packageName = packageName,
            applicationName = applicationName,
            title = title,
            text = text,
            expandedText = expandedText,
            subText = subText,
            postedAt = postedAt,
            savedAt = receivedAt,
            hasImageOrLargeIcon = hasImageOrLargeIcon,
            storageKey = storageKey
        )
    }

    companion object {
        const val DEFAULT_DUPLICATE_WINDOW_MILLIS =
            NotificationHistoryContentDuplicatePolicy.DEFAULT_DUPLICATE_CONTENT_WINDOW_MILLIS
        private const val SYSTEM_UI_PACKAGE_NAME = "com.android.systemui"
        private const val SERVICE_CATEGORY = "service"
    }
}


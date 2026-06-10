package com.example.notificationreader

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationSpeechDuplicatePolicyTest {
    @Test
    fun identicalPackageAndContentWithinWindowSkipsSecondEvent() {
        val policy = NotificationSpeechDuplicatePolicy(duplicateFingerprintWindowMillis = 5_000L)
        val fingerprint = fingerprint(packageName = "com.example.mail", title = "Mail", text = "Hello")

        assertEquals(NotificationSpeechDuplicateDecision.Allow, policy.evaluate("key-1", fingerprint, nowMillis = 10_000L))
        assertEquals(
            NotificationSpeechDuplicateDecision.DuplicateFingerprintWithinWindow,
            policy.evaluate("key-2", fingerprint, nowMillis = 12_000L)
        )
    }

    @Test
    fun identicalPackageAndContentAfterWindowIsAllowed() {
        val policy = NotificationSpeechDuplicatePolicy(duplicateFingerprintWindowMillis = 5_000L)
        val fingerprint = fingerprint(packageName = "com.example.mail", title = "Mail", text = "Hello")

        assertEquals(NotificationSpeechDuplicateDecision.Allow, policy.evaluate("key-1", fingerprint, nowMillis = 10_000L))
        assertEquals(NotificationSpeechDuplicateDecision.Allow, policy.evaluate("key-2", fingerprint, nowMillis = 15_001L))
    }

    @Test
    fun differentContentWithinWindowIsAllowed() {
        val policy = NotificationSpeechDuplicatePolicy(duplicateFingerprintWindowMillis = 5_000L)

        assertEquals(
            NotificationSpeechDuplicateDecision.Allow,
            policy.evaluate("key-1", fingerprint(packageName = "com.example.mail", title = "Mail", text = "Hello"), nowMillis = 10_000L)
        )
        assertEquals(
            NotificationSpeechDuplicateDecision.Allow,
            policy.evaluate("key-2", fingerprint(packageName = "com.example.mail", title = "Mail", text = "Different"), nowMillis = 12_000L)
        )
    }

    @Test
    fun sameContentFromDifferentPackagesIsAllowed() {
        val policy = NotificationSpeechDuplicatePolicy(duplicateFingerprintWindowMillis = 5_000L)

        assertEquals(
            NotificationSpeechDuplicateDecision.Allow,
            policy.evaluate("key-1", fingerprint(packageName = "com.example.mail", title = "Mail", text = "Hello"), nowMillis = 10_000L)
        )
        assertEquals(
            NotificationSpeechDuplicateDecision.Allow,
            policy.evaluate("key-2", fingerprint(packageName = "com.other.mail", title = "Mail", text = "Hello"), nowMillis = 12_000L)
        )
    }

    @Test
    fun duplicateNotificationKeyStillSkipsEvent() {
        val policy = NotificationSpeechDuplicatePolicy(duplicateFingerprintWindowMillis = 5_000L)

        assertEquals(
            NotificationSpeechDuplicateDecision.Allow,
            policy.evaluate("same-key", fingerprint(packageName = "com.example.mail", title = "Mail", text = "Hello"), nowMillis = 10_000L)
        )
        assertEquals(
            NotificationSpeechDuplicateDecision.DuplicateNotificationKey,
            policy.evaluate("same-key", fingerprint(packageName = "com.example.mail", title = "Mail", text = "Different"), nowMillis = 16_000L)
        )
    }

    @Test
    fun fingerprintNormalizesWhitespaceAndEmptyValues() {
        assertEquals(
            "com.example.mail|Mail title|Body text||Inbox",
            fingerprint(
                packageName = "  com.example.mail  ",
                title = " Mail\n title ",
                text = "Body\t text",
                expandedText = " ",
                subText = " Inbox "
            ).toString()
        )
    }

    private fun fingerprint(
        packageName: String,
        title: String,
        text: String,
        expandedText: String = "",
        subText: String = ""
    ): NotificationSpeechFingerprint {
        return NotificationSpeechFingerprint(
            packageName = com.example.notificationreader.history.NotificationTextNormalizer.normalize(packageName),
            title = com.example.notificationreader.history.NotificationTextNormalizer.normalize(title),
            text = com.example.notificationreader.history.NotificationTextNormalizer.normalize(text),
            expandedText = com.example.notificationreader.history.NotificationTextNormalizer.normalize(expandedText),
            subText = com.example.notificationreader.history.NotificationTextNormalizer.normalize(subText)
        )
    }
}

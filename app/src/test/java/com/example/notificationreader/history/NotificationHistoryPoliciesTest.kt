package com.example.notificationreader.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHistoryPoliciesTest {
    @Test
    fun normalizeTrimsAndCollapsesWhitespace() {
        assertEquals("Zażółć gęślą jaźń", NotificationTextNormalizer.normalize("  Zażółć\n\tgęślą   jaźń  "))
        assertEquals("", NotificationTextNormalizer.normalize(null))
    }

    @Test
    fun applicationNameFallbackUsesLabelThenPackageThenUnknown() {
        assertEquals("Poczta", ApplicationNameResolver.fallbackName("  Poczta  ", "com.example.mail"))
        assertEquals("com.example.mail", ApplicationNameResolver.fallbackName("   ", " com.example.mail "))
        assertEquals("Nieznana aplikacja", ApplicationNameResolver.fallbackName(null, "   "))
    }

    @Test
    fun duplicatePolicyRejectsRecentDuplicateAndAllowsEvictedKey() {
        val policy = NotificationHistoryDuplicatePolicy(maxRecentEntries = 2)

        assertTrue(policy.shouldStore("one"))
        assertFalse(policy.shouldStore("one"))
        assertTrue(policy.shouldStore("two"))
        assertTrue(policy.shouldStore("three"))
        assertTrue(policy.shouldStore("one"))
    }

    @Test
    fun storageKeyUsesNotificationKeyWhenAvailable() {
        val key = NotificationHistoryStorageKey.from(
            NotificationHistoryCandidate(
                notificationKey = "abc",
                packageName = "pkg",
                title = "title",
                text = "text",
                expandedText = "big",
                subText = "sub",
                postedAt = 12L
            )
        )

        assertEquals("key:abc", key)
    }

    @Test
    fun storageKeyFallsBackToNotificationFields() {
        val key = NotificationHistoryStorageKey.from(
            NotificationHistoryCandidate(
                notificationKey = "",
                packageName = "pkg",
                title = "title",
                text = "text",
                expandedText = "big",
                subText = "sub",
                postedAt = 12L
            )
        )

        assertEquals("fallback|pkg|12|title|text|big|sub", key)
    }

    @Test
    fun limitPolicyDeletesOldestIdsAfterNewestRecords() {
        assertEquals(emptyList<Long>(), NotificationHistoryLimitPolicy.idsToDelete(listOf(3L, 2L, 1L), maxRecords = 3))
        assertEquals(listOf(1L), NotificationHistoryLimitPolicy.idsToDelete(listOf(4L, 3L, 2L, 1L), maxRecords = 3))
    }
}

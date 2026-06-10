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
    fun storageKeyUsesMeaningfulNotificationFields() {
        val key = NotificationHistoryStorageKey.from(
            NotificationHistoryCandidate(
                packageName = "pkg",
                title = "title",
                text = "text",
                expandedText = "big",
                subText = "sub",
                postedAt = 12L
            )
        )

        assertEquals("event|pkg|12|title|text|big|sub", key)
    }

    @Test
    fun storageKeyDoesNotUseAndroidNotificationKey() {
        val key = NotificationHistoryStorageKey.from(
            NotificationHistoryCandidate(
                packageName = "pkg",
                title = "title",
                text = "text",
                expandedText = "big",
                subText = "sub",
                postedAt = 12L
            )
        )

        assertEquals("event|pkg|12|title|text|big|sub", key)
    }

    @Test
    fun limitPolicyDeletesOldestIdsAfterNewestRecords() {
        assertEquals(emptyList<Long>(), NotificationHistoryLimitPolicy.idsToDelete(listOf(3L, 2L, 1L), maxRecords = 3))
        assertEquals(listOf(1L), NotificationHistoryLimitPolicy.idsToDelete(listOf(4L, 3L, 2L, 1L), maxRecords = 3))
    }
    @Test
    fun upsertPolicySavesNewStorageKey() {
        assertEquals(
            NotificationHistoryUpsertAction.SAVED,
            NotificationHistoryUpsertPolicy.actionFor(existing = null, incoming = record(storageKey = "key:one"))
        )
    }

    @Test
    fun upsertPolicyLeavesSameStorageKeyAndSameContentUnchanged() {
        val existing = record(id = 1L, storageKey = "key:one")
        val incoming = record(
            id = 0L,
            notificationKey = "new-notification-key",
            packageName = "new.package",
            applicationName = "New app name",
            postedAt = 200L,
            savedAt = 300L,
            storageKey = "key:one"
        )

        assertEquals(NotificationHistoryUpsertAction.UNCHANGED, NotificationHistoryUpsertPolicy.actionFor(existing, incoming))
    }

    @Test
    fun upsertPolicyUpdatesSameStorageKeyWhenTitleChanges() {
        assertEquals(
            NotificationHistoryUpsertAction.UPDATED,
            NotificationHistoryUpsertPolicy.actionFor(
                record(storageKey = "key:one", title = "Old title"),
                record(storageKey = "key:one", title = "New title")
            )
        )
    }

    @Test
    fun upsertPolicyUpdatesSameStorageKeyWhenTextChanges() {
        assertEquals(
            NotificationHistoryUpsertAction.UPDATED,
            NotificationHistoryUpsertPolicy.actionFor(
                record(storageKey = "key:one", text = "Old text"),
                record(storageKey = "key:one", text = "New text")
            )
        )
    }

    @Test
    fun upsertPolicyUpdatesSameStorageKeyWhenExpandedTextChanges() {
        assertEquals(
            NotificationHistoryUpsertAction.UPDATED,
            NotificationHistoryUpsertPolicy.actionFor(
                record(storageKey = "key:one", expandedText = "Old expanded text"),
                record(storageKey = "key:one", expandedText = "New expanded text")
            )
        )
    }

    @Test
    fun upsertPolicyUpdatesSameStorageKeyWhenSubTextChanges() {
        assertEquals(
            NotificationHistoryUpsertAction.UPDATED,
            NotificationHistoryUpsertPolicy.actionFor(
                record(storageKey = "key:one", subText = "Old sub text"),
                record(storageKey = "key:one", subText = "New sub text")
            )
        )
    }

    @Test
    fun upsertPolicyUpdatesSameStorageKeyWhenImagePresenceChanges() {
        assertEquals(
            NotificationHistoryUpsertAction.UPDATED,
            NotificationHistoryUpsertPolicy.actionFor(
                record(storageKey = "key:one", hasImageOrLargeIcon = false),
                record(storageKey = "key:one", hasImageOrLargeIcon = true)
            )
        )
    }

    @Test
    fun upsertPolicyPreservesSingleLogicalRowForSameStorageKeyUpdate() {
        val existing = record(id = 7L, storageKey = "key:one", text = "Old text")
        val incoming = record(storageKey = "key:one", text = "New text")
        val records = if (NotificationHistoryUpsertPolicy.actionFor(existing, incoming) == NotificationHistoryUpsertAction.UPDATED) {
            listOf(incoming.copy(id = existing.id))
        } else {
            listOf(existing, incoming)
        }

        assertEquals(1, records.size)
        assertEquals(7L, records.single().id)
        assertEquals("New text", records.single().text)
    }

    private fun record(
        id: Long = 0L,
        notificationKey: String = "notification-key",
        packageName: String = "com.example",
        applicationName: String = "Example",
        title: String = "Title",
        text: String = "Text",
        expandedText: String = "Expanded",
        subText: String = "Sub",
        postedAt: Long = 100L,
        savedAt: Long = 101L,
        hasImageOrLargeIcon: Boolean = false,
        storageKey: String = "key:notification-key"
    ): NotificationHistoryRecord {
        return NotificationHistoryRecord(
            id = id,
            notificationKey = notificationKey,
            packageName = packageName,
            applicationName = applicationName,
            title = title,
            text = text,
            expandedText = expandedText,
            subText = subText,
            postedAt = postedAt,
            savedAt = savedAt,
            hasImageOrLargeIcon = hasImageOrLargeIcon,
            storageKey = storageKey
        )
    }
}

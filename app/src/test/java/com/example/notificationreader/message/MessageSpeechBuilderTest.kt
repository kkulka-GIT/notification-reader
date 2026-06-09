package com.example.notificationreader.message

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSpeechBuilderTest {
    @Test
    fun senderAndContentAvailableUsesFullVariant() {
        val message = SemanticMessageNotification(
            packageName = "com.example.messages",
            sender = "Anna",
            content = "Cześć"
        )

        assertEquals(MessageSpeechVariant.FULL, MessageSpeechBuilder.variantFor(message))
        assertEquals("Anna. Cześć", MessageSpeechBuilder.build(message))
    }

    @Test
    fun onlySenderAvailableUsesSenderOnlyVariant() {
        val message = SemanticMessageNotification(
            packageName = "com.example.messages",
            sender = "Anna",
            content = null
        )

        assertEquals(MessageSpeechVariant.SENDER_ONLY, MessageSpeechBuilder.variantFor(message))
        assertEquals("Nowa wiadomość od: Anna", MessageSpeechBuilder.build(message))
    }

    @Test
    fun senderAndContentMissingUsesMinimalVariant() {
        val message = SemanticMessageNotification(
            packageName = "com.example.messages",
            sender = null,
            content = null
        )

        assertEquals(MessageSpeechVariant.MINIMAL, MessageSpeechBuilder.variantFor(message))
        assertEquals("Nowa wiadomość", MessageSpeechBuilder.build(message))
    }

    @Test
    fun contentWithoutSenderUsesMinimalVariantAndDoesNotSpeakContent() {
        val message = SemanticMessageNotification(
            packageName = "com.example.messages",
            sender = null,
            content = "Prywatna treść"
        )

        val output = MessageSpeechBuilder.build(message)

        assertEquals(MessageSpeechVariant.MINIMAL, MessageSpeechBuilder.variantFor(message))
        assertEquals("Nowa wiadomość", output)
        assertFalse(output.contains("Prywatna treść"))
    }

    @Test
    fun emptyAndWhitespaceOnlyStringsAreMissing() {
        val message = SemanticMessageNotification(
            packageName = "com.example.messages",
            sender = "   ",
            content = "\n\t"
        )

        assertEquals(MessageSpeechVariant.MINIMAL, MessageSpeechBuilder.variantFor(message))
        assertEquals("Nowa wiadomość", MessageSpeechBuilder.build(message))
    }

    @Test
    fun generatedOutputNeverContainsNullAndIsNeverBlank() {
        val messages = listOf(
            SemanticMessageNotification("pkg", "Anna", "Cześć"),
            SemanticMessageNotification("pkg", "Anna", null),
            SemanticMessageNotification("pkg", null, null),
            SemanticMessageNotification("pkg", null, "Prywatna treść"),
            SemanticMessageNotification("pkg", "   ", "\n\t")
        )

        messages.forEach { message ->
            val output = MessageSpeechBuilder.build(message)
            assertFalse(output.contains("null"))
            assertTrue(output.isNotBlank())
        }
    }

    @Test
    fun generatedOutputCollapsesDuplicatedSpaces() {
        val message = SemanticMessageNotification(
            packageName = "com.example.messages",
            sender = " Anna   Kowalska ",
            content = " Cześć   teraz "
        )

        assertEquals("Anna Kowalska. Cześć teraz", MessageSpeechBuilder.build(message))
    }
}

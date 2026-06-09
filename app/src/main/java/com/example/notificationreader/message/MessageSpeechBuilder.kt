package com.example.notificationreader.message

object MessageSpeechBuilder {
    fun variantFor(message: SemanticMessageNotification): MessageSpeechVariant {
        val sender = clean(message.sender)
        val content = clean(message.content)
        return when {
            sender != null && content != null -> MessageSpeechVariant.FULL
            sender != null -> MessageSpeechVariant.SENDER_ONLY
            else -> MessageSpeechVariant.MINIMAL
        }
    }

    fun build(message: SemanticMessageNotification): String {
        val sender = clean(message.sender)
        val content = clean(message.content)
        return when {
            sender != null && content != null -> "$sender. $content"
            sender != null -> "Nowa wiadomość od: $sender"
            else -> "Nowa wiadomość"
        }
    }

    fun clean(value: CharSequence?): String? {
        return value
            ?.toString()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}

package com.parishod.watomatic.model.utils

import com.parishod.watomatic.model.App

object Constants {
    const val PERMISSION_DIALOG_TITLE = "permission_dialog_title"
    const val PERMISSION_DIALOG_MSG = "permission_dialog_msg"
    const val PERMISSION_DIALOG_DENIED_TITLE = "permission_dialog_denied_title"
    const val PERMISSION_DIALOG_DENIED_MSG = "permission_dialog_denied_msg"
    const val PERMISSION_DIALOG_DENIED = "permission_dialog_denied"
    const val LOGS_DB_NAME = "logs_messages_db"
    const val NOTIFICATION_CHANNEL_ID = "watomatic"
    const val NOTIFICATION_CHANNEL_NAME = "Default"
    const val BITCOIN_ADDRESS = "bc1qv6zmgw845pktq9jr5qztup80qufu0yg46ur2kw"
    const val btcUrl = "https://www.blockchain.com/btc/address/";
    const val libraPayUrl = "https://liberapay.com/dk"
    const val paypalUrl = "https://www.paypal.com/paypalme/deek"

    // Forceful role-play prompt. GPT-4o-mini consistently sneaks in
    // "as an AI assistant" / "I'm here to help" disclaimers even with mild
    // instructions, so this prompt explicitly bans those phrases and gives
    // the model a worked example of the right tone.
    const val DEFAULT_LLM_PROMPT =
        "You are the device owner. You are NOT an AI, NOT an assistant, NOT a " +
        "chatbot, NOT a language model. Reply to this chat message in 1-2 SHORT " +
        "sentences as if you typed it yourself on your phone. Be warm and casual. " +
        "If asked to do something now, decline politely (e.g. 'sorry, tied up — " +
        "rain check?'). If you don't know a personal detail, say 'I'll get back " +
        "to you on that soon'.\n\n" +
        "STRICT RULES:\n" +
        "- NEVER say the words: AI, assistant, chatbot, language model, model, " +
        "AI assistant, virtual, here to help, here to assist, as a, I am here.\n" +
        "- NEVER mention your profession in the reply. Profile info is context " +
        "only — do not quote it back.\n" +
        "- NEVER add disclaimers, qualifiers, or explanations of what you can " +
        "or cannot do. Just reply.\n" +
        "- NEVER use signoffs longer than two words.\n\n" +
        "Example. Message: 'want to grab dinner tonight?'  " +
        "Reply: 'Hey! Tied up tonight — rain check tomorrow?'"
    const val DEFAULT_LLM_MODEL = "gpt-4o-mini"

    enum class EnabledAppsDisplayType {
        VERTICAL,
        HORIZONTAL
    }

    /**
     * Set of apps this app can auto reply
     */
    @JvmField
    val SUPPORTED_APPS: Set<App> = setOf(
        App("WhatsApp", "com.whatsapp"),
        App("Facebook Messenger", "com.facebook.orca"),
        App("Facebook Messenger Lite", "com.facebook.mlite"),
        App("Telegram", "org.telegram.messenger"),
        App("LinkedIn", "com.linkedin.android", isExperimental = true),
    )

    const val MIN_DAYS = 0
    const val MAX_DAYS = 30
    const val MIN_REPLIES_TO_ASK_APP_RATING = 5
    const val EMAIL_ADDRESS = "watomatic@deekshith.in"
    const val EMAIL_SUBJECT = "Watomatic-Feedback"
    const val TELEGRAM_URL = "tg://resolve?domain=watomatic"

    val PROVIDER_URLS = mapOf(
        "OpenAI" to "https://api.openai.com/",
        "Claude" to "https://api.anthropic.com/",
        "Grok" to "https://api.x.ai/",
        "Gemini" to "https://generativelanguage.googleapis.com/",
        "DeepSeek" to "https://api.deepseek.com/",
        "Mistral" to "https://api.mistral.ai/"
    )
}

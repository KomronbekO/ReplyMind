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

    // Strong role-play prompt. The instruction "respond IN FIRST PERSON as the
    // owner" plus "Do NOT say you are an AI" is repeated because gpt-4o-mini
    // and similar models will otherwise default to ChatGPT-style disclaimers
    // ("As an AI, I don't experience days…") that ruin the auto-reply feel.
    const val DEFAULT_LLM_PROMPT =
        "You are roleplaying as the device owner replying to a chat message while " +
        "they are unavailable. Respond IN FIRST PERSON as the owner, in 1-2 short " +
        "sentences. Be warm, natural and casual. Decline plans politely if asked " +
        "(e.g. 'sorry, can't tonight — rain check?'). " +
        "Do NOT say you are an AI, a chatbot, an assistant, or that you can't experience things. " +
        "Do NOT begin with 'As an AI'. Do NOT add disclaimers. " +
        "If you don't know something personal, say 'will get back to you on that soon'."
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

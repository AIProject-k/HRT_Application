package com.hormonelog.app.chat

/** Transient state for the optional on-device model installer; never stored in medical backups. */
enum class ChatSetupStage { INTRO, DOWNLOADING, READY, ERROR }

data class ChatSetupUi(
    val visible: Boolean,
    val stage: ChatSetupStage = ChatSetupStage.INTRO,
    val message: String = "",
    val busy: Boolean = false,
)

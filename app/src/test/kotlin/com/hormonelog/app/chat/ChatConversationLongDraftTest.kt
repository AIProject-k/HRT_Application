package com.hormonelog.app.chat

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A draft longer than 8000 characters is kept by the composer, so the saved conversation must load it back. */
class ChatConversationLongDraftTest {
    @Test fun longDraftAndTranscriptSurviveAReload() {
        val file = File.createTempFile("conversation", ".json")
        val draft = "가".repeat(9000)
        ChatConversationStore(file).save(ChatConversation(lines = listOf(ChatLine(true, "안녕")), input = draft))

        val reader = ChatConversationStore(file)
        val loaded = reader.load()

        assertEquals(1, loaded.lines.size)
        assertEquals(draft, loaded.input)
        assertTrue("the transcript must not be moved aside", file.exists())
        assertEquals(null, reader.warning)
    }
}

package com.hormonelog.core.data

import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.ChatModel
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatModelSettingsTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun eachSelectionSurvivesRestartAndFullBackup() {
        val file = temp.newFile("settings.json")
        for (model in ChatModel.entries) {
            val settings = AppSettings(chatModel = model)
            SettingsStore(file).save(settings)
            assertEquals(model, SettingsStore(file).load()!!.chatModel)
            val restored = BackupCodec.open(BackupCodec.create(RecordSnapshot(), settings, 0)) as BackupCodec.Opened.Ok
            assertEquals(model, restored.settings!!.chatModel)
        }
    }

    @Test fun oldAndUnknownSettingsKeepHaikuDefault() {
        assertEquals(ChatModel.HAIKU, SettingsStore.fromJson(JSONObject()).chatModel)
        assertEquals(ChatModel.HAIKU, SettingsStore.fromJson(JSONObject().put("chatModel", "future")).chatModel)
    }
}

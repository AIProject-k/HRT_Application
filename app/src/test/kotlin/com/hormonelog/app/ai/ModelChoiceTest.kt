package com.hormonelog.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelChoiceTest {
    @Test fun builtInModelStaysTheDefaultAndItsPinIsUnchanged() {
        assertEquals(ModelSpec.QWEN, ModelSpec.chosen(null))
        assertEquals(ModelSpec.QWEN, ModelSpec.chosen("not-a-model.litertlm"))
        assertEquals("Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm", ModelSpec.QWEN.fileName)
        assertEquals(344671744L, ModelSpec.QWEN.size)
        assertEquals("03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856", ModelSpec.QWEN.sha256)
    }

    @Test fun pickedGemmaIsPinnedToACommitAndAHash() {
        assertEquals(ModelSpec.GEMMA, ModelSpec.chosen("gemma-4-E2B-it.litertlm"))
        assertTrue(ModelSpec.GEMMA.url.contains("/resolve/1caa527c1e88b55f046fcd8d2b4265606680663d/"))
        assertEquals(64, ModelSpec.GEMMA.sha256.length)
        assertEquals(2588147712L, ModelSpec.GEMMA.size)
    }
}

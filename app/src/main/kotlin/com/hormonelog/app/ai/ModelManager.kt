package com.hormonelog.app.ai

import java.io.File
import java.security.MessageDigest

data class ModelSpec(val fileName: String, val url: String, val size: Long, val sha256: String) {
    companion object {
        val QWEN=ModelSpec("Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/e6687177e00897731e7716267eebd4eba0dde7c2/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
            344671744L,"03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856")
    }
}

/** Private, no-backup storage. Only a complete hash-verified file becomes the installed model. */
class ModelManager(val directory: File, val spec: ModelSpec=ModelSpec.QWEN) {
    val model=File(directory,spec.fileName)
    val partial=File(directory,spec.fileName+".part")
    fun isInstalled(): Boolean = model.isFile && valid(model)
    private fun valid(file: File): Boolean {
        if (file.length()!=spec.size) return false
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer=ByteArray(128*1024)
            while (true) { val n=input.read(buffer); if(n<0) break; digest.update(buffer,0,n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }==spec.sha256
    }
    fun finishInstall(cancelled: ()->Boolean = { false }) {
        require(valid(partial)) { "모델 크기 또는 SHA-256이 일치하지 않아요. 다시 설치해 주세요." }
        if(cancelled()) throw java.util.concurrent.CancellationException()
        check(partial.renameTo(model)) { "모델 설치 파일을 이동하지 못했어요." }
    }
    fun delete() {
        for (file in listOf(model,partial)) check(!file.exists() || file.delete()) { "모델 파일을 지우지 못했어요." }
    }
}

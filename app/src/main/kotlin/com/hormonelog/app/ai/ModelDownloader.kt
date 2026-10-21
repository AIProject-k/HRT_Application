package com.hormonelog.app.ai

import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CancellationException

/** One pinned HTTPS artifact, resumable partial file, verify before atomic installation. */
class ModelDownloader(private val manager: ModelManager,
    private val open: (String)->HttpURLConnection = { URL(it).openConnection() as HttpURLConnection }) {
    @Volatile private var connection: HttpURLConnection?=null
    @Volatile private var cancelled=false
    fun cancel() { cancelled=true; connection?.disconnect() }
    fun reset() { cancelled=false }
    fun download(progress: (Long)->Unit = {}) {
        manager.directory.mkdirs()
        if (manager.partial.length()>manager.spec.size) check(manager.partial.delete())
        val offset=manager.partial.length()
        check(manager.directory.usableSpace > manager.spec.size-offset+64*1024*1024) { "모델 설치에 필요한 저장 공간이 부족해요." }
        if (offset < manager.spec.size) {
            val c=open(manager.spec.url)
            connection=c
            c.connectTimeout=15_000; c.readTimeout=20_000
            c.setRequestProperty("Accept-Encoding","identity")
            if (offset>0) c.setRequestProperty("Range","bytes=$offset-")
            try {
                if (cancelled) throw CancellationException()
                val status=c.responseCode
                check(status==200 || status==206) { "모델 다운로드 실패 (HTTP $status). 다시 시도해 주세요." }
                val resume=status==206
                if (resume) {
                    val range=Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(c.getHeaderField("Content-Range").orEmpty())
                        ?: error("이어받기 응답의 범위를 확인할 수 없어요.")
                    check(range.groupValues[1].toLong()==offset && range.groupValues[3].toLong()==manager.spec.size &&
                        range.groupValues[2].toLong() in offset until manager.spec.size) { "이어받기 범위가 일치하지 않아요." }
                }
                var count=if(resume) offset else 0L
                c.inputStream.buffered().use { input -> FileOutputStream(manager.partial,resume).use { output ->
                    val buffer=ByteArray(128*1024)
                    var reported=count
                    while (true) {
                        if (cancelled) throw CancellationException()
                        val n=input.read(buffer)
                        if(n<0) break
                        check(count+n<=manager.spec.size) { "모델 파일 크기가 예상보다 커요." }
                        output.write(buffer,0,n); count+=n
                        if(count-reported>=1024*1024) { progress(count); reported=count }
                    }
                    output.fd.sync()
                } }
                progress(count)
                check(count==manager.spec.size) { "다운로드가 중간에 끊겼어요. 다시 누르면 이어받아요." }
            } finally { c.disconnect(); connection=null }
        }
        if (cancelled) throw CancellationException()
        try { manager.finishInstall { cancelled } }
        catch(e: IllegalArgumentException) { manager.partial.delete(); throw e }
    }
}

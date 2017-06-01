package web

import recognizer.RecognitionResult
import recognizer.Status
import recognizer.recognize
import spark.Request
import spark.Response
import spark.Service.ignite
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

//0.0.0.0:4567
fun main(args: Array<String>) {
    val http = ignite()

    val tessPath = "tesseract"
    val convertPath = "convert"

    val recognitionService = RecognitionWebService(tessPath, convertPath)

    http.get("/hello") { _, _ -> "Hello World v.128" }
    http.post("/ocr", { request: Request, response: Response ->
        val type = request.headers("Content-Type")
        println("Type: $type")
        val ext = type.substringAfter("/")
        val fileName = "image.$ext"
        println("fileName: " + fileName)

        val result = recognitionService.ocr(fileName, request.bodyAsBytes())

        response.status(if (result.status == Status.OK) 200 else 500)
        result.text
    })
}


class RecognitionWebService(val tessPath: String,
                            val convertPath: String) {
    private val counter = AtomicInteger()

    private fun uniqueDirName(): File {
        val uniqueDirName = counter.incrementAndGet().toString()
        val dir = File(uniqueDirName)
        if (dir.exists()) {
            dir.deleteRecursively()
        }
        dir.mkdir()
        return dir
    }

    fun ocr(fileName: String, body: ByteArray): RecognitionResult {
        val dirName = uniqueDirName()
        val filename = dirName.absolutePath + "/$fileName"
        File(filename).writeBytes(body)

        val result: RecognitionResult
        try {
            result = recognize(filename, tessPath, convertPath)
        } finally {
            dirName.deleteRecursively()
        }
        return result
    }

}



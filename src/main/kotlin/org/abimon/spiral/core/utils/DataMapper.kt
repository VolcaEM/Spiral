package org.abimon.spiral.core.utils

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

object DataMapper {
    var byteArrayToMap: (ByteArray) -> Map<String, Any?>? = { streamToMap(ByteArrayInputStream(it)) }
    var stringToMap: (String) -> Map<String, Any?>? = { streamToMap(ByteArrayInputStream(it.toByteArray())) }
    var fileToMap: (File) -> Map<String, Any?>? = { streamToMap(FileInputStream(it)) }
    lateinit var streamToMap: (InputStream) -> Map<String, Any?>?

    fun readMapFromByteArray(byteArray: ByteArray): Map<String, Any?>? = byteArrayToMap(byteArray)
    fun readMapFromString(string: String): Map<String, Any?>? = stringToMap(string)
    fun readMapFromFile(file: File): Map<String, Any?>? = fileToMap(file)
    fun readMapFromStream(stream: InputStream): Map<String, Any?>? = streamToMap(stream)

    fun shouldReadMap(): Boolean = this::streamToMap.isInitialized
}
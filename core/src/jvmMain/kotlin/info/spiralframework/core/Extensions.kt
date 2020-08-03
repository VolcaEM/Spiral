package info.spiralframework.core

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.ResponseResultOf
import com.github.kittinunf.fuel.core.isSuccessful
import info.spiralframework.base.common.SpiralContext
import info.spiralframework.base.common.events.*
import info.spiralframework.base.jvm.outOrElseGet
import info.spiralframework.core.formats.ReadableSpiralFormat
import info.spiralframework.core.formats.compression.*
import dev.brella.kornea.io.common.DataSource
import org.yaml.snakeyaml.error.YAMLException
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Executes the given [block] function on this resource and then closes it down correctly whether an exception
 * is thrown or not.
 *
 * @param block a function to process this [Closeable] resource.
 * @return the result of [block] function invoked on this resource.
 */
public inline fun <T : Closeable?, R> (() -> T).use(block: (T) -> R): R {
    var exception: Throwable? = null
    val stream = this()
    try {
        return block(stream)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        when {
            stream == null -> {
            }
            exception == null -> stream.close()
            else ->
                try {
                    stream.close()
                } catch (closeException: Throwable) {
                    exception.addSuppressed(closeException)
                }
        }
    }
}

val COMPRESSION_FORMATS = arrayOf(CrilaylaCompressionFormat, DRVitaFormat, SpcCompressionFormat, DRv3CompressionFormat)

suspend fun SpiralContext.decompress(dataSource: DataSource<*>): Pair<DataSource<*>, List<ReadableSpiralFormat<DataSource<*>>>> {
    val (format, result) = COMPRESSION_FORMATS.map { format -> format to format.identify(source = dataSource, context = this) }
            .filter { pair -> pair.second.didSucceed }
            .minBy { pair -> pair.second.chance }
            ?: return dataSource to emptyList()

    val (decompressed, list) = decompress(result.obj.outOrElseGet {
        @Suppress("UNCHECKED_CAST")
        (result.format as ReadableSpiralFormat<DataSource<*>>).read(source = dataSource, context = this).obj
    })

    return decompressed to mutableListOf(format).apply { addAll(list) }
}

fun Request.userAgent(ua: String = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.14; rv:64.0) Gecko/20100101 Firefox/64.0"): Request = this.header("User-Agent", ua)

inline fun <reified T : Any> ObjectMapper.tryReadValue(src: ByteArray): T? {
    try {
        return this.readValue(src)
    } catch (jsonProcessing: JsonProcessingException) {
    } catch (jsonMapping: JsonMappingException) {
    } catch (jsonParsing: JsonParseException) {
    } catch (yamlParsing: YAMLException) {
    }

    return null
}

inline fun <reified T : Any> ObjectMapper.tryReadValue(src: InputStream): T? {
    try {
        return this.readValue(src)
    } catch (jsonProcessing: JsonProcessingException) {
    } catch (jsonMapping: JsonMappingException) {
    } catch (jsonParsing: JsonParseException) {
    } catch (yamlParsing: YAMLException) {
    }

    return null
}

inline fun <reified T : Any> ObjectMapper.tryReadValue(src: File): T? {
    try {
        return this.readValue(src)
    } catch (jsonProcessing: JsonProcessingException) {
    } catch (jsonMapping: JsonMappingException) {
    } catch (jsonParsing: JsonParseException) {
    } catch (io: FileNotFoundException) {
    } catch (yamlParsing: YAMLException) {
    }

    return null
}

fun <T : Any> ResponseResultOf<T>.takeResponseIfSuccessful(): Response? {
    val (_, response, result) = this

    if (response.isSuccessful)
        return response
    return null
}

fun <T : Any> ResponseResultOf<T>.takeIfSuccessful(): T? {
    val (_, response, result) = this

    if (response.isSuccessful)
        return result.get()
    return null
}

suspend fun <T : CancellableSpiralEvent> SpiralEventBus.postCancellable(context: SpiralContext, event: T): Boolean {
    context.post(event)

    return event.cancelled
}

fun <T> T.identifySelf(): T = this

fun <T : SpiralEventBus> T.installLoggingSubscriber(): T {
    register("Logging", SpiralEventPriority.HIGHEST) { event: SpiralEvent -> trace("core.eventbus.logging.event", event) }
    return this
}
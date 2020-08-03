package info.spiralframework.core.formats

import info.spiralframework.base.common.SpiralContext
import dev.brella.kornea.io.common.DataSource
import dev.brella.kornea.io.common.flow.OutputFlow
import java.io.OutputStream
import java.util.*

interface SpiralFormat {
    /** A **RECOGNISABLE** name, not necessarily the full name. May commonly be the extension */
    val name: String

    /**
     * The usual extension for this format. Some formats don't have a proper extension, so this can be nullable
     */
    val extension: String?

    companion object {
        const val DEFAULT_EXTENSION = "dat"
    }
}

/**
 * A Spiral format that supports reading from a source
 */
interface ReadableSpiralFormat<T>: SpiralFormat {
    /**
     * Specifies a preferred conversion format for files that match this format.
     * This is used primarily for Danganronpa formats to specify we should convert to a nicer, more usable format.
     * It should **not** be used in contexts where there is ambiguity about what format may be desired; thus, it should not be defined for regular formats to Danganronpa formats in mots cases.
     */
    fun preferredConversionFormat(): WritableSpiralFormat? = null

    /**
     * Should we attempt to automatically identify this file?
     * Return false for text based formats in particular
     */
    fun shouldAutoIdentify(): Boolean = true

    /**
     * Attempts to identify the data source as an instance of [T]
     *
     * Formats are recommended to override this where possible.
     *
     * @param readContext Reading context for this data
     * @param source A function that returns an input stream
     *
     * @return A FormatResult containing either an optional with the value [T] or null, if the stream does not seem to match an object of type [T]
     */
    suspend fun identify(context: SpiralContext, readContext: FormatReadContext? = null, source: DataSource<*>): FormatResult<Optional<T>>
        = read(context, readContext, source).map { Optional.of(it) }

    /**
     * Attempts to read the data source as [T]
     *
     * @param readContext Reading context for this data
     * @param source A function that returns an input stream
     *
     * @return a FormatResult containing either [T] or null, if the stream does not contain the data to form an object of type [T]
     */
    suspend fun read(context: SpiralContext, readContext: FormatReadContext? = null, source: DataSource<*>): FormatResult<T>
}

/**
 * A Spiral format that supports writing to a stream
 */
interface WritableSpiralFormat: SpiralFormat {
    /**
     * Does this format support writing [data]?
     *
     * @return If we are able to write [data] as this format
     */
    fun supportsWriting(context: SpiralContext, writeContext: FormatWriteContext?, data: Any): Boolean

    /**
     * Writes [data] to [stream] in this format
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param dataContext Context that we retrieved this file in
     * @param data The data to wrote
     * @param stream The stream to write to
     *
     * @return An enum for the success of the operation
     */
    suspend fun write(context: SpiralContext, writeContext: FormatWriteContext?, data: Any, flow: OutputFlow): FormatWriteResponse
}

sealed class FormatWriteResponse {
    object SUCCESS: FormatWriteResponse()
    object WRONG_FORMAT: FormatWriteResponse()

    //TODO: Replace this with a result
    class FAIL(val reason: Throwable): FormatWriteResponse() {
        constructor(context: SpiralContext): this(Throwable(context.localise("gurren.errors.no_reason")))
    }
}

fun <T, F: FormatResult<T>> F.withFormat(format: SpiralFormat?): F {
    this.nullableFormat = format
    return this
}
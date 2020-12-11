package info.spiralframework.core.formats.audio

import info.spiralframework.base.common.SpiralContext
import dev.brella.kornea.errors.common.KorneaResult
import dev.brella.kornea.errors.common.Optional
import dev.brella.kornea.io.common.DataSource
import dev.brella.kornea.io.common.flow.OutputFlow
import info.spiralframework.base.common.properties.SpiralProperties
import info.spiralframework.core.common.formats.FormatWriteResponse
import info.spiralframework.core.common.formats.ReadableSpiralFormat
import info.spiralframework.core.common.formats.WritableSpiralFormat
import java.io.File

open class SpiralAudioFormat(override val name: String, override val extension: String): ReadableSpiralFormat<File>, WritableSpiralFormat {
    open val needsMediaPlugin: Boolean = true

    override suspend fun identify(context: SpiralContext, readContext: SpiralProperties?, source: DataSource<*>): KorneaResult<Optional<File>> {
        try {
            return super.identify(context, readContext, source)
        } catch (ise: IllegalStateException) {
            return KorneaResult.WithException.of(ise)
        }
    }

    /**
     * Attempts to read the data source as [T]
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param context Context that we retrieved this file in
     * @param source A function that returns an input stream
     *
     * @return a FormatResult containing either [T] or null, if the stream does not contain the data to form an object of type [T]
     */
    override suspend fun read(context: SpiralContext, readContext: SpiralProperties?, source: DataSource<*>): KorneaResult<File> {
        throw IllegalStateException(context.localise("core.formats.no_audio_impl.read", this))
    }

    /**
     * Does this format support writing [data]?
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param context Context that we retrieved this file in
     *
     * @return If we are able to write [data] as this format
     */
    override fun supportsWriting(context: SpiralContext, writeContext: SpiralProperties?, data: Any): Boolean {
        throw IllegalStateException(context.localise("core.formats.no_audio_impl.support_write", this))
    }

    /**
     * Writes [data] to [stream] in this format
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param context Context that we retrieved this file in
     * @param data The data to wrote
     * @param stream The stream to write to
     *
     * @return An enum for the success of the operation
     */
    override suspend fun write(context: SpiralContext, writeContext: SpiralProperties?, data: Any, flow: OutputFlow): FormatWriteResponse {
        throw IllegalStateException(context.localise("core.formats.no_audio_impl.write", this))
    }
}
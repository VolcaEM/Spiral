package info.spiralframework.core.formats.compression

import com.soywiz.krypto.sha256
import info.spiralframework.base.common.SpiralContext
import info.spiralframework.base.common.io.cacheShortTerm
import info.spiralframework.base.common.toHexString
import info.spiralframework.core.formats.FormatReadContext
import info.spiralframework.core.formats.FormatResult
import info.spiralframework.core.formats.ReadableSpiralFormat
import info.spiralframework.formats.common.archives.SpcArchive
import info.spiralframework.formats.common.archives.SpcFileEntry
import info.spiralframework.formats.common.compression.SPC_COMPRESSION_MAGIC_NUMBER
import info.spiralframework.formats.common.compression.decompressSpcData
import info.spiralframework.formats.common.compression.decompressVita
import info.spiralframework.formats.common.games.DrGame
import dev.brella.kornea.errors.common.*
import dev.brella.kornea.io.common.*
import dev.brella.kornea.io.common.flow.PeekableInputFlow
import dev.brella.kornea.io.common.flow.readBytes
import java.util.*

data class SpcEntryFormatReadContextdata(val entry: SpcFileEntry?, override val name: String? = null, override val game: DrGame? = null) : FormatReadContext

object SpcCompressionFormat : ReadableSpiralFormat<DataSource<*>> {
    const val NOT_SPC_DATA = 0x1000
    const val NOT_COMPRESSED = 0x1001

    const val NOT_SPC_DATA_KEY = "formats.compression.spc.not_spc_data"
    const val NOT_COMPRESSED_KEY = "formats.compression.spc.not_compressed"

    override val name: String = "SPC Compression"
    override val extension: String = "cmp"

    override suspend fun identify(context: SpiralContext, readContext: FormatReadContext?, source: DataSource<*>): FormatResult<Optional<DataSource<*>>> {
        if (source.useInputFlow { flow -> flow.readInt32LE() == SPC_COMPRESSION_MAGIC_NUMBER }.getOrElse(false) || (readContext as? SpcEntryFormatReadContextdata)?.entry?.compressionFlag == SpcArchive.COMPRESSED_FLAG)
            return FormatResult.Success(Optional.empty(), 1.0)
        return FormatResult.Fail(1.0)
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
    override suspend fun read(context: SpiralContext, readContext: FormatReadContext?, source: DataSource<*>): FormatResult<DataSource<*>> {
        val data = source.useInputFlow { flow ->
            val entry = (readContext as? SpcEntryFormatReadContextdata)?.entry

            if (entry == null) {
                if (flow.readInt32LE() != SPC_COMPRESSION_MAGIC_NUMBER)
                    return@useInputFlow KorneaResult.errorAsIllegalArgument<ByteArray>(NOT_SPC_DATA, context.localise(NOT_SPC_DATA_KEY))
            } else if (entry.compressionFlag != SpcArchive.COMPRESSED_FLAG) {
                return@useInputFlow KorneaResult.errorAsIllegalArgument<ByteArray>(NOT_COMPRESSED, context.localise(NOT_COMPRESSED_KEY))
            }

            KorneaResult.success(flow.readBytes())
        }.flatten().getOrBreak { return FormatResult.Fail(this, 1.0, it) }

        val cache = context.cacheShortTerm(context, "spc:${data.sha256().toHexString()}")

        return cache.openOutputFlow()
            .flatMap { output ->
                @Suppress("DEPRECATION")
                decompressSpcData(data).map { data ->
                    output.write(data)
                    val result = FormatResult.Success<DataSource<*>>(this, cache, 1.0)
                    result.release.add(cache)

                    result
                }.doOnFailure {
                    cache.close()
                    output.close()
                }
            }.getOrElseRun {
                cache.close()

                decompressSpcData(data)
                    .map<ByteArray, FormatResult<DataSource<*>>> { decompressed -> FormatResult.Success(this, BinaryDataSource(decompressed), 1.0) }
                    .getOrElseTransform { failure -> FormatResult.Fail(this, 1.0, failure) }
            }
    }
}
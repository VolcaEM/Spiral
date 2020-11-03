package info.spiralframework.core.panels

import dev.brella.kornea.errors.common.Optional
import dev.brella.kornea.errors.common.filterNotNull
import dev.brella.kornea.errors.common.getOrBreak
import dev.brella.kornea.errors.common.getOrElseRun
import dev.brella.kornea.io.common.DataSource
import dev.brella.kornea.toolkit.common.use
import info.spiralframework.base.common.SpiralContext
import info.spiralframework.base.common.io.cache
import info.spiralframework.base.common.locale.constNull
import info.spiralframework.base.common.logging.trace
import info.spiralframework.core.ReadableCompressionFormat
import info.spiralframework.core.decompress
import info.spiralframework.core.formats.FormatReadContext
import info.spiralframework.core.formats.FormatResult
import info.spiralframework.core.formats.ReadableSpiralFormat
import info.spiralframework.core.mapResults
import info.spiralframework.core.sortedAgainst
import info.spiralframework.formats.common.archives.SpiralArchive

interface IdentifyCommand {
    val identifiableFormats: List<ReadableSpiralFormat<*>>

    suspend fun beginIdentification(context: SpiralContext, readContext: FormatReadContext, dataSource: DataSource<*>, formats: List<ReadableSpiralFormat<*>>)

    suspend fun noFormatFound(context: SpiralContext, readContext: FormatReadContext, dataSource: DataSource<*>)
    suspend fun foundFileFormat(context: SpiralContext, readContext: FormatReadContext, dataSource: DataSource<*>, result: FormatResult<Optional<*>, *>, compressionFormats: List<ReadableCompressionFormat>?)

    suspend fun finishIdentification(context: SpiralContext, readContext: FormatReadContext, dataSource: DataSource<*>)

    suspend operator fun invoke(context: SpiralContext, readContext: FormatReadContext, dataSource: DataSource<*>) {
        val (decompressedDataSource, archiveCompressionFormats) = if (dataSource.reproducibility.isUnreliable() || dataSource.reproducibility.isUnstable()) {
            dataSource.cache(context).use { ds -> context.decompress(ds) }
        } else {
            context.decompress(dataSource)
        }

        val result = performIdentification(context, readContext, dataSource, identifiableFormats.sortedAgainst(readContext)) { formats ->
            formats.mapResults { archive -> archive.identify(this, readContext, decompressedDataSource) }
                .filterIsInstance<FormatResult<Optional<*>, *>>()
                .sortedBy(FormatResult<*, *>::confidence)
                .asReversed()
                .firstOrNull()
        }

        if (result == null) {
            noFormatFound(context, readContext, dataSource)
            return
        }

        foundFileFormat(context, readContext, dataSource, result, archiveCompressionFormats)
    }
}

suspend inline fun <T> IdentifyCommand.performIdentification(context: SpiralContext, readContext: FormatReadContext, dataSource: DataSource<*>, formats: List<ReadableSpiralFormat<*>>, operation: SpiralContext.(formats: List<ReadableSpiralFormat<*>>) -> T): T {
    try {
        beginIdentification(context, readContext, dataSource, formats)
        return operation(context, formats)
    } finally {
        finishIdentification(context, readContext, dataSource)
    }
}
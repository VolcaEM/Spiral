package info.spiralframework.console.jvm.commands.pilot

import dev.brella.knolus.context.KnolusContext
import dev.brella.knolus.objectTypeParameter
import dev.brella.knolus.stringTypeParameter
import dev.brella.knolus.types.asString
import dev.brella.kornea.errors.common.*
import dev.brella.kornea.io.common.DataSource
import dev.brella.kornea.io.common.Uri
import dev.brella.kornea.io.common.flow.OutputFlow
import dev.brella.kornea.io.jvm.files.AsyncFileDataSource
import dev.brella.kornea.io.jvm.files.AsyncFileOutputFlow
import dev.brella.kornea.toolkit.common.use
import dev.brella.kornea.toolkit.coroutines.ascii.arbitraryProgressBar
import info.spiralframework.base.common.SpiralContext
import info.spiralframework.base.common.locale.printlnLocale
import info.spiralframework.base.common.properties.ISpiralProperty
import info.spiralframework.base.common.properties.SpiralProperties
import info.spiralframework.base.common.properties.populate
import info.spiralframework.console.jvm.commands.CommandRegistrar
import info.spiralframework.console.jvm.commands.shared.GurrenShared
import info.spiralframework.console.jvm.data.GurrenSpiralContext
import info.spiralframework.console.jvm.pipeline.DataSourceType
import info.spiralframework.console.jvm.pipeline.registerFunctionWithContextWithoutReturn
import info.spiralframework.console.jvm.pipeline.spiralContext
import info.spiralframework.core.common.formats.FormatResult
import info.spiralframework.core.common.formats.ReadableSpiralFormat
import info.spiralframework.core.common.formats.WritableSpiralFormat
import info.spiralframework.core.common.formats.filterIsIdentifyFormatResult
import info.spiralframework.core.mapResults
import info.spiralframework.core.sortedAgainst
import info.spiralframework.formats.common.archives.SpiralArchive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import kotlin.coroutines.CoroutineContext

class GurrenConvertPilot(val readableFormats: MutableList<ReadableSpiralFormat<Any>>, val writableFormats: MutableList<WritableSpiralFormat>) : CoroutineScope {
    companion object : CommandRegistrar {
        override suspend fun register(spiralContext: SpiralContext, knolusContext: KnolusContext) {
            with(knolusContext) {
                registerFunctionWithContextWithoutReturn(
                    "convert",
                    objectTypeParameter("file_path"),
                    stringTypeParameter("from").asOptional(),
                    stringTypeParameter("to").asOptional(),
                    stringTypeParameter("save_as").asOptional()
                ) { context, filePath, from, to, saveAs ->
                    val spiralContext = context.spiralContext().getOrBreak { return@registerFunctionWithContextWithoutReturn }

                    if (filePath is DataSourceType) {
                        filePath.inner.use { ds ->
                            GurrenConvertPilot(spiralContext, ds, GurrenPilot.formatContext.let { formatContext ->
                                formatContext.withOptional(ISpiralProperty.FileName, ds.locationAsUri().getOrNull()?.path)
                            }, from, to, saveAs)
                        }
                    } else {
                        filePath.asString(knolusContext).doOnSuccess { filePath ->
                            convertStub(spiralContext, filePath, from, to, saveAs)
                        }
                    }
                }
            }

            GurrenPilot.help("convert")
        }

        suspend fun convertStub(context: GurrenSpiralContext, filePath: String, from: KorneaResult<String>, to: KorneaResult<String>, saveAs: KorneaResult<String>) {
            val file = File(filePath)

            if (!file.exists()) {
                context.printlnLocale("error.file.does_not_exist", filePath)
                return
            }

            if (file.isDirectory) {
                // Directory was passed; this is a potential ambiguity, so don't do anything here
                context.printlnLocale("commands.pilot.convert.err_path_is_directory", filePath)
                return
            } else if (file.isFile) {
                return AsyncFileDataSource(file).use { ds -> GurrenConvertPilot(context, ds, GurrenPilot.formatContext.with(ISpiralProperty.FileName, file.name), from, to, saveAs) }
            } else {
                context.printlnLocale("commands.pilot.convert.err_path_not_file_or_directory", filePath)
                return
            }
        }

        suspend operator fun invoke(context: GurrenSpiralContext, dataSource: DataSource<*>, readContext: SpiralProperties, from: KorneaResult<String>, to: KorneaResult<String>, saveAs: KorneaResult<String>) =
            GurrenConvertPilot(GurrenShared.READABLE_FORMATS, GurrenShared.WRITABLE_FORMATS)(context, dataSource, readContext, from, to, saveAs.map { path ->
                Pair(AsyncFileOutputFlow(File(path)), GurrenPilot.formatContext.with(ISpiralProperty.FileName, path))
            })
    }

    override val coroutineContext: CoroutineContext = SupervisorJob()

    suspend fun noMatchingFormatName(formatName: String) {
        println("No matching format with name '$formatName'")
    }

    suspend fun formatDoesNotMatch(formatError: KorneaResult<*>, dataSource: DataSource<*>) {
        println("Format error: $formatError / $dataSource")
    }

    suspend operator fun invoke(context: GurrenSpiralContext, dataSource: DataSource<*>, readContext: SpiralProperties, from: KorneaResult<String>, to: KorneaResult<String>, saveAs: KorneaResult<Pair<OutputFlow, SpiralProperties>>) {
        val (readingResult, readFormatFail) = from.map { name ->
            readableFormats.firstOrNull { format -> format.name.equals(name, true) }
            ?: readableFormats.firstOrNull { format -> format.extension?.equals(name, true) == true }
            ?: return noMatchingFormatName(name)
        }.flatMap { format ->
            format.identify(context, source = dataSource)
        }.switchIfEmpty {
            readableFormats.sortedAgainst(readContext)
                .mapResults { archive -> archive.identify(context, readContext, dataSource) }
                .filterIsInstance<FormatResult<Optional<SpiralArchive>, SpiralArchive>>()
                .sortedBy(FormatResult<*, *>::confidence)
                .asReversed()
                .firstOrNull() ?: KorneaResult.empty()
        }.filterIsIdentifyFormatResult<Any>()

        if (readingResult == null) return formatDoesNotMatch(readFormatFail!!, dataSource)

        val writingFormat = to.map { name ->
            writableFormats.firstOrNull { format -> format.name.equals(name, true) }
            ?: writableFormats.firstOrNull { format -> format.extension?.equals(name, true) == true }
            ?: return noMatchingFormatName(name)
        }.switchIfEmpty {
            KorneaResult.successOrEmpty(readingResult.format().preferredConversionFormat())
        }.getOrBreak { failure -> return println("No writing format: $failure") }

        val readingData = KorneaResult.successOrEmpty(readingResult.get().getOrNull())
            .switchIfEmpty { readingResult.format().read(context, readContext, dataSource) }
            .getOrBreak { failure -> return println("Could not read ${readingResult.format().name}: $failure") }

        val (outputFlow, writeContext) = saveAs.getOrElseTransform { failure ->
            if (failure !is KorneaResult.Empty) return println("Output failed: $failure")

            val outputFile = dataSource.locationAsUri()
                .filter { uri -> uri.protocol == Uri.PROTOCOL_FILE }
                .getOrBreak { return println("Can't obtain output location: $it") }
                .let { uri ->
                    File(buildString {
                        append(uri.path.substringBeforeLast('.'))
                        writingFormat.extension?.let {
                            append('.')
                            append(it)
                        }
                    })
                }

            Pair(AsyncFileOutputFlow(outputFile), GurrenPilot.formatContext.with(ISpiralProperty.FileName, outputFile.name))
        }

        if (!writingFormat.supportsWriting(context, writeContext, readingData)) {
            return println("${writingFormat.name} does not support writing this type of data (${readingResult.format().name} / ${readingData::class.simpleName})")
        }

        println("Converting from ${readingResult.format().name} -> ${writingFormat.name} ($readingData), and saving to $outputFlow")

        val requiredProperties = writingFormat.requiredPropertiesForWrite(context, writeContext, readingData)

        val formatContext: SpiralProperties?

        if (requiredProperties.isNotEmpty()) {
//            println("Attempting to fill in ${requiredProperties.size} propert(y/ies)")

            formatContext = context.populate(writeContext, readingData, requiredProperties) ?: writeContext
        } else {
            formatContext = writeContext
        }

        val writeResult = arbitraryProgressBar(loadingText = "Converting...", loadedText = "Finished converting!") {
            writingFormat.write(context, formatContext, readingData, outputFlow)
        }

        println(writeResult)
    }
}
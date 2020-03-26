package info.spiralframework.core.formats.archives

import info.spiralframework.base.common.SpiralContext
import info.spiralframework.base.common.concurrent.suspendForEach
import info.spiralframework.base.common.io.cacheShortTerm
import info.spiralframework.core.formats.*
import info.spiralframework.formats.common.archives.*
import org.abimon.kornea.io.common.BinaryDataSource
import org.abimon.kornea.io.common.DataPool
import org.abimon.kornea.io.common.DataSource
import org.abimon.kornea.io.common.copyTo
import org.abimon.kornea.io.common.flow.OutputFlow
import org.abimon.kornea.io.jvm.JVMInputFlow
import java.io.InputStream
import java.util.zip.ZipFile

object SpcArchiveFormat : ReadableSpiralFormat<SpcArchive>, WritableSpiralFormat {
    override val name: String = "Spc"
    override val extension: String = "spc"

    override fun preferredConversionFormat(): WritableSpiralFormat? = ZipFormat

    /**
     * Attempts to read the data source as [T]
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param dataContext Context that we retrieved this file in
     * @param source A function that returns an input stream
     *
     * @return a FormatResult containing either [T] or null, if the stream does not contain the data to form an object of type [T]
     */
    override suspend fun read(context: SpiralContext, readContext: FormatReadContext?, source: DataSource<*>): FormatResult<SpcArchive> {
        val spc = SpcArchive(context, source) ?: return FormatResult.Fail(this, 1.0)

        if (spc.files.size == 1)
            return FormatResult.Success(this, spc, 0.75)

        return FormatResult(this, spc, spc.files.isNotEmpty(), 1.0) //Not positive on this one chief but we're going with it
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
    override fun supportsWriting(context: SpiralContext, writeContext: FormatWriteContext?, data: Any): Boolean = data is AwbArchive || data is WadArchive || data is CpkArchive || data is SpcArchive || data is PakArchive || data is ZipFile

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
    override suspend fun write(context: SpiralContext, writeContext: FormatWriteContext?, data: Any, flow: OutputFlow): FormatWriteResponse {
        val customSpc = CustomSpcArchive()
        val caches: MutableList<DataPool<*, *>> = ArrayList()

        when (data) {
            is AwbArchive -> data.files.forEach { entry -> customSpc[entry.id.toString()] = data.openSource(entry) }
            is CpkArchive -> data.files.forEach { entry ->
                customSpc[entry.name, if (entry.isCompressed) SpcArchive.COMPRESSED_FLAG else 0, entry.fileSize.toLong(), entry.extractSize.toLong()] =
                        data.openRawSource(context, entry)
            }
            is PakArchive -> data.files.forEach { entry -> customSpc[entry.index.toString()] = data.openSource(entry) }
            is SpcArchive -> data.files.forEach { entry ->
                customSpc[entry.name] = data.openDecompressedSource(context, entry) ?: return@forEach
            }
            is WadArchive -> data.files.forEach { entry -> customSpc[entry.name] = data.openSource(entry) }
            is ZipFile -> data.entries().iterator().forEach { entry ->
                val cache = context.cacheShortTerm(context, "zip:${entry.name}")

                val output = cache.openOutputFlow()
                if (output == null) {
                    //Cache has failed; store in memory
                    cache.close()
                    customSpc[entry.name] = BinaryDataSource(data.getInputStream(entry).use(InputStream::readBytes))
                } else {
                    data.getInputStream(entry).use { inStream -> JVMInputFlow(inStream, entry.name).copyTo(output) }
                    customSpc[entry.name] = cache
                    caches.add(cache)
                }
            }
            else -> return FormatWriteResponse.WRONG_FORMAT
        }

        customSpc.compile(flow)
        caches.suspendForEach(DataPool<*, *>::close)
        return FormatWriteResponse.SUCCESS
    }
}
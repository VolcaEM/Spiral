package info.spiralframework.core.formats.data

import info.spiralframework.base.common.SpiralContext
import info.spiralframework.core.formats.FormatReadContext
import info.spiralframework.core.formats.FormatResult
import info.spiralframework.core.formats.ReadableSpiralFormat
import info.spiralframework.formats.common.data.DataTableStructure
import dev.brella.kornea.errors.common.getOrBreak
import dev.brella.kornea.io.common.DataSource

object DataTableStructureFormat: ReadableSpiralFormat<DataTableStructure> {
    /** A **RECOGNISABLE** name, not necessarily the full name. May commonly be the extension */
    override val name: String = "Data Table Structure"
    /**
     * The usual extension for this format. Some formats don't have a proper extension, so this can be nullable
     */
    override val extension: String = "dat"

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
    override suspend fun read(context: SpiralContext, readContext: FormatReadContext?, source: DataSource<*>): FormatResult<DataTableStructure> {
        val dataTable = DataTableStructure(context, source).getOrBreak { return FormatResult.Fail(this, 1.0, it) }

        return FormatResult.Success(this, dataTable, 0.7)
    }
}
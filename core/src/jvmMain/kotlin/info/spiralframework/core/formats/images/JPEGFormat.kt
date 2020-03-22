package info.spiralframework.core.formats.images

import info.spiralframework.base.common.SpiralContext
import info.spiralframework.base.common.io.FlowOutputStream
import info.spiralframework.base.common.io.asOutputStream
import info.spiralframework.core.formats.FormatWriteContext
import info.spiralframework.core.formats.FormatWriteResponse
import info.spiralframework.core.formats.WritableSpiralFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.abimon.kornea.io.common.flow.OutputFlow
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO

object JPEGFormat : SpiralImageIOFormat("jpg", "jpeg"), WritableSpiralFormat {
    override val name: String = "jpg"
    override val extension: String = "jpg"

    /**
     * Does this format support writing [data]?
     *
     * @param name Name of the data, if any
     * @param game Game relevant to this data
     * @param context Context that we retrieved this file in
     *
     * @return If we are able to write [data] as this format
     */
    override fun supportsWriting(context: SpiralContext, writeContext: FormatWriteContext?, data: Any): Boolean = data is Image

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
    override suspend fun write(context: SpiralContext, writeContext: FormatWriteContext?, data: Any, flow: OutputFlow): FormatWriteResponse {
        with(context) {
            if (data !is Image)
                return FormatWriteResponse.WRONG_FORMAT

            val width = data.getWidth(null)
            val height = data.getHeight(null)

            if (width < 0 || height < 0)
                return FormatWriteResponse.FAIL(IllegalArgumentException(localise("core.formats.img.invalid_dimensions", width, height)))

            val jpg = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = jpg.graphics
            try {
                g.drawImage(data, 0, 0, null)
            } finally {
                g.dispose()
            }

            try {
                withContext(Dispatchers.IO) {
                    asOutputStream(flow, false) { out ->
                        ImageIO.write(jpg, "JPG", out)
                    }
                }

                return FormatWriteResponse.SUCCESS
            } catch (io: IOException) {
                return FormatWriteResponse.FAIL(io)
            }
        }
    }
}
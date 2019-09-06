package info.spiralframework.formats.archives.srd

import info.spiralframework.base.common.SpiralContext
import info.spiralframework.formats.archives.SRD
import info.spiralframework.base.util.readInt16LE
import info.spiralframework.base.util.readNullTerminatedString

open class MATEntry(context: SpiralContext, dataType: String, offset: Long, dataLength: Int, subdataLength: Int, srd: SRD): SRDEntry(context, dataType, offset, dataLength, subdataLength, srd) {
    val materials: Map<String, String>
    override val rsiEntry: RSIEntry = super.rsiEntry!!
    
    init {
        val stream = dataStream

        try {
            stream.skip(20)

            val materialsOffset = stream.readInt16LE()
            val materialsCount = stream.readInt16LE()

            materials = dataStream.use { materialStream ->
                materialStream.skip(materialsOffset.toLong())
                val map = HashMap<String, String>()

                for (i in 0 until materialsCount) {
                    val textureNameOffset = materialStream.readInt16LE()
                    val materialTypeOffset = materialStream.readInt16LE()

                    val textureName = dataStream.use { nameStream ->
                        nameStream.skip(textureNameOffset.toLong())
                        nameStream.readNullTerminatedString()
                    }

                    val materialType = dataStream.use { nameStream ->
                        nameStream.skip(materialTypeOffset.toLong())
                        nameStream.readNullTerminatedString()
                    }

                    map[materialType] = textureName
                }

                return@use map
            }
        } finally {
            stream.close()
        }
    }
}
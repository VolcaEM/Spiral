package info.spiralframework.formats.common.archives.srd

import info.spiralframework.base.common.io.DataSource

@ExperimentalUnsignedTypes
data class UnknownSrdEntry(override val classifier: Int, override val mainDataLength: ULong, override val subDataLength: ULong, override val unknown: Int, override val dataSource: DataSource<*>):
        BaseSrdEntry(classifier, mainDataLength, subDataLength, unknown, dataSource)
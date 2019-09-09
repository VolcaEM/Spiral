package info.spiralframework.base

import java.io.InputStream

@Deprecated("Use InputFlow instead")
class OffsetInputStream(offsetInputStream: InputStream, val offset: Long) : CountingInputStream(offsetInputStream) {
    override val streamOffset: Long
        get() = offset + count

    override fun reset() {
        super.reset()
        skip(offset)
        count = 0
    }

    init {
        skip(offset)
        count = count.minus(offset).coerceAtLeast(0)
    }
}
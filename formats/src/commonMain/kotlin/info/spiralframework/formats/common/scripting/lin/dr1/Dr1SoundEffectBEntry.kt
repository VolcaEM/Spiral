package info.spiralframework.formats.common.scripting.lin.dr1

import info.spiralframework.formats.common.scripting.lin.LinEntry
import info.spiralframework.formats.common.scripting.lin.MutableLinEntry

inline class Dr1SoundEffectBEntry(override val rawArguments: IntArray): MutableLinEntry {
    constructor(opcode: Int, rawArguments: IntArray) : this(rawArguments)
    constructor(arg1: Int, arg2: Int): this(intArrayOf(arg1, arg2))

    override val opcode: Int
        get() = 0x0B

//    val sfxID: Int
//        get() =

    var arg1: Int
        get() = get(0)
        set(value) = set(0, value)

    var arg2: Int
        get() = get(1)
        set(value) = set(1, value)

    override fun format(): String = "SFX B|$arg1, $arg2"
}
package info.spiralframework.formats.common.scripting.lin.dr1

import info.spiralframework.formats.common.scripting.lin.LinEntry
import info.spiralframework.formats.common.scripting.lin.MutableLinEntry

inline class Dr1SetStudentReportInfoEntry(override val rawArguments: IntArray): MutableLinEntry {
    constructor(opcode: Int, rawArguments: IntArray) : this(rawArguments)
    constructor(characterID: Int, arg2: Int, state: Int): this(intArrayOf(characterID, arg2, state))

    override val opcode: Int
        get() = 0x10

    var characterID: Int
        get() = get(0)
        set(value) = set(0, value)

    var arg2: Int
        get() = get(1)
        set(value) = set(1, value)

    var state: Int
        get() = get(2)
        set(value) = set(2, value)

    override fun format(): String = "Set Report Info|$characterID, $arg2, $state"
}
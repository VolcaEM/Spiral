package org.abimon.spiral.core.utils

import org.abimon.spiral.core.objects.archives.srd.RSIEntry
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.*

typealias OpCodeMap<A, S> = Map<Int, Triple<Array<String>, Int, (Int, A) -> S>>
typealias OpCodeMutableMap<A, S> = MutableMap<Int, Triple<Array<String>, Int, (Int, A) -> S>>
typealias OpCodeHashMap<A, S> = HashMap<Int, Triple<Array<String>, Int, (Int, A) -> S>>

typealias UV = Pair<Float, Float>
typealias Vertex = Triple<Float, Float, Float>
typealias TriFace = Triple<Int, Int, Int>

typealias Mipmap = RSIEntry.ResourceArray
typealias VertexBlock = RSIEntry.ResourceArray
typealias IndexBlock = RSIEntry.ResourceArray
typealias FaceBlock = RSIEntry.ResourceArray

infix fun <A, B, C> Pair<A, B>.and(c: C): Triple<A, B, C> = Triple(first, second, c)

operator fun <A, S> OpCodeMutableMap<A, S>.set(key: Int, value: Triple<String?, Int, (Int, A) -> S>) {
    if(value.first == null)
        this[key] = Triple(emptyArray<String>(), value.second, value.third)
    else
        this[key] = Triple(arrayOf(value.first!!), value.second, value.third)
}

operator fun <A, S> OpCodeMutableMap<A, S>.set(key: Int, value: Pair<Int, (Int, A) -> S>) {
    this[key] = Triple(emptyArray<String>(), value.first, value.second)
}

fun assertAsArgument(statement: Boolean, illegalArgument: String) {
    if (!statement)
        throw IllegalArgumentException(illegalArgument)
}

fun assertOrThrow(statement: Boolean, ammo: Throwable) {
    if(!statement)
        throw ammo
}

fun Float.roundToPrecision(places: Int = 4): Float {
    try {
        return BigDecimal(java.lang.Float.toString(this)).setScale(places, BigDecimal.ROUND_HALF_UP).toFloat()
    } catch (nfe: NumberFormatException) {
        nfe.printStackTrace()
        throw nfe
    }
}

fun DecimalFormat.formatPair(pair: Pair<Float, Float>): Pair<String, String> = Pair(format(pair.first), format(pair.second))
fun DecimalFormat.formatTriple(triple: Triple<Float, Float, Float>): Triple<String, String, String> = Triple(format(triple.first), format(triple.second), format(triple.third))

fun Int.paddingFor(size: Int = 0x10): Int = (size - this % size) % size
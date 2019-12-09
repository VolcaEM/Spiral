package info.spiralframework.formats.scripting

import info.spiralframework.base.util.writeInt16BE
import info.spiralframework.base.util.writeInt16LE
import info.spiralframework.base.util.writeInt32LE
import info.spiralframework.formats.scripting.wrd.WrdScript
import info.spiralframework.formats.utils.DataHandler
import info.spiralframework.formats.utils.removeEscapes
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class CustomWordScript {
    val entries: ArrayList<WrdScript> = ArrayList()
    val labels: MutableList<String> = ArrayList()
    val parameters: MutableList<String> = ArrayList()
    val strings: MutableList<String> = ArrayList()

    var externalStringCount: Int = 0

    fun add(entry: WrdScript) {
        entries.add(entry)
    }

    fun addAll(entries: Array<WrdScript>) {
        this.entries.addAll(entries)
    }

    fun addAll(entries: List<WrdScript>) {
        this.entries.addAll(entries)
    }

    fun string(base: String): Int {
        val str = base.removeEscapes()
        if (str !in strings)
            strings.add(str)
        return strings.indexOf(str)
    }

    fun label(base: String): Int {
        val label = base.removeEscapes()
        if (label !in labels)
            labels.add(label)

        return labels.indexOf(label)
    }

    fun parameter(base: String): Int {
        val parameter = base.removeEscapes()
        if (parameter !in parameters)
            parameters.add(parameter)

        return parameters.indexOf(parameter)
    }

    fun compile(wrd: OutputStream) {
        val entryData = ByteArrayOutputStream()
        val labelData = ByteArrayOutputStream()
        val parameterData = ByteArrayOutputStream()

        val localBranches: MutableList<Pair<Int, Int>> = ArrayList()

        val sublabels = entries.filter { entry -> entry.opCode == 0x4A }
                .map { entry -> entry.rawArguments[0] }
                .distinct()
                .sorted()
        if (sublabels.isNotEmpty() && sublabels[0] < 0) {
//            DataHandler.LOGGER.warn("formats.custom_wrd.sublabel_too_small", 0, sublabels[0])
        }

        if (sublabels.isNotEmpty() && sublabels.last() > sublabels.size) {
//            DataHandler.LOGGER.warn("formats.custom_wrd.sublabel_too_large", sublabels.size + 1, sublabels.last())
        }
        if (strings.isEmpty() && externalStringCount == 0) {
            val highestTextID = entries.filter { entry -> entry.opCode == 0x46 }
                    .maxBy { script -> script.rawArguments[0] }
                    ?.rawArguments?.get(0)

            if (highestTextID != null) {
                externalStringCount = highestTextID + 1
            }
        }

        val sections: MutableList<Int> = ArrayList()

        entries.forEach { entry ->
            val mutatedArguments = entry.rawArguments

            if (entry.opCode == 0x4A) {
                mutatedArguments[0] = sublabels.indexOf(mutatedArguments[0])
                localBranches.add(entryData.size() to mutatedArguments[0])
            } else if (entry.opCode == 0x14)
                sections.add(entryData.size())
            else if (entry.opCode == 0x4B) {
                mutatedArguments[0] = sublabels.indexOf(mutatedArguments[0])
            }

            entryData.write(0x70)
            entryData.write(entry.opCode)
            mutatedArguments.forEach { arg -> entryData.writeInt16BE(arg) }
        }

        labels.forEach { label ->
            val bytes = label.toByteArray(Charsets.UTF_8)

            labelData.write(bytes.size and 0xFF)
            labelData.write(bytes)
            labelData.write(0x00)
        }

        parameters.forEach { parameter ->
            val bytes = parameter.toByteArray(Charsets.UTF_8)

            parameterData.write(bytes.size and 0xFF)
            parameterData.write(bytes)
            parameterData.write(0x00)
        }

        wrd.writeInt16LE(if (strings.isEmpty()) externalStringCount else strings.size) //String Count
        wrd.writeInt16LE(labels.size)
        wrd.writeInt16LE(parameters.size)
        wrd.writeInt16LE(localBranches.size)

        wrd.writeInt32LE(0)
        wrd.writeInt32LE(0x20 + entryData.size()) //localBranchOffset
        wrd.writeInt32LE(0x20 + entryData.size() + (4 * localBranches.size)) //sectionOffset
        wrd.writeInt32LE(0x20 + entryData.size() + (4 * localBranches.size) + 2 * sections.size) //labelOffset
        wrd.writeInt32LE(0x20 + entryData.size() + (4 * localBranches.size) + 2 * sections.size + labelData.size()) //parameterOffset

        if (strings.isEmpty())
            wrd.writeInt32LE(0)
        else
            wrd.writeInt32LE(0x20 + entryData.size() + (4 * localBranches.size) + 2 * sections.size + labelData.size() + parameterData.size()) //stringOffset

        entryData.writeTo(wrd)

        localBranches.forEach { (offset, arg) ->
            wrd.writeInt16LE(arg)
            wrd.writeInt16LE(offset)
        }

        sections.forEach(wrd::writeInt16LE)

        labelData.writeTo(wrd)
        parameterData.writeTo(wrd)

        strings.forEach { str ->
            val bytes = str.toByteArray(Charsets.UTF_16LE)

            if(bytes.size >= 0x80) {
                val copy = bytes.copyOfRange(0, 0x80)
                wrd.write(copy.size)
                wrd.write(copy)
                wrd.write(0x00)
                wrd.write(0x00)
            } else {
                wrd.write(bytes.size)
                wrd.write(bytes)
                wrd.write(0x00)
                wrd.write(0x00)
            }
        }
    }
}
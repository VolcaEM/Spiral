package org.abimon.spiral.mvc.gurren

import com.jakewharton.fliptables.FlipTable
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import org.abimon.spiral.core.SpiralFormats
import org.abimon.spiral.core.archives.IArchive
import org.abimon.spiral.core.archives.WADArchive
import org.abimon.spiral.core.data.SpiralData
import org.abimon.spiral.core.debug
import org.abimon.spiral.core.isDebug
import org.abimon.spiral.modding.ModManager
import org.abimon.spiral.mvc.SpiralModel
import org.abimon.spiral.mvc.SpiralModel.Command
import org.abimon.visi.collections.joinToPrefixedString
import org.abimon.visi.io.*
import org.abimon.visi.lang.child
import org.abimon.visi.lang.extension
import org.abimon.visi.lang.parents
import org.abimon.visi.lang.replaceLast
import org.abimon.visi.security.sha512Hash
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.system.measureTimeMillis

@Suppress("unused")
object GurrenOperation {
    val helpTable: String = FlipTable.of(
            arrayOf("Command", "Arguments", "Description", "Example Command"),
            arrayOf(
                    arrayOf("help", "", "Display this message", ""),
                    arrayOf("extract", "[extraction location] {regex}", "Extracts the contents of this WAD file to [extract location], for all files matching {regex} if provided (all files otherwise)", "extract \"dr1${File.separator}bustups\" \".*bustup.*tga\""),
                    arrayOf("exit", "", "Exits the operate scope", "")
            )
    )

    val operatingArchive: IArchive
        get() = IArchive(SpiralModel.operating ?: throw IllegalStateException("Attempt to get the archive while operating is null, this is a bug!")) ?: throw IllegalStateException("Attempts to create an archive return null, this is a bug!")
    val operatingName: String
        get() = SpiralModel.operating?.nameWithoutExtension ?: ""

    val help = Command("help", "operate") { println(helpTable) }

    val extract = Command("extract", "operate") { (params) ->
        if(params.size == 1)
            return@Command errPrintln("[$operatingName] Error: No directory to extract to provided")

        val directory = File(params[1])
        if(directory.exists()) {
            if (directory.isFile)
                return@Command errPrintln("[$operatingName] Error: $directory is a file")
            else if (!directory.isDirectory)
                return@Command errPrintln("[$operatingName] Error: $directory is not a directory")
        } else {
            errPrintln("[$operatingName] Warn: $directory does not exist, creating...")
            if(!directory.mkdirs())
                return@Command errPrintln("[$operatingName] Error: $directory could not be created, returning...")
        }

        val regex = (if(params.size > 2) params[2] else ".*").toRegex()

        val matching = operatingArchive.fileEntries.filter { (name) -> name.matches(regex) || name.child.matches(regex) }

        println("[$operatingName] Attempting to extract files matching the regex ${regex.pattern}, which is the following list of files: ")
        println("")
        println(matching.joinToPrefixedString("\n", "[$operatingName]\t") { first })
        println("")
        if(question("[$operatingName] Proceed with extraction (Y/n)? ", "Y")) {
            val rows: MutableCollection<Array<String>> = ArrayList<Array<String>>()
            val duration = measureTimeMillis {
                matching.forEach { (entryName, entry) ->
                    val parents = File(directory, entryName.parents)
                    if (!parents.exists() && !parents.mkdirs()) //Second check due to concurrency
                        return@forEach errPrintln("[$operatingName] Warn: $parents could not be created; skipping $entryName")

                    val output = File(directory, entryName)
                    FileOutputStream(output).use { outputStream -> SpiralFormats.decompressFully(entry).use { inputStream -> inputStream.writeTo(outputStream) } }
                    debug("[$operatingName] Wrote $entryName to $output")
                    rows.add(arrayOf(entryName, output relativePathTo directory))
                }
            }

            println(FlipTable.of(arrayOf("File", "Output"), rows.toTypedArray()))
            if(isDebug) println("Took $duration ms")
        }
    }
    val extractNicely = Command("extract_nicely", "operate") { (params) ->
        if(params.size == 1)
            return@Command errPrintln("[$operatingName] Error: No directory to extract to provided")

        val directory = File(params[1])
        if(directory.exists()) {
            if (directory.isFile)
                return@Command errPrintln("[$operatingName] Error: $directory is a file")
            else if (!directory.isDirectory)
                return@Command errPrintln("[$operatingName] Error: $directory is not a directory")
        } else {
            errPrintln("[$operatingName] Warn: $directory does not exist, creating...")
            if(!directory.mkdirs())
                return@Command errPrintln("[$operatingName] Error: $directory could not be created, returning...")
        }

        val regex = (if(params.size > 2) params[2] else ".*").toRegex()

        val matching = operatingArchive.fileEntries.filter { (name) -> name.matches(regex) || name.child.matches(regex) }

        println("[$operatingName] Attempting to extract files matching the regex ${regex.pattern}, which is the following list of files: ")
        println("")
        println(matching.joinToPrefixedString("\n", "[$operatingName]\t") { first })
        println("")
        if(question("[$operatingName] Proceed with extraction (Y/n)? ", "Y")) {
            val formatParams = mapOf("pak:convert" to true, "lin:dr1" to operatingName.startsWith("dr1"))

            val rows: MutableCollection<Array<String>> = ConcurrentLinkedQueue()
            val duration = measureTimeMillis {
                runBlocking {
                    matching.forEach { (entryName) ->
                        val parents = File(directory, entryName.parents)
                        if (!parents.exists())
                            parents.mkdirs()
                    }

                    matching.map { (entryName, entry) ->
                        launch(CommonPool) {
                            val parents = File(directory, entryName.parents)
                            if (!parents.exists() && !parents.mkdirs() && !parents.exists())
                                return@launch errPrintln("[$operatingName] Warn: $parents could not be created; skipping $entryName")
                            val data = SpiralFormats.decompressFully(entry)
                            val format = SpiralFormats.formatForExtension(entryName.extension, SpiralFormats.drArchiveFormats) ?: SpiralFormats.formatForData(data, SpiralFormats.drArchiveFormats)

                            val convertingTo = format?.conversions?.firstOrNull()

                            if (format == null) {
                                val output = File(directory, entryName)
                                FileOutputStream(output).use { outputStream -> data.use { inputStream -> inputStream.writeTo(outputStream) } }
                                rows.add(arrayOf(entryName, "Unknown", "None", output relativePathTo directory))
                            } else if (convertingTo == null) {
                                val output = File(directory, entryName)
                                FileOutputStream(output).use { outputStream -> data.use { inputStream -> inputStream.writeTo(outputStream) } }
                                rows.add(arrayOf(entryName, format.name, "None", output relativePathTo directory))
                            } else {
                                try {
                                    val output = File(directory, entryName.replace(".${format.extension}", "") + ".${convertingTo.extension ?: "unk"}")
                                    FileOutputStream(output).use { outputStream -> format.convert(convertingTo, data, outputStream, formatParams) }
                                    rows.add(arrayOf(entryName, format.name, convertingTo.name, output relativePathTo directory))
                                } catch (iea: IllegalArgumentException) {
                                    val output = File(directory, entryName)
                                    FileOutputStream(output).use { outputStream -> data.use { inputStream -> inputStream.writeTo(outputStream) } }
                                    rows.add(arrayOf(entryName, format.name, "ERR", output relativePathTo directory))
                                }
                            }
                        }
                    }.forEach { it.join() }
                }
            }

            println(FlipTable.of(arrayOf("File", "File Format", "Converted Format", "Output"), rows.toTypedArray()))
            if(isDebug) println("Took $duration ms")
        }
    }

    val compile = Command("compile", "operate") { (params) ->
        if(params.size == 1)
            return@Command errPrintln("[$operatingName] Error: No directory to compile from provided")

        val directory = File(params[1])
        if(directory.exists()) {
            if (directory.isFile)
                return@Command errPrintln("[$operatingName] Error: $directory is a file")
            else if (!directory.isDirectory)
                return@Command errPrintln("[$operatingName] Error: $directory is not a directory")
        } else
            return@Command errPrintln("[$operatingName] Error: $directory does not exist")

        val regex = (if(params.size > 2) params[2] else ".*").toRegex()

        val matching = directory.iterate(filters = Gurren.ignoreFilters).filter { (it relativePathFrom directory).matches(regex) || it.name.matches(regex) }

        println("[$operatingName] Attempting to compile files matching the regex ${regex.pattern}, which is the following list of files: ")
        println("")
        println(matching.joinToPrefixedString("\n", "[$operatingName]\t") { this relativePathFrom directory })
        println("")
        if(question("[$operatingName] Proceed with compilation (Y/n)? ", "Y")) {
            val tmpFile = File(SpiralModel.operating!!.absolutePath + ".tmp")
            val backupFile = File(SpiralModel.operating!!.absolutePath + ".backup")
            try {
                FileOutputStream(tmpFile).use { operatingArchive.compile(matching.map { file -> (file relativePathFrom directory) to FileDataSource(file) }, it) }

                if(backupFile.exists()) backupFile.delete()
                SpiralModel.operating!!.renameTo(backupFile)
                tmpFile.renameTo(SpiralModel.operating!!)

                println("[$operatingName] Successfully compiled ${matching.size} files into $operatingName.wad")
            } finally {
                tmpFile.delete()
            }
        }
    }

    val compileNicely = Command("compile_nicely", "operate") { (params) ->
        if(params.size == 1)
            return@Command errPrintln("[$operatingName] Error: No directory to compile from provided")

        val directory = File(params[1])
        if(directory.exists()) {
            if (directory.isFile)
                return@Command errPrintln("[$operatingName] Error: $directory is a file")
            else if (!directory.isDirectory)
                return@Command errPrintln("[$operatingName] Error: $directory is not a directory")
        } else
            return@Command errPrintln("[$operatingName] Error: $directory does not exist")

        val regex = (if(params.size > 2) params[2] else ".*").toRegex()

        val matching = directory.iterate(filters = Gurren.ignoreFilters)
                .filter { (it relativePathFrom directory).matches(regex) || it.name.matches(regex) }
                .map { file -> file to (SpiralFormats.formatForExtension(file.extension) ?: SpiralFormats.formatForData(FileDataSource(file))) }
                .map { (file, format) -> if(format in SpiralFormats.drArchiveFormats) file to null else file to format }
                .toMap()

        println("[$operatingName] Attempting to convert and compile files matching the regex ${regex.pattern}, which is the following list of files: ")
        println("")
        println(matching.entries.joinToPrefixedString("\n", "[$operatingName]\t") {
            if(this.value == null)
                "${this.key relativePathFrom directory} (No known format)"
            else if(this.value!!.conversions.isEmpty())
                "${this.key relativePathFrom directory} (Cannot convert from ${this.value!!.name})"
            else
                "${this.key relativePathFrom directory} (${this.value!!.name} -> ${this.value!!.conversions.first().name})"
        })
        println("")
        if(question("[$operatingName] Proceed with conversion and compilation (Y/n)? ", "Y")) {
            val formatParams = mapOf("pak:convert" to true, "lin:dr1" to operatingName.startsWith("dr1"))
//            val customWad = make<CustomWAD> {
//                wad(wad)
//
//                matching.filter { (_, format) -> format == null }.forEach { (entry) -> file(entry, entry relativePathFrom directory) }
//                matching.filter { (_, format) -> format != null }.forEach { (entry, from) ->
//                    val name = (entry relativePathFrom directory).replaceLast(".${from!!.extension ?: "unk"}", ".${from.conversions.first().extension ?: "unk"}")
//                    data(name, ByteArrayDataSource(from.convertToBytes(from.conversions.first(), FileDataSource(entry), formatParams)))
//                }
//            }

            val newEntries: MutableList<Pair<String, DataSource>> = ArrayList()

            newEntries.addAll(matching.filter { (_, format) -> format == null }.map { (file) -> (file relativePathFrom directory) to FileDataSource(file) })

            newEntries.addAll(matching.filter { (_, format) -> format != null }.map { (entry, from) ->
                val name = (entry relativePathFrom directory).replaceLast(".${from!!.extension ?: "unk"}", ".${from.conversions.first().extension ?: "unk"}")
                return@map name to FunctionDataSource { from.convertToBytes(from.conversions.first(), FileDataSource(entry), formatParams) }
            })

            val tmpFile = File(SpiralModel.operating!!.absolutePath + ".tmp")
            val backupFile = File(SpiralModel.operating!!.absolutePath + ".backup")
            try {
                FileOutputStream(tmpFile).use { operatingArchive.compile(newEntries, it) }

                if(backupFile.exists()) backupFile.delete()
                SpiralModel.operating!!.renameTo(backupFile)
                tmpFile.renameTo(SpiralModel.operating!!)

                println("[$operatingName] Successfully compiled ${matching.size} files into $operatingName.wad")
            } finally {
                tmpFile.delete()
            }
        }
    }

    val fingerprintWad = Command("fingerprint_wad", "operate") {
        val fileMap: MutableMap<String, Map<String, String>> = HashMap()
        val fingerprints: MutableMap<String, String> = HashMap()
        print("Version: ")
        fileMap[readLine() ?: "unknown"] = fingerprints

        operatingArchive.fileEntries.forEach { (name, data) -> fingerprints[name] = data.use { it.sha512Hash() } }

        SpiralData.MAPPER.writeValue(File("fingerprints_${SpiralModel.operating?.nameWithoutExtension}.json"), fileMap)
    }

    val info = Command("info", "operate") { (params) ->
        val regex = (if(params.size > 1) params[1] else ".*").toRegex()
        when(operatingArchive) {
            is WADArchive -> {
                val wad = (operatingArchive as WADArchive).wad

                val matching = wad.files.filter { (name) -> name.matches(regex) || name.child.matches(regex) }.map { file -> arrayOf(file.name, "${file.fileSize} B", "${file.offset} B from the beginning", ModManager.getModForFingerprint(file)?.run { "${first.name} v$second" } ?: "Unknown") }.toTypedArray()
                println(FlipTable.of(arrayOf("Entry Name", "Entry Size", "Entry Offset", "Mod Origin"), matching))
            }
        }
    }

    val exit = Command("exit", "operate") { SpiralModel.scope = "> " to "default" }

    val operateOn = Command("operate", "default") { (params) ->
        if (SpiralModel.archives.isEmpty())
            return@Command errPrintln("Error: No archives registered")
        if (params.size > 1) {
            for(i in 1 until params.size) {
                val archiveName = params[i]
                val archive = SpiralModel.archives.firstOrNull { file -> file.nameWithoutExtension == archiveName || file.absolutePath == archiveName }
                if (archive == null)
                    println("Invalid archive $archive")
                else {
                    SpiralModel.operating = archive
                    SpiralModel.scope = "[${archive.nameWithoutExtension}]|> " to "operate"
                    println("Now operating on ${archive.nameWithoutExtension}")

                    return@Command
                }
            }
        }

        println("Select an archive to operate on")
        println(SpiralModel.archives.joinToPrefixedString("\n", "\t") { "$nameWithoutExtension ($absolutePath)" })
        while (true) {
            print("[operate] > ")
            val archiveName = readLine() ?: break
            val archive = SpiralModel.archives.firstOrNull { file -> file.nameWithoutExtension == archiveName || file.absolutePath == archiveName }
            if (archive == null)
                println("Invalid archive $archive")
            else {
                SpiralModel.operating = archive
                SpiralModel.scope = "[${archive.nameWithoutExtension}]|> " to "operate"
                println("Now operating on ${archive.nameWithoutExtension}")

                break
            }
        }
    }

    fun process() {}
}
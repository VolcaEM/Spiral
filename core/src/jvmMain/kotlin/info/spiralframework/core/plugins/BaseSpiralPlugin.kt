package info.spiralframework.core.plugins

import com.fasterxml.jackson.module.kotlin.readValue
import info.spiralframework.core.SpiralCoreContext
import dev.brella.kornea.errors.common.map
import dev.brella.kornea.io.common.BinaryDataSource
import dev.brella.kornea.io.common.DataSource
import dev.brella.kornea.io.common.flow.readBytes
import dev.brella.kornea.io.common.useAndMapInputFlow
import dev.brella.kornea.io.jvm.files.AsyncFileDataSource
import dev.brella.kornea.toolkit.common.SemanticVersion
import java.io.File

abstract class BaseSpiralPlugin protected constructor(val context: SpiralCoreContext, val callingClass: Class<*>, val resourceName: String, val yaml: Boolean = true) : ISpiralPlugin {
    lateinit var pojo: SpiralPluginDefinitionPojo

    override val name: String by lazy { pojo.name }
    override val uid: String by lazy { pojo.uid }
    override val version: SemanticVersion by lazy { pojo.semanticVersion }
    private val jarFile = callingClass::class.java.protectionDomain.codeSource?.location?.path?.let(::File)?.takeIf(File::isFile)
    override val dataSource: DataSource<*> = jarFile?.let { f -> AsyncFileDataSource(f) } ?: BinaryDataSource(byteArrayOf())

    protected suspend fun init() {
        pojo = context.loadResource(resourceName, callingClass.kotlin)
            .useAndMapInputFlow { flow -> flow.readBytes() }
            .map { data ->
                if (yaml) context.yamlMapper.readValue<SpiralPluginDefinitionPojo.Builder>(data).build()
                else context.jsonMapper.readValue<SpiralPluginDefinitionPojo.Builder>(data).build()
            }.get()
    }
}
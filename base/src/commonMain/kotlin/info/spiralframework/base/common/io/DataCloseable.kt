package info.spiralframework.base.common.io

interface DataCloseable {
    suspend fun close()
}

@ExperimentalUnsignedTypes
public suspend inline fun <T : DataCloseable?, R> T.use(block: (T) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        this.closeFinally(exception)
    }
}

@ExperimentalUnsignedTypes
@PublishedApi
internal suspend fun DataCloseable?.closeFinally(cause: Throwable?) = when {
    this == null -> {
    }
    cause == null -> close()
    else ->
        try {
            close()
        } catch (closeException: Throwable) {
            //cause.addSuppressed(closeException)
        }
}

suspend fun <T: DataCloseable> List<T>.closeAll() = forEach { data -> data.close() }
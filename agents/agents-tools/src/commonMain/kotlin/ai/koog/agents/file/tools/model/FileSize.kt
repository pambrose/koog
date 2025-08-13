package ai.koog.agents.file.tools.model

import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
public sealed interface FileSize {
    public fun display(): String

    public companion object {
        public const val KB: Long = 1024L
        public const val MB: Long = KB * 1024L

        public suspend fun <Path> of(path: Path, fs: FileSystemProvider.ReadOnly<Path>): List<FileSize> {
            val bytes = Bytes(fs.size(path))

            return if (bytes.bytes > MB) {
                listOf(bytes)
            } else {
                val content = fs.readBytes(path).decodeToString()
                val lines = if (content.isEmpty()) 0 else content.lines().size
                listOf(bytes, Lines(lines))
            }
        }

        public fun Double.formatDecimals(decimals: Int): String {
            val roundedToDecimals = ((this * 10.0.pow(decimals)).toLong() / 10.0.pow(decimals))
            //in case of js everything is double, so we need to enforce 0 after the dot
            if (roundedToDecimals == roundedToDecimals.toInt().toDouble()) return "${roundedToDecimals.toInt()}.${"0".repeat(decimals)}"
            return "$roundedToDecimals"
        }
    }

    @Serializable
    public data class Bytes(val bytes: Long) : FileSize {
        override fun display(): String {
            return when {
                bytes == 0L -> "0 bytes"
                bytes >= MB -> "${(bytes.toDouble() / MB).formatDecimals(1)} mb"
                bytes.toDouble() / KB < 0.1 -> "<0.1 KB"
                else -> "${(bytes.toDouble() / KB).formatDecimals(1)} kb"
            }
        }
    }

    @Serializable
    public data class Lines(val lines: Int) : FileSize {
        override fun display(): String {
            return "$lines lines"
        }
    }
}

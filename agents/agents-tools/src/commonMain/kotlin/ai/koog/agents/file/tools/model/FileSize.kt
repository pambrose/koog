package ai.koog.agents.file.tools.model

import ai.koog.rag.base.files.FileSystemProvider
import kotlin.math.pow
import kotlinx.serialization.Serializable

/**
 * Represents human-readable file size information in different formats.
 *
 * This sealed interface provides multiple ways to represent a file's size or length:
 * - [Bytes] shows file size in bytes, kilobytes, or megabytes
 * - [Lines] shows file size as a count of text lines
 *
 * Use the companion object's [of] method to create appropriate instances for a file.
 */
@Serializable
public sealed interface FileSize {
    /**
     * Returns a formatted string representation of the file size.
     *
     * @return a human-readable size string suitable for display
     */
    public fun display(): String

    /** Provides utility methods and constants for creating and working with file sizes. */
    public companion object {
        /** Constant representing exactly 1 kilobyte (1024 bytes). */
        public const val KB: Long = 1024L

        /** Constant representing exactly 1 megabyte (1024 * 1024 bytes). */
        public const val MB: Long = KB * 1024L

        /**
         * Creates FileSize representations for a file at the given path.
         *
         * This method always returns a list containing at least a [Bytes] instance. For files
         * smaller than or equal to 1MB, it also includes a [Lines] instance. For files larger than
         * 1MB, only the [Bytes] instance is returned to avoid reading large content into memory.
         *
         * @param Path the type parameter representing the filesystem path type
         * @param path the path to the file to measure
         * @param fs the filesystem provider used to access the file
         * @return a list containing:
         *     - Always a [Bytes] instance representing the file size in bytes
         *     - For files <= 1MB, also a [Lines] instance representing the line count
         */
        public suspend fun <Path> of(
            path: Path,
            fs: FileSystemProvider.ReadOnly<Path>,
        ): List<FileSize> {
            val bytes = Bytes(fs.size(path))

            return if (bytes.bytes > MB) {
                listOf(bytes)
            } else {
                val content = fs.readBytes(path).decodeToString()
                val lines = if (content.isEmpty()) 0 else content.lines().size
                listOf(bytes, Lines(lines))
            }
        }

        /**
         * Extension function to format a Double value with a fixed number of decimal places.
         *
         * This method ensures trailing zeros are preserved after the decimal point. For example,
         * formatting 1.0 with 2 decimal places will produce "1.00". This is particularly important
         * for JavaScript targets where numbers are IEEE 754 doubles and trailing zeros would
         * otherwise be lost.
         *
         * @param decimals the number of decimal places to include (must be non-negative)
         * @return a string representation of the number with exactly [decimals] digits after the
         *   decimal point
         * @receiver the Double value to format
         */
        private fun Double.formatDecimals(decimals: Int): String {
            val roundedToDecimals = ((this * 10.0.pow(decimals)).toLong() / 10.0.pow(decimals))
            if (roundedToDecimals == roundedToDecimals.toInt().toDouble())
                return "${roundedToDecimals.toInt()}.${"0".repeat(decimals)}"
            return "$roundedToDecimals"
        }
    }

    /**
     * Represents a file's size in bytes with automatic unit selection for display.
     *
     * This implementation stores the raw byte count and provides a formatted display string using
     * appropriate units (bytes, KB, or MB) depending on the size.
     *
     * @property bytes the exact size in bytes (always non-negative)
     */
    @Serializable
    public data class Bytes(val bytes: Long) : FileSize {
        /**
         * Formats the byte count as a human-readable string with appropriate units.
         *
         * The format varies based on the byte count:
         * - 0 bytes → "0 bytes"
         * - Size ≥ 1MB → Value in MB with one decimal place (e.g., "1.2 mb")
         * - Size < 0.1KB → "<0.1 KB" (shown instead of very small KB values)
         * - All other sizes → Value in KB with one decimal place (e.g., "12.3 kb")
         *
         * @return a formatted string with units (bytes, kb, or mb)
         */
        override fun display(): String {
            return when {
                bytes == 0L -> "0 bytes"
                bytes >= MB -> "${(bytes.toDouble() / MB).formatDecimals(1)} mb"
                bytes.toDouble() / KB < 0.1 -> "<0.1 KB"
                else -> "${(bytes.toDouble() / KB).formatDecimals(1)} kb"
            }
        }
    }

    /**
     * Represents a file's size as a count of text lines.
     *
     * This implementation is typically used for text files where line count is more meaningful than
     * byte size for understanding content length.
     *
     * @property lines the number of lines in the file (always non-negative)
     */
    @Serializable
    public data class Lines(val lines: Int) : FileSize {
        /**
         * Returns the line count as a formatted string.
         *
         * Simply appends "lines" to the integer count.
         *
         * @return a string in the format "{lines} lines"
         */
        override fun display(): String {
            return "$lines lines"
        }
    }
}

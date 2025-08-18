package ai.koog.agents.file.tools.model

import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single entry in a file system.
 *
 * This sealed interface provides a unified abstraction for both files and directories. Common
 * properties like name, path, and hidden status are available on all entries, while type-specific
 * properties are available on concrete implementations.
 *
 * @property name Base name of the entry without path separators.
 * @property extension File extension without the dot; null for directories or files without
 *   extensions.
 * @property path Absolute path string in the provider's native format.
 * @property hidden Flag indicating if this entry is hidden in the filesystem.
 */
@Serializable
public sealed interface FileSystemEntry {
    /** Base name of the entry without path separators. */
    public val name: String

    /** File extension without the dot, or null if none exists. */
    public val extension: String?

    /** Absolute path string in the provider's native format. */
    public val path: String

    /**
     * Flag indicating if this entry is hidden in the filesystem.
     *
     * The exact definition of "hidden" depends on the underlying filesystem:
     * - Windows: files with the hidden attribute set.
     * - Unix-like: files starting with a dot (.).
     * - macOS: files with the hidden flag or starting with a dot.
     *
     * This value is retrieved from [FileMetadata.hidden] via the filesystem provider.
     */
    public val hidden: Boolean

    /**
     * Traverses this entry and its descendants in depth-first order.
     *
     * @param depth Maximum recursion depth to traverse. Use 0 to visit only this entry.
     * @param visitor Callback invoked for each visited [FileSystemEntry].
     */
    public suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit)

    /** Factory for creating [FileSystemEntry] instances from filesystem paths. */
    public companion object {
        /**
         * Creates the appropriate [FileSystemEntry] subtype from a path.
         *
         * @param Path Type parameter representing the provider's path type.
         * @param path The path to the filesystem entry.
         * @param fs The filesystem provider used to access the entry.
         * @return A [File] for regular files, a [Folder] for directories, or null if the path
         *   doesn't exist.
         * @throws kotlinx.io.IOException If an I/O error occurs while accessing the filesystem.
         */
        public suspend fun <Path> of(
            path: Path,
            fs: FileSystemProvider.ReadOnly<Path>,
        ): FileSystemEntry? {
            val metadata = fs.metadata(path) ?: return null
            return when (metadata.type) {
                FileMetadata.FileType.File ->
                    File(
                        name = fs.name(path),
                        extension = fs.extension(path).takeIf { it.isNotEmpty() },
                        path = fs.toAbsolutePathString(path),
                        hidden = metadata.hidden,
                        contentType = fs.getFileContentType(path),
                        size = FileSize.of(path, fs),
                    )
                FileMetadata.FileType.Directory ->
                    Folder(
                        name = fs.name(path),
                        path = fs.toAbsolutePathString(path),
                        hidden = metadata.hidden,
                    )
            }
        }
    }

    /**
     * Represents a regular file in the filesystem.
     *
     * @property name Base name of the file (may include extension).
     * @property extension File extension without the dot; null if absent.
     * @property path Absolute path string in the provider's native format.
     * @property content Optional file content payload.
     * @property hidden Flag indicating if this file is hidden in the filesystem.
     * @property contentType Detected content type for this file.
     * @property size One or more size measurements for this file.
     * @property nameWithoutExtension File name without its extension.
     */
    @Serializable
    public data class File(
        override val name: String,
        override val extension: String?,
        override val path: String,
        override val hidden: Boolean,
        public val size: List<FileSize>,
        @SerialName("content_type") public val contentType: FileMetadata.FileContentType,
        public val content: Content = Content.None,
        ) : FileSystemEntry {

        /** File name without its extension. */
        public val nameWithoutExtension: String =
            if (extension != null && extension.isNotEmpty()) name.removeSuffix(".${extension}")
            else name

        /** Factory for creating [File] instances from filesystem paths. */
        public companion object {
            /**
             * Creates a [File] instance from a filesystem path.
             *
             * @param Path Type parameter representing the provider's path type.
             * @param path The path to the file.
             * @param content Optional content to include with the file.
             * @param fs The filesystem provider used to access the file.
             * @return A [File] if the path exists and is a regular file; otherwise null.
             * @throws kotlinx.io.IOException If an I/O error occurs while accessing the filesystem.
             */
            public suspend fun <Path> of(
                path: Path,
                content: Content = Content.None,
                fs: FileSystemProvider.ReadOnly<Path>,
            ): File? {
                val metadata = fs.metadata(path) ?: return null
                if (metadata.type != FileMetadata.FileType.File) return null
                return File(
                    name = fs.name(path),
                    extension = fs.extension(path).takeIf { it.isNotEmpty() },
                    path = fs.toAbsolutePathString(path),
                    hidden = metadata.hidden,
                    contentType = fs.getFileContentType(path),
                    size = FileSize.of(path, fs),
                    content = content,
                )
            }
        }

        /**
         * Visits this file by invoking [visitor] once with this file.
         *
         * @param depth Ignored for files.
         * @param visitor Function called once with this file.
         */
        override suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit) {
            visitor(this)
        }

        /** Various forms of file content: none, full text, or excerpts. */
        @Serializable
        public sealed interface Content {
            /** Represents the absence of file content. */
            @Serializable public data object None : Content

            /**
             * Full-text content of a file.
             *
             * @property text Complete text content of the file.
             */
            @Serializable public data class Text(val text: String) : Content

            /**
             * One or more excerpts from a file with position information.
             *
             * @property snippets List of text snippets with their original document positions.
             */
            @Serializable
            public data class Excerpt(val snippets: List<Snippet>) : Content {
                /** Convenience vararg constructor. */
                public constructor(vararg snippets: Snippet) : this(snippets.toList())

                /**
                 * Single excerpt from a file with its position information.
                 *
                 * @property text The text content of this snippet.
                 * @property range The document range indicating where this snippet is located.
                 */
                @Serializable
                public data class Snippet(
                    val text: String,
                    val range: DocumentProvider.DocumentRange,
                )
            }

            /** Factory for creating [Content] instances. */
            public companion object {
                /** Creates an [Excerpt] from specific [DocumentProvider.DocumentRange] values. */
                public fun of(
                    content: String,
                    ranges: List<DocumentProvider.DocumentRange>,
                ): Content = Excerpt(ranges.map { r -> Excerpt.Snippet(r.substring(content), r) })

                /**
                 * Creates either [Text] or [Excerpt] based on line-windowing parameters.
                 *
                 * @param content Full file content.
                 * @param skipLines Lines to skip from the beginning. A Negative value is treated as 0.
                 * @param takeLines Max lines to include after skipping; use -1 or null for no
                 *   limit.
                 */
                public fun of(content: String, skipLines: Int?, takeLines: Int?): Content {
                    val lines = content.lines()
                    val skip = (skipLines ?: 0).coerceAtLeast(0)
                    val take = takeLines ?: -1
                    val afterSkip = lines.drop(skip)
                    val window = if (take >= 0) afterSkip.take(take) else afterSkip
                    val text = window.joinToString("\n")

                    val truncated = skip > 0 || (take >= 0 && window.size < afterSkip.size)
                    if (truncated) {
                        val start = DocumentProvider.Position(skip, 0)
                        val end = DocumentProvider.Position(skip + window.size, 0)
                        return Excerpt(
                            listOf(
                                Excerpt.Snippet(
                                    text = text,
                                    range = DocumentProvider.DocumentRange(start, end),
                                )
                            )
                        )
                    }
                    return Text(text)
                }
            }
        }
    }

    /**
     * Represents a directory in the filesystem.
     *
     * @property name Base name of the directory.
     * @property path Absolute path string in the provider's native format.
     * @property entries Optional list of entries contained in this folder.
     * @property hidden Flag indicating if this directory is hidden in the filesystem.
     * @property extension Always null for directories.
     */
    @Serializable
    public data class Folder(
        override val name: String,
        override val path: String,
        val entries: List<FileSystemEntry>? = null,
        override val hidden: Boolean,
    ) : FileSystemEntry {
        /** Always null since directories don't have file extensions. */
        override val extension: String? = null

        /** Factory for creating [Folder] instances from filesystem paths. */
        public companion object {
            /**
             * Creates a [Folder] instance from a filesystem path.
             *
             * @param Path Type parameter representing the provider's path type.
             * @param path The path to the directory.
             * @param entries Optional list of entries contained in this folder.
             * @param fs The filesystem provider used to access the directory.
             * @return A [Folder] if the path exists and is a directory; otherwise null.
             * @throws kotlinx.io.IOException If an I/O error occurs while accessing the filesystem.
             */
            public suspend fun <Path> of(
                path: Path,
                entries: List<FileSystemEntry>? = null,
                fs: FileSystemProvider.ReadOnly<Path>,
            ): Folder? {
                val metadata = fs.metadata(path) ?: return null
                if (metadata.type != FileMetadata.FileType.Directory) return null
                return Folder(
                    name = fs.name(path),
                    path = fs.toAbsolutePathString(path),
                    entries = entries,
                    hidden = metadata.hidden,
                )
            }
        }

        /**
         * Visits this folder and optionally its descendants.
         *
         * @param depth Maximum recursion depth for visiting descendants.
         * @param visitor Function called for each visited [FileSystemEntry].
         */
        override suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit) {
            visitor(this)
            if (depth == 0) return
            entries?.forEach { it.visit(depth - 1, visitor) }
        }
    }
}

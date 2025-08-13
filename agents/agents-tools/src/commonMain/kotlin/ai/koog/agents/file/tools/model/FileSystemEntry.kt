package ai.koog.agents.file.tools.model


import ai.koog.rag.base.files.DocumentProvider
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public sealed interface FileSystemEntry {
    public val name: String
    public val extension: String?

    public val path: String

    public val hidden: Boolean

    public suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit)

    public companion object {
        public suspend fun <Path> of(path: Path, fs: FileSystemProvider.ReadOnly<Path>): FileSystemEntry? {
            val metadata = fs.metadata(path) ?: return null
            when (metadata.type) {
                FileMetadata.FileType.File -> {
                    return File(
                        name = fs.name(path),
                        extension = fs.extension(path).takeIf { it.isNotEmpty() },
                        path = fs.toAbsolutePathString(path),
                        hidden = metadata.hidden,
                        contentType = fs.getFileContentType(path),
                        size = FileSize.Companion.of(path, fs),
                    )
                }

                FileMetadata.FileType.Directory -> {
                    return Folder(
                        name = fs.name(path),
                        path = fs.toAbsolutePathString(path),
                        hidden = metadata.hidden,
                    )
                }
            }
        }

        private fun extension(path: String): String? {
            return path.split("/").lastOrNull()?.split('.', limit = 2)?.lastOrNull()
        }

        private fun name(path: String): String {
            return path.split("/").lastOrNull()?.split('.', limit = 2)?.firstOrNull() ?: path.split("/").lastOrNull()
            ?: ""
        }
    }

    @Serializable
    public data class File(
        override val name: String,
        override val extension: String?,
        override val path: String,
        val content: Content = Content.None,
        override val hidden: Boolean,
        @SerialName("content_type")
        public val contentType: FileMetadata.FileContentType,
        val size: List<FileSize>
    ) : FileSystemEntry {

        public val nameWithoutExtension: String = if (extension != null) name.removeSuffix(".$extension") else name

        public companion object {

            public suspend fun <Path> of(
                path: Path,
                content: Content = Content.None,
                fs: FileSystemProvider.ReadOnly<Path>
            ): File? {
                val metadata = fs.metadata(path) ?: return null
                return File(
                    name = fs.name(path),
                    extension = fs.extension(path).takeIf { it.isNotEmpty() },
                    path = fs.toAbsolutePathString(path),
                    hidden = metadata.hidden,
                    contentType = fs.getFileContentType(path),
                    size = FileSize.Companion.of(path, fs),
                    content = content,
                )
            }
        }

        override suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit) {
            visitor(this)
        }

        @Serializable
        public sealed interface Content {
            @Serializable
            public data object None : Content

            @Serializable
            public data class Text(val text: String) : Content

            @Serializable
            public data class Excerpt(val snippets: List<Snippet>) : Content {
                public constructor(vararg snippets: Snippet) : this(snippets.toList())

                @Serializable
                public data class Snippet(
                    val text: String,
                    val range: DocumentProvider.DocumentRange
                )
            }

            public companion object {
                public fun of(content: String, ranges: List<DocumentProvider.DocumentRange>): Content {
                    return Excerpt(
                        ranges.map { range -> Excerpt.Snippet(range.substring(content), range)}
                    )
                }

                public fun of(content: String, skipLines: Int?, takeLines: Int?): Content {
                    val lines = content.lines()
                    var result = lines
                    if (skipLines != null) result = result.drop(skipLines)
                    if (takeLines != null) result = result.take(takeLines)

                    val text = result.joinToString("\n")
                    val truncated = result.size != lines.size
                    if (truncated) {
                        val start = DocumentProvider.Position(skipLines ?: 0, 0)
                        val end = DocumentProvider.Position(start.line + result.size, 0)

                        return Excerpt(
                            listOf(
                                Excerpt.Snippet(
                                    text = text,
                                    DocumentProvider.DocumentRange(start, end)
                                )
                            )
                        )
                    }

                    return Text(text)
                }
            }
        }

        public constructor(
            path: String,
            content: Content = Content.None,
            hidden: Boolean,
            contentType: FileMetadata.FileContentType,
            size: FileSize
        ) : this(
            name(path),
            extension(path),
            path,
            content,
            hidden,
            contentType,
            listOf(size)
        )
    }

    @Serializable
    public data class Folder(
        override val name: String,
        override val path: String,
        val entries: List<FileSystemEntry>? = null,
        override val hidden: Boolean,
    ) : FileSystemEntry {
        override val extension: String? = null


        public companion object {
            public suspend fun <Path> of(
                path: Path,
                entries: List<FileSystemEntry>? = null,
                fs: FileSystemProvider.ReadOnly<Path>
            ): Folder? {
                val metadata = fs.metadata(path) ?: return null
                return Folder(
                    name = fs.name(path),
                    path = fs.toAbsolutePathString(path),
                    entries = entries,
                    hidden = metadata.hidden,
                )
            }
        }

        override suspend fun visit(depth: Int, visitor: suspend (FileSystemEntry) -> Unit) {
            visitor(this)
            if (depth == 0) return
            entries?.forEach { it.visit(depth - 1, visitor) }
        }

        public constructor(path: String, entries: List<FileSystemEntry>? = null, hidden: Boolean) : this(
            name(path),
            path,
            entries,
            hidden
        )
    }
}

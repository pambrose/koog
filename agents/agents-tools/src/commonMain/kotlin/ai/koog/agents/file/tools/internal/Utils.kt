package ai.koog.agents.file.tools.internal

import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider

internal fun String.trimFilePathSeparator() = trimStart('/', '\\')

/**
 * Builds a list of metadata strings for a file, including content type, size, and hidden status.
 */
internal fun buildFileMetadata(file: FileSystemEntry.File): List<String> = buildList {
    if (file.contentType != FileMetadata.FileContentType.Text) add(file.contentType.display)
    add(file.size.joinToString(", ") { it.display() })
    if (file.hidden) add("hidden")
}

internal suspend fun<Path> listDirectory(
    path: Path,
    fs: FileSystemProvider.ReadOnly<Path>,
    maxDepth: Int,
    unwrapSingleFolderPaths: Boolean = true,
    filter: GlobPattern? = null,
    currentDepth: Int = 0,
): FileSystemEntry? {
    val metadata = fs.metadata(path) ?: return null

    when (metadata.type) {
        FileMetadata.FileType.File -> {
            if (currentDepth > maxDepth) return null
            if (filter?.matches(fs.name(path)) == false) return null
            return FileSystemEntry.File.of(
                path = path,
                fs = fs
            )
        }

        FileMetadata.FileType.Directory -> {
            val files = fs.list(path)
            if ((currentDepth > maxDepth) && (!unwrapSingleFolderPaths || files.size > 1)) return null
            val entries = if (currentDepth < maxDepth || (unwrapSingleFolderPaths && files.size == 1)) {
                files.mapNotNull {
                    listDirectory(it, fs, maxDepth, unwrapSingleFolderPaths, filter, currentDepth + 1)
                }

            } else emptyList()
            return FileSystemEntry.Folder.of(
                path = path,
                entries = entries,
                fs = fs
            )
        }
    }
}

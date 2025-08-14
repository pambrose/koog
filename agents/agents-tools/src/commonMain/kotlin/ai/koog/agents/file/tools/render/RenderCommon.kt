package ai.koog.agents.file.tools.render

import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.rag.base.files.FileMetadata


internal fun String.trimFilePathSeparator() = trimStart('/', '\\')

/**
 * Builds a list of metadata strings for a file, including content type, size, and hidden status.
 */
internal fun buildFileMetadata(file: FileSystemEntry.File): List<String> = buildList {
    if (file.contentType != FileMetadata.FileContentType.Text) add(file.contentType.display)
    add(file.size.joinToString(", ") { it.display() })
    if (file.hidden) add("hidden")
}

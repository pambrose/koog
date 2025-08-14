package ai.koog.agents.file.tools.render

import ai.koog.agents.file.tools.buildFileMetadata
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.agents.file.tools.trimFilePathSeparator
import ai.koog.prompt.text.TextContentBuilder

public fun TextContentBuilder.entry(entry: FileSystemEntry, parent: FileSystemEntry? = null) {
    when (entry) {
        is FileSystemEntry.File -> file(entry, parent)
        is FileSystemEntry.Folder -> directory(entry, parent)
    }
}

public fun TextContentBuilder.directory(directory: FileSystemEntry.Folder, parent: FileSystemEntry? = null) {
    val entries = directory.entries
    val single = entries?.singleOrNull()
    if (single != null) {
        entry(single, parent)
        return
    }

    val displayPath = if (parent != null) {
        // Remove the root path prefix and show only the relative part
        val relativePath = directory.path.removePrefix(parent.path).trimFilePathSeparator()
        relativePath.ifEmpty { parent }
    } else {
        // This is the root directory, show full path
        directory.path
    }

    val meta = buildList {
        if (directory.hidden) add("hidden")
    }

    +"${displayPath}/${
        meta.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " (", postfix = ")") ?: ""
    }"
    if (directory.entries != null && directory.entries.isNotEmpty()) {
        padding(" ".repeat(2)) {
            for (entry in directory.entries) {
                when (entry) {
                    is FileSystemEntry.File -> file(entry, directory)
                    is FileSystemEntry.Folder -> directory(entry, directory)
                }
            }
        }
    }
}

private fun TextContentBuilder.codeblock(text: String) {
    +"```"
    +text
    +"```"
}

/** Renders file content (helper used by top-level and list item rendering). */
internal fun TextContentBuilder.file(file: FileSystemEntry.File, parent: FileSystemEntry? = null) {
    val displayPath = if (parent != null) {
        // Remove the root path prefix and show only the relative part
        val relativePath = file.path.removePrefix(parent.path).trimFilePathSeparator()
        relativePath.ifEmpty { file.name }
    } else {
        // This is the root level, show full path
        file.path
    }
    
    val meta = buildFileMetadata(file)
    +"$displayPath (${meta.joinToString(", ")})"
    when (val content = file.content) {
        is FileSystemEntry.File.Content.Excerpt -> {
            if (content.snippets.isNotEmpty()) {
                +"Excerpts:"
                content.snippets.forEach { snippet ->
                    +"Lines ${snippet.range.start.line}-${snippet.range.end.line}:"
                    codeblock(snippet.text.trim())
                }
            } else {
                +"(No excerpts)"
            }
        }

        is FileSystemEntry.File.Content.Text -> {
            +"Content:"
            codeblock(content.text.trim())
        }

        FileSystemEntry.File.Content.None -> {}
    }
}

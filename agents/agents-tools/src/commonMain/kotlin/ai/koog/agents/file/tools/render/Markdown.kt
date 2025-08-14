package ai.koog.agents.file.tools.render

import ai.koog.agents.file.tools.buildFileMetadata
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.agents.file.tools.trimFilePathSeparator
import ai.koog.prompt.markdown.MarkdownContentBuilder

public fun MarkdownContentBuilder.entry(entry: FileSystemEntry, parent: FileSystemEntry? = null) {
    when (entry) {
        is FileSystemEntry.File -> file(entry, parent)
        is FileSystemEntry.Folder -> directory(entry, parent)
    }
}

public fun MarkdownContentBuilder.directory(directory: FileSystemEntry.Folder, parent: FileSystemEntry? = null) {
    val entries = directory.entries
    val single = entries?.singleOrNull()
    if (single != null) {
        entry(single, parent)
        return
    }

    val displayPath = if (parent != null) {
        // Remove the root path prefix and show only the relative part
        val relativePath = directory.path.removePrefix(parent.path).trimFilePathSeparator()
        if (relativePath.isEmpty()) directory.name else relativePath
    } else {
        // This is the root directory, show full path
        directory.path.trimEnd { it == '/' } + "/"
    }

    line {
        code(displayPath + if (parent != null && !displayPath.endsWith("/")) "/" else "")
        if (directory.hidden) {
            space()
            text("(hidden)")
        }
    }
    if (directory.entries != null && directory.entries.isNotEmpty()) {
        bulleted {
            for (entry in directory.entries) {
                item {
                    when (entry) {
                        is FileSystemEntry.File -> file(entry, directory)
                        is FileSystemEntry.Folder -> directory(entry, directory)
                    }
                }
            }
        }
    }
}

/** Renders file content (helper used by top-level and list item rendering). */
public  fun MarkdownContentBuilder.file(file: FileSystemEntry.File, parent: FileSystemEntry? = null) {
    line {
        val displayPath = if (parent != null) {
            // Remove the root path prefix and show only the relative part
            val relativePath = file.path.removePrefix(parent.path).trimFilePathSeparator()
            if (relativePath.isEmpty()) file.name else relativePath
        } else {
            // This is the root level, show full path
            file.path
        }
        code(displayPath)

        val meta = buildFileMetadata(file)
        if (meta.isNotEmpty()) {
            space()
            text(meta.joinToString(", ", prefix = "(", postfix = ")"))
        }
    }
    when (val content = file.content) {
        is FileSystemEntry.File.Content.Excerpt -> {
            if (content.snippets.isEmpty()) return
            if (content.snippets.size == 1) {
                val snippet = content.snippets.single()
                line { bold("Lines ${snippet.range.start.line}-${snippet.range.end.line}:") }
                codeblock(snippet.text.trim())
                return
            }
            bulleted {
                for (snippet in content.snippets) {
                    item {
                        bold("Lines ${snippet.range.start.line}-${snippet.range.end.line}:")
                        newline()
                        codeblock(snippet.text.trim())
                    }
                }
            }
        }

        is FileSystemEntry.File.Content.Text -> {
            +"Content:"
            codeblock(content.text.trim(), "")
        }

        FileSystemEntry.File.Content.None -> {}
    }
}


package ai.koog.agents.file.tools.render

import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.prompt.xml.XmlContentBuilder

public fun XmlContentBuilder.entry(entry: FileSystemEntry) {
    when (entry) {
        is FileSystemEntry.File -> file(entry)
        is FileSystemEntry.Folder -> directory(entry)
    }
}

public fun XmlContentBuilder.file(file: FileSystemEntry.File) {
    tag(
        "file",
        attributes = listOfNotNull(
            "path" to file.path,
            "content_type" to file.contentType.display,
            if (file.hidden) "hidden" to "true" else null,
            "size" to file.size.joinToString(", ") { it.display() },
        ).let { linkedMapOf(*it.toTypedArray()) },
    ) {
        when (file.content) {
            is FileSystemEntry.File.Content.Excerpt -> {
                for (snippet in file.content.snippets) {
                    tag(
                        "snippet",
                        attributes = linkedMapOf(
                            "start_line" to (snippet.range.start.line).toString(),
                            "end_line" to (snippet.range.end.line).toString()
                        )
                    ) {
                        +snippet.text
                    }
                }
            }

            is FileSystemEntry.File.Content.Text -> {
                +file.content.text
            }

            FileSystemEntry.File.Content.None -> {}
        }
    }
}

public fun XmlContentBuilder.directory(directory: FileSystemEntry.Folder) {
    tag(
        "folder",
        attributes = listOfNotNull(
            "path" to directory.path,
            if (directory.hidden) "hidden" to "true" else null,
        ).let { linkedMapOf(*it.toTypedArray()) }
    ) {
        if (directory.entries != null) {
            for (entry in directory.entries) {
                when (entry) {
                    is FileSystemEntry.File -> file(entry)
                    is FileSystemEntry.Folder -> directory(entry)
                }
            }
        }
    }
}

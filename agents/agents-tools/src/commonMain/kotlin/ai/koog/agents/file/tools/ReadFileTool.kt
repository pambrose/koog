package ai.koog.agents.file.tools

import ai.koog.agents.core.tools.*
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.agents.file.tools.render.file
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.xml.xml
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import ai.koog.rag.base.files.readText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

public class ReadFileTool<Path>(
    private val fs: FileSystemProvider.ReadOnly<Path>,
) : Tool<ReadFileTool.Args, ReadFileTool.Result>() {
    public companion object {
        public val descriptor: ToolDescriptor = ToolDescriptor(
            name = "read_file",
            description = markdown {
                +"Reads the contents of a text file provided via file system path (e.g., '/home/user/config.json') and returns its as content within specified limit."
                +"Tool by default takes the first 300 lines ('takeLines') and skips the first 0 lines ('skipLines')."
                newline()

                +"When using tool carefully consider whether you need to read part of the file or the entire file."
                +"If the file is relatively small, it is recommended to read it fully via 'takeLines' == -1"
                +"If the file is relatively large, consider limiting the number of  lines to read via 'takeLines' parameter and 'skipLines' to read a window of the file."
            },
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = markdown {
                        +"The absolute path to the file to read. Must point to a file, not a directory. "
                        +"The path must be complete and valid within the current file system. "
                        +"Examples: \"/home/user/project/src/main.kt\" or \"/home/user/project/README.md\""
                    },
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "takeLines",
                    description = markdown {
                        +"Maximum number of lines to take since 'skipLines' position."
                        +"Default is 300 and you can specify -1 not to limit the number of lines returned."
                    },
                    type = ToolParameterType.Integer,
                ),
                ToolParameterDescriptor(
                    name = "skipLines",
                    description = markdown {
                        +"Number of lines to skip at the beginning of the file."
                        +"Default is 0 -- start of the file."
                    },
                    type = ToolParameterType.Integer,
                )
            )
        )
    }

    @Serializable
    public data class Result(val file: FileSystemEntry.File) : ToolResult.JSONSerializable<Result> {
        override fun getSerializer(): KSerializer<Result> = serializer()

        public fun toXML(): String = xml(indented = false) {
            file(file)
        }

        public fun toMarkdown(): String = markdown {
            file(file)
        }

        public override fun toStringDefault(): String = toMarkdown()
        public override fun toString(): String = toStringDefault()
    }

    @Serializable
    public data class Args(
        val path: String,
        val takeLines: Int = 300,
        val skipLines: Int = 0,
    ) : ToolArgs

    public override val argsSerializer: KSerializer<Args> = Args.serializer()

    public override val descriptor: ToolDescriptor = ReadFileTool.descriptor

    public override suspend fun execute(args: Args): Result {
        val path = fs.fromAbsolutePathString(args.path)

        validate(fs.exists(path)) { "File does not exist: ${args.path}" }

        validate(fs.metadata(path)?.type == FileMetadata.FileType.File) {
            "Path must point to a file, not a directory: ${args.path}"
        }
        val file = FileSystemEntry.File.of(
            path,
            content = FileSystemEntry.File.Content.of(
                fs.readText(path),
                args.skipLines,
                args.takeLines.takeIf { it >= 0 }
            ),
            fs = fs
        ) ?: fail("Unable to read file that does not exist: ${args.path}")
        return Result(file)
    }
}

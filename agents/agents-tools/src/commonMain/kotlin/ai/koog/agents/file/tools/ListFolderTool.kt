package ai.koog.agents.file.tools

import ai.koog.agents.core.tools.*
import ai.koog.agents.file.tools.model.filter.GlobPattern
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.agents.file.tools.render.directory
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.text.text
import ai.koog.prompt.xml.xml
import ai.koog.rag.base.files.FileMetadata
import ai.koog.rag.base.files.FileSystemProvider
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

public class ListFolderTool<Path>(
    private val fs: FileSystemProvider.ReadOnly<Path>,
) : Tool<ListFolderTool.Args, ListFolderTool.Result>() {

    @Serializable
    public  data class Result(val root: FileSystemEntry.Folder) : ToolResult.JSONSerializable<Result> {
        override fun getSerializer(): KSerializer<Result> = serializer()

        public fun toXML(): String = xml {
            directory(root)
        }

        public  fun toMarkdown(): String = markdown {
            directory(root)
        }

        public  fun toPlain(): String = text {
            directory(root)
        }

        override fun toStringDefault(): String = toPlain()
        override fun toString(): String = toStringDefault()
    }

    public companion object {
        public val descriptor: ToolDescriptor = ToolDescriptor(
            name = "list_folder",
            description = markdown {
                +"Lists the contents of a directory at the specified path and returns a formatted list of entries."

                +"This tool explores directory structures in the file system, showing both files and subdirectories with metadata."
                +"It should be used when you need to browse directory contents, locate files, or understand project structure."

                +"The tool accepts absolute file system paths (e.g., '/project/src') and returns organized directory listings."

                +"This tool does NOT:"
                bulleted {
                    item("Create or modify directories or files")
                    item("Execute files within directories")
                    item("Access restricted directories without proper permissions")
                    item("Return file contents (use read_file for that purpose)")
                }

                +"Example scenarios for using this tool:"
                bulleted {
                    item("Exploring project structure to locate specific files or directories")
                    item("Identifying all files matching a particular pattern or extension")
                    item("Examining nested directory hierarchies to understand organization")
                    item("Verifying the presence of expected files within a directory")
                }
            },
            requiredParameters = listOf(
                ToolParameterDescriptor(
                    name = "path",
                    description = markdown {
                        +"The absolute path to the directory to list."
                        +"Must point to a directory, not a file."
                    },
                    type = ToolParameterType.String
                )
            ),
            optionalParameters = listOf(
                ToolParameterDescriptor(
                    name = "unwrap_single_folder_paths",
                    description = markdown {
                        +"Controls automatic unwrapping of single-item directories."
                        +"When true (default), automatically navigates through directories containing only one item until reaching multiple items or a file."
                        +"Set to false to return exactly the specified directory level without unwrapping."
                    },
                    type = ToolParameterType.Boolean
                ),
                ToolParameterDescriptor(
                    name = "file_filter",
                    description = markdown {
                        +"A glob pattern to filter directory contents."
                        +"Use patterns like '*.txt' for text files, '*.{jpg,png}' for images, or '**/test/*.java' for Java test files."
                        +"Default is '**' which includes all files and directories."
                    },
                    type = ToolParameterType.String,
                ),
                ToolParameterDescriptor(
                    name = "depth",
                    description = markdown {
                        +"The maximum recursion depth for listing directory contents."
                        +"1 (default): Lists only direct contents of the specified directory."
                        +"2: Lists direct contents plus contents of immediate subdirectories."
                        +"Higher values increase the depth of recursion accordingly."
                        +"Note: Even with depth=1, single-item directories will still be unwrapped if unwrap_single_folder_paths=true."
                        +"For large directory structures, higher depth values may impact performance."
                    },
                    type = ToolParameterType.Integer
                )
            )
        )
    }

    @Serializable
    public data class Args(
        val path: String,
        val depth: Int = 1,
        val unwrapSingleFolderPaths: Boolean = true,
        val filter: String? = null
    ) : ToolArgs

    public override val argsSerializer: KSerializer<Args> = Args.serializer()

    public override val descriptor: ToolDescriptor = ListFolderTool.descriptor

    public override suspend fun execute(args: Args): Result {
        val path = fs.fromAbsolutePathString(args.path)

        validate(fs.exists(path)) { "Path does not exist: ${args.path}" }
        validate(fs.metadata(path)?.type == FileMetadata.FileType.Directory) {
            "Path must be a directory: ${args.path}"
        }
        validate(args.depth >= 1) { "Depth must be more than zero ${args.depth}" }

        val entry = listDirectory(
            fs.fromAbsolutePathString(args.path),
            fs,
            args.depth,
            args.unwrapSingleFolderPaths,
            GlobPattern.compile(args.filter ?: "**", caseSensitive = false),
            0
        )

        return Result(entry as FileSystemEntry.Folder)
    }
}

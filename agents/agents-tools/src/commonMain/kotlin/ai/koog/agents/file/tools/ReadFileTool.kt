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

/**
 * Reads a text file with optional line windowing.
 *
 * This tool returns text content from a file without loading the entire file into memory.
 * It allows skipping lines at the start and limiting the number of lines read.
 *
 * Typical use cases:
 * - Reading large logs or config files
 * - Skipping headers or comments
 * - Previewing file contents
 *
 * ### Parameters:
 * - `path`: Absolute file path. Must exist and point to a regular file.
 * - `skipLines`: Number of lines to skip at the beginning (default: 0).
 * - `takeLines`: Max number of lines to return after skipping (default: 300).
 *    Use -1 to read all remaining lines.
 *
 * ### Result:
 * Returns a file entry with content (windowed) and metadata.
 *
 * ### Example:
 * ```kotlin
 * val tool = ReadFileTool(JVMFileSystemProvider.ReadOnly)
 * val result = tool.execute(ReadFileTool.Args(
 *     path = "/var/log/app.log",
 *     skipLines = 10,
 *     takeLines = 100
 * ))
 * println(result.file.content.text)
 * ```
 */
public class ReadFileTool<Path>(
	private val fs: FileSystemProvider.ReadOnly<Path>,
) : Tool<ReadFileTool.Args, ReadFileTool.Result>() {

	public companion object {
		public val descriptor: ToolDescriptor = ToolDescriptor(
			name = "read-file",
			description = markdown {
				+"Reads a text file at a given absolute path (e.g., '/home/user/file.txt')."
				+"Returns content with optional windowing using 'skipLines' and 'takeLines'."
				newline()
				+"Defaults: 'takeLines' = 300, 'skipLines' = 0."
				+"Use 'takeLines = -1' to read full file after skip."
			},
			requiredParameters = listOf(
				ToolParameterDescriptor(
					name = "path",
					description = markdown {
						+"Absolute path to a file (not directory)."
						+"Must be valid within the current file system."
					},
					type = ToolParameterType.String
				)
			),
			optionalParameters = listOf(
				ToolParameterDescriptor(
					name = "takeLines",
					description = markdown {
						+"Max number of lines to return after skipping."
						+"Use -1 to disable limit."
					},
					type = ToolParameterType.Integer,
				),
				ToolParameterDescriptor(
					name = "skipLines",
					description = markdown {
						+"Lines to skip at file start before reading."
					},
					type = ToolParameterType.Integer,
				)
			)
		)
	}

	/**
	 * Contains the result of a file read operation with windowed content and metadata.
	 *
	 * The content includes only the lines specified by the windowing parameters from the original request.
	 * If line windowing was applied, the content reflects only the requested slice of the file.
	 * Metadata includes the original file path, total file size, and windowing information.
	 *
	 * @property file File entry containing both content and metadata.
	 *                Access the text content via `file.content.text`.
	 *                Access metadata via `file.metadata` (path, size, etc.).
	 */
	@Serializable
	public data class Result(val file: FileSystemEntry.File) : ToolResult.JSONSerializable<Result> {
		override fun getSerializer(): KSerializer<Result> = serializer()

		/**
		 * Formats the file result as compact XML.
		 *
		 * Generates XML containing file metadata (path, size, line counts) and content.
		 * The output is compact (non-indented) for efficient transmission and storage.
		 *
		 * @return XML string with structured file information and content.
		 */
		public fun toXML(): String = xml(indented = false) { file(file) }

		/**
		 * Formats the file result as Markdown.
		 *
		 * Generates human-readable Markdown with file metadata in headers and content
		 * in fenced code blocks. Suitable for documentation, logs, and display in
		 * Markdown-aware interfaces.
		 *
		 * @return Markdown formatted string with file information and content.
		 */
		public fun toMarkdown(): String = markdown { file(file) }

		/** Default string form (Markdown). */
		override fun toStringDefault(): String = toMarkdown()

		/** Same as `toStringDefault()` (Markdown). */
		override fun toString(): String = toStringDefault()
	}

	/**
	 * Parameters that specify which file to read and how to apply line windowing.
	 *
	 * All parameters are validated during execution. Invalid values will result in
	 * IllegalArgumentException being thrown before any file operations occur.
	 *
	 * @param path Absolute path to the file to read. Must point to an existing regular file,
	 *             not a directory. The path is resolved using the provided FileSystemProvider.
	 * @param takeLines Maximum number of lines to read after skipping (default: 300).
	 *                  Use -1 to read all remaining lines without limit.
	 *                  Use 0 to read no content (metadata only).
	 *                  Must be >= -1.
	 * @param skipLines Number of lines to skip from the beginning of the file (default: 0).
	 *                  Useful for skipping headers, comments, or reaching specific file sections.
	 *                  Must be >= 0.
	 */
	@Serializable
	public data class Args(
		val path: String,
		val takeLines: Int = 300,
		val skipLines: Int = 0,
	) : ToolArgs


	override val argsSerializer: KSerializer<Args> = Args.serializer()
	override val descriptor: ToolDescriptor = ReadFileTool.descriptor

	/**
	 * Reads text content from a file with optional line windowing applied.
	 *
	 * This method performs the following operations in sequence:
	 * 1. Validates that the specified path exists in the filesystem
	 * 2. Verifies that the path points to a regular file (not a directory)
	 * 3. Validates the windowing parameters (`skipLines >= 0`, `takeLines >= -1`)
	 * 4. Reads the entire file content into memory
	 * 5. Applies line windowing by skipping the first `skipLines` lines and taking up to `takeLines` lines
	 * 6. Wraps the windowed content and file metadata in a Result object
	 *
	 * Line windowing behavior:
	 * - If `skipLines > 0`: skips the specified number of lines from the beginning of the file
	 * - If `takeLines = -1`: reads all remaining lines after skipping (no limit)
	 * - If `takeLines > 0`: reads at most the specified number of lines after skipping
	 * - If `takeLines = 0`: returns empty content (only metadata)
	 *
	 * @param args Contains the file path and windowing parameters. The path must be absolute.
	 * @return Result object containing the windowed file content accessible via `result.file.content.text`,
	 *         plus file metadata including path, size, and line count information.
	 * @throws IllegalArgumentException if the path doesn't exist in the filesystem,
	 *         if the path points to a directory instead of a regular file,
	 *         if `skipLines` is negative, or if `takeLines` is less than -1.
	 * @throws kotlinx.io.IOException if an I/O error occurs while reading the file content.
	 */
	override suspend fun execute(args: Args): Result {
		val path = fs.fromAbsolutePathString(args.path)

		validate(fs.exists(path)) { "File does not exist: ${args.path}" }
		validate(fs.metadata(path)?.type == FileMetadata.FileType.File) {
			"Path must point to a file, not a directory: ${args.path}"
		}
		validate(args.skipLines >= 0) { "skipLines must be >= 0: ${args.skipLines}" }
		validate(args.takeLines >= -1) { "takeLines must be >= -1: ${args.takeLines}" }

		val file = FileSystemEntry.File.of(
			path,
			content = FileSystemEntry.File.Content.of(
				fs.readText(path),
				args.skipLines,
				args.takeLines.takeIf { it >= 0 }
			),
			fs = fs
		) ?: fail("Unable to read file: ${args.path}")

		return Result(file)
	}
}


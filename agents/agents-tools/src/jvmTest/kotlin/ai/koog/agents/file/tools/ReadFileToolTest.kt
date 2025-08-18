package ai.koog.agents.file.tools

import ai.koog.agents.core.tools.DirectToolCallsEnabler
import ai.koog.agents.core.tools.annotations.InternalAgentToolsApi
import ai.koog.agents.file.tools.model.FileSystemEntry
import ai.koog.rag.base.files.JVMFileSystemProvider
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail
import kotlinx.coroutines.test.runTest

@OptIn(InternalAgentToolsApi::class) object Enabler : DirectToolCallsEnabler

@OptIn(InternalAgentToolsApi::class)
class ReadFileToolTest {

    private fun tempDir(): Path = Files.createTempDirectory("read-file-tool-test").toAbsolutePath()

    private fun writeTempFile(dir: Path, name: String, lines: List<String>): Path {
        val file = dir.resolve(name)
        file.writeText(lines.joinToString("\n"))
        return file
    }

    // Reads a whole small file using default parameters, expecting full Text content.
    @Test
    fun `reads full small file with defaults`() = runTest {
        val dir = tempDir()
        val content = listOf("a", "b", "c")
        val file = writeTempFile(dir, "small.txt", content)

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        val result = tool.execute(ReadFileTool.Args(path = file.toString()), Enabler)
        val fileEntry = result.fileEntry
        val textContent = assertIs<FileSystemEntry.File.Content.Text>(fileEntry.content)
        assertEquals(content.joinToString("\n"), textContent.text)
    }

    // Applies skipLines and takeLines windowing; expects Excerpt with the selected lines.
    @Test
    fun `windowing skipLines and takeLines produces excerpt`() = runTest {
        val dir = tempDir()
        val lines = (1..10).map { "L$it" }
        val file = writeTempFile(dir, "ten.txt", lines)

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        val result =
            tool.execute(
                ReadFileTool.Args(path = file.toString(), skipLines = 2, takeLines = 3),
                Enabler,
            )
        val fileEntry = result.fileEntry
        val excerpt = assertIs<FileSystemEntry.File.Content.Excerpt>(fileEntry.content)
        assertEquals(1, excerpt.snippets.size)
        assertEquals(listOf("L3", "L4", "L5").joinToString("\n"), excerpt.snippets.first().text)
    }

    // With takeLines = -1, reads all remaining lines after skipping N lines.
    @Test
    fun `takeLines -1 reads all remaining after skip`() = runTest {
        val dir = tempDir()
        val lines = (1..10).map { "L$it" }
        val file = writeTempFile(dir, "ten_all_after_skip.txt", lines)

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        val result =
            tool.execute(
                ReadFileTool.Args(path = file.toString(), skipLines = 8, takeLines = -1),
                Enabler,
            )
        val fileEntry = result.fileEntry
        val excerpt = assertIs<FileSystemEntry.File.Content.Excerpt>(fileEntry.content)
        assertEquals(1, excerpt.snippets.size)
        assertEquals(listOf("L9", "L10").joinToString("\n"), excerpt.snippets.first().text)
    }

    // Fails with a validation error when the target file path does not exist.
    @Test
    fun `non-existent path throws`() = runTest {
        val dir = tempDir()
        val missing = dir.resolve("missing.txt")

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        try {
            tool.execute(ReadFileTool.Args(path = missing.toString()), Enabler)
            fail("Expected IllegalArgumentException for missing file")
        } catch (e: ai.koog.agents.core.tools.ToolException.ValidationFailure) {
            assertEquals(
                e.message.startsWith("File does not exist:"),
                true,
                "Unexpected message: ${e.message}",
            )
        }
    }

    // Fails with a validation error when given a directory path instead of a file.
    @Test
    fun `directory path throws`() = runTest {
        val dir = tempDir()

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        try {
            tool.execute(ReadFileTool.Args(path = dir.toString()), Enabler)
            fail("Expected IllegalArgumentException for directory path")
        } catch (e: ai.koog.agents.core.tools.ToolException.ValidationFailure) {
            assertEquals(
                e.message.startsWith("Path must point to a file, not a directory:"),
                true,
                "Unexpected message: ${e.message}",
            )
        }
    }

    // Empty file with default params returns empty Text content.
    @Test
    fun `empty file with defaults returns empty text`() = runTest {
        val dir = tempDir()
        val file = writeTempFile(dir, "empty.txt", emptyList())

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        val result = tool.execute(ReadFileTool.Args(path = file.toString()), Enabler)
        val fileEntry = result.fileEntry
        val textContent = assertIs<FileSystemEntry.File.Content.Text>(fileEntry.content)
        assertEquals("", textContent.text, "Empty file should yield empty Text content")
    }

    // If skipLines exceeds file length, returns an Excerpt with an empty snippet.
    @Test
    fun `skipLines greater than file length yields empty excerpt`() = runTest {
        val dir = tempDir()
        val lines = listOf("only one line")
        val file = writeTempFile(dir, "single_line.txt", lines)

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        val result =
            tool.execute(
                ReadFileTool.Args(path = file.toString(), skipLines = 10, takeLines = 300),
                Enabler,
            )
        val fileEntry = result.fileEntry
        val excerpt = assertIs<FileSystemEntry.File.Content.Excerpt>(fileEntry.content)
        assertEquals(1, excerpt.snippets.size)
        assertEquals(
            "",
            excerpt.snippets.first().text,
            "Excessive skip should produce empty snippet text",
        )
    }

    @Test
    fun `takeLines 0 yields empty excerpt`() = runTest {
        val dir = tempDir()
        val file = writeTempFile(dir, "three.txt", listOf("a", "b", "c"))

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        val result =
            tool.execute(
                ReadFileTool.Args(path = file.toString(), skipLines = 0, takeLines = 0),
                Enabler,
            )
        val fileEntry = result.fileEntry
        val excerpt = assertIs<FileSystemEntry.File.Content.Excerpt>(fileEntry.content)
        assertEquals(1, excerpt.snippets.size)
        assertEquals(
            "",
            excerpt.snippets.first().text,
            "Zero takeLines should produce empty snippet text",
        )
    }

    // Validation: negative skipLines should throw a validation error.
    @Test
    fun `negative skipLines throws`() = runTest {
        val dir = tempDir()
        val file = writeTempFile(dir, "neg_skip.txt", listOf("x", "y"))

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        try {
            tool.execute(ReadFileTool.Args(path = file.toString(), skipLines = -1), Enabler)
            fail("Expected validation failure for negative skipLines")
        } catch (e: ai.koog.agents.core.tools.ToolException.ValidationFailure) {
            assertEquals(
                e.message.startsWith("skipLines must be >= 0:"),
                true,
                "Unexpected message: ${e.message}",
            )
        }
    }

    // Validation: takeLines less than -1 should throw a validation error.
    @Test
    fun `takeLines less than minus one throws`() = runTest {
        val dir = tempDir()
        val file = writeTempFile(dir, "neg_take.txt", listOf("x", "y"))

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        try {
            tool.execute(ReadFileTool.Args(path = file.toString(), takeLines = -2), Enabler)
            fail("Expected validation failure for takeLines < -1")
        } catch (e: ai.koog.agents.core.tools.ToolException.ValidationFailure) {
            assertEquals(
                e.message.startsWith("takeLines must be >= -1:"),
                true,
                "Unexpected message: ${e.message}",
            )
        }
    }

    // Defaults should truncate to 300 lines producing an Excerpt (first 300 lines only).
    @Test
    fun `defaults truncate to 300 lines producing excerpt`() = runTest {
        val dir = tempDir()
        val lines = (1..350).map { "L$it" }
        val file = writeTempFile(dir, "three_fifty.txt", lines)

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        val result = tool.execute(ReadFileTool.Args(path = file.toString()), Enabler)
        val excerpt = assertIs<FileSystemEntry.File.Content.Excerpt>(result.fileEntry.content)
        assertEquals(1, excerpt.snippets.size)
        val snippetText = excerpt.snippets.first().text
        val snippetLines = snippetText.lines()
        assertEquals(300, snippetLines.size)
        assertEquals("L1", snippetLines.firstOrNull())
        assertEquals("L300", snippetLines.lastOrNull())
    }

    // No skip and takeLines = -1 should return full Text (no truncation).
    @Test
    fun `takeLines -1 with no skip returns full text`() = runTest {
        val dir = tempDir()
        val lines = (1..10).map { "L$it" }
        val file = writeTempFile(dir, "ten_full.txt", lines)

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        val result =
            tool.execute(
                ReadFileTool.Args(path = file.toString(), skipLines = 0, takeLines = -1),
                Enabler,
            )
        val textContent = assertIs<FileSystemEntry.File.Content.Text>(result.fileEntry.content)
        assertEquals(lines.joinToString("\n"), textContent.text)
    }

    // Excerpt range metadata should match skip and take (start.line == skip, end.line == skip +
    // take).
    @Test
    fun `excerpt range lines match skip and take`() = runTest {
        val dir = tempDir()
        val lines = (1..10).map { "L$it" }
        val file = writeTempFile(dir, "ten_range.txt", lines)

        val fs = JVMFileSystemProvider.ReadOnly
        val tool = ReadFileTool(fs)

        val skip = 2
        val take = 3
        val result =
            tool.execute(
                ReadFileTool.Args(path = file.toString(), skipLines = skip, takeLines = take),
                Enabler,
            )
        val excerpt = assertIs<FileSystemEntry.File.Content.Excerpt>(result.fileEntry.content)
        val snippet = excerpt.snippets.first()
        assertEquals(skip, snippet.range.start.line)
        assertEquals(skip + take, snippet.range.end.line)
    }
}

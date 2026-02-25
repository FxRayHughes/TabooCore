package taboocore.console

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

/**
 * Brigadier 补全器。
 * Provider 返回格式: [rangeStart, text1, text2, ...]
 */
class ConsoleCompleter : Completer {

    override fun complete(reader: LineReader, line: ParsedLine, candidates: MutableList<Candidate>) {
        val provider = TabooCoreConsole.completionProvider ?: return
        val rawInput = line.line()
        val input = rawInput.removePrefix("/")
        try {
            val result = provider.apply(input)
            if (result.size < 2) return
            val rangeStart = result[0].toIntOrNull() ?: return
            for (i in 1 until result.size) {
                candidates.add(Candidate(result[i]))
            }
        } catch (_: Exception) {
        }
    }
}

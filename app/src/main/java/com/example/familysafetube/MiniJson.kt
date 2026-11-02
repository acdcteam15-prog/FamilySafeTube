package com.example.familysafetube

class JsonException(message: String) : Exception(message)

/**
 * Small, strict JSON parser (objects, arrays, strings, numbers, booleans, null) used for the remote
 * settings file. Written here rather than using org.json so it runs in plain JVM unit tests.
 * Values: Map<String, Any?>, List<Any?>, String, Double, Boolean, null.
 */
object MiniJson {
    private const val MAX_DEPTH = 20

    /** Serialises Map / List / String / Number / Boolean / null back to compact JSON. */
    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v.toString())
            is Double -> sb.append(if (v.isFinite() && v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else if (v.isFinite()) v.toString() else "null")
            is Float -> write(sb, v.toDouble())
            is String -> quote(sb, v)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString()); sb.append(':'); write(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (item in v) { if (!first) sb.append(','); first = false; write(sb, item) }
                sb.append(']')
            }
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value(0)
        p.skipWs()
        if (!p.atEnd()) throw JsonException("Unexpected trailing data at ${p.pos}")
        return v
    }

    private class Parser(private val s: String) {
        var pos = 0
        fun atEnd() = pos >= s.length
        fun skipWs() { while (pos < s.length && s[pos].isWhitespace()) pos++ }
        private fun peek(): Char = if (pos < s.length) s[pos] else throw JsonException("Unexpected end")
        private fun expect(c: Char) { if (peek() != c) throw JsonException("Expected '$c' at $pos"); pos++ }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) throw JsonException("Nesting too deep")
            return when (val c = peek()) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw JsonException("Unexpected '$c' at $pos")
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, pos)) throw JsonException("Bad literal at $pos")
            pos += word.length
            return v
        }

        private fun num(): Double {
            val start = pos
            if (peek() == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            return s.substring(start, pos).toDoubleOrNull() ?: throw JsonException("Bad number at $start")
        }

        private fun str(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) throw JsonException("Unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) throw JsonException("Bad escape")
                        when (val e = s[pos++]) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) throw JsonException("Bad unicode escape")
                                sb.append(s.substring(pos, pos + 4).toIntOrNull(16)?.toChar() ?: throw JsonException("Bad unicode escape"))
                                pos += 4
                            }
                            else -> throw JsonException("Bad escape '\\$e'")
                        }
                    }
                    else -> if (c < ' ') throw JsonException("Control character in string") else sb.append(c)
                }
            }
        }

        private fun arr(depth: Int): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') { pos++; return out }
            while (true) {
                skipWs(); out.add(value(depth + 1)); skipWs()
                when (peek()) { ',' -> pos++; ']' -> { pos++; return out }; else -> throw JsonException("Expected , or ] at $pos") }
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') { pos++; return out }
            while (true) {
                skipWs()
                val k = str(); skipWs(); expect(':'); skipWs()
                out[k] = value(depth + 1); skipWs()
                when (peek()) { ',' -> pos++; '}' -> { pos++; return out }; else -> throw JsonException("Expected , or } at $pos") }
            }
        }
    }
}

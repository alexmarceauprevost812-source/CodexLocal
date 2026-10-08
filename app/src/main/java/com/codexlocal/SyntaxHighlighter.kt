package com.codexlocal

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.util.Locale

/**
 * Coloration syntaxique maison, sans dependance externe.
 *
 * Principe : une grosse expression reguliere a groupes nommes reconnait les
 * commentaires, chaines, nombres, annotations, mots et ponctuation ; chaque
 * famille recoit une couleur. Le langage est deduit de l'etiquette du bloc
 * de code (```kotlin, ```python, ...).
 *
 * Le rendu se fait en ajoutant des spans a un SpannableStringBuilder deja
 * rempli : `colorize(sb, offset, code, lang)`.
 */
object SyntaxHighlighter {

    // ------------------------------------------------------------------ couleurs
    private const val C_KEYWORD = 0xFFFF9A3D.toInt()
    private const val C_STRING = 0xFF9ECE6A.toInt()
    private const val C_COMMENT = 0xFF6B7280.toInt()
    private const val C_NUMBER = 0xFFFFC777.toInt()
    private const val C_FUNCTION = 0xFF7AA2F7.toInt()
    private const val C_TYPE = 0xFF2AC3DE.toInt()
    private const val C_ANNOTATION = 0xFFBB9AF7.toInt()
    private const val C_PUNCT = 0xFF9AA5B1.toInt()
    private const val C_TAG = 0xFFFF7A00.toInt()
    private const val C_ATTR = 0xFF2AC3DE.toInt()

    /** Au-dela, on ne colore pas : on garde l'affichage instantane. */
    private const val MAX_LENGTH = 24_000

    // ------------------------------------------------------------------ expressions

    private const val ALT_STR =
        """|(?<str>\"\"\"[\s\S]*?\"\"\"|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*'|`(?:\\.|[^`\\])*`)"""
    private const val ALT_NUM =
        """|(?<num>\b\d[\d_]*(?:\.\d+)?(?:[eE][+-]?\d+)?[fFlLuU]*\b|\b0[xX][0-9a-fA-F_]+\b)"""
    private const val ALT_REST =
        """|(?<ann>@[A-Za-z_]\w*)|(?<word>[A-Za-z_]\w*)|(?<punct>[{}()\[\];:,.<>=+\-*/%!&|^~?]+)"""

    /** Familles C / Java / Kotlin / JS / Go / Rust... : commentaires // et bloc. */
    private val RE_C = Regex(
        """(?<comment>//[^\n]*|/\*[\s\S]*?\*/)""" + ALT_STR + ALT_NUM + ALT_REST
    )

    /** Python, shell, YAML... : le diese commente. */
    private val RE_HASH = Regex(
        """(?<comment>#[^\n]*|//[^\n]*|/\*[\s\S]*?\*/)""" + ALT_STR + ALT_NUM + ALT_REST
    )

    /** SQL : -- commente. */
    private val RE_SQL = Regex(
        """(?<comment>--[^\n]*|/\*[\s\S]*?\*/)""" + ALT_STR + ALT_NUM + ALT_REST
    )

    /** Balisage XML / HTML. */
    private val RE_MARKUP = Regex(
        """(?<comment><!--[\s\S]*?-->)|(?<str>"[^"]*"|'[^']*')""" +
            """|(?<attr>[A-Za-z_][\w:.\-]*(?=\s*=))""" +
            """|(?<tag></?[A-Za-z_][\w:.\-]*|/?>)"""
    )

    // ------------------------------------------------------------------ listes

    private val HASH_LANGS = setOf(
        "py", "python", "py3", "sh", "bash", "zsh", "shell", "console", "yaml", "yml",
        "rb", "ruby", "pl", "perl", "r", "toml", "ini", "cfg", "conf", "properties",
        "dockerfile", "makefile", "ps1", "powershell", "cmake", "gitignore"
    )

    private val MARKUP_LANGS = setOf("xml", "html", "htm", "svg", "xaml", "vue", "cshtml", "xhtml")

    private val SQL_LANGS = setOf("sql", "mysql", "postgres", "postgresql", "sqlite", "tsql")

    private val KEYWORDS: Set<String> = hashSetOf(
        // structures / controle
        "if", "else", "elif", "unless", "then", "fi", "for", "foreach", "while", "do",
        "switch", "case", "default", "when", "match", "break", "continue", "return",
        "goto", "yield", "await", "async", "defer", "panic", "recover", "select",
        "try", "catch", "finally", "except", "raise", "throw", "throws", "assert",
        // declarations
        "fun", "func", "function", "fn", "def", "lambda", "val", "var", "let", "const",
        "class", "struct", "interface", "trait", "enum", "object", "record", "type",
        "typealias", "namespace", "module", "package", "import", "from", "export",
        "require", "include", "using", "use", "mod", "crate", "pub", "new", "delete",
        "init", "constructor", "companion", "extension", "protocol", "impl", "dyn",
        // modificateurs
        "public", "private", "protected", "internal", "static", "final", "abstract",
        "sealed", "open", "override", "virtual", "inline", "noinline", "crossinline",
        "reified", "suspend", "lateinit", "const", "mutable", "volatile", "transient",
        "synchronized", "native", "explicit", "extern", "register", "auto", "friend",
        "operator", "infix", "tailrec", "vararg", "out", "in", "as", "is", "where",
        "actual", "expect", "unsafe", "mut", "ref", "value", "data", "annotation",
        // types
        "void", "int", "long", "short", "byte", "char", "float", "double", "boolean",
        "bool", "string", "String", "any", "Any", "dynamic", "size_t", "unsigned",
        "signed", "struct", "union", "typedef", "typename", "template", "sizeof",
        // litteraux / valeurs
        "true", "false", "null", "nil", "None", "True", "False", "undefined", "NaN",
        "Infinity", "this", "self", "super", "base", "parent",
        // divers
        "with", "without", "pass", "global", "nonlocal", "and", "or", "not", "is",
        "instanceof", "typeof", "extends", "implements", "interface", "package",
        "print", "echo", "puts", "printf", "range", "len", "local", "end", "begin",
        // SQL (minuscules : la comparaison est insensible a la casse pour SQL)
        "select", "insert", "update", "delete", "create", "alter", "drop", "table",
        "index", "view", "join", "inner", "left", "right", "outer", "full", "cross",
        "on", "group", "order", "by", "having", "limit", "offset", "distinct", "union",
        "all", "exists", "between", "like", "asc", "desc", "into", "values", "set",
        "primary", "key", "foreign", "references", "constraint", "unique", "not",
        "and", "or", "null", "is", "as", "count", "sum", "avg", "min", "max", "case",
        "when", "then", "else", "end", "begin", "commit", "rollback", "transaction"
    )

    // ------------------------------------------------------------------ API

    /**
     * Colore le texte `code` deja present dans [sb] a partir de [offset].
     */
    fun colorize(sb: SpannableStringBuilder, offset: Int, code: String, lang: String) {
        if (code.length > MAX_LENGTH || code.isEmpty()) return
        val l = lang.trim().lowercase(Locale.US).substringBefore(' ')

        when {
            l in MARKUP_LANGS -> colorizeMarkup(sb, offset, code)
            l in SQL_LANGS -> colorizeGeneric(sb, offset, code, RE_SQL, foldCase = true)
            l in HASH_LANGS -> colorizeGeneric(sb, offset, code, RE_HASH, foldCase = false)
            else -> colorizeGeneric(sb, offset, code, RE_C, foldCase = false)
        }
    }

    // ------------------------------------------------------------------ interne

    private fun colorizeGeneric(
        sb: SpannableStringBuilder,
        offset: Int,
        code: String,
        regex: Regex,
        foldCase: Boolean
    ) {
        for (m in regex.findAll(code)) {
            val g = m.groups
            val start = offset + m.range.first
            val end = offset + m.range.last + 1
            when {
                g["comment"] != null -> span(sb, start, end, C_COMMENT, italic = true)
                g["str"] != null -> span(sb, start, end, C_STRING)
                g["num"] != null -> span(sb, start, end, C_NUMBER)
                g["ann"] != null -> span(sb, start, end, C_ANNOTATION)
                g["punct"] != null -> span(sb, start, end, C_PUNCT)
                g["word"] != null -> {
                    val w = m.value
                    val isKeyword = w in KEYWORDS ||
                        (foldCase && w.lowercase(Locale.US) in KEYWORDS)
                    when {
                        isKeyword -> span(sb, start, end, C_KEYWORD, bold = true)
                        w.length > 1 && w[0].isUpperCase() -> span(sb, start, end, C_TYPE)
                        isCall(sb, end) -> span(sb, start, end, C_FUNCTION)
                    }
                }
            }
        }
    }

    private fun colorizeMarkup(sb: SpannableStringBuilder, offset: Int, code: String) {
        for (m in RE_MARKUP.findAll(code)) {
            val g = m.groups
            val start = offset + m.range.first
            val end = offset + m.range.last + 1
            when {
                g["comment"] != null -> span(sb, start, end, C_COMMENT, italic = true)
                g["str"] != null -> span(sb, start, end, C_STRING)
                g["attr"] != null -> span(sb, start, end, C_ATTR)
                g["tag"] != null -> span(sb, start, end, C_TAG, bold = true)
            }
        }
    }

    /** Vrai si le prochain caractere non blanc est une parenthese ouvrante. */
    private fun isCall(sb: CharSequence, from: Int): Boolean {
        var i = from
        while (i < sb.length && (sb[i] == ' ' || sb[i] == '\t')) i++
        return i < sb.length && sb[i] == '('
    }

    private fun span(
        sb: SpannableStringBuilder,
        start: Int,
        end: Int,
        color: Int,
        bold: Boolean = false,
        italic: Boolean = false
    ) {
        if (start < 0 || end > sb.length || start >= end) return
        sb.setSpan(ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (bold || italic) {
            val style = (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0)
            sb.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

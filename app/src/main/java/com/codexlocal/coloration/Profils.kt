package com.codexlocal.coloration

import java.util.Locale

/** Les regles d'un langage « a la C » (ou a diese, ou SQL) : commentaires, chaines, mots reserves... */
internal class Profil(
    val commentairesLigne: List<String> = listOf("//"),
    val commentairesBloc: List<Pair<String, String>> = listOf("/*" to "*/"),
    /** Caracteres qui ouvrent une chaine. */
    val guillemets: String = "\"'`",
    /** Chaines a triple guillemet (Python, Kotlin...). */
    val triples: Boolean = false,
    val motsCles: Set<String> = emptySet(),
    /** Mots apres lesquels vient un nom qu'on declare : fun, class, def... */
    val declarations: Set<String> = emptySet(),
    val constantes: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val integres: Set<String> = emptySet(),
    val sensibleCasse: Boolean = true,
    /** $nom et ${nom} sont des variables (shell, PHP, Perl). */
    val variablesDollar: Boolean = false,
    /** Dans quelles chaines ${...} et $nom sont des variables (ex. "\"" ou "`"). */
    val interpolation: String = "",
    /** $nom sans accolades est aussi une variable dans ces chaines. */
    val interpolationSimple: Boolean = false,
    val annotations: Boolean = true,
    /** Lignes #include, #define... (C, C++, C#). */
    val directives: Boolean = false,
    /** Options de ligne de commande : -x, --long (shell, PowerShell). */
    val options: Boolean = false,
    /** « Mot » commencant par une majuscule = type ou classe. */
    val majusculeEstType: Boolean = true,
    /** Une chaine suivie de « : » est une cle (JSON). */
    val cleEntreGuillemets: Boolean = false,
    /** Les identifiants peuvent contenir « - » (Lisp, CSS, Dockerfile...). */
    val tiretDansMots: Boolean = false,
    /** Signes lus comme de la ponctuation et non comme des operateurs (JSON : `:`). */
    val ponctuationSeule: String = "",
    /** `objet.propriete` : le mot apres un point est une propriete ou un appel. */
    val proprietes: Boolean = true,
    /** `objet->membre` (C, PHP, Perl) ; ailleurs « -> » est une fleche de type ou de lambda. */
    val flecheMembre: Boolean = false
)

internal object Profils {

    private fun mots(texte: String): Set<String> = texte.split(' ', '\n').filter { it.isNotEmpty() }.toSet()

    private val C_LIKE_TYPES =
        "void int long short byte char float double boolean bool string size_t unsigned signed auto " +
            "uint8_t uint16_t uint32_t uint64_t int8_t int16_t int32_t int64_t"

    private val KOTLIN = Profil(
        triples = true,
        guillemets = "\"'",
        motsCles = mots(
            "if else for while do when break continue return throw try catch finally in is as " +
                "fun val var class interface object enum typealias package import constructor init " +
                "public private protected internal open final abstract sealed override data inline " +
                "noinline crossinline reified suspend lateinit const operator infix tailrec vararg " +
                "companion by get set where out this super annotation value expect actual external"
        ),
        declarations = mots("fun class interface object typealias val var enum"),
        constantes = mots("true false null"),
        types = mots("Int Long Short Byte Char Float Double Boolean String Any Unit Nothing Array List Map Set"),
        integres = mots("println print listOf mutableListOf mapOf setOf arrayOf require check error lazy"),
        interpolation = "\"", interpolationSimple = true
    )

    private val JAVA = Profil(
        triples = true,
        guillemets = "\"'",
        motsCles = mots(
            "if else for while do switch case default break continue return throw throws try catch finally " +
                "class interface enum extends implements new instanceof import package public private protected " +
                "static final abstract synchronized volatile transient native strictfp this super var record " +
                "sealed permits assert void yield"
        ),
        declarations = mots("class interface enum record"),
        constantes = mots("true false null"),
        types = mots(C_LIKE_TYPES),
        integres = mots("System out println print")
    )

    private val JS = Profil(
        guillemets = "\"'`",
        motsCles = mots(
            "if else for while do switch case default break continue return throw try catch finally " +
                "function class extends new delete typeof instanceof void in of var let const import export " +
                "from as async await yield static get set super this with debugger interface type enum " +
                "implements private public protected readonly abstract declare namespace module keyof"
        ),
        declarations = mots("function class interface type enum namespace let const var"),
        constantes = mots("true false null undefined NaN Infinity"),
        types = mots("string number boolean any unknown never object symbol bigint void"),
        integres = mots("console document window Math JSON Object Array Promise require module exports process"),
        interpolation = "`"
    )

    private val PYTHON = Profil(
        commentairesLigne = listOf("#"),
        commentairesBloc = emptyList(),
        triples = true,
        guillemets = "\"'",
        motsCles = mots(
            "if elif else for while break continue return raise try except finally with as import from " +
                "def class lambda pass yield global nonlocal assert del in is not and or async await match case self cls"
        ),
        declarations = mots("def class"),
        constantes = mots("True False None"),
        types = mots("int float str bool list dict set tuple bytes object"),
        integres = mots("print len range open input type isinstance enumerate zip map filter sorted sum min max abs super"),
        majusculeEstType = true
    )

    private val C = Profil(
        directives = true,
        guillemets = "\"'",
        motsCles = mots(
            "if else for while do switch case default break continue return goto struct union enum typedef " +
                "static const extern volatile register inline sizeof class namespace template typename using " +
                "public private protected virtual override final new delete this operator try catch throw " +
                "constexpr noexcept nullptr explicit friend mutable"
        ),
        declarations = mots("struct union enum class namespace typedef"),
        constantes = mots("true false NULL nullptr TRUE FALSE"),
        types = mots(C_LIKE_TYPES + " wchar_t FILE"),
        integres = mots("printf scanf malloc free memcpy strlen cout cin endl std"),
        flecheMembre = true
    )

    private val CSHARP = Profil(
        directives = true,
        guillemets = "\"'",
        motsCles = mots(
            "if else for foreach while do switch case default break continue return throw try catch finally " +
                "class struct interface enum namespace using new public private protected internal static readonly " +
                "const abstract sealed virtual override async await var in out ref is as this base params partial " +
                "get set where yield lock event delegate record init"
        ),
        declarations = mots("class struct interface enum namespace record"),
        constantes = mots("true false null"),
        types = mots(C_LIKE_TYPES + " object decimal"),
        integres = mots("Console Math String List Dictionary Task")
    )

    private val GO = Profil(
        guillemets = "\"'`",
        motsCles = mots(
            "if else for range switch case default break continue return goto go defer select chan func var const " +
                "type struct interface map package import fallthrough"
        ),
        declarations = mots("func type var const package"),
        constantes = mots("true false nil iota"),
        types = mots("int int8 int16 int32 int64 uint uint8 uint16 uint32 uint64 float32 float64 string bool byte rune error any"),
        integres = mots("fmt make new len cap append copy delete panic recover print println")
    )

    private val RUST = Profil(
        guillemets = "\"",
        motsCles = mots(
            "if else for while loop match break continue return fn let mut const static struct enum impl trait " +
                "type use mod pub crate self super as in ref move where async await dyn unsafe extern box"
        ),
        declarations = mots("fn struct enum trait type mod const static let"),
        constantes = mots("true false None Some Ok Err"),
        types = mots("i8 i16 i32 i64 i128 isize u8 u16 u32 u64 u128 usize f32 f64 bool char str String Vec Option Result Box"),
        integres = mots("println print format vec panic assert assert_eq todo unimplemented")
    )

    private val SWIFT = Profil(
        triples = true,
        guillemets = "\"",
        motsCles = mots(
            "if else for while repeat switch case default break continue return throw try catch guard defer in " +
                "func let var class struct enum protocol extension import init deinit subscript typealias public " +
                "private fileprivate internal open static final override mutating lazy weak unowned async await " +
                "where is as self super some any"
        ),
        declarations = mots("func class struct enum protocol extension typealias let var"),
        constantes = mots("true false nil"),
        types = mots("Int Double Float Bool String Character Array Dictionary Set Optional Any AnyObject Void"),
        integres = mots("print debugPrint fatalError assert")
    )

    private val DART = Profil(
        triples = true,
        guillemets = "\"'",
        motsCles = mots(
            "if else for while do switch case default break continue return throw try catch finally on rethrow " +
                "class extends implements with mixin abstract enum import export library part as is in new const final " +
                "var late static async await yield sync required factory get set typedef this super void dynamic"
        ),
        declarations = mots("class mixin enum typedef"),
        constantes = mots("true false null"),
        types = mots("int double num bool String List Map Set Future Stream Object dynamic void"),
        integres = mots("print"),
        interpolation = "\"'", interpolationSimple = true
    )

    private val PHP = Profil(
        commentairesLigne = listOf("//", "#"),
        guillemets = "\"'`",
        motsCles = mots(
            "if else elseif for foreach while do switch case default break continue return throw try catch finally " +
                "function class interface trait extends implements new use namespace public private protected static " +
                "final abstract const echo print require require_once include include_once global as instanceof clone " +
                "fn match yield isset unset empty list array die exit"
        ),
        declarations = mots("function class interface trait namespace const"),
        constantes = mots("true false null TRUE FALSE NULL"),
        types = mots("int float string bool array object mixed void callable iterable self parent"),
        integres = mots("strlen count array_map array_filter in_array str_replace json_encode json_decode var_dump"),
        variablesDollar = true, interpolation = "\"", interpolationSimple = true,
        flecheMembre = true
    )

    private val RUBY = Profil(
        commentairesLigne = listOf("#"),
        commentairesBloc = emptyList(),
        guillemets = "\"'`",
        motsCles = mots(
            "if elsif else unless case when then while until for in do end begin rescue ensure raise return yield " +
                "break next redo retry def class module self super alias undef and or not defined? require " +
                "require_relative include extend attr_accessor attr_reader attr_writer private public protected lambda proc loop"
        ),
        declarations = mots("def class module"),
        constantes = mots("true false nil"),
        integres = mots("puts print p gets"),
        interpolation = "\""
    )

    private val BASH = Profil(
        commentairesLigne = listOf("#"),
        commentairesBloc = emptyList(),
        guillemets = "\"'`",
        motsCles = mots(
            "if then elif else fi for while until do done case esac in function select time return exit break " +
                "continue local export readonly declare unset source alias set shift trap eval exec"
        ),
        declarations = mots("function"),
        constantes = mots("true false"),
        integres = mots(
            "echo printf cd ls cat grep sed awk find mkdir rm cp mv chmod chown curl wget git sudo apt npm pip " +
                "python python3 java gradle make test read pwd touch tar ssh scp kill ps docker"
        ),
        variablesDollar = true, interpolation = "\"", interpolationSimple = true,
        annotations = false, options = true, majusculeEstType = false,
        proprietes = false
    )

    private val POWERSHELL = Profil(
        commentairesLigne = listOf("#"),
        commentairesBloc = listOf("<#" to "#>"),
        guillemets = "\"'",
        motsCles = mots(
            "if elseif else foreach for while do switch break continue return throw try catch finally function " +
                "param begin process end in filter class enum using exit trap"
        ),
        declarations = mots("function filter class enum"),
        constantes = mots("true false null"),
        integres = mots("Write-Host Write-Output Get-ChildItem Set-Location Get-Content Set-Content Remove-Item New-Item"),
        sensibleCasse = false, variablesDollar = true, interpolation = "\"", interpolationSimple = true,
        annotations = false, options = true, majusculeEstType = false
    )

    private val SQL = Profil(
        commentairesLigne = listOf("--"),
        guillemets = "\"'`",
        motsCles = mots(
            "select insert update delete create alter drop table index view join inner left right outer full cross " +
                "on group order by having limit offset distinct union all exists between like in asc desc into values " +
                "set primary key foreign references constraint unique not and or is as case when then else end begin " +
                "commit rollback transaction from where default check add column database if with returning using " +
                "truncate grant revoke trigger procedure function"
        ),
        declarations = mots("table view index database"),
        constantes = mots("null true false"),
        types = mots("int integer bigint smallint varchar char text boolean bool date datetime timestamp float double decimal numeric real blob serial uuid json"),
        integres = mots("count sum avg min max coalesce now length lower upper substr round cast"),
        sensibleCasse = false, annotations = false, majusculeEstType = false
    )

    private val LUA = Profil(
        commentairesLigne = listOf("--"),
        commentairesBloc = listOf("--[[" to "]]"),
        guillemets = "\"'",
        motsCles = mots("if then elseif else end for while do repeat until break return function local in and or not goto"),
        declarations = mots("function local"),
        constantes = mots("true false nil"),
        integres = mots("print pairs ipairs require table string math os io type tostring tonumber"),
        annotations = false
    )

    private val GROOVY = Profil(
        triples = true,
        guillemets = "\"'",
        motsCles = mots(
            "if else for while do switch case default break continue return throw try catch finally def class " +
                "interface enum extends implements new import package public private protected static final abstract " +
                "in as instanceof this super assert plugins dependencies repositories android"
        ),
        declarations = mots("def class interface enum"),
        constantes = mots("true false null"),
        types = mots(C_LIKE_TYPES + " String Object List Map"),
        integres = mots("println implementation api compileOnly testImplementation id apply task"),
        interpolation = "\"", interpolationSimple = true
    )

    private val SCALA = Profil(
        triples = true,
        guillemets = "\"'",
        motsCles = mots(
            "if else for while do match case try catch finally throw return yield def val var lazy class object trait " +
                "extends with new import package type sealed abstract final override implicit private protected " +
                "given using enum extension this super then"
        ),
        declarations = mots("def val var class object trait type enum"),
        constantes = mots("true false null"),
        types = mots("Int Long Short Byte Char Float Double Boolean String Any AnyRef Unit Nothing Option List Map Seq"),
        integres = mots("println print"),
        interpolation = "\"", interpolationSimple = true
    )

    private val PERL = Profil(
        commentairesLigne = listOf("#"),
        commentairesBloc = emptyList(),
        guillemets = "\"'`",
        motsCles = mots("if elsif else unless while until for foreach do last next redo return sub my our local use no package require die"),
        declarations = mots("sub package"),
        constantes = mots("undef"),
        integres = mots("print printf say open close push pop shift unshift split join map grep sort keys values length"),
        variablesDollar = true, interpolation = "\"", interpolationSimple = true, majusculeEstType = false,
        flecheMembre = true
    )

    private val R = Profil(
        commentairesLigne = listOf("#"),
        commentairesBloc = emptyList(),
        guillemets = "\"'`",
        motsCles = mots("if else for while repeat break next return function in"),
        declarations = mots("function"),
        constantes = mots("TRUE FALSE NULL NA NaN Inf"),
        integres = mots("print cat library require c list data.frame paste length seq rep sum mean"),
        annotations = false, majusculeEstType = false
    )

    private val CMAKE = Profil(
        commentairesLigne = listOf("#"),
        commentairesBloc = listOf("#[[" to "]]"),
        guillemets = "\"",
        motsCles = mots(
            "if else elseif endif foreach endforeach while endwhile function endfunction macro endmacro " +
                "return break continue"
        ),
        declarations = mots("function macro"),
        constantes = mots("ON OFF TRUE FALSE YES NO"),
        integres = mots(
            "cmake_minimum_required project add_executable add_library add_subdirectory target_link_libraries " +
                "target_include_directories target_compile_definitions set option find_package include message install"
        ),
        sensibleCasse = false, annotations = false, majusculeEstType = false, interpolation = "\"",
        variablesDollar = false,
        proprietes = false
    )

    private val MAKE = Profil(
        commentairesLigne = listOf("#"),
        commentairesBloc = emptyList(),
        guillemets = "\"'",
        motsCles = mots("ifeq ifneq ifdef ifndef else endif include define endef export override"),
        constantes = mots("true false"),
        integres = mots("echo rm cp mv mkdir cd gcc g++ cc ld ar"),
        variablesDollar = true, annotations = false, majusculeEstType = false,
        proprietes = false
    )

    private val DOCKER = Profil(
        commentairesLigne = listOf("#"),
        commentairesBloc = emptyList(),
        guillemets = "\"'",
        motsCles = mots(
            "FROM RUN CMD LABEL EXPOSE ENV ADD COPY ENTRYPOINT VOLUME USER WORKDIR ARG ONBUILD STOPSIGNAL " +
                "HEALTHCHECK SHELL AS"
        ),
        sensibleCasse = false, variablesDollar = true, interpolation = "\"", annotations = false,
        options = true, majusculeEstType = false,
        proprietes = false
    )

    private val JSON = Profil(
        guillemets = "\"",
        constantes = mots("true false null"),
        annotations = false, majusculeEstType = false, cleEntreGuillemets = true, ponctuationSeule = ":"
    )

    /** Langage inconnu ou sans etiquette : l'ancien comportement (commentaires // et blocs, mots reserves courants). */
    private val GENERIQUE = Profil(
        triples = true,
        motsCles = mots(
            "if else elif for foreach while do switch case default when match break continue return goto yield " +
                "await async defer try catch finally except raise throw throws assert fun func function fn def lambda " +
                "val var let const class struct interface trait enum object record type typealias namespace module " +
                "package import from export require include using use mod pub new delete init constructor " +
                "public private protected internal static final abstract sealed open override virtual inline suspend " +
                "extends implements instanceof typeof with in is as not and or this self super"
        ),
        declarations = mots("fun func function fn def class struct interface trait enum object record type namespace module"),
        constantes = mots("true false null nil None True False undefined NaN"),
        types = mots(C_LIKE_TYPES + " any Any dynamic string String")
    )

    fun pour(langage: String): Profil = when (langage) {
        "kotlin" -> KOTLIN
        "java" -> JAVA
        "javascript", "typescript" -> JS
        "python" -> PYTHON
        "c", "cpp" -> C
        "csharp" -> CSHARP
        "go" -> GO
        "rust" -> RUST
        "swift" -> SWIFT
        "dart" -> DART
        "php" -> PHP
        "ruby" -> RUBY
        "bash" -> BASH
        "powershell" -> POWERSHELL
        "sql" -> SQL
        "lua" -> LUA
        "groovy" -> GROOVY
        "scala" -> SCALA
        "perl" -> PERL
        "r" -> R
        "cmake" -> CMAKE
        "makefile" -> MAKE
        "dockerfile" -> DOCKER
        "json" -> JSON
        else -> GENERIQUE
    }

    /** Noms courts et extensions ramenes a un nom de langage unique (« kt » -> « kotlin »). */
    fun normaliser(brut: String): String {
        val nom = brut.trim().lowercase(Locale.US).substringBefore(' ').substringBefore('{').trimStart('.')
        return ALIAS[nom] ?: nom
    }

    private val ALIAS: Map<String, String> = HashMap<String, String>().apply {
        fun alias(cible: String, vararg noms: String) = noms.forEach { put(it, cible) }
        alias("kotlin", "kt", "kts")
        alias("java", "jav")
        alias("javascript", "js", "jsx", "mjs", "cjs", "node", "nodejs")
        alias("typescript", "ts", "tsx", "mts", "cts")
        alias("python", "py", "py3", "python3", "pyw")
        alias("c", "h")
        alias("cpp", "c++", "cc", "cxx", "hpp", "hh", "hxx", "ino")
        alias("csharp", "cs", "c#")
        alias("go", "golang")
        alias("rust", "rs")
        alias("ruby", "rb", "rake", "gemspec")
        alias("bash", "sh", "zsh", "shell", "console", "ksh", "fish", "shellscript", "bats")
        alias("powershell", "ps1", "psm1", "pwsh")
        alias("sql", "mysql", "postgres", "postgresql", "sqlite", "tsql", "plsql", "psql")
        alias("groovy", "gradle")
        alias("perl", "pl", "pm")
        alias("makefile", "make", "mk")
        alias("yaml", "yml")
        alias("html", "htm", "xhtml", "vue", "svelte", "cshtml", "jsp", "erb", "ejs")
        alias("xml", "svg", "xaml", "plist", "pom", "rss", "atom", "xsl", "xslt", "wsdl", "manifest", "axml")
        alias("markdown", "md", "mdx", "rmd")
        alias("json", "json5", "jsonc", "jsonl", "geojson")
        alias("css", "scss", "sass", "less", "styl")
        alias("ini", "cfg", "conf", "properties", "env", "editorconfig", "gitconfig", "desktop")
        alias("plain", "txt", "text", "log", "csv", "tsv", "plaintext", "none", "output", "diff", "patch")
        alias("cmake", "cmakelists")
        alias("dockerfile", "docker", "containerfile")
        alias("lua", "luau")
        alias("scala", "sbt", "sc")
        alias("r", "rscript")
        alias("dart", "flutter")
        alias("php", "php3", "php4", "php5", "phtml")
        alias("swift", "swiftui")
    }
}

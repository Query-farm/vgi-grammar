package farm.query.vgi.grammar;

import farm.query.vgi.Worker;
import farm.query.vgi.catalog.CatalogTable;
import farm.query.vgi.internal.SchemaUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VGI worker entry point for LanguageTool grammar / style / spelling checking.
 *
 * <p>Attach from DuckDB with:
 * <pre>{@code
 * ATTACH 'grammar' (TYPE vgi, LOCATION 'java -jar vgi-grammar-all.jar');
 * SELECT * FROM grammar.grammar_check('She go to school every day.');
 * SELECT grammar.correct('She go to school every day.');
 * }</pre>
 */
public final class Main {

    private Main() {}

    public static final String GIT_COMMIT =
            System.getenv("VGI_GRAMMAR_GIT_COMMIT") != null
                    ? System.getenv("VGI_GRAMMAR_GIT_COMMIT") : "unknown";

    private static final String CATALOG = "grammar";
    private static final String SCHEMA = "main";

    /** Catalog-level metadata tags surfaced to DuckDB and the vgi-lint linter. */
    private static Map<String, String> catalogTags() {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("vgi.title", "Grammar, Style & Spelling Checker");
        t.put("vgi.keywords", Meta.keywordsJson(
                "grammar, spelling, spell check, style, proofread, proofreading, "
                        + "correct, autocorrect, languagetool, text quality, writing, linter, "
                        + "grammar check, typos, punctuation"));
        t.put("vgi.doc_llm",
                "Check grammar, style, and spelling of free text with LanguageTool and "
                        + "auto-correct it, directly in SQL. Use to find writing issues (one row "
                        + "per problem with rule, category, message, the flagged span, and suggested "
                        + "replacements), count issues, test whether text is clean, rewrite text by "
                        + "applying the top suggestion, and list the supported language codes. "
                        + "English (en-US, en-GB, en-CA, ...) is shipped.");
        t.put("vgi.doc_md",
                "# Grammar, Style & Spelling Checking in SQL\n\n"
                        + "![LanguageTool logo](https://upload.wikimedia.org/wikipedia/commons/"
                        + "thumb/4/45/LanguageTool_Logo.svg/250px-LanguageTool_Logo.svg.png)\n\n"
                        + "**Proofread, spell-check, and auto-correct natural-language text directly "
                        + "in DuckDB SQL** — grammar checking, style suggestions, typo detection, and "
                        + "one-call rewriting, powered by [LanguageTool](https://languagetool.org) and "
                        + "served to DuckDB over Apache Arrow.\n\n"
                        + "This VGI worker turns the open-source LanguageTool proofreading engine into "
                        + "a set of SQL functions, so you can clean up and quality-check text without "
                        + "leaving your database or calling an external API. It is built for data "
                        + "engineers, analysts, and anyone running text-heavy pipelines: validating "
                        + "user-generated content, normalizing product descriptions and support "
                        + "tickets, flagging typos in scraped or imported data, scoring writing "
                        + "quality, or batch-correcting columns of free text. Everything runs locally "
                        + "in the worker process — your text is never sent to a third-party service.\n\n"
                        + "Under the hood the worker embeds [LanguageTool](https://languagetool.org), "
                        + "the same multilingual grammar and style checker used by millions of writers. "
                        + "Each query runs against a cached `JLanguageTool` engine that applies "
                        + "LanguageTool's rule set (grammar, punctuation, style, and spelling rules) to "
                        + "your text and returns structured results. The English language pack "
                        + "(`en-US`, `en-GB`, `en-CA`, and other English variants) is shipped in the "
                        + "JAR; every function accepts an optional `language` argument that defaults to "
                        + "`en-US`. For implementation details and the rule catalog, see the "
                        + "[LanguageTool developer documentation](https://dev.languagetool.org) and "
                        + "the [LanguageTool source on GitHub]"
                        + "(https://github.com/languagetool-org/languagetool).\n\n"
                        + "**Table functions**\n\n"
                        + "- `grammar_check(text [, language])` — returns one row per issue with "
                        + "`rule_id`, `category`, `message`, the flagged span (`offset`, `length`, "
                        + "`bad_text`), and a `VARCHAR[]` of suggested replacements.\n"
                        + "- `grammar_languages()` — lists every supported language `code` and its "
                        + "human-readable `name`.\n\n"
                        + "**Scalar functions**\n\n"
                        + "- `grammar_count(text [, language])` — number of issues found in the text.\n"
                        + "- `is_grammatical(text [, language])` — `true` when the text has no issues.\n"
                        + "- `correct(text [, language])` — the text rewritten by applying the top "
                        + "suggestion for each fixable issue.\n\n"
                        + "Example: `SELECT * FROM grammar.main.grammar_check('She go to school every "
                        + "day.');` flags the subject-verb agreement error and suggests `goes`, while "
                        + "`SELECT grammar.main.correct('She go to school evry day.');` returns the "
                        + "cleaned-up sentence.");
        t.put("vgi.author", "Query.Farm");
        t.put("vgi.copyright", "Copyright 2026 Query Farm LLC - https://query.farm");
        // The worker's own code is MIT (see LICENSE); LanguageTool is bundled as an
        // unmodified, swappable LGPL-2.1 Maven dependency.
        t.put("vgi.license", "MIT");
        t.put("vgi.support_contact", "https://github.com/Query-farm/vgi-grammar/issues");
        t.put("vgi.support_policy_url",
                "https://github.com/Query-farm/vgi-grammar/blob/main/README.md");
        return t;
    }

    /** Schema-level metadata tags for the single {@code main} schema. */
    private static Map<String, String> schemaTags() {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("vgi.title", "Grammar — main");
        t.put("vgi.keywords", Meta.keywordsJson(
                "grammar, spelling, style, proofreading, languagetool, "
                        + "grammar_check, grammar_count, is_grammatical, correct, "
                        + "grammar_languages, text quality, writing"));
        // VGI123 classifying tags MUST use BARE keys (not vgi.-namespaced).
        t.put("domain", "text");
        t.put("category", "grammar-and-style");
        t.put("topic", "proofreading");
        // VGI139: per-object source_url is dropped; source_url lives only on the
        // catalog object (set via Worker.builder().sourceUrl(...)).
        t.put("vgi.doc_llm",
                "Grammar, style, and spelling functions: check text and get one row per issue "
                        + "with suggested fixes, count issues, test whether text is grammatical, "
                        + "auto-correct text, and list supported language codes. Every function "
                        + "accepts an optional language argument (default en-US).");
        t.put("vgi.doc_md",
                "## grammar.main\n\n"
                        + "Grammar, style, and spelling checking and auto-correction over Apache "
                        + "Arrow, powered by LanguageTool.\n\n"
                        + "- `grammar_check(text [, language])` — one row per issue with suggestions\n"
                        + "- `grammar_count(text [, language])` — number of issues\n"
                        + "- `is_grammatical(text [, language])` — true when text is clean\n"
                        + "- `correct(text [, language])` — auto-corrected text\n"
                        + "- `grammar_languages()` — supported language codes\n\n"
                        + "The `language` argument defaults to `en-US`.");
        t.put("vgi.example_queries",
                "SELECT * FROM grammar.main.grammar_check('She go to school every day.');\n"
                        + "SELECT grammar.main.grammar_count('I has two dog.');\n"
                        + "SELECT grammar.main.is_grammatical('The quick brown fox jumps.');\n"
                        + "SELECT grammar.main.correct('She go to school evry day.');\n"
                        + "SELECT * FROM grammar.main.grammar_languages();");
        return t;
    }

    /**
     * VGI311: {@code grammar_languages} is a parameterless table function (always
     * returns the same rows), so also expose it as a regular table that scans the
     * function. That lets consumers write {@code SELECT * FROM
     * grammar.main.grammar_languages} (no parentheses). The table and the table
     * function share the SQL name; DuckDB keeps them in separate namespaces.
     */
    private static CatalogTable languagesTable() {
        Map<String, String> tags = Meta.objectTags(
                "Supported Grammar Languages",
                "The language codes accepted by the `language` argument of the grammar "
                        + "functions (`grammar_check`, `grammar_count`, `is_grammatical`, "
                        + "`correct`), each with a human-readable name. This worker ships "
                        + "English only (`en-US`, `en-GB`, `en-CA`, ...). Query it to discover "
                        + "valid `language` values before passing one, or to build a picker.",
                "## grammar.main.grammar_languages\n\n"
                        + "A table of every language code accepted by the `language` argument "
                        + "of the grammar functions, with its human-readable name. This worker "
                        + "ships English only.\n\n"
                        + "```sql\n"
                        + "SELECT code, name FROM grammar.main.grammar_languages;\n"
                        + "```",
                "languages, language codes, locales, supported, en-US, en-GB, "
                        + "list languages, discovery, dialects");
        tags.put("vgi.example_queries",
                "[{\"sql\": \"SELECT * FROM grammar.main.grammar_languages;\", "
                        + "\"description\": \"List every supported language code with its "
                        + "human-readable name.\"}, "
                        + "{\"sql\": \"SELECT code FROM grammar.main.grammar_languages "
                        + "WHERE code LIKE 'en%';\", "
                        + "\"description\": \"Filter to the English variants accepted by the "
                        + "language argument.\"}]");
        tags.put("vgi.executable_examples",
                "[{\"description\": \"List every supported language code with its "
                        + "human-readable name.\", \"sql\": \"SELECT code, name FROM "
                        + "grammar.main.grammar_languages ORDER BY code\"}]");
        // VGI123 classifying tags MUST use BARE keys (not vgi.-namespaced).
        tags.put("domain", "text");
        tags.put("category", "grammar-and-style");
        tags.put("topic", "proofreading");
        return CatalogTable.builder(
                        SCHEMA, "grammar_languages",
                        SchemaUtil.serializeSchema(GrammarSchemas.LANGUAGES_SCHEMA))
                .comment("One row per language code accepted by the grammar functions' "
                        + "language argument, with its human-readable name.")
                .tags(tags)
                .scanFunction("grammar_languages")
                .cardinality(40L, 40L)
                // VGI807/VGI806: `code` uniquely identifies each language row.
                .primaryKey(java.util.List.of(java.util.List.of(0)))
                .build();
    }

    public static Worker buildWorker() {
        return Worker.builder()
                .catalogName(CATALOG)
                .implementationVersion(GIT_COMMIT)
                .catalogComment("Grammar, style, and spelling checking + auto-correction (LanguageTool)")
                .catalogTags(catalogTags())
                .sourceUrl("https://github.com/Query-farm/vgi-grammar")
                .schemaComment(SCHEMA,
                        "Grammar, style, and spelling checking and auto-correction functions "
                                + "powered by LanguageTool.")
                .schemaTags(SCHEMA, schemaTags())
                .registerTable(new GrammarCheckFunction())
                .registerTable(new GrammarLanguagesFunction())
                // VGI311: also expose the parameterless grammar_languages function
                // as a regular table (SELECT * FROM grammar.main.grammar_languages).
                .registerCatalogTable(languagesTable())
                // Overloaded scalars: one class per arity (the SDK requires exactly
                // one compute() per class), both under the same SQL name.
                .registerScalar(new GrammarCount.OneArg())
                .registerScalar(new GrammarCount.TwoArg())
                .registerScalar(new IsGrammatical.OneArg())
                .registerScalar(new IsGrammatical.TwoArg())
                .registerScalar(new Correct.OneArg())
                .registerScalar(new Correct.TwoArg());
    }

    public static void main(String[] args) {
        String stderrPath = System.getenv("VGI_WORKER_STDERR");
        if (stderrPath != null && !stderrPath.isEmpty()) {
            try {
                java.io.PrintStream ps = new java.io.PrintStream(
                        new java.io.FileOutputStream(stderrPath, true), true);
                System.setErr(ps);
            } catch (Exception ignore) {
                // best-effort stderr redirect
            }
        }
        // EXPENSIVE INIT: building a JLanguageTool for the default language is slow.
        // Warm it once at startup, on a background thread so the worker can start
        // serving immediately while the first (default-language) query is primed.
        Thread warm = new Thread(() -> GrammarEngine.shared().warmDefault(), "vgi-grammar-warmup");
        warm.setDaemon(true);
        warm.start();

        buildWorker().runFromArgs(args);
    }
}

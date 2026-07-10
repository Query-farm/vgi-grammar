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
                        + "**Key concepts.** An *issue* is a single problem LanguageTool flags — a "
                        + "grammar, punctuation, style, or spelling rule that fired over a span of "
                        + "your text — carrying the rule that matched, a human-readable category and "
                        + "message, the offending span, and an ordered list of suggested "
                        + "replacements (best first). A *language* is a dialect/locale code (this "
                        + "worker ships the English family: `en-US`, `en-GB`, `en-CA`, ...) that "
                        + "decides which spellings and rules apply; it defaults to `en-US`.\n\n"
                        + "**When to reach for it.** Use this worker whenever a SQL pipeline needs "
                        + "to find, quantify, gate on, or repair writing problems in a text column "
                        + "without leaving the database or shipping data to an external proofreading "
                        + "API: validating user-generated content, scoring writing quality, flagging "
                        + "typos in scraped data, or batch-cleaning free-text columns. The functions "
                        + "group into checking (surface or count issues, or test whether text is "
                        + "clean), correction (rewrite text by applying the top suggested fix), and "
                        + "reference (discover the supported language codes); list the `grammar` "
                        + "schema to see them.");
        // VGI152/VGI920: fixed agent-suitability suite. Each task's reference_sql
        // calls the worker's own function on a fixed input, so grading is strict
        // yet version-independent — it measures whether an analyst discovers and
        // correctly invokes the right function, not a hard-coded LanguageTool
        // output that could drift between rule-set versions. Column names vary by
        // analyst phrasing, so every task sets ignore_column_names. The six tasks
        // cover all five worker objects (is_grammatical, grammar_count,
        // grammar_check, correct, grammar_languages).
        t.put("vgi.agent_test_tasks", agentTestTasksJson());
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

    /**
     * The {@code vgi.agent_test_tasks} suite (VGI152/VGI920). A JSON array of
     * {@code {name, prompt, reference_sql, ignore_column_names}} analyst tasks.
     * Each {@code reference_sql} invokes the worker's own function on a fixed
     * input, so the expected result is whatever the current LanguageTool build
     * returns — grading stays deterministic across rule-set versions and only
     * checks that the analyst reached for the right function.
     */
    private static String agentTestTasksJson() {
        return "["
                + "{\"name\": \"clean-text-grammatical\", "
                + "\"prompt\": \"Is the sentence 'The quick brown fox jumps over the lazy "
                + "dog.' grammatically correct? Return a single boolean value.\", "
                + "\"reference_sql\": \"SELECT grammar.main.is_grammatical('The quick brown "
                + "fox jumps over the lazy dog.') AS ok\", "
                + "\"ignore_column_names\": true}, "
                + "{\"name\": \"flag-ungrammatical-text\", "
                + "\"prompt\": \"Is the sentence 'She go to school every day.' grammatically "
                + "correct? Return a single boolean value.\", "
                + "\"reference_sql\": \"SELECT grammar.main.is_grammatical('She go to school "
                + "every day.') AS ok\", "
                + "\"ignore_column_names\": true}, "
                + "{\"name\": \"count-issues-in-clean-text\", "
                + "\"prompt\": \"How many grammar, style, or spelling issues does the sentence "
                + "'The quick brown fox jumps over the lazy dog.' contain? Return the count as "
                + "a single number.\", "
                + "\"reference_sql\": \"SELECT grammar.main.grammar_count('The quick brown fox "
                + "jumps over the lazy dog.') AS n\", "
                + "\"ignore_column_names\": true}, "
                + "{\"name\": \"detect-issue-in-text\", "
                + "\"prompt\": \"When you check the sentence 'She go to school every day.' for "
                + "writing problems, does it have at least one issue? Return a single boolean "
                + "value.\", "
                + "\"reference_sql\": \"SELECT count(*) > 0 AS has_issue FROM "
                + "grammar.main.grammar_check('She go to school every day.')\", "
                + "\"ignore_column_names\": true}, "
                + "{\"name\": \"auto-correct-text\", "
                + "\"prompt\": \"Return the sentence 'I have thre aples.' rewritten with its "
                + "spelling and grammar automatically corrected.\", "
                + "\"reference_sql\": \"SELECT grammar.main.correct('I have thre aples.') AS "
                + "corrected\", "
                + "\"ignore_column_names\": true}, "
                + "{\"name\": \"is-language-supported\", "
                + "\"prompt\": \"Is the language code 'en-GB' one of the languages this worker "
                + "supports for grammar checking? Return a single boolean value.\", "
                + "\"reference_sql\": \"SELECT count(*) > 0 AS supported FROM "
                + "grammar.main.grammar_languages WHERE code = 'en-GB'\", "
                + "\"ignore_column_names\": true}"
                + "]";
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
                        + "The single schema for this worker: grammar, style, and spelling "
                        + "checking plus auto-correction over Apache Arrow, powered by "
                        + "LanguageTool. Its functions fall into three groups — checking (surface "
                        + "or quantify writing issues, or test whether text is clean), correction "
                        + "(rewrite text by applying the top suggested fix), and reference "
                        + "(discover the supported language codes).\n\n"
                        + "Every text-checking function takes an optional `language` argument that "
                        + "defaults to `en-US` and controls which dialect's spellings and rules "
                        + "apply. This worker ships the English family (`en-US`, `en-GB`, "
                        + "`en-CA`, ...); query the reference function to enumerate the exact codes "
                        + "before passing one.");
        // VGI413/VGI410: schema-level category registry. Each object carries a
        // matching `vgi.category` (see each function's metadata / languagesTable).
        t.put("vgi.categories",
                "[{\"name\": \"Checking\", \"description\": \"Find, count, or test for "
                        + "grammar, style, and spelling issues in text.\"}, "
                        + "{\"name\": \"Correction\", \"description\": \"Rewrite text by applying "
                        + "LanguageTool's top suggested fix for each issue.\"}, "
                        + "{\"name\": \"Reference\", \"description\": \"Discover the language codes "
                        + "accepted by the grammar functions.\"}]");
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
                        + "ships English only (`en-US`, `en-GB`, `en-CA`, ...). See the worked "
                        + "examples for typical projections.",
                "languages, language codes, locales, supported, en-US, en-GB, "
                        + "list languages, discovery, dialects");
        tags.put("vgi.example_queries",
                "[{\"sql\": \"SELECT code, name FROM grammar.main.grammar_languages "
                        + "ORDER BY code\", "
                        + "\"description\": \"List every supported language code with its "
                        + "human-readable name, alphabetically.\"}, "
                        + "{\"sql\": \"SELECT code FROM grammar.main.grammar_languages "
                        + "WHERE code LIKE 'en-%' ORDER BY code\", "
                        + "\"description\": \"Filter to the English variants accepted by the "
                        + "language argument.\"}, "
                        + "{\"sql\": \"SELECT count(*) AS supported_languages "
                        + "FROM grammar.main.grammar_languages\", "
                        + "\"description\": \"Count how many language codes this worker "
                        + "accepts.\"}]");
        tags.put("vgi.executable_examples",
                "[{\"description\": \"List every supported language code with its "
                        + "human-readable name.\", \"sql\": \"SELECT code, name FROM "
                        + "grammar.main.grammar_languages ORDER BY code\"}]");
        // VGI123 classifying tags MUST use BARE keys (not vgi.-namespaced).
        tags.put("domain", "text");
        tags.put("category", "grammar-and-style");
        tags.put("topic", "proofreading");
        // VGI409/VGI411: primary category from the schema's vgi.categories registry.
        tags.put("vgi.category", "Reference");
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

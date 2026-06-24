package farm.query.vgi.grammar;

import farm.query.vgi.Worker;

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
        t.put("vgi.description_llm",
                "Check grammar, style, and spelling of free text with LanguageTool and "
                        + "auto-correct it, directly in SQL. Use to find writing issues (one row "
                        + "per problem with rule, category, message, the flagged span, and suggested "
                        + "replacements), count issues, test whether text is clean, rewrite text by "
                        + "applying the top suggestion, and list the supported language codes. "
                        + "English (en-US, en-GB, en-CA, ...) is shipped.");
        t.put("vgi.description_md",
                "# grammar\n\n"
                        + "Grammar, style, and spelling checking plus auto-correction powered by "
                        + "[LanguageTool](https://languagetool.org), exposed as DuckDB SQL functions "
                        + "over Apache Arrow.\n\n"
                        + "**Scalars:** `grammar_count`, `is_grammatical`, `correct`.\n"
                        + "**Tables:** `grammar_check` (one row per issue), `grammar_languages`.\n\n"
                        + "Each function takes an optional `language` argument (default `en-US`).");
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
        t.put("vgi.description_llm",
                "Grammar, style, and spelling functions: check text and get one row per issue "
                        + "with suggested fixes, count issues, test whether text is grammatical, "
                        + "auto-correct text, and list supported language codes.");
        t.put("vgi.description_md",
                "Grammar, style, and spelling checking and auto-correction functions "
                        + "(LanguageTool) over Apache Arrow.");
        return t;
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

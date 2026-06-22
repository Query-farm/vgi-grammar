package farm.query.vgi.grammar;

import farm.query.vgi.Worker;

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

    public static Worker buildWorker() {
        return Worker.builder()
                .catalogName("grammar")
                .implementationVersion(GIT_COMMIT)
                .catalogComment("Grammar, style, and spelling checking + auto-correction (LanguageTool)")
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

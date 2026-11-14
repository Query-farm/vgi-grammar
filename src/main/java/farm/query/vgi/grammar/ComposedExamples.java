package farm.query.vgi.grammar;

import farm.query.vgi.protocol.FunctionExample;

/**
 * Example queries that combine two or more of this worker's functions in one
 * realistic query (the per-function examples elsewhere each show one function in
 * isolation). Each SQL is defined once here and referenced from the schema-level
 * {@code vgi.example_queries} list and from the example list of the function that
 * drives the query (the vgi-lint agent simulator only shows per-function
 * examples, never the schema-level list).
 *
 * <p>All inputs are inline literals, so every example is offline and
 * deterministic for a given LanguageTool build.
 */
final class ComposedExamples {

    private ComposedExamples() {}

    /** One composed example: a description plus its SQL. */
    record Example(String description, String sql) {
        /** This example as a {@code {"sql", "description"}} JSON object. */
        String json() {
            return "{\"sql\": " + jsonString(sql) + ", \"description\": "
                    + jsonString(description) + "}";
        }

        /** This example as an SDK {@link FunctionExample}. */
        FunctionExample functionExample() {
            return new FunctionExample(sql, description, "");
        }
    }

    /**
     * {@code correct} feeding {@code grammar_count}: auto-correct a batch of
     * sentences and re-count what is left, so rewrites a single pass did not
     * fully clean (e.g. 'This are a test of the the system.' becomes 'These is a
     * test of the system.', still 1 issue) sort first. Driven by {@code correct}.
     */
    static final Example CORRECT_THEN_RECOUNT = new Example(
            "Auto-correct a batch of sentences and re-count the issues left in each "
                    + "rewrite (grammar_count over correct), so rows that one pass of "
                    + "top-suggestion fixes did not fully clean (issues_after > 0) sort first.",
            "SELECT txt AS original, grammar.main.grammar_count(txt) AS issues_before, "
                    + "grammar.main.correct(txt) AS corrected, "
                    + "grammar.main.grammar_count(grammar.main.correct(txt)) AS issues_after "
                    + "FROM (VALUES ('Thanks for you order, it will ship tomorow.'), "
                    + "('I could of done it better.'), "
                    + "('This are a test of the the system.'), "
                    + "('Your package has been delivered.')) AS t(txt) "
                    + "ORDER BY issues_after DESC, original");

    /**
     * {@code grammar_check} beside {@code correct}: explain an auto-correction
     * by listing each flagged span and its top suggestion next to the rewritten
     * sentence. Driven by {@code grammar_check}.
     */
    static final Example EXPLAIN_CORRECTION = new Example(
            "Explain an auto-correction: list each issue grammar_check flags in a "
                    + "sentence with its category, flagged text, and top suggestion (the "
                    + "replacement correct applies) next to the corrected sentence.",
            "SELECT c.category, c.bad_text, c.suggestions[1] AS applied_fix, "
                    + "grammar.main.correct('Thanks for you order, it will ship tomorow.') "
                    + "AS corrected "
                    + "FROM grammar.main.grammar_check('Thanks for you order, it will ship "
                    + "tomorow.') AS c "
                    + "ORDER BY c.\"offset\"");

    /**
     * {@code is_grammatical} gating {@code correct} (with {@code grammar_count}
     * for triage): rewrite only the rows that fail the check, worst first.
     * Driven by {@code is_grammatical}.
     */
    static final Example FIX_ONLY_FAILING = new Example(
            "Rewrite only the rows that fail the grammar check: filter a text column "
                    + "with is_grammatical, then show each failing row's issue count "
                    + "(grammar_count) and its auto-corrected text (correct), worst first.",
            "SELECT txt AS original, grammar.main.grammar_count(txt) AS issues, "
                    + "grammar.main.correct(txt) AS corrected "
                    + "FROM (VALUES ('Its a nice day, isnt it?'), "
                    + "('Your package has been delivered.'), "
                    + "('I could of done it better.')) AS t(txt) "
                    + "WHERE NOT grammar.main.is_grammatical(txt) "
                    + "ORDER BY issues DESC, original");

    /** Every composed example, in the order the schema-level list shows them. */
    static final Example[] ALL = {CORRECT_THEN_RECOUNT, EXPLAIN_CORRECTION, FIX_ONLY_FAILING};

    /** The composed examples as comma-joined JSON objects (no enclosing brackets). */
    static String jsonObjects(Example... examples) {
        StringBuilder sb = new StringBuilder();
        for (Example e : examples) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(e.json());
        }
        return sb.toString();
    }

    /** Encode {@code s} as a JSON string literal. */
    static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}

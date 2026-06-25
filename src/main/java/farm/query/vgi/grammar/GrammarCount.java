package farm.query.vgi.grammar;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;

import java.util.List;

/**
 * Base for {@code grammar_count} scalars. Each concrete subclass declares its own
 * single {@code compute} method (the SDK requires exactly one per class), so the
 * one-arg and two-arg overloads live in {@link GrammarCount.OneArg} and
 * {@link GrammarCount.TwoArg}, both registered under the SQL name
 * {@code grammar_count} (DuckDB resolves on arity).
 */
abstract class GrammarCount extends ScalarFn {

    final GrammarEngine engine;

    GrammarCount(GrammarEngine engine) { this.engine = engine; }

    @Override public final String name() { return "grammar_count"; }

    @Override protected final ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.INT32;
    }

    final void run(VarCharVector in, String language, IntVector out) {
        String lang = (language == null || language.isBlank())
                ? GrammarEngine.DEFAULT_LANGUAGE : language;
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            if (in.isNull(i)) { out.setNull(i); continue; }
            String text = in.getObject(i).toString();
            try {
                out.setSafe(i, engine.count(text, lang));
            } catch (GrammarEngine.UnknownLanguageException e) {
                throw e; // unknown language is a hard, query-failing error
            } catch (Exception e) {
                out.setNull(i); // never crash the batch on one bad row
            }
        }
    }

    /** {@code grammar_count(text)} — default language. */
    public static final class OneArg extends GrammarCount {
        public OneArg() { super(GrammarEngine.shared()); }
        public OneArg(GrammarEngine engine) { super(engine); }

        @Override public String description() {
            return "Count grammar, style, and spelling issues in a piece of text, "
                    + "checked in the default language (en-US) with LanguageTool.";
        }

        @Override public FunctionMetadata metadata() {
            return FunctionMetadata.describe(description())
                    .withCategories("text", "grammar", "languagetool")
                    .withTags(Meta.objectTags(
                            "Count Grammar Issues",
                            "Return the number of grammar, style, and spelling issues LanguageTool "
                                    + "finds in a piece of text, checked in the default language "
                                    + "(`en-US`). Use it to score or rank free text by writing "
                                    + "quality, to gate content (e.g. `WHERE grammar_count(body) = "
                                    + "0`), or as a quick health check before running the fuller "
                                    + "`grammar_check` table function. Returns an INTEGER; "
                                    + "`NULL` input yields `NULL`. A row that errors internally is "
                                    + "skipped (counted as `NULL`), but an unknown language is a "
                                    + "hard, query-failing error.",
                            "## grammar_count(text)\n\n"
                                    + "Counts grammar, style, and spelling issues in `text` using "
                                    + "the default language (`en-US`).\n\n"
                                    + "Returns an `INTEGER`. `NULL` text returns `NULL`. For the "
                                    + "issue details use `grammar_check`; for a boolean clean/dirty "
                                    + "test use `is_grammatical`.\n\n"
                                    + "```sql\n"
                                    + "SELECT grammar.main.grammar_count('She go to the store "
                                    + "yesterday.');\n"
                                    + "```",
                            "grammar count, count issues, number of errors, writing score, "
                                    + "quality, spelling count, grammar, default language"))
                    .withTag("vgi.example_queries",
                            "[{\"sql\": \"SELECT grammar.main.grammar_count("
                                    + "'She go to the store yesterday.');\", "
                                    + "\"description\": \"Count the grammar/spelling issues in a "
                                    + "sentence (default en-US).\"}]")
                    .withTag("vgi.executable_examples",
                            "[{\"description\": \"Count the grammar/spelling issues in a sentence "
                                    + "(default en-US).\", \"sql\": \"SELECT "
                                    + "grammar.main.grammar_count('She go to the store yesterday.') "
                                    + "AS n\"}]")
                    .withExamples(List.of(new FunctionExample(
                            "SELECT grammar.main.grammar_count('She go to the store yesterday.');",
                            "Count the grammar/spelling issues in a sentence (default en-US).",
                            "")));
        }

        public void compute(
                @farm.query.vgi.scalar.Vector(value = "text",
                        doc = "The text to check; each row's grammar, style, and spelling "
                                + "issues are counted using the default language (en-US). "
                                + "NULL yields NULL.")
                VarCharVector in,
                IntVector out) {
            run(in, GrammarEngine.DEFAULT_LANGUAGE, out);
        }
    }

    /** {@code grammar_count(text, language)}. */
    public static final class TwoArg extends GrammarCount {
        public TwoArg() { super(GrammarEngine.shared()); }
        public TwoArg(GrammarEngine engine) { super(engine); }

        @Override public String description() {
            return "Count grammar, style, and spelling issues in a piece of text, "
                    + "checked in an explicit language (e.g. en-GB) with LanguageTool.";
        }

        @Override public FunctionMetadata metadata() {
            return FunctionMetadata.describe(description())
                    .withCategories("text", "grammar", "languagetool")
                    .withTags(Meta.objectTags(
                            "Count Grammar Issues (Language)",
                            "Return the number of grammar, style, and spelling issues LanguageTool "
                                    + "finds in a piece of text, checked in an explicit language "
                                    + "code (e.g. `en-GB`, `en-CA`). Behaves like the single-argument "
                                    + "`grammar_count` but lets you pick the dialect/locale, which "
                                    + "changes which spellings and rules apply (e.g. `colour` is "
                                    + "fine in `en-GB` but flagged in `en-US`). Returns an "
                                    + "INTEGER; `NULL` text yields `NULL`; an unknown language code "
                                    + "is a hard, query-failing error.",
                            "## grammar_count(text, language)\n\n"
                                    + "Counts grammar, style, and spelling issues in `text` using "
                                    + "the given `language` code (e.g. `en-US`, `en-GB`).\n\n"
                                    + "Returns an `INTEGER`. `NULL` text returns `NULL`. The "
                                    + "language choice affects which spellings/rules apply.\n\n"
                                    + "```sql\n"
                                    + "SELECT grammar.main.grammar_count('I love this colour.', "
                                    + "'en-US');\n"
                                    + "```",
                            "grammar count, count issues, language, locale, dialect, en-GB, "
                                    + "en-US, number of errors, spelling count, grammar"))
                    .withTag("vgi.example_queries",
                            "[{\"sql\": \"SELECT grammar.main.grammar_count("
                                    + "'I love this colour.', 'en-US');\", "
                                    + "\"description\": \"Count issues using a specific language; "
                                    + "'colour' flags as a spelling issue in en-US.\"}]")
                    .withExamples(List.of(new FunctionExample(
                            "SELECT grammar.main.grammar_count('I love this colour.', 'en-US');",
                            "Count issues using a specific language; 'colour' flags as a spelling "
                                    + "issue in en-US.",
                            "")));
        }

        public void compute(
                @farm.query.vgi.scalar.Vector(value = "text",
                        doc = "The text to check; each row's grammar, style, and spelling "
                                + "issues are counted. NULL yields NULL.")
                VarCharVector in,
                @farm.query.vgi.scalar.Const(value = "language",
                        doc = "Language/locale code to check against (e.g. en-US, en-GB, "
                                + "en-CA); a per-call constant. Controls which spellings and "
                                + "rules apply. An unknown code fails the query.")
                String language,
                IntVector out) {
            run(in, language, out);
        }
    }
}

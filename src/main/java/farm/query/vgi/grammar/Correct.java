package farm.query.vgi.grammar;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

import java.util.List;

/**
 * {@code grammar.correct(text [, language]) -> VARCHAR} — apply the first
 * suggestion of each issue (non-overlapping, right-to-left) to produce a
 * corrected string. NULL text yields NULL; text with no fixable issues is
 * returned unchanged.
 */
abstract class Correct extends ScalarFn {

    final GrammarEngine engine;

    Correct(GrammarEngine engine) { this.engine = engine; }

    @Override public final String name() { return "correct"; }

    @Override protected final ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.UTF8;
    }

    final void run(VarCharVector in, String language, VarCharVector out) {
        String lang = (language == null || language.isBlank())
                ? GrammarEngine.DEFAULT_LANGUAGE : language;
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            if (in.isNull(i)) { out.setNull(i); continue; }
            String text = in.getObject(i).toString();
            try {
                String fixed = engine.correct(text, lang);
                if (fixed == null) out.setNull(i);
                else out.setSafe(i, new Text(fixed));
            } catch (GrammarEngine.UnknownLanguageException e) {
                throw e;
            } catch (Exception e) {
                // On any other failure, pass the original text through unchanged.
                out.setSafe(i, new Text(text));
            }
        }
    }

    /** {@code correct(text)}. */
    public static final class OneArg extends Correct {
        public OneArg() { super(GrammarEngine.shared()); }
        public OneArg(GrammarEngine engine) { super(engine); }

        @Override public String description() {
            return "Auto-correct text by applying the first LanguageTool suggestion for each "
                    + "issue, using the default language (en-US).";
        }

        @Override public FunctionMetadata metadata() {
            return FunctionMetadata.describe(description())
                    .withCategories("text", "grammar", "languagetool")
                    // VGI409/VGI411: primary category from schema's vgi.categories registry.
                    .withTag("vgi.category", "Correction")
                    .withTags(Meta.objectTags(
                            "Auto-Correct Text",
                            "Rewrite a piece of text by applying the first LanguageTool suggestion "
                                    + "for each detected grammar, style, and spelling issue, using "
                                    + "the default language (`en-US`). Suggestions are applied "
                                    + "right-to-left and non-overlapping so earlier offsets stay "
                                    + "valid. Use it to clean up free text inline in SQL. Returns a "
                                    + "`VARCHAR`; `NULL` input yields `NULL`; text with no fixable "
                                    + "issues is returned unchanged. Only the top suggestion per "
                                    + "issue is applied, so the result is a best-effort fix, not a "
                                    + "guaranteed perfect rewrite.",
                            "## correct(text)\n\n"
                                    + "Returns `text` with the first suggested fix applied to each "
                                    + "issue, using the default language (`en-US`).\n\n"
                                    + "Fixes are applied right-to-left and non-overlapping. Returns "
                                    + "a `VARCHAR`; `NULL` text returns `NULL`; text with no fixable "
                                    + "issues is unchanged. For the underlying issues/suggestions "
                                    + "use `grammar_check`.\n\n"
                                    + "```sql\n"
                                    + "SELECT grammar.main.correct('She go to school evry day.');\n"
                                    + "```",
                            "correct, autocorrect, auto-correct, fix, rewrite, clean up, "
                                    + "proofread, apply suggestions, spelling fix, grammar fix"))
                    .withTag("vgi.example_queries",
                            "[{\"sql\": \"SELECT grammar.main.correct("
                                    + "'She go to school evry day.');\", "
                                    + "\"description\": \"Auto-correct a sentence in the default "
                                    + "language (fixes agreement and the 'evry' typo).\"}]")
                    .withTag("vgi.executable_examples",
                            "[{\"description\": \"Auto-correct a sentence in the default language.\", "
                                    + "\"sql\": \"SELECT grammar.main.correct('She go to school evry "
                                    + "day.') AS fixed\"}]")
                    .withExamples(List.of(new FunctionExample(
                            "SELECT grammar.main.correct('She go to school evry day.');",
                            "Auto-correct a sentence in the default language "
                                    + "(fixes agreement and the 'evry' typo).",
                            "")));
        }

        public void compute(
                @farm.query.vgi.scalar.Vector(value = "text",
                        doc = "The text to auto-correct; the first suggested fix for each "
                                + "issue is applied (right-to-left, non-overlapping) using the "
                                + "default language (en-US). NULL yields NULL; text with no "
                                + "fixable issues is returned unchanged.")
                VarCharVector in,
                VarCharVector out) {
            run(in, GrammarEngine.DEFAULT_LANGUAGE, out);
        }
    }

    /** {@code correct(text, language)}. */
    public static final class TwoArg extends Correct {
        public TwoArg() { super(GrammarEngine.shared()); }
        public TwoArg(GrammarEngine engine) { super(engine); }

        @Override public String description() {
            return "Auto-correct text by applying the first LanguageTool suggestion for each "
                    + "issue, using an explicit language (e.g. en-GB).";
        }

        @Override public FunctionMetadata metadata() {
            return FunctionMetadata.describe(description())
                    .withCategories("text", "grammar", "languagetool")
                    // VGI409/VGI411: primary category from schema's vgi.categories registry.
                    .withTag("vgi.category", "Correction")
                    .withTags(Meta.objectTags(
                            "Auto-Correct Text (Language)",
                            "Rewrite a piece of text by applying the first LanguageTool suggestion "
                                    + "for each detected grammar, style, and spelling issue, using "
                                    + "an explicit language code (e.g. `en-GB`). Behaves like the "
                                    + "single-argument `correct` but lets you pick the "
                                    + "dialect/locale, which changes which spellings and rules "
                                    + "apply. Returns a `VARCHAR`; `NULL` text yields `NULL`; text "
                                    + "with no fixable issues is returned unchanged; an unknown "
                                    + "language code is a hard, query-failing error.",
                            "## correct(text, language)\n\n"
                                    + "Returns `text` with the first suggested fix applied to each "
                                    + "issue, using the given `language` code (e.g. `en-US`, "
                                    + "`en-GB`).\n\n"
                                    + "Fixes are applied right-to-left and non-overlapping. Returns "
                                    + "a `VARCHAR`; `NULL` text returns `NULL`; text with no fixable "
                                    + "issues is unchanged.\n\n"
                                    + "```sql\n"
                                    + "SELECT grammar.main.correct('I has two dog.', 'en-US');\n"
                                    + "```",
                            "correct, autocorrect, auto-correct, fix, rewrite, language, locale, "
                                    + "dialect, en-GB, apply suggestions, proofread, clean up"))
                    .withTag("vgi.example_queries",
                            "[{\"sql\": \"SELECT grammar.main.correct("
                                    + "'I has two dog.', 'en-US');\", "
                                    + "\"description\": \"Auto-correct text in a specific language "
                                    + "(fixes verb and noun agreement).\"}]")
                    .withExamples(List.of(new FunctionExample(
                            "SELECT grammar.main.correct('I has two dog.', 'en-US');",
                            "Auto-correct text in a specific language "
                                    + "(fixes verb and noun agreement).",
                            "")));
        }

        public void compute(
                @farm.query.vgi.scalar.Vector(value = "text",
                        doc = "The text to auto-correct; the first suggested fix for each "
                                + "issue is applied (right-to-left, non-overlapping). NULL "
                                + "yields NULL; text with no fixable issues is returned "
                                + "unchanged.")
                VarCharVector in,
                @farm.query.vgi.scalar.Const(value = "language",
                        doc = "Language/locale code to check against (e.g. en-US, en-GB, "
                                + "en-CA); a per-call constant. Controls which spellings and "
                                + "rules apply. An unknown code fails the query.")
                String language,
                VarCharVector out) {
            run(in, language, out);
        }
    }
}

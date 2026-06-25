package farm.query.vgi.grammar;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.protocol.FunctionExample;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;

import java.util.List;

/**
 * {@code grammar.is_grammatical(text [, language]) -> BOOLEAN} — true when the
 * text has zero issues. NULL text yields NULL.
 */
abstract class IsGrammatical extends ScalarFn {

    final GrammarEngine engine;

    IsGrammatical(GrammarEngine engine) { this.engine = engine; }

    @Override public final String name() { return "is_grammatical"; }

    @Override protected final ArrowType outputType(Schema inputSchema, Arguments args) {
        return Schemas.BOOL;
    }

    final void run(VarCharVector in, String language, BitVector out) {
        String lang = (language == null || language.isBlank())
                ? GrammarEngine.DEFAULT_LANGUAGE : language;
        int n = in.getValueCount();
        for (int i = 0; i < n; i++) {
            if (in.isNull(i)) { out.setNull(i); continue; }
            String text = in.getObject(i).toString();
            try {
                out.setSafe(i, engine.isGrammatical(text, lang) ? 1 : 0);
            } catch (GrammarEngine.UnknownLanguageException e) {
                throw e;
            } catch (Exception e) {
                out.setNull(i);
            }
        }
    }

    /** {@code is_grammatical(text)}. */
    public static final class OneArg extends IsGrammatical {
        public OneArg() { super(GrammarEngine.shared()); }
        public OneArg(GrammarEngine engine) { super(engine); }

        @Override public String description() {
            return "True when a piece of text has zero grammar/style/spelling issues, "
                    + "checked in the default language (en-US) with LanguageTool.";
        }

        @Override public FunctionMetadata metadata() {
            return FunctionMetadata.describe(description())
                    .withCategories("text", "grammar", "languagetool")
                    .withTags(Meta.objectTags(
                            "Is Text Grammatical",
                            "Return `true` when a piece of text has zero grammar, style, and "
                                    + "spelling issues according to LanguageTool, checked in the "
                                    + "default language (`en-US`), and `false` otherwise. Use it as "
                                    + "a boolean predicate to filter or validate text (e.g. "
                                    + "`WHERE is_grammatical(comment)`), or as a cheap clean/dirty "
                                    + "gate before invoking the fuller `grammar_check`. Returns a "
                                    + "BOOLEAN; `NULL` input yields `NULL`. An unknown language is a "
                                    + "hard, query-failing error.",
                            "## is_grammatical(text)\n\n"
                                    + "Returns `true` when `text` has no grammar/style/spelling "
                                    + "issues in the default language (`en-US`), else `false`.\n\n"
                                    + "Returns a `BOOLEAN`; `NULL` text returns `NULL`. Equivalent "
                                    + "to `grammar_count(text) = 0`, but short-circuits.\n\n"
                                    + "```sql\n"
                                    + "SELECT grammar.main.is_grammatical('The quick brown fox "
                                    + "jumps.');\n"
                                    + "```",
                            "is grammatical, valid, clean text, no errors, predicate, filter, "
                                    + "boolean, grammar check, validate writing"))
                    .withTag("vgi.example_queries",
                            "[{\"sql\": \"SELECT grammar.main.is_grammatical("
                                    + "'The quick brown fox jumps.');\", "
                                    + "\"description\": \"Test whether a sentence is free of issues "
                                    + "in the default language (returns true).\"}]")
                    .withTag("vgi.executable_examples",
                            "[{\"description\": \"Test whether a clean sentence is grammatical "
                                    + "(default en-US).\", \"sql\": \"SELECT "
                                    + "grammar.main.is_grammatical('The quick brown fox jumps.') "
                                    + "AS ok\"}]")
                    .withExamples(List.of(new FunctionExample(
                            "SELECT grammar.main.is_grammatical('The quick brown fox jumps.');",
                            "Test whether a sentence is free of issues in the default language "
                                    + "(returns true).",
                            "")));
        }

        public void compute(
                @farm.query.vgi.scalar.Vector(value = "text",
                        doc = "The text to test; returns true when it has zero grammar, "
                                + "style, and spelling issues in the default language (en-US), "
                                + "else false. NULL yields NULL.")
                VarCharVector in,
                BitVector out) {
            run(in, GrammarEngine.DEFAULT_LANGUAGE, out);
        }
    }

    /** {@code is_grammatical(text, language)}. */
    public static final class TwoArg extends IsGrammatical {
        public TwoArg() { super(GrammarEngine.shared()); }
        public TwoArg(GrammarEngine engine) { super(engine); }

        @Override public String description() {
            return "True when a piece of text has zero grammar/style/spelling issues, "
                    + "checked in an explicit language (e.g. en-GB) with LanguageTool.";
        }

        @Override public FunctionMetadata metadata() {
            return FunctionMetadata.describe(description())
                    .withCategories("text", "grammar", "languagetool")
                    .withTags(Meta.objectTags(
                            "Is Text Grammatical (Language)",
                            "Return `true` when a piece of text has zero grammar, style, and "
                                    + "spelling issues according to LanguageTool, checked in an "
                                    + "explicit language code (e.g. `en-GB`). Behaves like the "
                                    + "single-argument `is_grammatical` but lets you pick the "
                                    + "dialect/locale, which changes which spellings and rules "
                                    + "apply. Returns a BOOLEAN; `NULL` text yields `NULL`; an "
                                    + "unknown language code is a hard, query-failing error.",
                            "## is_grammatical(text, language)\n\n"
                                    + "Returns `true` when `text` has no grammar/style/spelling "
                                    + "issues in the given `language` code, else `false`.\n\n"
                                    + "Returns a `BOOLEAN`; `NULL` text returns `NULL`. The language "
                                    + "choice affects which spellings/rules apply.\n\n"
                                    + "```sql\n"
                                    + "SELECT grammar.main.is_grammatical('She don''t like it.', "
                                    + "'en-US');\n"
                                    + "```",
                            "is grammatical, valid, clean text, language, locale, dialect, "
                                    + "en-GB, predicate, filter, boolean, validate writing"))
                    .withTag("vgi.example_queries",
                            "[{\"sql\": \"SELECT grammar.main.is_grammatical("
                                    + "'She don''t like it.', 'en-US');\", "
                                    + "\"description\": \"Test grammaticality in a specific language "
                                    + "(returns false - subject-verb agreement).\"}]")
                    .withExamples(List.of(new FunctionExample(
                            "SELECT grammar.main.is_grammatical('She don''t like it.', 'en-US');",
                            "Test grammaticality in a specific language (returns false — "
                                    + "subject-verb agreement).",
                            "")));
        }

        public void compute(
                @farm.query.vgi.scalar.Vector(value = "text",
                        doc = "The text to test; returns true when it has zero grammar, "
                                + "style, and spelling issues, else false. NULL yields NULL.")
                VarCharVector in,
                @farm.query.vgi.scalar.Const(value = "language",
                        doc = "Language/locale code to check against (e.g. en-US, en-GB, "
                                + "en-CA); a per-call constant. Controls which spellings and "
                                + "rules apply. An unknown code fails the query.")
                String language,
                BitVector out) {
            run(in, language, out);
        }
    }
}

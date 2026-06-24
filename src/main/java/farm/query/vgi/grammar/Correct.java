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
                    .withTag("vgi.example_queries",
                            "[{\"sql\": \"SELECT grammar.main.correct("
                                    + "'She go to school evry day.');\", "
                                    + "\"description\": \"Auto-correct a sentence in the default "
                                    + "language (fixes agreement and the 'evry' typo).\"}]")
                    .withExamples(List.of(new FunctionExample(
                            "SELECT grammar.main.correct('She go to school evry day.');",
                            "Auto-correct a sentence in the default language "
                                    + "(fixes agreement and the 'evry' typo).",
                            "")));
        }

        public void compute(@farm.query.vgi.scalar.Vector("text") VarCharVector in, VarCharVector out) {
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

        public void compute(@farm.query.vgi.scalar.Vector("text") VarCharVector in,
                            @farm.query.vgi.scalar.Const("language") String language,
                            VarCharVector out) {
            run(in, language, out);
        }
    }
}

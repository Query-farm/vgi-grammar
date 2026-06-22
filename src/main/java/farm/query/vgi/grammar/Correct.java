package farm.query.vgi.grammar;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

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

    @Override public final String description() {
        return "Auto-correct text by applying the first LanguageTool suggestion for each issue.";
    }

    @Override public final FunctionMetadata metadata() {
        return FunctionMetadata.describe(description()).withCategories("text", "grammar", "languagetool");
    }

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

        public void compute(@farm.query.vgi.scalar.Vector("text") VarCharVector in, VarCharVector out) {
            run(in, GrammarEngine.DEFAULT_LANGUAGE, out);
        }
    }

    /** {@code correct(text, language)}. */
    public static final class TwoArg extends Correct {
        public TwoArg() { super(GrammarEngine.shared()); }
        public TwoArg(GrammarEngine engine) { super(engine); }

        public void compute(@farm.query.vgi.scalar.Vector("text") VarCharVector in,
                            @farm.query.vgi.scalar.Const("language") String language,
                            VarCharVector out) {
            run(in, language, out);
        }
    }
}

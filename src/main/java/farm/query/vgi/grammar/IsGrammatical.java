package farm.query.vgi.grammar;

import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.scalar.ScalarFn;
import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Schema;

/**
 * {@code grammar.is_grammatical(text [, language]) -> BOOLEAN} — true when the
 * text has zero issues. NULL text yields NULL.
 */
abstract class IsGrammatical extends ScalarFn {

    final GrammarEngine engine;

    IsGrammatical(GrammarEngine engine) { this.engine = engine; }

    @Override public final String name() { return "is_grammatical"; }

    @Override public final String description() {
        return "True when a piece of text has zero grammar/style/spelling issues (LanguageTool).";
    }

    @Override public final FunctionMetadata metadata() {
        return FunctionMetadata.describe(description()).withCategories("text", "grammar", "languagetool");
    }

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

        public void compute(@farm.query.vgi.scalar.Vector("text") VarCharVector in, BitVector out) {
            run(in, GrammarEngine.DEFAULT_LANGUAGE, out);
        }
    }

    /** {@code is_grammatical(text, language)}. */
    public static final class TwoArg extends IsGrammatical {
        public TwoArg() { super(GrammarEngine.shared()); }
        public TwoArg(GrammarEngine engine) { super(engine); }

        public void compute(@farm.query.vgi.scalar.Vector("text") VarCharVector in,
                            @farm.query.vgi.scalar.Const("language") String language,
                            BitVector out) {
            run(in, language, out);
        }
    }
}

package farm.query.vgi.grammar;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.function.Arguments;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.internal.SchemaUtil;
import farm.query.vgi.protocol.BindResponse;
import farm.query.vgi.table.TableBindParams;
import farm.query.vgi.table.TableFunction;
import farm.query.vgi.table.TableInitParams;
import farm.query.vgi.table.TableProducerState;
import farm.query.vgi.types.Schemas;
import farm.query.vgirpc.CallContext;
import farm.query.vgirpc.OutputCollector;
import farm.query.vgirpc.wire.Allocators;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.ListVector;

import java.util.List;

/**
 * {@code grammar.grammar_check(text, language := 'en-US')} — one row per
 * grammar/style/spelling issue LanguageTool reports for the input text.
 *
 * <p>Output columns: {@code rule_id, category, message, offset, length,
 * bad_text, suggestions VARCHAR[]}. NULL or empty text yields no rows. An
 * unknown language fails the query with a clear error.
 */
public final class GrammarCheckFunction implements TableFunction {

    private final GrammarEngine engine;

    public GrammarCheckFunction() { this(GrammarEngine.shared()); }
    public GrammarCheckFunction(GrammarEngine engine) { this.engine = engine; }

    @Override public String name() { return "grammar_check"; }

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(
                        "Check grammar, style, and spelling of a piece of text with LanguageTool; "
                                + "returns one row per issue with suggested replacements.")
                .withCategories("text", "grammar", "languagetool");
    }

    @Override public List<ArgSpec> argumentSpecs() {
        return List.of(
                ArgSpec.positional("text", 0, Schemas.UTF8),
                ArgSpec.named("language", Schemas.UTF8, GrammarEngine.DEFAULT_LANGUAGE));
    }

    @Override public BindResponse onBind(TableBindParams p) {
        return BindResponse.forSchema(SchemaUtil.serializeSchema(GrammarSchemas.CHECK_SCHEMA));
    }

    @Override public long cardinality(TableBindParams p) {
        return 4L; // a rough guess; most clean sentences have 0, bad ones a few.
    }

    @Override public TableProducerState createProducer(TableInitParams params) {
        Arguments a = params.arguments();
        Object textValue = a.positionalAt(0);
        String text = textValue == null ? null : textValue.toString();
        String language = a.namedString("language", GrammarEngine.DEFAULT_LANGUAGE);
        return new State(text, language, engine);
    }

    public static final class State extends TableProducerState {
        public String text;
        public String language;
        public boolean done;
        public transient GrammarEngine engine;

        public State() {}

        State(String text, String language, GrammarEngine engine) {
            this.text = text;
            this.language = language;
            this.engine = engine;
        }

        private GrammarEngine engine() { return engine != null ? engine : GrammarEngine.shared(); }

        @Override public void produceTick(OutputCollector out, CallContext ctx) {
            if (done) { out.finish(); return; }
            done = true;

            List<GrammarEngine.Issue> issues = engine().check(text, language);
            if (issues.isEmpty()) {
                out.finish();
                return;
            }

            VectorSchemaRoot root = VectorSchemaRoot.create(
                    GrammarSchemas.CHECK_SCHEMA, Allocators.root());
            root.allocateNew();
            ListVector suggestionsVec = (ListVector) root.getVector("suggestions");
            for (int i = 0; i < issues.size(); i++) {
                GrammarEngine.Issue issue = issues.get(i);
                GrammarSchemas.setUtf8(root, "rule_id", i, issue.ruleId());
                GrammarSchemas.setUtf8(root, "category", i, issue.category());
                GrammarSchemas.setUtf8(root, "message", i, issue.message());
                GrammarSchemas.setInt(root, "offset", i, issue.offset());
                GrammarSchemas.setInt(root, "length", i, issue.length());
                GrammarSchemas.setUtf8(root, "bad_text", i, issue.badText());
                GrammarSchemas.writeStringList(suggestionsVec, i, issue.suggestions());
            }
            root.setRowCount(issues.size());
            out.emit(root);
            out.finish();
        }
    }
}

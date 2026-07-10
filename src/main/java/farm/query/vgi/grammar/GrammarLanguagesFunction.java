package farm.query.vgi.grammar;

import farm.query.vgi.function.ArgSpec;
import farm.query.vgi.function.FunctionMetadata;
import farm.query.vgi.internal.SchemaUtil;
import farm.query.vgi.protocol.BindResponse;
import farm.query.vgi.table.TableBindParams;
import farm.query.vgi.table.TableFunction;
import farm.query.vgi.table.TableInitParams;
import farm.query.vgi.table.TableProducerState;
import farm.query.vgirpc.CallContext;
import farm.query.vgirpc.OutputCollector;
import farm.query.vgirpc.wire.Allocators;
import org.apache.arrow.vector.VectorSchemaRoot;

import java.util.List;

/**
 * {@code grammar.grammar_languages()} — the language codes accepted by the
 * {@code language} argument of the other functions, with human-readable names.
 */
public final class GrammarLanguagesFunction implements TableFunction {

    private final GrammarEngine engine;

    public GrammarLanguagesFunction() { this(GrammarEngine.shared()); }
    public GrammarLanguagesFunction(GrammarEngine engine) { this.engine = engine; }

    @Override public String name() { return "grammar_languages"; }

    @Override public FunctionMetadata metadata() {
        return FunctionMetadata.describe(
                        "List the language codes accepted by the language argument of the grammar "
                                + "functions (e.g. en-US, en-GB), with human-readable names.")
                .withCategories("text", "grammar", "languagetool")
                // VGI409/VGI411: primary category from the schema's vgi.categories registry.
                .withTag("vgi.category", "Reference")
                .withTags(Meta.objectTags(
                        "Supported Grammar Languages",
                        "List the language codes accepted by the `language` argument of the other "
                                + "grammar functions (`grammar_check`, `grammar_count`, "
                                + "`is_grammatical`, `correct`), each with a human-readable name. "
                                + "Use it to discover valid `language` values before passing one, "
                                + "or to build a picker UI. This worker ships English only "
                                + "(`en-US`, `en-GB`, `en-CA`, ...), so the result lists the "
                                + "English variants. Takes no arguments and always returns rows.",
                        "## grammar_languages()\n\n"
                                + "Lists every language code accepted by the `language` argument of "
                                + "the grammar functions, with its human-readable name. This "
                                + "worker ships English only, so the result enumerates the English "
                                + "variants (`en-US`, `en-GB`, `en-CA`, ...).\n\n"
                                + "Takes no arguments and always returns rows. See the worked "
                                + "examples for typical projections.",
                        "languages, language codes, locales, supported, en-US, en-GB, "
                                + "list languages, discovery, dialects"))
                .withTag("vgi.example_queries",
                        "[{\"sql\": \"SELECT code, name FROM grammar.main.grammar_languages() "
                                + "ORDER BY code\", "
                                + "\"description\": \"List every supported language code with its "
                                + "human-readable name, alphabetically.\"}, "
                                + "{\"sql\": \"SELECT code FROM grammar.main.grammar_languages() "
                                + "WHERE code LIKE 'en-%' ORDER BY code\", "
                                + "\"description\": \"Filter to the English variants accepted by the "
                                + "language argument.\"}, "
                                + "{\"sql\": \"SELECT count(*) AS supported_languages "
                                + "FROM grammar.main.grammar_languages()\", "
                                + "\"description\": \"Count how many language codes this worker "
                                + "accepts.\"}]")
                .withTag("vgi.executable_examples",
                        "[{\"description\": \"List every supported language code with its "
                                + "human-readable name.\", \"sql\": \"SELECT code, name FROM "
                                + "grammar.main.grammar_languages() ORDER BY code\"}]")
                .withTag("vgi.result_columns_schema",
                        "["
                                + "{\"name\": \"code\", \"type\": \"VARCHAR\", "
                                + "\"description\": \"Language code accepted by the language argument (e.g. en-US).\"},"
                                + "{\"name\": \"name\", \"type\": \"VARCHAR\", "
                                + "\"description\": \"Human-readable language name.\"}"
                                + "]");
    }

    @Override public List<ArgSpec> argumentSpecs() {
        return List.of();
    }

    @Override public BindResponse onBind(TableBindParams p) {
        return BindResponse.forSchema(SchemaUtil.serializeSchema(GrammarSchemas.LANGUAGES_SCHEMA));
    }

    @Override public long cardinality(TableBindParams p) {
        return 40L;
    }

    @Override public TableProducerState createProducer(TableInitParams params) {
        return new State(engine);
    }

    public static final class State extends TableProducerState {
        public boolean done;
        public transient GrammarEngine engine;

        public State() {}

        State(GrammarEngine engine) { this.engine = engine; }

        private GrammarEngine engine() { return engine != null ? engine : GrammarEngine.shared(); }

        @Override public void produceTick(OutputCollector out, CallContext ctx) {
            if (done) { out.finish(); return; }
            done = true;

            List<String[]> langs = engine().languages();
            VectorSchemaRoot root = VectorSchemaRoot.create(
                    GrammarSchemas.LANGUAGES_SCHEMA, Allocators.root());
            root.allocateNew();
            for (int i = 0; i < langs.size(); i++) {
                GrammarSchemas.setUtf8(root, "code", i, langs.get(i)[0]);
                GrammarSchemas.setUtf8(root, "name", i, langs.get(i)[1]);
            }
            root.setRowCount(langs.size());
            out.emit(root);
            out.finish();
        }
    }
}

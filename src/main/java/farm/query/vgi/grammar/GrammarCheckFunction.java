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
                                + "returns one row per issue with suggested replacements. Accepts an "
                                + "optional language argument (default en-US).")
                .withCategories("text", "grammar", "languagetool")
                // VGI409/VGI411: primary category from the schema's vgi.categories registry.
                .withTag("vgi.category", "Checking")
                .withTags(Meta.objectTags(
                        "Grammar Check Issues",
                        "Run LanguageTool over a piece of text and return one row per grammar, "
                                + "style, or spelling issue it finds, each with the rule that "
                                + "fired, its category, an explanatory message, the character "
                                + "offset/length of the flagged span, the flagged substring, and an "
                                + "ordered list of suggested replacements (best first). Accepts an "
                                + "optional `language` argument (default `en-US`). Use it to "
                                + "surface and explain every writing problem in detail; for a "
                                + "simple count use `grammar_count`, for a boolean clean test use "
                                + "`is_grammatical`, and to apply fixes use `correct`. `NULL` or "
                                + "empty text yields no rows; an unknown language fails the query.",
                        "## grammar_check(text [, language])\n\n"
                                + "Returns one row per grammar/style/spelling issue LanguageTool "
                                + "reports for `text`, with suggested replacements. Accepts an "
                                + "optional `language` (default `en-US`).\n\n"
                                + "`NULL` or empty text returns no rows; an unknown language fails "
                                + "the query.\n\n"
                                + "```sql\n"
                                + "SELECT rule_id, message, suggestions\n"
                                + "FROM grammar.main.grammar_check('She go to school every day.');\n"
                                + "```",
                        "grammar check, issues, errors, suggestions, rule, category, message, "
                                + "offset, spelling, style, proofread, one row per issue"))
                .withTag("vgi.example_queries",
                        "[{\"sql\": \"SELECT * FROM grammar.main.grammar_check("
                                + "'She go to school every day.');\", "
                                + "\"description\": \"List every grammar/spelling issue in a "
                                + "sentence, one row each.\"}, "
                                + "{\"sql\": \"SELECT rule_id, message, suggestions FROM "
                                + "grammar.main.grammar_check('I has a apple.', language := 'en-US');\", "
                                + "\"description\": \"Inspect the rule, message, and suggested "
                                + "replacements for each issue in a specific language.\"}]")
                .withTag("vgi.executable_examples",
                        "[{\"description\": \"List every grammar/spelling issue in a sentence, "
                                + "one row each.\", \"sql\": \"SELECT rule_id, category, message "
                                + "FROM grammar.main.grammar_check('She go to school every day.')\"}]")
                .withTag("vgi.result_columns_md",
                        "| column | type | description |\n"
                                + "|---|---|---|\n"
                                + "| `rule_id` | VARCHAR | LanguageTool rule identifier that fired. |\n"
                                + "| `category` | VARCHAR | Human-readable rule category (e.g. Grammar, Typos). |\n"
                                + "| `message` | VARCHAR | Explanation of the issue. |\n"
                                + "| `offset` | INTEGER | 0-based character offset of the issue in the input text. |\n"
                                + "| `length` | INTEGER | Character length of the flagged span. |\n"
                                + "| `bad_text` | VARCHAR | The flagged substring of the input text. |\n"
                                + "| `suggestions` | VARCHAR[] | Suggested replacements, best first (may be empty). |");
    }

    @Override public List<ArgSpec> argumentSpecs() {
        // Use the canonical ArgSpec constructor so each argument carries a doc
        // string (VGI312). Order: name, position, arrowType, doc, isConst,
        // hasDefault, defaultValue, typeBound, varargs, anyType, tableInput.
        return List.of(
                new ArgSpec("text", 0, Schemas.UTF8,
                        "The text to check; LanguageTool returns one output row per grammar, "
                                + "style, or spelling issue it finds in this text. NULL or empty "
                                + "text yields no rows.",
                        true, false, "", List.of(), false, false, false),
                new ArgSpec("language", -1, Schemas.UTF8,
                        "Optional language/locale code to check against (e.g. en-US, en-GB, "
                                + "en-CA); defaults to en-US. Controls which spellings and rules "
                                + "apply. An unknown code fails the query.",
                        true, true, GrammarEngine.DEFAULT_LANGUAGE, List.of(), false, false, false));
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

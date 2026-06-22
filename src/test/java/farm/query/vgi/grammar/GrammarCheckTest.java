package farm.query.vgi.grammar;

import farm.query.vgi.function.Arguments;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** In-process coverage for the grammar_check / grammar_languages table functions. */
class GrammarCheckTest {

    private final GrammarEngine engine = GrammarEngine.shared();

    private static Arguments textArgs(String text) {
        return new Arguments(List.of(text), Map.of());
    }

    private static Arguments textArgs(String text, String language) {
        return new Arguments(List.of(text), Map.of("language", language));
    }

    @Test void agreementErrorYieldsIssueWithSuggestion() {
        var result = TestSupport.invoke(new GrammarCheckFunction(engine),
                textArgs("She go to school every day."));
        List<Map<String, Object>> rows = result.rows();
        assertFalse(rows.isEmpty(), "expected at least one grammar issue");

        // At least one issue should suggest the corrected verb form "goes".
        boolean hasGoes = rows.stream().anyMatch(r -> {
            Object s = r.get("suggestions");
            return s instanceof List<?> list && list.contains("goes");
        });
        assertTrue(hasGoes, "expected a 'goes' suggestion among: " + rows);

        // Every row has the headline columns populated.
        Map<String, Object> first = rows.get(0);
        assertNotNull(first.get("rule_id"));
        assertNotNull(first.get("category"));
        assertNotNull(first.get("message"));
        assertNotNull(first.get("offset"));
        assertNotNull(first.get("length"));
    }

    @Test void spellingErrorGetsSuggestion() {
        var result = TestSupport.invoke(new GrammarCheckFunction(engine),
                textArgs("I havv a problemm."));
        List<Map<String, Object>> rows = result.rows();
        assertFalse(rows.isEmpty(), "expected spelling issues");
        boolean anySuggestion = rows.stream().anyMatch(r -> {
            Object s = r.get("suggestions");
            return s instanceof List<?> list && !list.isEmpty();
        });
        assertTrue(anySuggestion, "expected at least one spelling suggestion");
    }

    @Test void cleanSentenceYieldsNoRows() {
        var result = TestSupport.invoke(new GrammarCheckFunction(engine),
                textArgs("This sentence is correct."));
        assertEquals(0, result.totalRows());
    }

    @Test void nullAndEmptyTextYieldNoRows() {
        assertEquals(0, TestSupport.invoke(new GrammarCheckFunction(engine),
                new Arguments(java.util.Arrays.asList((Object) null), Map.of())).totalRows());
        assertEquals(0, TestSupport.invoke(new GrammarCheckFunction(engine),
                textArgs("")).totalRows());
    }

    @Test void britishVariantAccepted() {
        // 'colour' is fine in en-GB; pass a clean GB sentence and expect no rows.
        var result = TestSupport.invoke(new GrammarCheckFunction(engine),
                textArgs("The colour of the sky is blue.", "en-GB"));
        assertEquals(0, result.totalRows());
    }

    @Test void grammarLanguagesNonEmpty() {
        var result = TestSupport.invoke(new GrammarLanguagesFunction(engine), Arguments.empty());
        List<Map<String, Object>> rows = result.rows();
        assertFalse(rows.isEmpty(), "expected supported languages");
        boolean hasEnUs = rows.stream().anyMatch(r -> "en-US".equals(r.get("code")));
        assertTrue(hasEnUs, "expected en-US among supported languages");
        // every row has a code + name
        for (var r : rows) {
            assertNotNull(r.get("code"));
            assertNotNull(r.get("name"));
        }
    }
}

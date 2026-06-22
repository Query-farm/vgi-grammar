package farm.query.vgi.grammar;

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.util.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direct-vector coverage for the grammar_count / is_grammatical / correct scalars. */
class GrammarScalarTest {

    private final GrammarEngine engine = GrammarEngine.shared();
    private final RootAllocator alloc = new RootAllocator();

    @AfterEach void close() { alloc.close(); }

    private VarCharVector textVec(String... values) {
        VarCharVector v = new VarCharVector("text", alloc);
        v.allocateNew();
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) v.setNull(i);
            else v.setSafe(i, new Text(values[i]));
        }
        v.setValueCount(values.length);
        return v;
    }

    @Test void grammarCountCountsIssues() {
        try (VarCharVector in = textVec("She go to school every day.", "This sentence is correct.", null);
             IntVector out = new IntVector("out", alloc)) {
            out.allocateNew();
            new GrammarCount.OneArg(engine).compute(in, out);
            assertTrue(out.get(0) > 0, "expected issues for the bad sentence");
            assertEquals(0, out.get(1), "clean sentence has 0 issues");
            assertTrue(out.isNull(2), "NULL text -> NULL count");
        }
    }

    @Test void grammarCountWithLanguage() {
        try (VarCharVector in = textVec("She go to school every day.");
             IntVector out = new IntVector("out", alloc)) {
            out.allocateNew();
            new GrammarCount.TwoArg(engine).compute(in, "en-US", out);
            assertTrue(out.get(0) > 0);
        }
    }

    @Test void isGrammaticalTrueForCleanFalseForBad() {
        try (VarCharVector in = textVec("This sentence is correct.", "She go to school every day.", null);
             BitVector out = new BitVector("out", alloc)) {
            out.allocateNew();
            new IsGrammatical.OneArg(engine).compute(in, out);
            assertEquals(1, out.get(0), "clean sentence is grammatical");
            assertEquals(0, out.get(1), "bad sentence is not grammatical");
            assertTrue(out.isNull(2), "NULL text -> NULL");
        }
    }

    @Test void correctFixesAgreementError() {
        try (VarCharVector in = textVec("She go to school every day.", null);
             VarCharVector out = new VarCharVector("out", alloc)) {
            out.allocateNew();
            new Correct.OneArg(engine).compute(in, out);
            String fixed = out.getObject(0).toString();
            assertTrue(fixed.contains("goes"), "expected 'goes' in corrected: " + fixed);
            assertFalse(fixed.contains("She go to"), "agreement error should be gone: " + fixed);
            assertTrue(out.isNull(1), "NULL text -> NULL");
        }
    }

    @Test void correctLeavesCleanTextUnchanged() {
        String clean = "This sentence is correct.";
        try (VarCharVector in = textVec(clean);
             VarCharVector out = new VarCharVector("out", alloc)) {
            out.allocateNew();
            new Correct.TwoArg(engine).compute(in, "en-US", out);
            assertEquals(clean, out.getObject(0).toString());
        }
    }

    @Test void unknownLanguageThrowsClearError() {
        try (VarCharVector in = textVec("Some text.");
             IntVector out = new IntVector("out", alloc)) {
            out.allocateNew();
            var ex = org.junit.jupiter.api.Assertions.assertThrows(
                    GrammarEngine.UnknownLanguageException.class,
                    () -> new GrammarCount.TwoArg(engine).compute(in, "xx-ZZ", out));
            assertTrue(ex.getMessage().contains("xx-ZZ"));
        }
    }
}

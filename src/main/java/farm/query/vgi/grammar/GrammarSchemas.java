package farm.query.vgi.grammar;

import farm.query.vgi.types.Schemas;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.ListVector;
import org.apache.arrow.vector.complex.impl.UnionListWriter;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.arrow.vector.util.Text;

import java.util.List;
import java.util.Map;

/** Shared Arrow schemas + cell writers for the grammar table functions. */
public final class GrammarSchemas {

    private GrammarSchemas() {}

    /**
     * {@code grammar_check} output:
     * (rule_id, category, message, offset, length, bad_text, suggestions VARCHAR[]).
     */
    public static final Schema CHECK_SCHEMA = new Schema(List.of(
            commented("rule_id", Schemas.UTF8, "LanguageTool rule identifier that fired."),
            commented("category", Schemas.UTF8, "Human-readable rule category (e.g. Grammar, Typos)."),
            commented("message", Schemas.UTF8, "Explanation of the issue."),
            commented("offset", Schemas.INT32, "0-based character offset of the issue in the input text."),
            commented("length", Schemas.INT32, "Character length of the flagged span."),
            commented("bad_text", Schemas.UTF8, "The flagged substring of the input text."),
            listOfUtf8("suggestions", "Suggested replacements, best first (may be empty).")));

    /**
     * {@code grammar_languages} output: (code, name). Both columns are always
     * populated (never NULL), so they are declared non-nullable — this also backs
     * the {@code grammar_languages} catalog table's NOT NULL constraints (VGI804).
     */
    public static final Schema LANGUAGES_SCHEMA = new Schema(List.of(
            requiredCommented("code", Schemas.UTF8, "Language code accepted by the language argument (e.g. en-US)."),
            requiredCommented("name", Schemas.UTF8, "Human-readable language name.")));

    static Field commented(String name, ArrowType type, String comment) {
        return new Field(name, new FieldType(true, type, null, Map.of("comment", comment)), null);
    }

    /** A NOT NULL field with a column comment. */
    static Field requiredCommented(String name, ArrowType type, String comment) {
        return new Field(name, new FieldType(false, type, null, Map.of("comment", comment)), null);
    }

    /** A nullable LIST(VARCHAR) field — DuckDB {@code VARCHAR[]}. */
    static Field listOfUtf8(String name, String comment) {
        Field item = new Field(ListVector.DATA_VECTOR_NAME,
                new FieldType(true, Schemas.UTF8, null), null);
        return new Field(name,
                new FieldType(true, new ArrowType.List(), null, Map.of("comment", comment)),
                List.of(item));
    }

    static void setUtf8(VectorSchemaRoot root, String col, int row, String value) {
        VarCharVector v = (VarCharVector) root.getVector(col);
        if (value == null) {
            v.setNull(row);
        } else {
            v.setSafe(row, new Text(value));
        }
    }

    static void setInt(VectorSchemaRoot root, String col, int row, int value) {
        IntVector v = (IntVector) root.getVector(col);
        v.setSafe(row, value);
    }

    /** Write a List(VARCHAR) cell at {@code row}. */
    static void writeStringList(ListVector listVector, int row, List<String> values) {
        UnionListWriter writer = listVector.getWriter();
        writer.setPosition(row);
        writer.startList();
        if (values != null) {
            for (String s : values) {
                if (s != null) {
                    // A LIST(VARCHAR) writer writes elements directly; null
                    // elements are simply not written (suggestions never carry
                    // nulls anyway — they're concrete replacement strings).
                    writer.writeVarChar(s);
                }
            }
        }
        writer.endList();
        listVector.setLastSet(row);
    }
}

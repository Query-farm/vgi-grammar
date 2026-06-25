package farm.query.vgi.grammar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers for the per-object discovery/description metadata that the
 * {@code vgi-lint} strict profile expects on <em>every</em> function and table.
 *
 * <p>Each function/table surfaces these in its {@code FunctionMetadata.tags}:
 * <ul>
 *   <li>{@code vgi.title} (VGI124) — human-friendly display name</li>
 *   <li>{@code vgi.doc_llm} (VGI112) — Markdown narrative aimed at LLMs/agents</li>
 *   <li>{@code vgi.doc_md} (VGI113) — Markdown narrative for human docs</li>
 *   <li>{@code vgi.keywords} (VGI126/VGI138) — a JSON array of search terms</li>
 * </ul>
 *
 * <p>Per-object {@code vgi.source_url} is intentionally NOT set: VGI139 wants
 * {@code source_url} only on the catalog object (see {@link Main}).
 */
final class Meta {

    private Meta() {}

    /**
     * Serialize a comma-separated keyword list into a JSON array of strings, as
     * VGI138 requires (e.g. {@code ["a","b"]} rather than {@code "a, b"}).
     */
    static String keywordsJson(String commaSeparated) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String raw : commaSeparated.split(",")) {
            String kw = raw.trim();
            if (kw.isEmpty()) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"');
            // Escape the few characters that can appear in a JSON string.
            for (int i = 0; i < kw.length(); i++) {
                char c = kw.charAt(i);
                if (c == '"' || c == '\\') {
                    sb.append('\\');
                }
                sb.append(c);
            }
            sb.append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Build the standard per-object discovery/description tags.
     *
     * @param title    human-friendly display name (must add a word beyond the
     *                 machine name so VGI125 does not fire)
     * @param docLlm   Markdown narrative for LLMs/agents
     * @param docMd    Markdown narrative for human docs (distinct from docLlm)
     * @param keywords comma-separated search terms/synonyms (serialized to a JSON
     *                 array for VGI138)
     */
    static Map<String, String> objectTags(
            String title, String docLlm, String docMd, String keywords) {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("vgi.title", title);
        t.put("vgi.doc_llm", docLlm);
        t.put("vgi.doc_md", docMd);
        t.put("vgi.keywords", keywordsJson(keywords));
        return t;
    }
}

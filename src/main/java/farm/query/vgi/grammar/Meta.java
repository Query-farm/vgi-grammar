package farm.query.vgi.grammar;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers for the per-object discovery/description metadata that the
 * {@code vgi-lint} strict profile (0.26.0) expects on <em>every</em> function
 * and table.
 *
 * <p>Each function/table surfaces these in its {@code FunctionMetadata.tags}:
 * <ul>
 *   <li>{@code vgi.title} (VGI124) — human-friendly display name</li>
 *   <li>{@code vgi.doc_llm} (VGI112) — Markdown narrative aimed at LLMs/agents</li>
 *   <li>{@code vgi.doc_md} (VGI113) — Markdown narrative for human docs</li>
 *   <li>{@code vgi.keywords} (VGI126) — comma-separated search terms/synonyms</li>
 *   <li>{@code vgi.source_url} (VGI128) — link to the implementing source file</li>
 * </ul>
 */
final class Meta {

    private Meta() {}

    /** Base GitHub blob URL for source files in this repo (pinned to {@code main}). */
    static final String SOURCE_BASE =
            "https://github.com/Query-farm/vgi-grammar/blob/main/"
                    + "src/main/java/farm/query/vgi/grammar";

    /** Build the implementation {@code vgi.source_url} for a file under {@code grammar}. */
    static String sourceUrl(String relativeFile) {
        return SOURCE_BASE + "/" + relativeFile;
    }

    /**
     * Build the five standard per-object discovery/description tags.
     *
     * @param title        human-friendly display name (must add a word beyond the
     *                     machine name so VGI125 does not fire)
     * @param docLlm       Markdown narrative for LLMs/agents
     * @param docMd        Markdown narrative for human docs (distinct from docLlm)
     * @param keywords     comma-separated search terms/synonyms
     * @param relativeFile implementing source file under {@code grammar/}
     */
    static Map<String, String> objectTags(
            String title, String docLlm, String docMd, String keywords, String relativeFile) {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("vgi.title", title);
        t.put("vgi.doc_llm", docLlm);
        t.put("vgi.doc_md", docMd);
        t.put("vgi.keywords", keywords);
        t.put("vgi.source_url", sourceUrl(relativeFile));
        return t;
    }
}

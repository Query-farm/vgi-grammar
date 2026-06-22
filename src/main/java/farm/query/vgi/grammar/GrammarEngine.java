package farm.query.vgi.grammar;

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.Languages;
import org.languagetool.rules.RuleMatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe LanguageTool wrapper that checks, counts, and auto-corrects text.
 *
 * <p>A {@link JLanguageTool} instance per language is <b>expensive</b> to build
 * (it loads that language's full rule set + tokenizer resources), so each one is
 * created <b>once</b> and cached for the process lifetime, keyed by resolved
 * language code. The default ('en-US') is warmed at worker startup
 * ({@link #warmDefault()}) so the first query doesn't pay the build cost.
 *
 * <p>{@code JLanguageTool.check} is synchronized per instance here so concurrent
 * VGI calls sharing a cached tool don't interleave (LanguageTool's per-check
 * state is not guaranteed concurrent-safe across threads on one instance).
 */
public final class GrammarEngine {

    /** The default language used when a caller omits one. */
    public static final String DEFAULT_LANGUAGE = "en-US";

    private static final GrammarEngine SHARED = new GrammarEngine();

    /** Cache of resolved-code -> tool. Built once per language, kept forever. */
    private final Map<String, JLanguageTool> tools = new ConcurrentHashMap<>();

    public GrammarEngine() {}

    public static GrammarEngine shared() {
        return SHARED;
    }

    /**
     * Warm the default-language tool so the first real query is fast. Best
     * effort: a failure here is logged to stderr and never aborts startup.
     */
    public void warmDefault() {
        try {
            tool(DEFAULT_LANGUAGE);
        } catch (Exception e) {
            System.err.println("[vgi-grammar] warm-up of " + DEFAULT_LANGUAGE
                    + " failed: " + describe(e));
        }
    }

    /** One row per issue LanguageTool found. */
    public record Issue(
            String ruleId,
            String category,
            String message,
            int offset,
            int length,
            String badText,
            List<String> suggestions) {}

    /**
     * Resolve a (possibly null/blank) language code to a LanguageTool
     * {@link Language}, defaulting to {@link #DEFAULT_LANGUAGE}.
     *
     * @throws UnknownLanguageException if the code is not a supported language
     */
    static Language resolveLanguage(String code) {
        String want = (code == null || code.isBlank()) ? DEFAULT_LANGUAGE : code.trim();
        // isLanguageSupported is strict (no fuzzy country fallback), so an unknown
        // code is rejected with a clear error rather than silently mapped.
        if (!Languages.isLanguageSupported(want)) {
            throw new UnknownLanguageException(want);
        }
        try {
            return Languages.getLanguageForShortCode(want);
        } catch (IllegalArgumentException e) {
            throw new UnknownLanguageException(want);
        }
    }

    /** Build-once / cache-forever accessor for a language's tool. */
    private JLanguageTool tool(String code) {
        Language lang = resolveLanguage(code);
        // Key by the canonical short code so 'en' and 'en-US' don't both build.
        return tools.computeIfAbsent(lang.getShortCodeWithCountryAndVariant(),
                k -> new JLanguageTool(lang));
    }

    /**
     * Check text and return one {@link Issue} per match. NULL or empty text
     * yields an empty list. An unknown language throws
     * {@link UnknownLanguageException}; LanguageTool I/O errors are wrapped.
     */
    public List<Issue> check(String text, String language) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        JLanguageTool lt = tool(language);
        List<RuleMatch> matches;
        try {
            synchronized (lt) {
                matches = lt.check(text);
            }
        } catch (IOException e) {
            throw new RuntimeException("grammar check failed: " + describe(e), e);
        }
        List<Issue> issues = new ArrayList<>(matches.size());
        for (RuleMatch m : matches) {
            int from = m.getFromPos();
            int to = m.getToPos();
            String bad = (from >= 0 && to <= text.length() && from <= to)
                    ? text.substring(from, to) : null;
            issues.add(new Issue(
                    m.getRule().getId(),
                    m.getRule().getCategory().getName(),
                    m.getMessage(),
                    from,
                    to - from,
                    bad,
                    List.copyOf(m.getSuggestedReplacements())));
        }
        return issues;
    }

    /** Number of issues in the text (0 for NULL/empty). */
    public int count(String text, String language) {
        return check(text, language).size();
    }

    /** True when the text has zero issues (true for NULL/empty). */
    public boolean isGrammatical(String text, String language) {
        return count(text, language) == 0;
    }

    /**
     * Apply the first suggestion of each issue to produce a corrected string.
     *
     * <p>Replacements are applied <b>right-to-left</b> (highest offset first) so
     * that earlier offsets stay valid as we mutate, and overlapping matches are
     * skipped (we only apply a match whose end is at or before the last applied
     * match's start). NULL text returns NULL; text with no fixable issues returns
     * the original string unchanged.
     */
    public String correct(String text, String language) {
        if (text == null) {
            return null;
        }
        List<Issue> issues = check(text, language);
        if (issues.isEmpty()) {
            return text;
        }
        // Sort by offset ascending, then walk back-to-front so indices hold.
        List<Issue> ordered = new ArrayList<>(issues);
        ordered.sort((a, b) -> Integer.compare(a.offset(), b.offset()));

        StringBuilder sb = new StringBuilder(text);
        int lastStart = text.length(); // no replacement may end after this
        for (int i = ordered.size() - 1; i >= 0; i--) {
            Issue issue = ordered.get(i);
            List<String> sugg = issue.suggestions();
            if (sugg.isEmpty()) {
                continue; // nothing to apply for this issue
            }
            int from = issue.offset();
            int to = from + issue.length();
            if (from < 0 || to > sb.length() || from > to) {
                continue; // defensive: skip out-of-range
            }
            if (to > lastStart) {
                continue; // overlaps an already-applied (later) replacement
            }
            sb.replace(from, to, sugg.get(0));
            lastStart = from;
        }
        return sb.toString();
    }

    /** All supported language codes + human names, sorted by code. */
    public List<String[]> languages() {
        List<String[]> out = new ArrayList<>();
        for (Language lang : Languages.get()) {
            out.add(new String[] {
                    lang.getShortCodeWithCountryAndVariant(),
                    lang.getName(),
            });
        }
        out.sort((a, b) -> a[0].compareTo(b[0]));
        return out;
    }

    static String describe(Throwable t) {
        String msg = t.getMessage();
        String type = t.getClass().getSimpleName();
        return (msg == null || msg.isBlank()) ? type : type + ": " + msg;
    }

    /** Thrown when a caller passes a language code LanguageTool doesn't ship. */
    public static final class UnknownLanguageException extends RuntimeException {
        public UnknownLanguageException(String code) {
            super("unknown language '" + code + "'; see grammar_languages() for supported codes");
        }
    }
}

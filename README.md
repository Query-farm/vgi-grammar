# vgi-grammar

A [VGI](https://query.farm) worker (Java) that checks **grammar, style, and
spelling** and **auto-corrects** text as DuckDB SQL functions, powered by
[LanguageTool](https://languagetool.org). Catalog name: `grammar`.

```sql
INSTALL vgi FROM community; LOAD vgi;
ATTACH 'grammar' (TYPE vgi, LOCATION 'java -jar vgi-grammar-all.jar');

-- One row per issue, with suggested replacements (suggestions is VARCHAR[]):
SELECT rule_id, message, bad_text, suggestions
FROM grammar.grammar_check('She go to school every day.');

-- Auto-correct: apply the first suggestion of each issue.
SELECT grammar.correct('She go to school every day.');   -- 'She goes to school every day.'

-- Count / boolean helpers:
SELECT grammar.grammar_count('She go to school.');        -- 1
SELECT grammar.is_grammatical('This sentence is correct.'); -- true

-- Supported language codes:
SELECT * FROM grammar.grammar_languages();
```

The worker is a self-contained, shaded fat JAR. Its manifest sets
`Add-Opens: java.base/java.nio`, so a bare `java -jar vgi-grammar-all.jar` works
directly as a VGI `LOCATION` with no extra JVM flags.

## Functions

| Function | Kind | Signature | Result |
| --- | --- | --- | --- |
| `grammar_check` | table | `grammar_check(text, language := 'en-US')` | one row per issue: `(rule_id VARCHAR, category VARCHAR, message VARCHAR, "offset" INT, length INT, bad_text VARCHAR, suggestions VARCHAR[])` |
| `grammar_count` | scalar | `grammar_count(text)` / `grammar_count(text, language)` | `INT` — number of issues |
| `is_grammatical` | scalar | `is_grammatical(text)` / `is_grammatical(text, language)` | `BOOLEAN` — zero issues? |
| `correct` | scalar | `correct(text)` / `correct(text, language)` | `VARCHAR` — corrected text |
| `grammar_languages` | table | `grammar_languages()` | `(code VARCHAR, name VARCHAR)` — supported language codes |

Behaviour:

- **NULL / empty text** → `grammar_check` returns no rows; the scalars return
  `NULL` (count/bool) or the input unchanged (`correct` returns the original, or
  `NULL` for `NULL` input).
- **`correct`** applies the **first** suggestion of each issue **non-overlapping,
  right-to-left** (highest offset first) so earlier offsets stay valid; issues
  without a suggestion are skipped.
- **Unknown language** → a clear, query-failing error
  (`unknown language 'xx-ZZ'; see grammar_languages() for supported codes`).
- The worker **catches per-row errors** so one bad value never crashes a batch;
  all logging goes to **stderr only** (stdout is the Arrow-IPC channel).

## Languages

This worker ships the **English** rule set (`org.languagetool:language-en`),
which provides these `language` codes:

| Code | Name |
| --- | --- |
| `en` | English (generic) |
| `en-US` | English (US) — **default** |
| `en-GB` | English (GB) |
| `en-AU` | English (Australian) |
| `en-CA` | English (Canadian) |
| `en-NZ` | English (New Zealand) |
| `en-ZA` | English (South African) |

`grammar_languages()` is the authoritative list at runtime. English only is
shipped on purpose: each additional `org.languagetool:language-*` module carries
a large rule/resource set and would balloon the ~91 MB fat JAR. To add more
languages, declare the desired `language-*` artifacts in `build.gradle.kts` and
rebuild — the code resolves any language LanguageTool supports with no changes.

## Performance / warm-up

A `JLanguageTool` instance per language is **expensive** to build (it loads that
language's full rule set and tokenizer resources). The worker therefore builds
each one **once** and **caches it for the process lifetime**, keyed by resolved
language code. Because a VGI worker is a long-lived process, this cost is paid
once and amortized across every query.

The default language (`en-US`) is **warmed at startup** on a background daemon
thread, so the worker starts serving immediately and the first default-language
query doesn't pay the build cost.

## Dependencies & licensing

| Component | License | Notes |
| --- | --- | --- |
| `vgi-grammar` (this worker) | **MIT** | This repository's own code. |
| [LanguageTool](https://languagetool.org) (`org.languagetool:language-en`, `languagetool-core`) | **LGPL-2.1** | Grammar / style / spelling engine + English rules. **See the LGPL note below.** |
| [`farm.query:vgi` / `farm.query:vgirpc`](https://github.com/Query-farm) | Query Farm Source-Available | The VGI Java SDK (the worker/catalog + RPC API). |
| Apache Arrow, SLF4J, Log4j-to-SLF4J | Apache-2.0 | Arrow IPC transport + logging-to-stderr bridge. |

### LGPL note for LanguageTool

**LanguageTool is licensed under the LGPL-2.1.** This worker uses it as an
**unmodified, standard Maven dependency** — it is depended upon and called
through its public API, never copied into or patched within this repository.
Under the LGPL that is the "using the library" case (not "modifying" it), so
**`vgi-grammar`'s own code remains MIT and is fine for commercial use.**

The standard LGPL obligation is that a recipient of a distributed bundle must be
able to **relink or replace** the LGPL component with a modified version. That is
satisfied here because LanguageTool is consumed as an ordinary, **swappable**
Maven Central artifact (`org.languagetool:language-en`, pinned by version in
`build.gradle.kts`): the build is reproducible from source, the LanguageTool
JARs are unmodified upstream releases, and anyone can bump or replace the
LanguageTool version and rebuild the fat JAR to relink against a different (e.g.
self-modified) LanguageTool. If you ever **vendor or patch** LanguageTool itself,
those changes must in turn be offered under the LGPL.

## Building & testing

```sh
./gradlew test        # JUnit (in-process table/scalar drivers, real LanguageTool)
make test-sql         # shadowJar + haybarn-unittest over test/sql/*
make test             # both
```

`make test-sql` builds `build/libs/vgi-grammar-*-all.jar`, sets
`VGI_GRAMMAR_WORKER="java -jar <abs jar>"`, and runs `haybarn-unittest` with the
worker attached as a VGI `LOCATION`. Install the runner once:

```sh
uv tool install haybarn-unittest
export PATH="$HOME/.local/bin:$PATH"
```

The VGI SDK and LanguageTool both resolve from **Maven Central**, so the build is
fully self-contained — no `mavenLocal`, no sibling checkout, no composite build.
CI (`.github/workflows/test.yml`) is a single `build-and-test` job: JUnit →
shadowJar → HTTP boot smoke test → `make test-sql`.

## License

MIT — see [LICENSE](LICENSE). LanguageTool is LGPL-2.1 (see the note above).

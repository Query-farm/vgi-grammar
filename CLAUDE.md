# CLAUDE.md — vgi-grammar

Contributor/agent notes. User-facing docs live in `README.md`; this is the
"how it's built and where the sharp edges are" companion.

## What this is

A [VGI](https://query.farm) worker (Java) wrapping **LanguageTool** to check
grammar / style / spelling and auto-correct text, as DuckDB SQL functions.
Modeled on `vgi-tika` / `vgi-poi`; built with Gradle (Kotlin DSL, JDK 21) into a
shaded fat JAR. Catalog name `grammar` (single `main` schema). Package
`farm.query.vgi.grammar`.

## Layout

```
build.gradle.kts / settings.gradle.kts / gradle.properties   Gradle, shadow plugin (com.gradleup.shadow 9.4.2)
src/main/java/farm/query/vgi/grammar/
  Main.java                  Worker.builder().catalogName("grammar")...registerTable/registerScalar; warms en-US at startup
  GrammarEngine.java         LanguageTool integration: per-language JLanguageTool cache, check/count/correct/languages
  GrammarSchemas.java        Arrow schemas + cell writers (incl. LIST<VARCHAR> for suggestions)
  GrammarCheckFunction.java  table fn: grammar_check(text, language) -> one row per issue
  GrammarLanguagesFunction.java  table fn: grammar_languages() -> (code, name)
  GrammarCount.java          scalar grammar_count(text) / (text, language)   [OneArg / TwoArg]
  IsGrammatical.java         scalar is_grammatical(text) / (text, language)  [OneArg / TwoArg]
  Correct.java               scalar correct(text) / (text, language)         [OneArg / TwoArg]
src/test/java/...            JUnit: GrammarCheckTest (tables) + GrammarScalarTest (scalars) + TestSupport
test/sql/*.test              haybarn-unittest E2E (grammar.test, languages.test)
Makefile                     build / test-unit / test-sql / test / clean
```

## Sharp edges

1. **Overloaded scalars need one class per arity.** The SDK's `ScalarFn`
   reflects a **single** `compute()` per class (`ComputePlan.findCompute` throws
   `IllegalStateException` otherwise). So each overloaded scalar is an abstract
   base + nested `OneArg` / `TwoArg` final classes, each declaring exactly one
   `compute()`, all registered under the same SQL name (DuckDB resolves on
   arity). The two-arg `language` parameter is bound with `@Const("language")`
   (a per-call constant string); the text is a `@Vector("text")` column.
2. **`suggestions` is a DuckDB `VARCHAR[]`** = Arrow `LIST<utf8>`. Built the
   field with `ArrowType.List` + a child `$data$` utf8, and written via
   `ListVector.getWriter()` → `startList()` / `writeVarChar(s)` / `endList()`
   plus `setLastSet(row)`. See `GrammarSchemas.listOfUtf8` / `writeStringList`.
3. **Expensive init, cached forever.** A `JLanguageTool` per language is slow to
   build; `GrammarEngine` caches one per resolved code in a `ConcurrentHashMap`
   for the process lifetime. `Main` warms `en-US` on a background daemon thread
   at startup so the worker starts serving immediately and the first query is
   primed. `check()` is `synchronized` on the per-language tool (its per-check
   state isn't guaranteed concurrent-safe on one instance).
4. **Unknown language = hard error.** `GrammarEngine.resolveLanguage` uses
   `Languages.isLanguageSupported` (strict — no fuzzy country fallback; the
   2-arg `getLanguageForShortCode(code, fallbacks)` silently maps unknown codes,
   which is why we don't use it) and throws `UnknownLanguageException` with a
   clear message. The scalars re-throw it (query fails) but catch every other
   per-row error so one bad value never crashes a batch.
5. **`correct` is right-to-left, non-overlapping.** Apply the first suggestion of
   each issue from highest offset to lowest so earlier offsets stay valid; skip a
   match whose end overlaps an already-applied later match. NULL → NULL; no
   fixable issues → original string unchanged.
6. **Log4j / stdout.** LanguageTool's deps use the Log4j 2 API; without a
   provider, Log4j's StatusLogger writes to **stdout**, which is the Arrow-IPC
   channel for a stdio worker → protocol corruption. `log4j-to-slf4j` +
   `slf4j-simple` route all logging to **stderr**. Anything that writes to stdout
   breaks a stdio VGI worker.
7. **Fat-JAR SPI merge.** Like vgi-tika, a `generateMergedSpi` task pre-merges
   every `META-INF/services/*` so shadow's `mergeServiceFiles()` can't collapse
   colliding providers (LanguageTool registers rules/tokenizers via SPI).
8. **`haybarn-unittest` skips `require vgi`** — `.test` files use explicit
   `LOAD vgi;` + `require-env VGI_GRAMMAR_WORKER`.

## SDK dependency & CI (self-contained via Maven Central)

`farm.query:vgi:0.19.0` (pulls `farm.query:vgirpc` transitively; `vgirpc:0.16.0`
is declared explicitly since the code imports `farm.query.vgirpc.*`) and
`org.languagetool:language-en:6.8` are all on **Maven Central**, so the build is
fully self-contained: no sibling checkout, no `mavenLocal`, no composite build.
The in-process test driver (`TestSupport`) constructs `TableInitParams` directly
with the vgi `TableInitParams` record arity (trailing `atUnit`, `atValue`,
`storage`, `copyFrom` components as `null`); this arity holds through 0.19.0. vgi 0.19.0 adds the
`input_from_args` FunctionInfo field the current community extension expects
(0.18.0 emitted a 31-field schema and ATTACH failed with a FunctionInfo mismatch).
Get the exact constructor with
`javap -cp <vgi jar> farm.query.vgi.table.TableInitParams` if you bump the SDK.

## Testing

```sh
./gradlew test                # JUnit
make test-sql                 # shadowJar + haybarn-unittest over test/sql/*
make test                     # both
```

## Packaging

~95 MB shaded JAR (LanguageTool 6.8 + English rules). English only is shipped on
purpose (more `language-*` modules balloon the JAR). LanguageTool is **LGPL-2.1**,
used as an unmodified, swappable Maven dependency — the worker's own code is MIT.
See the README licensing table + LGPL note.

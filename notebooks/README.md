# Grammar notebooks

[01 · Recheck automatic corrections before accepting them](01_recheck_automatic_corrections.ipynb)
demonstrates `grammar_languages`, `correct`, `grammar_count`, `is_grammatical`, and
`grammar_check` through visible SQL. Ten synthetic drafts retain their original text
and each candidate version. Two charts compare detected rule matches and distinguish
nonempty checked text from blank or missing drafts.

The default first pass changes the eight nonempty drafts' total detected matches
from 11 to 3, with two drafts still flagged. One draft goes from one match to two.
The second-pass experiment leaves zero detected matches in this constructed sample;
it does not establish factual accuracy, preserved meaning, or editorial approval.
No draft is sent anywhere: the worker runs LanguageTool locally.

## Run

Install [uv](https://docs.astral.sh/uv/) and Java 21 or newer. Put `java` on `PATH`;
on macOS with Homebrew Java, this may require adding
`/opt/homebrew/opt/openjdk@21/bin`. The companion's cache lock supports macOS/Linux.
The initial run needs network access for the Python environment, VGI extension,
and approximately 97 MB released JAR. Language rules run offline after installation.

From the repository root:

```sh
uv sync --project notebooks --frozen
uv run --project notebooks --frozen python notebooks/execute.py
```

The complete `notebooks/` directory also works independently of the worker checkout:

```sh
cd notebooks
uv sync --frozen
uv run --frozen python execute.py
```

In an editor, select `notebooks/.venv/bin/python`, use `notebooks/` as the kernel
working directory, and restart/run all. The connection and analytical parameter
remain visible; import, plotting, assertion and cleanup code is folded. Useful
outputs are saved in the notebook. Charts use Seaborn styling and Matplotlib.

Run the single-parameter experiment into a separate directory:

```sh
uv run --project notebooks --frozen python notebooks/execute.py \
  --parameters '{"CORRECTION_PASSES":2}' \
  --output-dir notebooks/validation/two-passes
```

The runner uses a fresh kernel and only replaces a saved notebook after successful
execution. Overrides require a separate output directory; unknown parameters are
rejected. The default remains unchanged if a run fails. Chart review PNGs are saved
under ignored `notebooks/validation/charts/`; the notebook embeds them as well.

## Portable release connection

The visible SQL uses a literal location:

```sql
ATTACH 'grammar' (TYPE vgi,
 LOCATION 'uv run --frozen --project . python release_jar.py Query-farm/vgi-grammar v0.4.0 vgi-grammar-v0.4.0.jar 83586bbdaba1a3bfb59a3d17810e00259c53d5554cdac5d4ba8cd5b09628a02d');
```

[`release_jar.py`](release_jar.py) downloads the public
[v0.4.0 JAR](https://github.com/Query-farm/vgi-grammar/releases/tag/v0.4.0),
checks its pinned SHA-256, and starts Java. Cached bytes are checked on every launch;
downloads use a bounded POSIX lock and atomic file publication. The cache lives
under `${XDG_CACHE_HOME:-~/.cache}/vgi-notebook-jars/`.
No Gradle, worker build, credential, or sibling checkout is needed for the notebook.
Its separate lock uses Haybarn 2.0.0rc11 without changing repository Java dependencies.

## What the results mean

The release ships LanguageTool 6.8 English rules. `en-US` is fixed in this example;
other dialects can produce different matches. The first suggestion is not a verified
statement of author intent. The worker applies non-overlapping replacements from
right to left and can expose new matches on a subsequent check.

In the actual v0.4.0 release, empty text returns count `0` and flag `true`; SQL NULL
returns NULL for both. The notebook explicitly demonstrates this behavior and keeps
the empty and missing records outside the nonempty comparison. An internal scalar
error can also return NULL, so unavailable results are never filled with zero.
The intentionally false sentence about the Moon demonstrates that zero detected
writing issues is not fact checking.

`grammar_check` accepts literal text and returns one row per issue. Scalar functions
process a text column. Detailed offsets use zero-based Java UTF-16 units; all example
text is ASCII. Counts from the issue table reconcile with the scalar count for the
same sentence. The ten SQL audit checks preserve row identity, version count, original
text, empty/null behavior, scalar agreement, and detailed spans; they do not impose
a monotonic-improvement or publication-readiness assertion.

Validation includes default execution, the two-pass experiment from a detached
notebooks-only directory, chart review, runner guards, and notebook lint. Repository
checks passed 12 JUnit tests, 71 stdio / 77 HTTP / 71 Unix SQL assertions across three
cases per transport, and executed metadata lint at 100/100 L2 with zero findings.
Source checks build the worker for testing; notebook execution uses the release JAR.

The worker is MIT licensed; LanguageTool is LGPL-2.1. See the repository's licensing
details. Unmodified official branding links to [Query.Farm](https://query.farm/)
and [VGI](https://query.farm/vgi/).

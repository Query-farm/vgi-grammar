# Grammar Checker notebooks

[01 · Recheck automatic corrections before accepting them](01_recheck_automatic_corrections.ipynb)
checks ten synthetic English drafts, retains the original and each candidate version,
and rechecks the worker's suggested edits. One first-pass correction exposes more
matches. Another sentence has zero writing issues but makes an intentionally false
claim. Blank and missing drafts remain separate from checked nonempty text.

[Open in Colab](https://colab.research.google.com/github/Query-farm/vgi-grammar/blob/main/notebooks/01_recheck_automatic_corrections.ipynb)
and run all. The bootstrap installs only the base
[shared package v0.3.9](https://github.com/Query-farm/vgi-example-notebooks/tree/v0.3.9),
which owns the catalog-title heading standard, branding, SVG presentation and
hash-verified companion retrieval. Local publication uses its `[runner]` extra.
GitHub shows linked SVG assets; Jupyter and Colab reruns show native SVG.

## Run locally

Install Git and [uv](https://docs.astral.sh/uv/). From this directory:

```sh
uv sync --locked
uv run --locked vgi-notebooks 01_recheck_automatic_corrections.ipynb --timeout 300
```

In a Jupyter editor select `notebooks/.venv/bin/python`, restart the kernel, and run
all. The command above provides its own temporary kernel specification. No local
worker build, Gradle installation or system Java is needed for the notebook.
The Python environment locks Haybarn 2.0.0rc11; no engine build is required.

## Release and managed Java

The visible teaching connection stays explicit about the released worker:

```sql
INSTALL vgi FROM community;
LOAD vgi;
ATTACH 'grammar' (TYPE vgi,
 LOCATION 'uv run --locked --no-project --python 3.13 grammar_release.py v0.4.0');
```

The reviewed [launcher](grammar_release.py) and its [script lock](grammar_release.py.lock)
are retrieved through shared registry `grammar`, pinned to public source commit
`c65166c49a5d3949b1fd839f7b4886ea11f84f4c`. The wrapper accepts only its reviewed
release. It downloads `vgi-grammar-v0.4.0.jar` from the public v0.4.0 release and
requires SHA-256 `83586bbdaba1a3bfb59a3d17810e00259c53d5554cdac5d4ba8cd5b09628a02d`.
Cached JAR bytes are rechecked on every launch. Downloads use a bounded POSIX lock
and atomic publication under `${XDG_CACHE_HOME:-~/.cache}/vgi-notebook-jars/`.

The separate PEP 723 launcher environment pins
[jdk4py 21.0.8.2](https://pypi.org/project/jdk4py/21.0.8.2/), supplying Java 21.0.8.
It uses that package's absolute Java binary and verifies its actual `-version`
response before starting the worker. It neither trusts system Java nor installs a
Java package into the notebook kernel. The lock records the managed-runtime wheel
hashes. The launcher supports macOS and glibc Linux on x86-64/arm64, including
Colab's Linux platform; its POSIX locking implementation does not support Windows.
Initial setup downloads Python packages, the managed runtime, the approximately
97 MB worker JAR and the VGI extension. Language checks then run locally, with no
hosted model, LanguageTool API, credentials or messages.

Optional preflight, from this directory:

```sh
uv run --locked --no-project --python 3.13 grammar_release.py --check-runtime v0.4.0
uv run --locked --no-project --python 3.13 grammar_release.py --verify-only v0.4.0
```

Changing a release requires reviewing its artifact, digest and managed-runtime
compatibility, then rerunning the default and experiment. The notebook's shared
connection starts with an empty temporary SQL-secret directory and persistent
secrets disabled.

## One-parameter experiment

Change only the number of correction passes, preserving the default:

```sh
uv run --locked vgi-notebooks 01_recheck_automatic_corrections.ipynb \
  --parameters '{"CORRECTION_PASSES":2}' \
  --output-dir artifacts/two-passes --timeout 300
```

The first pass changes the eight nonempty drafts' total detected matches from
**11 to 3**, with **two drafts** still flagged; one draft gains a match. The second
pass leaves **zero detected matches** in this constructed sample. Original text,
blank/missing states and the false factual claim remain preserved. Inspect exact
wording as well as counts; this is not a benchmark or proof of convergence,
preserved meaning, factual accuracy, or editorial approval.

Overrides require a separate output directory, unknown names are rejected, and
failed runs preserve the previous successful default. Generated review PNGs, logs,
companion caches and experiment copies are ignored. A notebook-only copy retrieves
its immutable launcher and lock through the bootstrap; no manual cloning is needed.

## What the results mean

The release ships LanguageTool 6.8 English rules. The notebook materializes the
actual supported-language list, selects and requires `en-US`, and uses that code
explicitly for every correction and recheck. Another dialect may produce different
matches. A supported language is not a promise that its rules fit every author's
context.

`correct` applies each first suggestion from right to left and skips overlaps.
It can expose new matches on a later pass. The notebook retains all ten row IDs and
every requested version rather than overwriting the source. `grammar_count` and
`is_grammatical` report rule-match results, not a general writing-quality score.
The release returns **0 / true for empty text** and **NULL / NULL for SQL NULL**.
An internal per-row scalar error can also return NULL, so unavailable results stay
visible rather than becoming zero. The false Moon sentence demonstrates that a
zero count is not fact checking.

`grammar_check` explains the issue behind one edit with a rule, category, flagged
span and ordered suggestions. Its detailed count reconciles with the scalar count
for the same original text. Offsets use zero-based Java UTF-16 units; the synthetic
sample is ASCII. Ten SQL checks reconcile versions, row identity, original text,
availability, scalar agreement and detailed spans. They intentionally do not assert
that every edit improves the text or that a zero count means it is ready to publish.

## Coverage and provenance

[coverage.json](coverage.json) records meaningful visible executed SQL for all
**five of five** public logical names: supported languages, correction, issue count,
zero-match flag and detailed issue rows. Arity overloads and the language table/function
alias are counted once. Complete released and current function/table/view exports
are retained separately. The proper notebook title and subtitle come from the
released catalog's actual metadata.

The notebook executes released v0.4.0. Current public baseline
`8f32a700ae059c9b8cc82f5d22fc98907d10f873` has the same named interface; its additional
composed metadata examples do not change the demonstrated engine behavior. All
supported-language rows and representative correction/count/Boolean outputs agree
across both actual runtimes. Reviewed current-interface coverage is distinct from
exact current-runtime notebook execution.

Verification passed: 16 code cells, eight SQL cells, two SVG charts, ten SQL audit
checks and the selected-dialect gate. The default took 90.6 seconds; the detached
two-pass experiment took 79.1 seconds with empty companion, managed-runtime and
JAR caches. Both companions and the downloaded JAR matched their pinned hashes.
All four figures were reviewed. Three runner protections and two parameter
rejections preserved the saved default.

A fresh Python 3.12 kernel ran the notebook after base-only installation with
pandas 2.2.3, Matplotlib 3.10.0, IPython 8.30.0, ipykernel 6.17.1 and
jupyter-client 7.4.9 already imported. Their versions remained unchanged,
`pip check` passed, and neither the local runner nor `jdk4py` was installed in
that kernel. Separate real-worker probes passed with system Java missing and with
a deliberately old Java executable; neither invoked system Java. This is local
compatibility testing, not a claim of a manual hosted-Colab execution.

Source validation passed 12 freshly run JUnit tests, 71/77/71 SQL assertions over
stdio/HTTP/Unix, and executed metadata validation (100/100, L2, zero findings).
The existing green source CI baseline is retained for this notebook-only change;
no container publication is triggered.

Worker code is MIT licensed; LanguageTool is LGPL-2.1. The managed OpenJDK package
has its own runtime licensing, linked from its package documentation. Worker source,
Java build files and dependency declarations remain unchanged.

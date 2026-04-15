# Changelog

All notable changes to this project will be documented in this file.
This changelog follows [keepachangelog.com](https://keepachangelog.com/).

## [Unreleased]

## [0.1.2] - 2026-04-15

### Added
- **Tables as a first-class DSL form** (`[:table opts? & cols]`): The
  `clj-format` entry point now dispatches table specs through the new
  `clj-format.table` facility, matching the Hiccup convention used
  throughout the library. Writer semantics (`nil`/`false`, `true`, or a
  Writer) are unchanged — there is no separate `format-table` or
  `print-table` function.
  - Bare-keyword columns (`:name`), explicit `[:col :name {...}]` forms,
    and raw column maps all accepted.
  - Nine border styles: `:ascii`, `:unicode`, `:rounded`, `:heavy`,
    `:double`, `:markdown`, `:org`, `:simple`, `:none`, plus custom maps.
  - Per-column alignment (`:left`, `:right`, `:center`), auto-sizing,
    fixed widths, min/max constraints.
  - Cell formatting via any DSL directive: `:int`, `:money`, `:roman`,
    `[:int {:group true}]`, `[:if "Yes" "No"]`, `[:money {:sign :always}]`,
    or custom `(fn [v] string)`.
  - Text overflow handling: `:ellipsis` truncation, `:clip`, and
    `:wrap` word-wrapping. Wrap mode expands one logical row into
    multiple physical rows with other columns shown only on the first
    line.
  - Footer rows with built-in aggregates (`:sum`, `:avg`, `:min`, `:max`,
    `:count`) or custom aggregate functions.
  - Computed columns via `[:col (fn [row] ...) {...}]`.
  - Header case conversion, header suppression, row rules, nil-value
    display, and per-column title overrides.
  - `clj-format.core/table-dsl` exposes the generated DSL + argument
    list for inspection and reuse.
  - Worked examples in the README covering typed columns, word wrap,
    multi-line embedding (nested tables and FIGlet banners), markdown
    output, and footer aggregation.
- **`:figlet` directive** via `clj-format.figlet` — renders FIGlet
  ASCII-art banners using
  [clj-figlet](https://github.com/danlentz/clj-figlet). Requiring the
  namespace installs an expander into the new
  `clj-format.core/*dsl-preprocessor*` hook; the namespace is
  lazy-loaded, so projects that never `require` it pay nothing.
  `clj-figlet` now ships as a normal dependency of clj-format on all
  three runners (Leiningen, `deps.edn`, Babashka) — no extra
  dependency declaration is needed.
- **Extension hook**: `clj-format.core/*dsl-preprocessor*` — a dynamic
  var that extension namespaces can rebind to transform DSL forms
  before compilation. Defaults to `identity`.
- Clause-local `~;` support for `:justify` and `:logical-block` via
  `[:clause opts & body]`, allowing the DSL to preserve and compile
  separator-local parameters and flags such as `~0,20:;`.
- Full DSL coverage and documentation for the word-wrapping `~<...~>`
  example that previously had to be shown as string passthrough only.
- Adversarial input tests via the Big List of Naughty Strings (BLNS),
  verifying crash-free round-trips through the compiler, parser, and table
  formatter for ~670 strings including Unicode edge cases, control
  characters, zero-width joiners, emoji, RTL text, and Zalgo.

### Changed
- The README and examples now include stronger layout showcases, including
  richer tabular-report examples and a real DSL rendering of word-wrapped
  prose.
- GitHub Actions now asserts that every runner (JVM, CLJS, Babashka) emits
  a `Ran N tests` summary, so an empty or short-circuited test run can no
  longer pass CI silently.

### Fixed
- `lein test` was silently running only a subset of the test suite. The
  `bb_runner.clj` file has a top-level `(-main)` call (so `bb
  bb_runner.clj` works as a script); when Leiningen's bultitude-based
  discovery scanned the test directory it required the namespace, the
  `(-main)` fired at load time, ran its own hardcoded subset of tests,
  and called `System/exit` — which killed Leiningen's actual test run
  before it could execute the remaining namespaces. The auto-invoke is
  now guarded by `(when (System/getProperty "babashka.version") ...)` so
  it only fires under Babashka. Bare `lein test` now finds and runs
  every test namespace (including the new `clj-format.table-test`,
  `clj-format.figlet-test`, and `clj-format.naughty-nopes-test`).
- The CLJS test runner script (`bin/test-cljs`) was silently running zero
  tests: the `-c` (compile-only) invocation of `cljs.main` does not set
  `*main-cli-fn*`, so `cljs.nodejscli` had nothing to call when `node`
  launched the compiled bundle. `clj-format.cljs-runner` now sets
  `*main-cli-fn*` at load time, so the CLJS suite actually executes under
  both local runs and CI. The new CI `Ran N tests` assertion guards
  against any future regression of this kind.
- Parser and compiler now round-trip clause-local `~;` behavior correctly
  in justification forms, with regression coverage across JVM, CLJS, and
  Babashka.
- Word-wrap in `:overflow :wrap` now preserves interior whitespace on any
  line that already fits the column width, so pre-formatted multi-line
  content (ASCII art, nested tables, FIGlet banners) drops cleanly into
  cells without having its load-bearing spacing collapsed.

## [0.1.1] - 2026-04-02

### Added
- ClojureScript compatibility via shared `.cljc` parser/compiler/directive
  namespaces plus a unified `clj-format.core` (`.cljc`) that delegates to
  the host `cl-format` on both JVM and ClojureScript.
- Babashka compatibility — full test suite passes under `bb`.
- CLJS test coverage and runnable CLJS test entry points for both `lein`
  and `deps.edn` workflows.
- Generative property-based tests via `test.check` for DSL canonicalization,
  compile idempotence, execution equivalence, and structured error reporting.
- Host-parity tests pinning floating-point, monetary, justification, and
  logical-block output across JVM and ClojureScript.

### Changed
- Refactored special-dispatch directives (`~R`, `~*`, `~_`) to use shared
  data-driven metadata in `clj-format.directives`, simplifying parser and
  compiler logic while preserving behavior.
- The public `clj-format` API now validates output targets and reports
  invalid ones with structured `ExceptionInfo`.
- Clojure dependency is now `:scope "provided"` in `project.clj`.

### Fixed
- Preserved `~C` flag combinations during parse/compile round-trips,
  escaped `:each` separators containing `~`, and added regression tests
  for special-dispatch edge cases.

## 0.1.0 - 2026-03-27

### Added
- **Parser** (`clj-format.parser/parse-format`): Recursive descent parser
  that converts any cl-format string into the clj-format s-expression DSL.
  Covers all 33 cl-format directives including compound/nested forms.

- **Compiler** (`clj-format.compiler/compile-format`): Serializes the DSL
  back into cl-format strings. Full round-trip fidelity:
  `(= s (compile-format (parse-format s)))` for any valid format string.

- **Core API** (`clj-format.core/clj-format`): Drop-in replacement for
  `clojure.pprint/cl-format`. Accepts format strings (passthrough),
  DSL vectors, or bare keywords. Supports all writer modes (nil, true,
  false, Writer).

- **Shared directive config** (`clj-format.directives`): Single source of
  truth for all directive metadata (characters, parameter names, flag
  mappings). Used by both parser and compiler.

- **DSL design**: Hiccup-convention `[:keyword opts? & body]` with:
  - Semantic option names (`:width`, `:group`, `:sign`, `:sep`, etc.)
    instead of cl-format's `:colon`/`:at` flags
  - Distinct keywords for distinct behaviors (`:cardinal`/`:ordinal`/
    `:roman`; `:if`/`:when`/`:choose`)
  - Case conversion as `:case` option, flattened into the element
  - Bare keywords as shorthand (`:str` for `[:str]`)
  - `:sep` on `:each` abstracts the `~^separator` pattern

- **Documentation**:
  - `doc/dsl.md` — complete DSL reference
  - `doc/examples.md` — 50+ side-by-side examples from Practical Common
    Lisp, CLtL2, ClojureDocs, and the CL HyperSpec
  - `README.md` — quick start, DSL overview, real-world examples

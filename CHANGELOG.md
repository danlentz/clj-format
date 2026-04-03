# Changelog

All notable changes to this project will be documented in this file.
This changelog follows [keepachangelog.com](https://keepachangelog.com/).

## [Unreleased]

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

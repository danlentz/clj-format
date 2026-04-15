# Extending the clj-format DSL

clj-format has a single extension point: the dynamic var
`clj-format.core/*dsl-preprocessor*`. Before compilation, every vector-shaped
format argument is passed through this function. The default value is
`identity`, so by default nothing happens — the DSL flows straight into the
compiler.

An extension namespace rebinds this var (typically at load time) to a walker
that expands custom directives into forms the compiler already understands.
This document explains how the hook works, how to build one, and how the
bundled `clj-format.figlet` extension uses it.

## Contract

```clojure
(def ^:dynamic *dsl-preprocessor* identity)
```

- **Input:** a DSL vector (the `fmt` argument to `clj-format`, when it is a
  vector). Strings and bare keywords bypass the preprocessor entirely, so you
  only need to handle vector forms.
- **Output:** a DSL form the compiler accepts — a vector, a bare keyword, or a
  literal string. Most extensions return a vector, with their custom directives
  replaced by standard clj-format forms or literal strings.
- **Scope:** called once per top-level `clj-format` call. Your walker is
  responsible for recursing into nested directives where relevant.
- **Purity:** the preprocessor should be a pure function of its input. The
  DSL it emits is compiled exactly like a hand-written DSL, so no runtime
  state is threaded through it.

Because `*dsl-preprocessor*` is a dynamic var, callers can also rebind it
per-call with `binding`:

```clojure
(binding [clj-format.core/*dsl-preprocessor* my-preprocessor]
  (clj-format.core/clj-format nil [:my-directive ...]))
```

Extensions installed at the root binding apply globally; `binding` is the
right escape hatch for tests, for composition, or for scoping an extension to
a single call site.

## Writing an extension

A minimal extension has three parts:

1. A predicate that recognizes your custom directive shape.
2. An expander that turns one occurrence of your directive into standard DSL
   (or a literal string).
3. A recursive walker that calls the expander on every matching subform and
   leaves everything else alone.

Here is a skeleton that adds a `[:shout "hello"]` directive expanding to
upper-cased text:

```clojure
(ns my.app.shout
  (:require [clojure.string  :as str]
            [clj-format.core :as core]))

(defn- shout-form? [x]
  (and (vector? x) (= :shout (first x))))

(defn- expand-shout [[_ text]]
  (str/upper-case text))

(defn expand [dsl]
  (cond
    (shout-form? dsl) (expand-shout dsl)
    (vector? dsl)     (mapv expand dsl)
    (seq? dsl)        (mapv expand dsl)
    :else             dsl))

(alter-var-root #'core/*dsl-preprocessor* (constantly expand))
```

Requiring `my.app.shout` once installs the preprocessor. From then on:

```clojure
(clj-format.core/clj-format nil [:shout "hello"])     ;; => "HELLO"
(clj-format.core/clj-format nil ["[" [:shout "hi"] "]"]) ;; => "[HI]"
```

Notes on the walker:

- Recur into both vectors and seqs — the compiler accepts both, and user code
  passes both.
- Stop the walk wherever your directive lives; you do not need to understand
  every clj-format directive, only your own.
- Your expander can return any shape the compiler accepts, including a
  literal string (as above), a new vector (`[:str {:width 10}]`), or even
  another form that will itself be handled by the compiler.

## Composing multiple extensions

Because `*dsl-preprocessor*` is a single var, the last extension to call
`alter-var-root` wins at install time. If you need two extensions to coexist,
compose them explicitly:

```clojure
(require 'my.app.shout 'other.app.glow)     ;; each installs its own expander
(alter-var-root #'clj-format.core/*dsl-preprocessor*
                (constantly (comp my.app.shout/expand other.app.glow/expand)))
```

`comp` here runs the walkers in sequence on the same DSL tree. The order
matters if the extensions rewrite overlapping forms; if they do not, either
order is fine.

For per-call composition inside a test or library, `binding` is cleaner than
re-installing at the root:

```clojure
(binding [clj-format.core/*dsl-preprocessor*
          (comp my.app.shout/expand other.app.glow/expand)]
  (clj-format.core/clj-format nil dsl))
```

## Worked example: `clj-format.figlet`

The bundled `clj-format.figlet` namespace is the reference implementation of
the extension pattern. It adds a `[:figlet opts? & body-strings]` directive
that expands to a FIGlet ASCII-art banner.

Source: [`src/clj_format/figlet.clj`](../src/clj_format/figlet.clj).

Highlights:

- **Directive shape:**
  ```clojure
  [:figlet "Hello"]                       ;; default font
  [:figlet {:font "small"} "Hello"]       ;; named font
  [:figlet {:font "slant"} "Line 1" "Line 2"]
  ```
- **Expansion:** every matching form is replaced by the rendered banner as a
  literal multi-line string. The compiler then emits that string as plain
  literal text, so the generated cl-format call has no new directives.
- **Recursion:** `clj-format.figlet/expand` walks vectors and seqs the same
  way the skeleton above does, so figlet forms nested inside `:each`, `:if`,
  or `[:table … :format …]` are all rewritten.
- **Preprocessor installation:** loading the namespace runs
  ```clojure
  (alter-var-root #'core/*dsl-preprocessor* (constantly expand))
  ```
  once at the bottom of the file. Projects that never `require`
  `clj-format.figlet` leave `*dsl-preprocessor*` at its `identity` default and
  pay nothing.
- **Dependency:** `clj-figlet` ships as a normal dependency of clj-format on
  all three runners (Leiningen, `deps.edn`, Babashka). There's nothing extra
  to add to your own build — just `(require 'clj-format.figlet)` once at
  startup to install the preprocessor. Because the namespace is lazy-loaded,
  projects that never require it don't pay for it at runtime.
- **Literal-string constraint:** figlet expansion happens at preprocessing
  time, before cl-format sees any arguments. The body of a `[:figlet …]` form
  must therefore be literal strings, not runtime values. For runtime-derived
  banners, call `clj-figlet.core/render` yourself and pass the resulting
  string as a normal `clj-format` argument — the same way you would pass any
  other precomputed string.
- **Caching:** string and `java.io.File` fonts are memoized so repeated
  banners in a hot loop do not re-parse the font file. Pre-loaded font maps
  and `Reader` values skip the cache to keep key equality cheap and to avoid
  reusing consumed streams.

The figlet extension also composes with tables: because `:format` on a
`[:col …]` accepts any Clojure function, a row-local computed column can call
`clj-figlet.core/render` directly, and `:overflow :wrap` lays the resulting
multi-line banner into the cell. See the "Anything multi-line goes in a cell"
section of the README for a worked recipe.

## Design constraints worth knowing

A few things to keep in mind when designing your own extension:

- **Preprocessor runs before arguments are bound.** The DSL sees no
  runtime values, only the shape of the format spec. Any data you want to
  interpolate has to flow through the argument list the normal way.
- **Expansions must be compiler-valid.** Your output is handed directly to
  `clj-format.compiler/compile-format`. If you emit something the compiler
  does not understand, the error surfaces as a compile-phase
  `ExceptionInfo` — exactly the same path as a malformed hand-written DSL.
- **ClojureScript vs JVM.** `*dsl-preprocessor*` is defined in
  `clj-format.core` (`.cljc`), so extensions can be ClojureScript-compatible
  if they avoid JVM-only APIs. `clj-format.figlet` is JVM-only because
  `clj-figlet` is; a pure-Clojure extension can live in `.cljc` and target
  both platforms.
- **One hook, deliberately.** There is intentionally no registry of
  "plugins" — a single dynamic var keeps the contract small and makes
  testing, `binding`, and composition work uniformly. If you find yourself
  wanting multiple independent extensions, compose them with `comp` as shown
  above.

# bookmap

Java 21+ framework that maps COBOL books (copybooks) to Java POJOs with annotations. The core is independent of middleware and of DI containers. Base package: `pt.dazz.bookmap`. Build: Gradle.

Design spec: `docs/design/2026-11-10-bookmap-design.md`.

## Design documents and plans

- Write each design spec in `docs/design/`, with the name `YYYY-MM-DD-<topic>-design.md`. Commit the specs.
- Put a `Status:` line under the `Date:` line of each spec. When the feature is released, change the status to `implemented in vX.Y.Z.`
- Write implementation plans in `docs/superpowers/plans/`. Do not commit the plans: they are local work files, excluded in `.git/info/exclude`.
- Do not refer to a plan from a committed file. A plan is not available to other contributors.

## Language rules

- Write all code in English: identifiers, comments, Javadoc, log messages, exception messages, commit messages.
- Write all documentation in English: specs, plans, README, Javadoc, guides.
- Conversation with the author can be in Portuguese or English.

## Documentation style: ASD-STE100 (Simplified Technical English)

Follow ASD-STE100 for approximately 80% of all documentation. The most important rules:

- Use short sentences. Procedural sentences: 20 words maximum. Descriptive sentences: 25 words maximum.
- Write only one instruction in each sentence.
- Use the imperative form for instructions ("Add the dependency.", not "You should add the dependency.").
- Use the active voice as much as possible.
- Use one word for one meaning. Do not use synonyms for the same concept (for example, always "field", never "attribute" for the same thing).
- Use the approved simple verb forms: infinitive, imperative, simple present, simple past, simple future. Do not use "-ing" forms as verbs.
- Use articles ("a", "an", "the") where possible.
- Do not use phrasal verbs when a single verb is available ("remove", not "take out").
- Keep paragraphs short: one topic in each paragraph, six sentences maximum.
- Write warnings and cautions as a clear command, followed by the risk.
- Do not add any Claude signature on any commit message our in any other place

Technical names (class names, annotation names, COBOL terms such as `PIC`, `COMP-3`, `OCCURS DEPENDING ON`) are permitted as they are.

## Examples and test data

- Write examples and test data in English.
- Use an international business domain (for example: orders, invoices, bank accounts, shipments, customers). Do not use domains or identifiers specific to Brazil or Portugal (for example: CPF, CNPJ, NIF).
- We need to reach to 100% of coverage in our tests

## Git workflow

- `main` contains only finished work. Do not commit implementation work directly on `main`.
- Implement each implementation plan on its own branch, created from `main` (for example, `feat/bookmap-v1`).
- Commit often on the branch (one commit for each plan task, at minimum).
- Merge a branch into `main` only when the author approves it.
- Merge through a Pull Request. A ruleset on `main` blocks direct pushes and force pushes, and requires the `build` check.
- Use a merge commit or a rebase merge. Do not use a squash merge: the release version comes from each commit message (`feat:`, `fix:`).

## Technical rules

- Do not use reflection at runtime. Use compile-time code generation.
- Use JavaPoet for code generation in the annotation processor.
- The core and processor modules must not depend on Spring, Quarkus, Guice, or any middleware library.

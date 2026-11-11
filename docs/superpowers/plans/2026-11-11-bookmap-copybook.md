# Copybook Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate BookMap-annotated Java classes from a COBOL copybook through a parse → AST → model (JSON) → Java pipeline, delivered as a library (`bookmap-copybook`) and a CLI (`bookmap-copybook-cli`).

**Architecture:** A hand-written lexer and recursive parser build an AST of data items. A mapper applies BookMap rules (flattening, names, types, collections, directions, `REDEFINES`) and produces an immutable model with a deterministic JSON form (Jackson). A JavaPoet generator writes the classes. The CLI (picocli) only reads options and calls the library.

**Tech Stack:** Java 21, Gradle 9, JavaPoet (`com.palantir.javapoet` 0.22.0, already in the catalog), Jackson `jackson-databind` 2.19.2, picocli 4.7.7, JUnit 5, AssertJ, google compile-testing, JaCoCo.

**Spec:** `docs/superpowers/specs/2026-11-11-bookmap-copybook-design.md` (depends on `2026-11-11-bookmap-fixed-occurs-design.md`, implemented on `feat/fixed-occurs`).

## Global Constraints

- Branch `feat/copybook-codegen`, created from `feat/fixed-occurs`, in the worktree `.worktrees/copybook`. Rebase on `main` after PR #17 merges. One commit per task at minimum.
- Commit email `alexandre.lima@dazz.pt`. No Claude signature and no `Co-Authored-By` trailer.
- Base package `pt.dazz.bookmap.copybook` (library) and `pt.dazz.bookmap.copybook.cli` (CLI).
- `bookmap-copybook` depends on `bookmap-core` (for `Direction`), JavaPoet, and Jackson only. It does not depend on `bookmap-processor`, Gradle, Maven, Spring, or middleware. The processor is a test dependency only.
- No runtime reflection in `main` code (Jackson tree model only: `ObjectMapper.readTree` / `JsonNode` / `ObjectNode`; no data binding to POJOs).
- 100% line and branch coverage in both new modules. Both are added to `publishedModules` in the root `build.gradle.kts`.
- English code and docs; ASD-STE100 for docs and for diagnostic messages. Test copybooks use international business data (orders, invoices, accounts, shipments).
- Determinism: no timestamps, absolute paths, or `HashMap` iteration order in the model JSON or the Java output. Use `LinkedHashMap`/`TreeMap` or lists.

## Review Focus

1. A fixed-format line shorter than 7 columns, or empty, or with CRLF line ends: it is a blank line, not an error. Test in Task 1.
2. A copybook in lower case with a `PIC` inside a continuation or with `PIC IS`: same AST as upper case. Test in Task 3.
3. An elementary `OCCURS` of a `COMP` number: the generated LOW-VALUES mode rejects the value 0 on write. The mapper gives an `INFO` diagnostic that names the item and says to add `dependsOn` by hand if the program has a count. Test in Task 7.
4. A `REDEFINES` direction split in a `Continuable` operation whose collection resolves to `INPUT_OUTPUT`: the processor rejects it. The generated operation class does not implement `Continuable`, so this never happens in generated code; the README says so. Test: Task 10 compiles all realistic copybooks without warnings.
5. Two runs of the generator on the same input give byte-identical JSON and Java. Test in Task 5 and Task 9.

---

### Task 1: Module setup and the source reader

**Files:**
- Modify: `settings.gradle.kts` (include `bookmap-copybook`), `build.gradle.kts` (`publishedModules` entry: "BookMap copybook generator: parses COBOL copybooks and generates BookMap classes."), `gradle/libs.versions.toml` (`jackson = "2.19.2"`, `picocli = "4.7.7"`, libraries `jackson-databind`, `picocli`)
- Create: `bookmap-copybook/build.gradle.kts` (`api(project(":bookmap-core"))`, `implementation(libs.javapoet)`, `implementation(libs.jackson.databind)`, `testImplementation(project(":bookmap-processor"))`, `testImplementation(libs.compile.testing)`)
- Create: `bookmap-copybook/src/main/java/pt/dazz/bookmap/copybook/diag/Severity.java`, `Diagnostic.java`, `Diagnostics.java`
- Create: `bookmap-copybook/src/main/java/pt/dazz/bookmap/copybook/parse/SourceFormat.java`, `SourceLine.java`, `SourceReader.java`
- Test: `bookmap-copybook/src/test/java/pt/dazz/bookmap/copybook/parse/SourceReaderTest.java`

**Interfaces:**
- Produces:
  - `enum Severity { ERROR, WARNING, INFO }`
  - `record Diagnostic(Severity severity, String source, int line, int column, String message)` with `String format()` → `"<source>:<line>:<column>: <severity lower case>: <message>"`.
  - `final class Diagnostics` — collects diagnostics: `error(String source, int line, int column, String message)`, `warning(...)`, `info(...)`, `List<Diagnostic> all()`, `boolean hasErrors()`.
  - `enum SourceFormat { FIXED, FREE }`
  - `record SourceLine(String source, int number, int column, String text, boolean continuation)` — `text` is the code area; `column` is the 1-based column of the first character of `text` in the file (8 in fixed format, 1 in free format).
  - `static List<SourceLine> SourceReader.read(String source, String content, SourceFormat format, Diagnostics diagnostics)` — drops comment lines and blank lines.

Rules: fixed format — columns 1–6 ignored, column 7 indicator (`*`, `/`, `D`, `d` → comment; `-` → continuation; space → code; other → error `unknown indicator '<c>' in column 7`), columns 8–72 code, 73+ ignored. A tab in fixed format → error `a tab character makes the columns unclear. Replace tabs with spaces.` Free format — `*>` starts a comment to the end of the line; a line whose first non-blank characters are `*>` is a comment. Accept `\n`, `\r\n`, `\r`.

- [ ] **Step 1: Write the failing tests:** `fixedFormatKeepsColumns8To72`, `fixedFormatSkipsCommentAndDebugLines`, `fixedFormatMarksContinuation`, `fixedFormatShortAndEmptyLinesAreBlank` (Review Focus 1, including `\r\n`), `fixedFormatRejectsUnknownIndicator` (diagnostic line and column 7), `fixedFormatRejectsTab`, `freeFormatStripsInlineComments`, `diagnosticFormat` (`"order.cpy:3:8: error: ..."`).
- [ ] **Step 2: Run** `./gradlew :bookmap-copybook:test` — expect FAIL (classes missing).
- [ ] **Step 3: Implement** the classes above.
- [ ] **Step 4: Run** `./gradlew :bookmap-copybook:build` — expect PASS, 100% coverage.
- [ ] **Step 5: Commit** `feat: add the copybook module and the source reader`

### Task 2: Lexer

**Files:**
- Create: `bookmap-copybook/src/main/java/pt/dazz/bookmap/copybook/parse/Token.java`, `TokenType.java`, `Lexer.java`
- Test: `.../parse/LexerTest.java`

**Interfaces:**
- Consumes: `SourceLine`, `Diagnostics` (Task 1).
- Produces:
  - `enum TokenType { WORD, NUMBER, LITERAL, PICTURE, PERIOD, PSEUDO_TEXT }`
  - `record Token(TokenType type, String text, String source, int line, int column)` — `text` of a `WORD` is upper case; `PSEUDO_TEXT` holds the text between `==` delimiters, trimmed.
  - `static List<Token> Lexer.tokenize(List<SourceLine> lines, Diagnostics diagnostics)`

Rules: a word is letters, digits, `-`, `_`, and `:` (so `:PREFIX:-ID` is one word). A number is digits with optional sign and one decimal point. Literals use `'` or `"`; a continuation line continues an open literal from its first quote (COBOL rule). After the word `PIC` or `PICTURE` (and an optional `IS`), the next blank-delimited text is one `PICTURE` token; a final `.` followed by a blank or end of line is a separate `PERIOD`. A `.` followed by a blank or end of line is a `PERIOD`. `,` and `;` are separators. `==` starts and ends pseudo-text (across lines). An unterminated literal or pseudo-text → error.

- [ ] **Step 1: Write the failing tests:** `wordsAreUpperCase`, `tagWordIsOneToken` (`:PREFIX:-ID`), `pictureIsOneTokenWithoutTheFinalPeriod` (`PIC S9(7)V99.` → `PICTURE "S9(7)V99"`, `PERIOD`), `pictureIsAfterIs` (`PICTURE IS X(10)`), `pictureWithDecimalPoint` (`PIC 9.99` keeps `9.99` — the parser rejects it later as edited), `literalContinuesOnNextLine`, `pseudoTextAcrossLines`, `unterminatedLiteralIsAnError`, `positionsAreOneBased`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :bookmap-copybook:build` — expect PASS.
- [ ] **Step 5: Commit** `feat: add the copybook lexer`

### Task 3: Data description parser and AST

**Files:**
- Create: `.../copybook/ast/Copybook.java`, `DataItem.java`, `Occurs.java`, `Usage.java`, `QualifiedName.java`, `Condition.java`
- Create: `.../copybook/parse/DataParser.java`, `Picture.java`
- Test: `.../parse/DataParserTest.java`, `.../parse/PictureTest.java`

**Interfaces:**
- Consumes: `Token`, `Lexer`, `Diagnostics`.
- Produces:
  - `record Copybook(String sourceName, List<DataItem> records)`
  - `record DataItem(int level, String name, Picture picture, Usage usage, Occurs occurs, QualifiedName redefines, List<Condition> conditions, List<DataItem> children, int line, int column, String declaration)` — `name` is `"FILLER"` for FILLER or no name; `usage` is the clause on this item or `null`; `declaration` is the normalized text: level, name, and clauses joined by one space, ending with `.` (for example `05 ORDER-AMOUNT PIC S9(7)V99 COMP-3.`); `VALUE` clauses are not in it.
  - `boolean DataItem.isGroup()` (children not empty), `boolean isFiller()`.
  - `enum Usage { DISPLAY, COMP, COMP3 }`
  - `record Occurs(int min, int max, QualifiedName dependingOn)` — `dependingOn` is `null` for a fixed table; `min == max` for `OCCURS n`.
  - `record QualifiedName(List<String> parts)` — `parts.get(0)` is the item; later parts are the `OF`/`IN` qualifiers; `toString()` gives `A OF B`.
  - `record Condition(String name)` (level 88; values are not kept).
  - `record Picture(String text, boolean numeric, boolean signed, int integerDigits, int scale)`; `int length()` (`X(n)`: n; numeric: `integerDigits + scale`); `static Picture parse(String text)` throws `IllegalArgumentException` with a message for an edited or unsupported picture.
  - `static Copybook DataParser.parse(String sourceName, List<Token> tokens, String recordName, Diagnostics diagnostics)` — `recordName` names the synthetic record (spec 4.4).

Rules (spec 4.3–4.4): build the tree by level numbers (a higher level is a child of the nearest lower level; levels `01`–`49`). Level `66` and `77`: error. Level `88`: a `Condition` on the previous item, skip to its period. Clauses: `PIC`/`PICTURE`, `USAGE [IS]` and bare usage words (`COMP`, `COMPUTATIONAL`, `COMP-4`, `COMPUTATIONAL-4`, `BINARY` → `COMP`; `COMP-3`, `COMPUTATIONAL-3`, `PACKED-DECIMAL` → `COMP3`; `DISPLAY`), `OCCURS n [TIMES]`, `OCCURS m TO n [TIMES] DEPENDING ON name [OF name]...`, `OCCURS n [TIMES] DEPENDING ON ...`, `INDEXED BY ...`/`ASCENDING|DESCENDING [KEY] [IS] ...` (info `... is ignored`), `REDEFINES name`, `VALUE`/`VALUES [IS|ARE] ...` up to the next clause keyword or period (ignored), `SIGN [IS] TRAILING` (accepted). Errors (one diagnostic each, then skip to the period): `SIGN LEADING`, `SEPARATE`, `SYNC`/`SYNCHRONIZED`, `JUSTIFIED`/`JUST`, `BLANK`, `COMP-1`, `COMP-2`, `COMP-5`, `INDEX`, `POINTER`, `NATIONAL`, an edited picture, `P`, `A`, `N` in a picture, an elementary item without `PIC`, a `PIC` on a group, an unknown word. A message names the clause and says that BookMap does not support it. A copybook whose first item is not level `01`: wrap all items in a synthetic `01` named by `recordName`. A level `01` with `REDEFINES` of another `01`: warning, and keep both records.

- [ ] **Step 1: Write the failing tests** in `PictureTest` (`X(10)`, `XXX`, `9(5)`, `S9(13)V99`, `S9(7)V9(2)`, `V99`, `9.99` rejected, `ZZ9` rejected, `9(5)P` rejected, `A(3)` rejected) and `DataParserTest`: `parsesNestedGroups`, `parsesOccursFixedAndDependingOn` (with `OF` qualifier), `parsesRedefines`, `parsesUsageWordsAndGroupUsage`, `keepsConditionsAndIgnoresValues`, `normalizedDeclaration`, `lowerCaseAndPicIs` (Review Focus 2), `fillerWithoutName`, `syntheticRecordFromFileName`, `rejectsUnsupportedClauses` (parameterized: each clause → one error at the clause column), `rejectsLevel66And77`, `recordRedefinesIsAWarning`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :bookmap-copybook:build` — expect PASS.
- [ ] **Step 5: Commit** `feat: parse copybook data descriptions`

### Task 4: COPY ... REPLACING and the parse API

**Files:**
- Create: `.../copybook/parse/CopyExpander.java`, `ParseOptions.java`, `ParseResult.java`, `CopybookParser.java`
- Test: `.../parse/CopyExpanderTest.java`, `.../parse/CopybookParserTest.java`

**Interfaces:**
- Produces:
  - `record ParseOptions(SourceFormat format, Charset charset, List<Path> copyDirs, String recordName)` with `static ParseOptions defaults()` (`FIXED`, UTF-8, no dirs, `null` record name).
  - `record ParseResult(Copybook copybook, List<Diagnostic> diagnostics)` with `boolean hasErrors()`; `copybook` is `null` when there are errors.
  - `static ParseResult CopybookParser.parse(Path file, ParseOptions options)` — record name default: the file name without the extension, upper case.
  - `static ParseResult CopybookParser.parse(String sourceName, String content, ParseOptions options)`.
  - `List<Token> CopyExpander.expand(List<Token> tokens, ...)` — internal; the builder chooses the signature.

Rules (spec 4.2): `COPY name [OF|IN lib] [REPLACING ...] .` Find `name`, `name.cpy`, `name.cbl`, `name.cob` in the copy dirs in order; the file-name match is not case-sensitive. Replacements: pseudo-text `==a== BY ==b==`; word `A BY B`. A pseudo-text old value that is one word and starts and ends with `:` replaces the text inside any word (`:PREFIX:-ID` → `ORD-ID`). Any other old value matches whole token sequences. Nested `COPY` is expanded; a cycle → error `COPY cycle: A -> B -> A`; a missing member → error `copy member 'X' not found in <dirs>`; `REPLACE` statement → error. Diagnostics inside a member use the member file name as the source.

- [ ] **Step 1: Write the failing tests:** `copyIncludesMember`, `copyFindsExtensionsCaseInsensitive`, `copyOfLibraryIsIgnored`, `replacingWords`, `replacingPseudoTextSequence`, `replacingTagInsideWords`, `nestedCopy`, `copyCycleIsAnError`, `missingMemberIsAnError`, `replaceStatementIsAnError`, `memberDiagnosticsUseMemberName`, `parseFileUsesFileNameAsRecordName`, `parseResultHasNoCopybookOnError`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :bookmap-copybook:build` — expect PASS.
- [ ] **Step 5: Commit** `feat: expand COPY REPLACING in copybooks`

### Task 5: Model and model JSON

**Files:**
- Create: `.../copybook/model/BookModel.java`, `ClassModel.java`, `ClassKind.java`, `MemberModel.java`, `FieldMember.java`, `CollectionMember.java`, `JavaType.java`, `SubTypes.java`, `SubTypeEntry.java`
- Create: `.../copybook/json/ModelJson.java`
- Test: `.../json/ModelJsonTest.java`, resource `bookmap-copybook/src/test/resources/json/model.json` (golden)

**Interfaces:**
- Produces:
  - `record BookModel(int formatVersion, String sourceName, List<ClassModel> classes)`; `static final int FORMAT_VERSION = 1`.
  - `enum ClassKind { OPERATION, ITEM, SUBTYPE }`
  - `record ClassModel(String name, ClassKind kind, String superclass, SubTypes subTypes, List<MemberModel> members)` — `superclass` and `subTypes` may be `null`.
  - `record SubTypes(String discriminator, List<SubTypeEntry> types)`; `record SubTypeEntry(String value, String className)`.
  - `sealed interface MemberModel permits FieldMember, CollectionMember` with `name()`, `order()`, `direction()` (`pt.dazz.bookmap.annotation.Direction`), `cobolName()`, `declaration()`.
  - `record FieldMember(String name, JavaType javaType, int order, String pic, Direction direction, String cobolName, String declaration)`.
  - `record CollectionMember(String name, int order, Direction direction, String cobolName, String declaration, int maxOccurs, boolean fixed, String dependsOn, String itemClass)` — `dependsOn` is `null` when absent.
  - `enum JavaType { STRING, INT, LONG, BIG_DECIMAL }`.
  - `static String ModelJson.write(BookModel model)` and `static BookModel ModelJson.read(String json)` (throws `ModelJsonException extends RuntimeException` with a clear message).

JSON format (fixed key order; absent optional values are omitted; two-space indent; `\n`; final new line):
```json
{
  "formatVersion" : 1,
  "sourceName" : "ORDER.cpy",
  "classes" : [ {
    "name" : "GetOrder",
    "kind" : "OPERATION",
    "members" : [ {
      "kind" : "FIELD",
      "name" : "orderId",
      "javaType" : "STRING",
      "order" : 10,
      "pic" : "X(10)",
      "direction" : "INPUT_OUTPUT",
      "cobolName" : "ORDER-ID",
      "declaration" : "05 ORDER-ID PIC X(10)."
    } ]
  } ]
}
```
Use Jackson's `DefaultPrettyPrinter` with `DefaultIndenter("  ", "\n")`; the exact spacing is the one Jackson gives (golden file). The reader rejects: an unknown `formatVersion` (`unsupported formatVersion 2. This reader supports 1.`), an unknown key (`unknown key 'x' in <path>`), a missing mandatory key, a wrong value type.

- [ ] **Step 1: Write the failing tests:** `writesTheGoldenFile`, `roundTripGivesAnEqualModel` (a model with all member kinds, subtypes, superclass), `writeIsDeterministic` (Review Focus 5), `rejectsUnknownVersion`, `rejectsUnknownKey`, `rejectsMissingKey`, `rejectsWrongType`, `rejectsInvalidJson`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement** with `ObjectMapper` tree nodes (`ObjectNode.put` in fixed order).
- [ ] **Step 4: Run** `./gradlew :bookmap-copybook:build` — expect PASS.
- [ ] **Step 5: Commit** `feat: add the copybook model and its JSON form`

### Task 6: Names and qualified-name lookup

**Files:**
- Create: `.../copybook/map/Names.java`, `.../copybook/map/ItemIndex.java`
- Test: `.../map/NamesTest.java`, `.../map/ItemIndexTest.java`

**Interfaces:**
- Produces:
  - `Names(List<String> stripPrefixes)`; `String className(String cobolName)`; `String fieldName(String cobolName)`; `static boolean isJavaLangName(String)` (internal use).
  - `ItemIndex(Copybook copybook)`; `Lookup find(QualifiedName name)` where `sealed interface Lookup` has `Found(DataItem item, List<DataItem> path)`, `NotFound()`, `Ambiguous(List<String> matches)` (matches as qualified names, `A OF B OF C`); `List<DataItem> pathTo(DataItem item)` (ancestors from the record).
  - `static QualifiedName QualifiedName.parse(String text)` (add to the Task 3 record): `CITY OF BILLING-ADDRESS`, `IN` accepted, not case-sensitive.

Rules (spec 5.2, 5.5.1): split at `-` and `_`; PascalCase / camelCase with the rest of each part in lower case; strip the first matching prefix (not case-sensitive) unless that leaves nothing; Java keyword or literal → suffix `Value`; leading digit → `field` prefix (fields) or `Record` prefix (classes); class equal to a `java.lang` simple name (`String`, `Object`, `Integer`, `Long`, `Number`, `Math`, `System`, `Thread`, `Record`, `Enum`, `Class`, `Character`, `Boolean`, `Byte`, `Short`, `Double`, `Float`, `Void`, `Process`, `Runtime`, `Package`, `Module`, `Error`, `Exception`) → suffix `Record`. A qualified name matches an item whose name equals the first part and whose ancestors contain the other parts in order (not necessarily direct parents).

- [ ] **Step 1: Write the failing tests:** `pascalAndCamelCase` (`GET-CUSTOMER-ORDERS` → `GetCustomerOrders`, `ORDER-AMOUNT` → `orderAmount`, `ORDER_AMOUNT` → `orderAmount`), `stripsPrefix`, `keepsNameWhenStripLeavesNothing`, `keywordGetsValueSuffix` (`CLASS` → `classValue`), `leadingDigit` (`1ST-ADDRESS` → `field1stAddress`, class `Record1stAddress`), `javaLangClassGetsRecordSuffix` (`STRING` → `StringRecord`), `qualifiedNameParse`, `findUnique`, `findQualified`, `findAmbiguousListsMatches`, `findNotFound`, `findAcrossRecordsWithRecordQualifier`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — expect PASS.
- [ ] **Step 5: Commit** `feat: add copybook name rules and qualified names`

### Task 7: Mapper — classes, fields, and collections

**Files:**
- Create: `.../copybook/map/MappingOptions.java`, `MapResult.java`, `ModelMapper.java`, `PicText.java`
- Test: `.../map/ModelMapperTest.java`

**Interfaces:**
- Consumes: AST (Task 3), model (Task 5), `Names`, `ItemIndex` (Task 6).
- Produces:
  - `record MappingOptions(List<String> stripPrefixes, List<String> inputMarkers, List<String> outputMarkers, Map<String, Direction> directions, Map<String, SubTypeDecision> subTypes)` with `static MappingOptions defaults()` (markers `INPUT, IN, REQUEST, REQ` / `OUTPUT, OUT, RESPONSE, RESP`).
  - `record SubTypeDecision(String discriminator, Map<String, String> values, Map<String, String> classNames)` (used in Task 8; define it here).
  - `record MapResult(BookModel model, List<Diagnostic> diagnostics)`; `model` is `null` when there are errors.
  - `static MapResult ModelMapper.map(Copybook copybook, MappingOptions options)`.
  - `static String PicText.of(Picture picture, Usage effectiveUsage)` → `"S9(7)V99 COMP-3"`, `"X(10)"`, `"9(4) COMP"` (DISPLAY omitted; the picture text keeps the copybook spelling).

Rules (spec 5.1–5.4): record → `OPERATION` class; group without `OCCURS` → flattened; group with `OCCURS` → `ITEM` class + `CollectionMember`; elementary `OCCURS` → `ITEM` class with one field `value` + `CollectionMember`; `OCCURS` inside an `OCCURS` item → error. Effective usage: the item's usage, else the nearest group usage, else `DISPLAY`; a usage on a text item other than `DISPLAY` → error. Java type table from spec 5.3. `order` 10, 20, ... per class, in source order. FILLER → `filler1`, `filler2`, ... per class. Field collisions → prefix with the nearest distinct group name (camelCase of group + PascalCase of item), then a number suffix (`2`, `3`). Class collisions → prefix with the parent class name, then a number suffix. `OCCURS n` → `fixed = true`, no `dependsOn`; if the item class has a `COMP` field, add an `INFO`: `<name>: a COMP value of 0 is all LOW-VALUES and cannot be written. Add dependsOn if the program has a count field.` (Review Focus 3). `DEPENDING ON` → resolve with `ItemIndex`; the target must be in the same class and before the table, and its Java type must be `INT` or `LONG`, else error. Elementary `REDEFINES` → keep the redefined item, warning `<b> redefines <a> and is skipped`. Level `88` → info `condition <name> is ignored`. All members outside `REDEFINES` areas are `INPUT_OUTPUT`; a collection member always has a direction in the model. Group `REDEFINES` areas: leave a call to the Task 8 resolver; in this task, any group `REDEFINES` → error `REDEFINES of a group needs a direction or a subtype decision` (Task 8 replaces it).

- [ ] **Step 1: Write the failing tests** (each builds the AST from a copybook string through `CopybookParser`):
  `recordBecomesOperationClass`, `groupsAreFlattened`, `fieldTypesFollowThePicture` (X, 9(9), 9(10), 9(18), 9(19), S9V99), `picTextKeepsUsage` (group `COMP-3` inherited), `ordersStepByTen`, `fillersAreNumberedPerClass`, `fixedOccursBecomesFixedCollection`, `occursDependingOnUsesTheCountField`, `dependsOnInAnotherClassIsAnError`, `elementaryOccursGetsValueItem`, `nestedOccursIsAnError`, `fieldCollisionUsesGroupName`, `classCollisionUsesParentName`, `elementaryRedefinesIsSkippedWithWarning`, `conditionsAreInfo`, `compItemInFixedTableGivesInfo`, `textWithCompUsageIsAnError`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — expect PASS.
- [ ] **Step 5: Commit** `feat: map copybook items to classes and fields`

### Task 8: Mapper — REDEFINES areas, directions, and subtypes

**Files:**
- Create: `.../copybook/map/RedefinesResolver.java`
- Modify: `.../copybook/map/ModelMapper.java`
- Test: `.../map/RedefinesMappingTest.java`

**Interfaces:**
- Consumes: `MappingOptions.directions`, `MappingOptions.subTypes`, `ItemIndex`.

Rules (spec 5.5–5.7, owner decision "directions only in REDEFINES areas"):
- An area is a redefined group plus all groups that `REDEFINES` it (the alternatives), in source order.
- Resolution order: subtype decision (key = a name of any alternative, resolved with `ItemIndex`) → direction split (exactly two alternatives; each resolves through an explicit decision on the alternative group name, else markers on its name; one `INPUT` and one `OUTPUT`) → error `REDEFINES area <A> at line <n> needs a decision. Add a direction for each alternative, for example "directions": {"<A>": "INPUT", "<B>": "OUTPUT"}. Or add a subtype decision, for example "subTypes": {"<A>": {"discriminator": "...", "values": {"<A>": "01", "<B>": "02"}}}.`
- Errors: a direction decision for a name outside a `REDEFINES` alternative group, or for an item inside an alternative; a direction or subtype decision whose name is not found or is ambiguous; a direction split inside an `OCCURS` item (BookMap item fields have no direction).
- A group name with both an input and an output marker: warning, no marker direction.
- Direction split: all items of an alternative get its direction. If the alternatives have different byte lengths and any item follows the area in the same class, append a `FILLER` member to the shorter alternative, with its direction, so that both lengths are equal. Byte length uses the BookMap rules (`DISPLAY`: digits; `COMP-3`: `n/2+1`; `COMP`: 2/4/8; collections: `maxOccurs × item length`).
- Subtypes: the class that holds the area is the base class (not abstract). Each alternative → `SUBTYPE` class (`superclass` = base; name from `classNames` or PascalCase of the group name) with the alternative's members; their `order` continues after the highest base `order`. The base gets `SubTypes(discriminator field name, entries in alternative order)`. Errors: an item after the area at the area level or higher in the same class; a discriminator not found, not before the area, not in the same class, or not `STRING`/`INT`/`LONG`; an alternative without a value; a value for a name that is not an alternative; a subtype area inside another subtype area. The discriminator direction is `INPUT_OUTPUT` (outside the area), so the BookMap rule "no INPUT discriminator" holds.
- A count field inside an `INPUT`/`OUTPUT` alternative for a collection in the same alternative: same direction, no explicit direction needed. A count field outside the area for a collection inside an alternative: the count field stays `INPUT_OUTPUT`; the generator writes it with no `direction` attribute, and BookMap gives it the collection direction. This changes the offsets of the other direction. Thus, this case is an error: `count field <c> is outside the REDEFINES area of collection <t>`.

- [ ] **Step 1: Write the failing tests:** `directionSplitByMarkers` (the spec 5.6 `GET-INVOICE` example: `returnCode` and `trailer` `INPUT_OUTPUT`, `invoiceId` `INPUT`, `invoiceAmount`/`dueDate` `OUTPUT`, and a 1-byte `INPUT` filler after `invoiceId`), `directionSplitByExplicitDecision`, `noFillerWhenNothingFollows`, `ambiguousAreaIsAnError` (message text above), `bothMarkersIsAWarning`, `directionOutsideAreaIsAnError`, `directionOnItemInsideAlternativeIsAnError`, `directionSplitInsideOccursIsAnError`, `subtypesFromDecision` (the README `GET-ACCOUNT` example: base `GetAccount` with `SubTypes("accountType", [01 → CheckingData, 02 → SavingsAccount])`, subtype orders after the base), `subtypeErrors` (parameterized: each error above), `subtypeWinsOverMarkers`, `countFieldOutsideAreaIsAnError`, `unknownDecisionNameIsAnError`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — expect PASS.
- [ ] **Step 5: Commit** `feat: resolve REDEFINES areas through directions and subtypes`

### Task 9: Java generator

**Files:**
- Create: `.../copybook/generate/GenerateOptions.java`, `JavaClassGenerator.java`
- Test: `.../generate/JavaClassGeneratorTest.java`, golden files `bookmap-copybook/src/test/resources/golden/plain/*.java` and `golden/lombok/*.java`

**Interfaces:**
- Consumes: `BookModel` (Task 5).
- Produces:
  - `record GenerateOptions(String packageName, boolean lombok)`.
  - `static List<JavaFile> JavaClassGenerator.generate(BookModel model, GenerateOptions options)` (JavaPoet `JavaFile`, one per class, in model order).
  - `static void JavaClassGenerator.writeTo(List<JavaFile> files, Path outputDir)` (overwrites).

Output rules (spec 7, 5.3): class Javadoc `Generated by the BookMap copybook generator from {@code <sourceName>}.`; `OPERATION` → `@BookOperation`; base with `SubTypes` → `@BookSubTypes(discriminator = "...", value = {@BookSubTypes.Type(value = "01", type = X.class), ...})`; `SUBTYPE` → `extends <superclass>`. Field: `private`, Javadoc `{@code <declaration>}`, `@BookField(order = n, pic = "...")` plus `direction = Direction.X` only when not `INPUT_OUTPUT`. Collection: `@BookCollection(order = n, maxOccurs = m, direction = Direction.X)` plus `fixed = true` and `dependsOn = "..."` when set; type `List<Item>`. Field types: `String`, `int`, `long`, `BigDecimal`. Without Lombok: a public getter and setter per member (JavaBeans names). With Lombok: `@Data` and `@NoArgsConstructor`; a `SUBTYPE` adds `@EqualsAndHashCode(callSuper = true)` and `@ToString(callSuper = true)`. Import `Direction` members statically is not needed; use `Direction.INPUT`. JavaPoet indent: 4 spaces.

- [ ] **Step 1: Write the failing tests:** `plainOutputMatchesGolden` (a model with an operation, a fixed collection, a variable collection, an `OUTPUT` field, subtypes), `lombokOutputMatchesGolden`, `outputIsDeterministic` (Review Focus 5), `writeToCreatesPackageDirectories`, `writeToOverwrites`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement.** Create the golden files from the first correct output, and read them once by eye against the spec.
- [ ] **Step 4: Run** — expect PASS.
- [ ] **Step 5: Commit** `feat: generate Java classes from the copybook model`

### Task 10: Realistic copybooks, end-to-end compile and round-trip

**Files:**
- Create: test resources `bookmap-copybook/src/test/resources/copybooks/ORDERS.cpy` (fixed and variable tables, COMP-3 amounts, a field after a fixed table), `INVOICE.cpy` (`REQUEST-DATA`/`RESPONSE-DATA` direction split with a trailer), `ACCOUNT.cpy` (subtypes, as in the README `GET-ACCOUNT`), `SHIPMENT.cpy` + `copylib/ADDRESS.cpy` (`COPY ADDRESS REPLACING ==:PFX:== BY ==SHIP==`), and `account-config.json`
- Create: `.../copybook/Copybooks.java` (facade)
- Test: `.../copybook/EndToEndTest.java`

**Interfaces:**
- Produces: `static Result Copybooks.generate(Path copybook, ParseOptions parse, MappingOptions mapping, GenerateOptions generate)` where `record Result(BookModel model, List<JavaFile> files, List<Diagnostic> diagnostics)` (`model`/`files` empty on error); `static Result Copybooks.generate(BookModel model, GenerateOptions generate)`.

- [ ] **Step 1: Write the failing tests:** for each copybook, `generatesCompilableClasses` — compile the generated sources with `Compiler.javac().withProcessors(new BookmapProcessor())` (compile-testing) → `succeededWithoutWarnings()` (Review Focus 4). `roundTripsThroughTheCodec` — load the generated `<Class>_BookCodec` from the compilation (see `bookmap-processor/src/test/.../CompiledCodec.java` for the class-loader pattern; copy it into this test module), fill an instance by its setters (test-only reflection), `encodeResponse` then `decodeResponse` / `readResponse`, and compare the values; for `ACCOUNT.cpy`, check that the subtype class is selected. `modelJsonRoundTripGivesSameJava` — `generate(copybook)` and `generate(ModelJson.read(ModelJson.write(model)))` give identical sources.
- [ ] **Step 2: Run** — expect FAIL (facade missing).
- [ ] **Step 3: Implement** the facade.
- [ ] **Step 4: Run** `./gradlew :bookmap-copybook:build` — expect PASS. Fix mapper or generator defects that these tests find; each fix gets a unit test in the owning test class first.
- [ ] **Step 5: Commit** `feat: add the copybook facade and end-to-end tests`

### Task 11: Configuration file

**Files:**
- Create: `.../copybook/config/GeneratorConfig.java`, `GeneratorConfigJson.java`
- Test: `.../config/GeneratorConfigJsonTest.java`

**Interfaces:**
- Produces:
  - `record GeneratorConfig(ParseOptions parse, MappingOptions mapping, String packageName, Boolean lombok)` — `packageName`/`lombok` may be `null` (not set).
  - `static GeneratorConfig GeneratorConfigJson.read(String json, Path baseDir)` — keys of spec §9: `format` (`fixed`/`free`), `charset`, `copyDirs` (relative to `baseDir`), `recordName`, `stripPrefixes`, `inputMarkers`, `outputMarkers` (replace the defaults), `directions`, `subTypes` (`discriminator`, `values`, optional `classNames`), `package`, `lombok`. Unknown keys and wrong types → `ConfigException` with the JSON path.

- [ ] **Step 1: Write the failing tests:** `readsAllKeys` (the spec §9 example), `markersReplaceDefaults`, `copyDirsAreRelativeToTheFile`, `missingKeysUseDefaults`, `rejectsUnknownKey`, `rejectsWrongType`, `rejectsUnknownDirection`, `rejectsUnknownFormat`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** — expect PASS.
- [ ] **Step 5: Commit** `feat: read the copybook generator configuration file`

### Task 12: CLI module

**Files:**
- Modify: `settings.gradle.kts`, `build.gradle.kts` (`publishedModules`: "BookMap copybook generator command-line program."), `.github/workflows/release.yml` (add `bookmap-copybook-cli` to the JAR copy loop)
- Create: `bookmap-copybook-cli/build.gradle.kts` — `implementation(project(":bookmap-copybook"))`, `implementation(libs.picocli)`; a `Jar` task `allJar` with classifier `all`, `Main-Class` manifest, and the runtime classpath unpacked (exclude `META-INF/*.SF`, `*.DSA`, `*.RSA`, and `module-info.class`; `duplicatesStrategy = EXCLUDE`); add it to the Maven publication (`publishing.publications.withType<MavenPublication> { artifact(allJar) }`); `assemble` depends on it.
- Create: `bookmap-copybook-cli/src/main/java/pt/dazz/bookmap/copybook/cli/Main.java`, `ParseCommand.java`, `GenerateCommand.java`, `CommonOptions.java`
- Test: `bookmap-copybook-cli/src/test/java/pt/dazz/bookmap/copybook/cli/MainTest.java`

**Interfaces:**
- Consumes: `Copybooks`, `ModelJson`, `GeneratorConfigJson`, `JavaClassGenerator.writeTo`.
- Produces: `static int Main.run(PrintStream out, PrintStream err, String... args)`; `main` calls `System.exit(run(System.out, System.err, args))`.

Commands (spec §9): `parse <copybook> --output <file>` and `generate <copybook|model.json> --package <p> --output-dir <dir> [--lombok]`. Common parse/map options: `--format`, `--charset`, `--copy-dir` (repeatable), `--record-name`, `--strip-prefix` (repeatable), `--input-marker`/`--output-marker` (repeatable, add to the defaults or to the config lists), `--direction NAME=DIRECTION` (repeatable), `--config <file>`. A command-line option wins over the config file. With a `.json` input, parse/map options → exit 2 with `parse and mapping options are not permitted with a model input`. Missing `--package` (and none in the config) → exit 2. Diagnostics to `err` in `Diagnostic.format()`; exit 1 when an `ERROR` exists, else 0. picocli usage errors → exit 2.

- [ ] **Step 1: Write the failing tests** (use `@TempDir`, the Task 10 copybooks copied from `bookmap-copybook` test resources into this module's test resources): `parseWritesModelJson`, `generateFromCopybookWritesJavaFiles`, `generateFromModelJson`, `generateWithLombok`, `configFileIsApplied`, `commandLineWinsOverConfig`, `errorsGiveExitCode1AndDiagnostics`, `warningsGiveExitCode0`, `badOptionGivesExitCode2`, `modelInputRejectsParseOptions`, `missingPackageGivesExitCode2`, `helpGivesExitCode0`.
- [ ] **Step 2: Run** — expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run** `./gradlew :bookmap-copybook-cli:build`, then `java -jar bookmap-copybook-cli/build/libs/bookmap-copybook-cli-*-all.jar generate bookmap-copybook/src/test/resources/copybooks/ORDERS.cpy --package com.example.orders --output-dir build/tmp/cli-out` — expect exit 0 and `.java` files.
- [ ] **Step 5: Commit** `feat: add the copybook generator command line`

### Task 13: Examples — generation in the build

**Files:**
- Modify: `bookmap-examples/build.gradle.kts` — a `JavaExec` task `generateCopybookClasses` that runs `pt.dazz.bookmap.copybook.cli.Main` from `project(":bookmap-copybook-cli")` for each file in `src/main/copybook/` (inputs: the copybooks and `src/main/copybook/*.json`; output: `build/generated/sources/copybook/java/main`), and adds that directory to `sourceSets.main.java`; `compileJava` depends on it.
- Create: `bookmap-examples/src/main/copybook/LIST-INVOICES.cpy` (with `REQUEST`/`RESPONSE` areas and a fixed table), `GET-ACCOUNT-SUMMARY.cpy` + `GET-ACCOUNT-SUMMARY.json` (subtypes)
- Test: `bookmap-examples/src/test/java/pt/dazz/bookmap/examples/CopybookEndToEndTest.java`

- [ ] **Step 1: Write the failing tests** with `SimulatorTransport` (follow `FixedOccursEndToEndTest`): `generatedInvoiceListRoundTrip`, `generatedAccountSubtypeRoundTrip`.
- [ ] **Step 2: Run** `./gradlew :bookmap-examples:test` — expect FAIL.
- [ ] **Step 3: Implement** the Gradle task and copybooks (package `pt.dazz.bookmap.examples.copybook`).
- [ ] **Step 4: Run** `./gradlew build` — expect PASS in all modules.
- [ ] **Step 5: Commit** `feat: generate example classes from copybooks in the build`

### Task 14: Documentation

**Files:**
- Modify: `README.md` — module list; new section "Copybook generator": pipeline, CLI usage (both commands, the config file), the mapping table (spec 5.1, 5.3, 5.4), names, the direction rules (only in `REDEFINES` areas, with the reason), subtypes, diagnostics and exit codes, the model JSON (stateless; for future tools), limitations (no date detection, `VALUE`/`88` ignored, no Gradle/Maven plugin yet; a `COMP` value of 0 in a fixed table without `dependsOn`; generated operations do not implement `Continuable` or `Validatable` — add them by hand in scaffold mode).
- Modify: `docs/superpowers/specs/2026-11-11-bookmap-copybook-design.md` — add the two notes from the fixed OCCURS review (the `COMP` 0 case; `Continuable` and `INPUT_OUTPUT` collections) to §5.4.
- Modify: `docs/superpowers/specs/2026-11-11-bookmap-fixed-occurs-design.md` §2.1 — remove "in an item class of a collection" (nested collections stay a non-goal).

- [ ] **Step 1: Edit** the files. Follow ASD-STE100.
- [ ] **Step 2: Run** `./gradlew build` — expect PASS.
- [ ] **Step 3: Commit** `docs: document the copybook generator`

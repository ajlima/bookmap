package pt.dazz.copymapper.copybook.map;

import java.nio.charset.StandardCharsets;
import java.util.List;
import pt.dazz.copymapper.copybook.diag.Diagnostic;
import pt.dazz.copymapper.copybook.diag.Severity;
import pt.dazz.copymapper.copybook.model.ClassModel;
import pt.dazz.copymapper.copybook.model.MemberModel;
import pt.dazz.copymapper.copybook.parse.CopybookParser;
import pt.dazz.copymapper.copybook.parse.ParseOptions;
import pt.dazz.copymapper.copybook.parse.ParseResult;
import pt.dazz.copymapper.copybook.parse.SourceFormat;

/** Helpers for mapper tests: free-format copybook text to a map result. */
final class Mapping {

    private Mapping() {
    }

    static MapResult map(MappingOptions options, String... lines) {
        ParseResult parsed = CopybookParser.parse("ORDER.cpy", String.join("\n", lines),
            new ParseOptions(SourceFormat.FREE, StandardCharsets.UTF_8, List.of(), null));
        if (parsed.hasErrors()) {
            throw new AssertionError("parse errors: " + parsed.diagnostics());
        }
        return ModelMapper.map(parsed.copybook(), options);
    }

    static MapResult map(String... lines) {
        return map(MappingOptions.defaults(), lines);
    }

    /** Gives the diagnostics without the info diagnostics of the record lengths. */
    static List<Diagnostic> diagnostics(MapResult result) {
        return result.diagnostics().stream().filter(d -> d.severity() != Severity.INFO
            || !d.message().matches("record \\S+ has .*bytes")).toList();
    }

    static ClassModel type(MapResult result, String name) {
        return result.model().classes().stream().filter(c -> c.name().equals(name))
            .findFirst().orElseThrow(() -> new AssertionError("no class " + name + " in "
                + result.model().classes().stream().map(ClassModel::name).toList()));
    }

    static MemberModel member(ClassModel type, String name) {
        return type.members().stream().filter(m -> m.name().equals(name)).findFirst()
            .orElseThrow(() -> new AssertionError("no member " + name + " in "
                + type.members().stream().map(MemberModel::name).toList()));
    }
}

package pt.dazz.bookmap.copybook.map;

import java.nio.charset.StandardCharsets;
import java.util.List;
import pt.dazz.bookmap.copybook.model.ClassModel;
import pt.dazz.bookmap.copybook.model.MemberModel;
import pt.dazz.bookmap.copybook.parse.CopybookParser;
import pt.dazz.bookmap.copybook.parse.ParseOptions;
import pt.dazz.bookmap.copybook.parse.ParseResult;
import pt.dazz.bookmap.copybook.parse.SourceFormat;

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

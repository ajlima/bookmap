package pt.dazz.bookmap.copybook.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pt.dazz.bookmap.copybook.ast.Copybook;
import pt.dazz.bookmap.copybook.ast.DataItem;
import pt.dazz.bookmap.copybook.ast.QualifiedName;
import pt.dazz.bookmap.copybook.parse.CopybookParser;
import pt.dazz.bookmap.copybook.parse.ParseOptions;
import pt.dazz.bookmap.copybook.parse.SourceFormat;

class ItemIndexTest {

    private static final Copybook ORDER = CopybookParser.parse("ORDER.cpy", String.join("\n",
        "01 ORDER.",
        "   05 BILLING-ADDRESS.",
        "      10 CITY PIC X(20).",
        "   05 SHIPPING-ADDRESS.",
        "      10 CITY PIC X(20).",
        "      10 FILLER PIC X(5).",
        "   05 ORDER-ID PIC X(10).",
        "01 RETURN-ORDER.",
        "   05 ORDER-ID PIC X(10)."),
        new ParseOptions(SourceFormat.FREE, java.nio.charset.StandardCharsets.UTF_8, List.of(),
            null)).copybook();

    private final ItemIndex index = new ItemIndex(ORDER);

    private ItemIndex.Lookup find(String name) {
        return index.find(QualifiedName.parse(name));
    }

    @Test
    void qualifiedNameParse() {
        assertThat(QualifiedName.parse(" city of Billing-Address in order ").parts())
            .containsExactly("CITY", "BILLING-ADDRESS", "ORDER");
        assertThatThrownBy(() -> QualifiedName.parse("CITY OF"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("'CITY OF' is not a valid data name");
        assertThatThrownBy(() -> QualifiedName.parse(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void findUnique() {
        assertThat(find("BILLING-ADDRESS")).isInstanceOf(ItemIndex.Found.class);
    }

    @Test
    void findQualified() {
        ItemIndex.Found found = (ItemIndex.Found) find("CITY OF SHIPPING-ADDRESS");
        assertThat(index.fullName(found.item())).isEqualTo("CITY OF SHIPPING-ADDRESS OF ORDER");
        assertThat(index.pathTo(found.item())).extracting(DataItem::name)
            .containsExactly("ORDER", "SHIPPING-ADDRESS");
        assertThat(find("CITY OF SHIPPING-ADDRESS OF ORDER"))
            .isInstanceOf(ItemIndex.Found.class);
        assertThat(find("CITY OF ORDER OF SHIPPING-ADDRESS"))
            .isInstanceOf(ItemIndex.NotFound.class);
    }

    @Test
    void findAmbiguousListsMatches() {
        assertThat(find("CITY")).isEqualTo(new ItemIndex.Ambiguous(List.of(
            "CITY OF BILLING-ADDRESS OF ORDER", "CITY OF SHIPPING-ADDRESS OF ORDER")));
    }

    @Test
    void findNotFound() {
        assertThat(find("ZIP")).isEqualTo(new ItemIndex.NotFound());
        assertThat(find("FILLER")).isEqualTo(new ItemIndex.NotFound());
    }

    @Test
    void findAcrossRecordsWithRecordQualifier() {
        assertThat(find("ORDER-ID")).isInstanceOf(ItemIndex.Ambiguous.class);
        ItemIndex.Found found = (ItemIndex.Found) find("ORDER-ID OF RETURN-ORDER");
        assertThat(index.pathTo(found.item())).extracting(DataItem::name)
            .containsExactly("RETURN-ORDER");
    }
}

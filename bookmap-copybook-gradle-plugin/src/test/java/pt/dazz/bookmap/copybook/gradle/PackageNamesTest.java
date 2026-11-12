package pt.dazz.bookmap.copybook.gradle;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PackageNamesTest {

    private static final String BASE = "com.example.mainframe";

    @Test
    void segmentChangesToLowerCase() {
        assertThat(PackageNames.segment("Billing")).isEqualTo("billing");
    }

    @Test
    void segmentReplacesOtherCharacters() {
        assertThat(PackageNames.segment("Open-Invoices")).isEqualTo("open_invoices");
        assertThat(PackageNames.segment("ORDER.V2")).isEqualTo("order_v2");
        assertThat(PackageNames.segment("bank account")).isEqualTo("bank_account");
        assertThat(PackageNames.segment("A_B")).isEqualTo("a_b");
        assertThat(PackageNames.segment("Café")).isEqualTo("caf_");
    }

    @Test
    void segmentPrefixesALeadingDigit() {
        assertThat(PackageNames.segment("2026")).isEqualTo("_2026");
    }

    @Test
    void segmentSuffixesAKeyword() {
        assertThat(PackageNames.segment("new")).isEqualTo("new_");
        assertThat(PackageNames.segment("TRUE")).isEqualTo("true_");
        assertThat(PackageNames.segment("null")).isEqualTo("null_");
        assertThat(PackageNames.segment("class")).isEqualTo("class_");
    }

    @Test
    void rootCopybookGetsTheBasePackage() {
        assertThat(PackageNames.packageOf(BASE, Path.of(""), "ORDER", false))
            .isEqualTo(BASE);
    }

    @Test
    void directoriesGiveSubpackages() {
        assertThat(PackageNames.packageOf(BASE, Path.of("Billing", "Open-Invoices"), "INVOICE",
            false)).isEqualTo(BASE + ".billing.open_invoices");
    }

    @Test
    void perCopybookAddsTheCopybookSegment() {
        assertThat(PackageNames.packageOf(BASE, Path.of("billing"), "LIST-OPEN-INVOICES", true))
            .isEqualTo(BASE + ".billing.list_open_invoices");
        assertThat(PackageNames.packageOf(BASE, Path.of(""), "ORDER", true))
            .isEqualTo(BASE + ".order");
    }
}

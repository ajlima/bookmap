/**
 * Billing examples. The package defaults give usage COMP-3 and signed numbers to numeric fields.
 * A field with a pic and no usage clause gets usage COMP-3 from the defaults; the sign comes from the pic.
 * A field with only a length gets usage COMP-3 and the sign from the defaults.
 */
@BookDefaults(usage = Usage.COMP3, signed = true)
package pt.daaz.bookmap.examples.billing;

import pt.daaz.bookmap.annotation.BookDefaults;
import pt.daaz.bookmap.annotation.Usage;

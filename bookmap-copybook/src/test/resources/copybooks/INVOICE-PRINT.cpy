      * PRINT-INVOICE: THE PRINTABLE TOTALS OF ONE INVOICE.
      * IT HAS THE EDITED PICTURES OF ISSUE 48.
       01  PRINT-INVOICE.
           05  PRT-INVOICE-ID           PIC X(10).
           05  PRT-LINE-COUNT           PIC ZZ9.
           05  PRT-TOTAL                PIC $$,$$9.99CR.
           05  PRT-TAX                  PIC Z,ZZ9.99-.
           05  PRT-DUE-DAY              PIC **/**/99.
           05  PRT-DISCOUNT             PIC 9(3)V99 BLANK WHEN ZERO.
           05  PRT-STATUS               PIC 9 BLANK WHEN ZERO.
               88  PRT-OPEN             VALUE 1.
           05  PRT-FEE                  PIC +++9 VALUE '  +0'.

      * GET-ORDER-DATES: PACKED (COMP-3) DATES AND TIMES WITH DATE HINTS.
       01  GET-ORDER-DATES.
           05  ORDER-ID                 PIC X(10).
           05  ORDER-DATE               PIC S9(8) COMP-3.
           05  SHIP-DATE                PIC 9(7) COMP-3.
           05  SHIP-TIME                PIC S9(7) COMP-3.
           05  LAST-UPDATE              PIC S9(14) COMP-3.

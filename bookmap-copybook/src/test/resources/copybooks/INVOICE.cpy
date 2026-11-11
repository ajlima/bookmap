      * GET-INVOICE: THE REQUEST AND THE RESPONSE SHARE ONE AREA.
       01  GET-INVOICE.
           05  RETURN-CODE              PIC X(4).
           05  INVOICE-REQUEST.
               10  INVOICE-ID           PIC X(12).
               10  CURRENCY-CODE        PIC X(3).
           05  INVOICE-RESPONSE REDEFINES INVOICE-REQUEST.
               10  INVOICE-AMOUNT       PIC S9(7)V99 COMP-3.
               10  TAX-AMOUNT           PIC S9(7)V99 COMP-3.
               10  DUE-DATE             PIC 9(8).
           05  TRAILER                  PIC X(10).

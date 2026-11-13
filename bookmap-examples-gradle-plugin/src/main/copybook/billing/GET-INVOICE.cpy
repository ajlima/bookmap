      * GET-INVOICE: READ ONE INVOICE, ITS BILLING ADDRESS, ITS LINES,
      * AND ITS TAX SUMMARY. A COUNT FIELD GIVES THE USED ENTRIES OF
      * EACH FIXED TABLE.
       01  GET-INVOICE.
           05  INV-INVOICE-ID           PIC X(12).
           05  INV-AMOUNT-DUE           PIC S9(7)V99 COMP-3.
           05  INV-PARTY-ROLE           PIC X(8).
               88  PARTY-IS-SELLER      VALUE 'SELLER'.
               88  PARTY-IS-BILL-TO     VALUE 'BILL-TO'.
               88  PARTY-IS-SHIP-TO     VALUE 'SHIP-TO'.
           COPY ADDRESS.
           05  INVOICE-LINE-COUNT       PIC 9(3).
           05  INVOICE-LINE OCCURS 100 TIMES.
               10  PRODUCT-CODE         PIC X(12).
               10  LINE-AMOUNT          PIC S9(7)V99 COMP-3.
           05  TAX-SUMMARY-COUNT        PIC 9(2).
           05  TAX-SUMMARY-ENTRY OCCURS 10 TIMES.
               10  TAX-RATE             PIC 9(2)V99.
               10  TAX-AMOUNT           PIC S9(7)V99 COMP-3.

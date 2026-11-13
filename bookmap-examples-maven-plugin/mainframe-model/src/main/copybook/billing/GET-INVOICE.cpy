      * GET-INVOICE: READ ONE INVOICE AND ITS BILLING ADDRESS.
       01  GET-INVOICE.
           05  INV-INVOICE-ID           PIC X(12).
           05  INV-AMOUNT-DUE           PIC S9(7)V99 COMP-3.
           05  INV-PARTY-ROLE           PIC X(8).
               88  PARTY-IS-SELLER      VALUE 'SELLER'.
               88  PARTY-IS-BILL-TO     VALUE 'BILL-TO'.
               88  PARTY-IS-SHIP-TO     VALUE 'SHIP-TO'.
           COPY ADDRESS.

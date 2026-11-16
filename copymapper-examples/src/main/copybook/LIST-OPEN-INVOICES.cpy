      * LIST-OPEN-INVOICES: THE REQUEST AND THE RESPONSE SHARE ONE AREA.
       01  LIST-OPEN-INVOICES.
           05  RETURN-CODE              PIC X(4).
           05  INVOICE-REQUEST.
               10  CUSTOMER-ID          PIC X(10).
               10  FROM-DATE            PIC 9(8).
           05  INVOICE-RESPONSE REDEFINES INVOICE-REQUEST.
               10  INVOICE-LINE OCCURS 5 TIMES.
                   15  INVOICE-NUMBER   PIC X(10).
                   15  AMOUNT-DUE       PIC S9(7)V99 COMP-3.

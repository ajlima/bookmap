      *----------------------------------------------------------------*
      * GET-ORDER-TAXES: GIVES THE LINES OF AN ORDER AND THEIR TAXES.  *
      *----------------------------------------------------------------*
       01  GET-ORDER-TAXES.
           05  ORDER-ID                 PIC X(10).
           05  LINE-COUNT               PIC 9(2).
           05  ORDER-LINE OCCURS 1 TO 20 TIMES
                   DEPENDING ON LINE-COUNT.
               10  PRODUCT-CODE         PIC X(8).
               10  QUANTITY             PIC 9(5).
               10  TAX-COUNT            PIC 9.
               10  LINE-TAX OCCURS 3 TIMES.
                   15  TAX-CODE         PIC X(4).
                   15  TAX-AMOUNT       PIC S9(7)V99 COMP-3.
           05  ORDER-TOTAL              PIC S9(9)V99 COMP-3.
      *----------------------------------------------------------------*
      * GET-PAYMENTS: A REDEFINES AREA WITH A TABLE IN A TABLE ENTRY.  *
      *----------------------------------------------------------------*
       01  GET-PAYMENTS.
           05  PAYMENT OCCURS 3 TIMES.
               10  PAYMENT-TYPE         PIC X.
               10  CARD-DATA.
                   15  CARD-NUMBER      PIC X(4).
               10  SPLIT-DATA REDEFINES CARD-DATA.
                   15  SPLIT-PART OCCURS 2 TIMES.
                       20  PART-AMOUNT  PIC 9(2).

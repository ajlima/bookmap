      * GET-ORDER: READ ONE ORDER WITH ITS LINES.
       01  GET-ORDER.
           05  ORDER-ID                 PIC X(10).
           05  ORDER-TOTAL              PIC S9(7)V99 COMP-3.
           05  ORDER-LINE OCCURS 3 TIMES.
               10  PRODUCT-CODE         PIC X(12).
               10  QUANTITY             PIC 9(5).

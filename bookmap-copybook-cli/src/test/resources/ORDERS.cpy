      *----------------------------------------------------------------*
      * LIST-ORDERS: LISTS THE ORDERS OF A CUSTOMER.                   *
      *----------------------------------------------------------------*
       01  LIST-ORDERS.
           05  CUSTOMER-ID              PIC X(10).
           05  RESTART-KEY              PIC X(12).
           05  MORE-DATA                PIC X.
               88  MORE-ORDERS          VALUE 'Y'.
           05  ORDER-COUNT              PIC 9(3).
           05  ORDER-ENTRY OCCURS 1 TO 20 TIMES
                   DEPENDING ON ORDER-COUNT.
               10  ORDER-NUMBER         PIC X(10).
               10  ORDER-DATE           PIC 9(8).
               10  ORDER-TOTAL          PIC S9(9)V99 COMP-3.
           05  TOP-PRODUCT OCCURS 3 TIMES.
               10  PRODUCT-CODE         PIC X(8).
               10  QUANTITY             PIC 9(5).
           05  GRAND-TOTAL              PIC S9(11)V99 COMP-3.

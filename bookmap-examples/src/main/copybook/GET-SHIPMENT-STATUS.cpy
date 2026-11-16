      * GET-SHIPMENT-STATUS: NO FIELD SELECTS THE LAYOUT. THE CALLER
      * READS THE VIEW THAT THE RESULT CODE GIVES.
       01  GET-SHIPMENT-STATUS.
           05  SHIPMENT-ID              PIC X(10).
           05  TRACKING-CODE            PIC X(16).
           05  TRACKING-PARTS REDEFINES TRACKING-CODE.
               10  CARRIER-CODE         PIC X(4).
               10  ROUTE-NUMBER         PIC 9(6).
               10  SEQUENCE-NUMBER      PIC 9(6).
           05  RESULT-CODE              PIC X(2).
           05  RESULT-DETAIL.
               10  ERROR-TEXT           PIC X(40).
           05  DELIVERY-DATA REDEFINES RESULT-DETAIL.
               10  DELIVERY-DATE        PIC 9(8).
               10  DELIVERED-WEIGHT     PIC S9(5)V99 COMP-3.
               10  SIGNED-BY            PIC X(20).
           05  SHIPPING-CHARGE          PIC S9(7)V99 COMP-3.

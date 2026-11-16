      * LIST-SHIPMENTS: LISTS THE SHIPMENTS OF A CUSTOMER, ONE PAGE AT A TIME.
       01  LIST-SHIPMENTS.
           05  RETURN-CODE              PIC X(2).
           05  RESTART-KEY              PIC X(12).
           05  SHIPMENT-REQUEST.
               10  CUSTOMER-ID          PIC X(10).
               10  FROM-DATE            PIC 9(8).
           05  SHIPMENT-RESPONSE REDEFINES SHIPMENT-REQUEST.
               10  MORE-DATA            PIC X.
               10  SHIPMENT-ENTRY OCCURS 2 TIMES.
                   15  SHIPMENT-ID      PIC X(12).
                   15  SHIP-DATE        PIC 9(8).
                   15  SHIP-TIME        PIC X(8).
                   15  LAST-UPDATE      PIC 9(14).

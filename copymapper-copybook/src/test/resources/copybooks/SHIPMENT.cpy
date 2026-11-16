      * CREATE-SHIPMENT: THE ADDRESS COMES FROM A COPY MEMBER.
       01  CREATE-SHIPMENT.
           05  SHIPMENT-ID              PIC X(12).
           COPY ADDRESS REPLACING ==:PFX:== BY ==SHIP-TO==.
           05  WEIGHT-KG                PIC 9(5)V9(3) COMP-3.
           05  PACKAGE-COUNT            PIC 9(2) COMP.

      * GET-TRACKING: READ THE STATUS OF ONE SHIPMENT.
      * IT HAS THE ITEMS OF ISSUE 37: 66, 77, REPLACE, SYNC, COMP-5,
      * INDEX, JUSTIFIED, AND BLANK WHEN ZERO.
       77  WS-RETRY-COUNT               PIC 9(4) COMP.
           REPLACE ==:TAG:== BY ==TRK==.
       01  GET-TRACKING.
           05  :TAG:-SHIPMENT-ID        PIC X(10).
           05  :TAG:-STATUS             PIC X(1).
           05  :TAG:-EVENT-COUNT        PIC S9(4) COMP SYNC.
           05  :TAG:-WEIGHT-GRAMS       PIC 9(9) COMP-5 SYNC.
           05  :TAG:-CARRIER            PIC X(8) JUSTIFIED RIGHT.
           05  :TAG:-CHARGE             PIC 9(5)V99 BLANK WHEN ZERO.
           05  :TAG:-EVENT-IDX          USAGE IS INDEX.
       66  :TAG:-KEY RENAMES :TAG:-SHIPMENT-ID THRU :TAG:-STATUS.
           REPLACE OFF.

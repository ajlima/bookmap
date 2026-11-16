      *----------------------------------------------------------------*
      * GET-INVOICE-PARTY: READS ONE PARTY OF AN INVOICE.              *
      *----------------------------------------------------------------*
       01  GET-INVOICE-PARTY.
           05  INVOICE-ID               PIC X(12).
           05  PARTY-ROLE               PIC X(8) VALUE 'SELLER'.
               88  PARTY-IS-SELLER      VALUE 'SELLER'.
               88  PARTY-IS-BILL-TO     VALUE 'BILL-TO'.
               88  PARTY-IS-SHIP-TO     VALUE 'SHIP-TO'.
           05  PARTY-STATUS             PIC 9(2) VALUE 1.
               88  PARTY-ACTIVE         VALUE 1 THRU 5.
               88  PARTY-CLOSED         VALUE 9.
           05  PARTY-GRADE              PIC X.
               88  GRADE-GOOD           VALUE 'A' THRU 'C' 'X'.
           05  BATCH-NUMBER             PIC 9(12) VALUE 100.
               88  BATCH-FIRST          VALUE 1.
               88  BATCH-RANGE          VALUE 100 THRU 199.
           05  CURRENCY-CODE            PIC X(3) VALUE 'EUR'.
           05  CREDIT-LIMIT         PIC S9(7)V99 COMP-3 VALUE 1000.50.
               88  NO-CREDIT            VALUE ZERO.
               88  SMALL-CREDIT         VALUE 0.01 THRU 99.99.
           05  PARTY-NAME               PIC X(30) VALUE SPACES.
           05  PARTY-COUNTRY            PIC X(2).
               88  COUNTRY-UNKNOWN      VALUE SPACES.

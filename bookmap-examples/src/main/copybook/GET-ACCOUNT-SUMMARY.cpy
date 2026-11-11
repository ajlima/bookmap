      * GET-ACCOUNT-SUMMARY: THE ACCOUNT KIND SELECTS THE LAYOUT.
       01  GET-ACCOUNT-SUMMARY.
           05  ACCOUNT-ID               PIC X(12).
           05  ACCOUNT-KIND             PIC X.
               88  CARD-ACCOUNT         VALUE 'C'.
               88  LOAN-ACCOUNT         VALUE 'L'.
           05  CARD-SUMMARY.
               10  CARD-LIMIT           PIC S9(7)V99 COMP-3.
               10  CARD-BALANCE         PIC S9(7)V99 COMP-3.
           05  LOAN-SUMMARY REDEFINES CARD-SUMMARY.
               10  LOAN-AMOUNT          PIC S9(9)V99 COMP-3.
               10  INSTALMENTS-LEFT     PIC 9(3).

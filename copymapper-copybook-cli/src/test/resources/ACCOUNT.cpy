      * GET-ACCOUNT: THE ACCOUNT TYPE SELECTS THE LAYOUT OF THE DATA.
       01  GET-ACCOUNT.
           05  ACCOUNT-ID               PIC X(12).
           05  ACCOUNT-TYPE             PIC 9(2).
               88  CHECKING-ACCOUNT     VALUE 01.
               88  SAVINGS-ACCOUNT      VALUE 02.
           05  CHECKING-DATA.
               10  OVERDRAFT-LIMIT      PIC S9(9)V99 COMP-3.
               10  ENTRY-COUNT          PIC 9(2).
               10  STATEMENT-ENTRY OCCURS 0 TO 10 TIMES
                       DEPENDING ON ENTRY-COUNT.
                   15  ENTRY-DATE       PIC 9(8).
                   15  ENTRY-AMOUNT     PIC S9(7)V99 COMP-3.
           05  SAVINGS-DATA REDEFINES CHECKING-DATA.
               10  INTEREST-RATE        PIC 9V9(4).
               10  MATURITY-DATE        PIC 9(8).

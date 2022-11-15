package com.sun.tools.sjavac;

/** Result codes.
 */
public enum Result {
    OK(0),        // Compilation completed with no errors.
    ERROR(1),     // Completed but reported errors.
    CMDERR(2),    // Bad command-line arguments
    SYSERR(3),    // System error or resource exhaustion.
    ABNORMAL(4);  // Compiler terminated abnormally

    Result(int exitCode) {
        this.exitCode = exitCode;
    }

    public boolean isOK() {
        return (exitCode == 0);
    }

    public final int exitCode;
}
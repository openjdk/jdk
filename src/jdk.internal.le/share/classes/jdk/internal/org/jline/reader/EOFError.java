/*
 * Copyright (c) 2023, the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

public class EOFError extends SyntaxError {

    private static final long serialVersionUID = 1L;

    private final String missing;
    private final int openBrackets;
    private final String nextClosingBracket;

    public EOFError(int line, int column, String message) {
        this(line, column, message, null);
    }

    public EOFError(int line, int column, String message, String missing) {
        this(line, column, message, missing, 0, null);
    }

    public EOFError(int line, int column, String message, String missing, int openBrackets, String nextClosingBracket) {
        super(line, column, message);
        this.missing = missing;
        this.openBrackets = openBrackets;
        this.nextClosingBracket = nextClosingBracket;
    }

    public String getMissing() {
        return missing;
    }

    public int getOpenBrackets() {
        return openBrackets;
    }

    public String getNextClosingBracket() {
        return nextClosingBracket;
    }
}

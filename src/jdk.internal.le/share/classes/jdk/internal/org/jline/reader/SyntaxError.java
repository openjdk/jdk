/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

/**
 * Exception thrown when a syntax error is encountered during parsing.
 * <p>
 * SyntaxError is thrown by the {@link Parser} when it encounters invalid syntax
 * in the input line. It provides information about the location of the error
 * (line and column) and a descriptive message about the nature of the error.
 * <p>
 * This exception is typically caught by the LineReader, which may then display
 * an error message to the user or take other appropriate action based on the
 * parsing context.
 * <p>
 * The {@link EOFError} subclass is used specifically for incomplete input errors,
 * such as unclosed quotes or brackets, which might be completed by additional input.
 *
 * @see Parser
 * @see EOFError
 * @see Parser.ParseContext
 */
public class SyntaxError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int line;
    private final int column;

    public SyntaxError(int line, int column, String message) {
        super(message);
        this.line = line;
        this.column = column;
    }

    /**
     * Returns the column position where the syntax error occurred.
     *
     * @return the column position (0-based index)
     */
    public int column() {
        return column;
    }

    /**
     * Returns the line number where the syntax error occurred.
     *
     * @return the line number (0-based index)
     */
    public int line() {
        return line;
    }
}

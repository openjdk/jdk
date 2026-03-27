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
 * Exception thrown when parsing is incomplete due to unexpected end of input.
 * <p>
 * EOFError is a specialized type of {@link SyntaxError} that indicates the input
 * is incomplete rather than invalid. This typically occurs when the user has entered
 * an incomplete construct, such as an unclosed quote, parenthesis, or bracket.
 * <p>
 * This exception provides additional information about what is missing to complete
 * the input, which can be used by the LineReader to provide appropriate feedback
 * to the user, such as a continuation prompt that indicates what needs to be closed.
 * <p>
 * The name "EOFError" refers to "End Of File Error", indicating that the parser
 * reached the end of the input before the syntax was complete.
 *
 * @see SyntaxError
 * @see Parser
 * @see Parser.ParseContext#SECONDARY_PROMPT
 */
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

    /**
     * Returns the string that is missing to complete the input.
     * <p>
     * This is typically a closing delimiter such as a quote, parenthesis, or bracket
     * that would complete the current syntactic construct.
     *
     * @return the missing string, or null if not applicable
     */
    public String getMissing() {
        return missing;
    }

    /**
     * Returns the number of unclosed brackets in the input.
     * <p>
     * This count can be used to determine how many closing brackets are needed
     * to complete the input.
     *
     * @return the number of unclosed brackets
     */
    public int getOpenBrackets() {
        return openBrackets;
    }

    /**
     * Returns the next closing bracket that is expected.
     * <p>
     * This indicates the specific type of bracket (e.g., ')', ']', or '}') that
     * is expected next to continue closing the open brackets.
     *
     * @return the next expected closing bracket, or null if not applicable
     */
    public String getNextClosingBracket() {
        return nextClosingBracket;
    }
}

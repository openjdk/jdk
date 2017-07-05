/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.nashorn.internal.runtime;

import static jdk.nashorn.internal.runtime.ECMAErrors.rangeError;

import java.io.PrintWriter;
import jdk.nashorn.internal.parser.Token;

/**
 * Handles JavaScript error reporting.
 */
public class ErrorManager {
    // TODO - collect and sort/collapse error messages.
    // TODO - property based error messages.
    /** Reporting writer. */
    private final PrintWriter writer;

    /** Error count. */
    private int errors;

    /** Warning count */
    private int warnings;

    /** Limit of the number of messages. */
    private int limit;

    /** Treat warnings as errors. */
    private boolean warningsAsErrors;

    /**
     * Constructor
     */
    public ErrorManager() {
        this(new PrintWriter(System.err, true)); //bootstrapping, context may not be initialized
    }

    /**
     * Constructor.
     * @param writer I/O writer to report on.
     */
    public ErrorManager(final PrintWriter writer) {
        this.writer           = writer;
        this.limit            = 100;
        this.warningsAsErrors = false;
    }

    /**
     * Check to see if number of errors exceed limit.
     */
    private void checkLimit() {
        int count = errors;

        if (warningsAsErrors) {
            count += warnings;
        }

        if (limit != 0 && count > limit) {
            throw rangeError("too.many.errors", Integer.toString(limit));
        }
    }

    /**
     * Format an error message to include source and line information.
     * @param message Error message string.
     * @param source  Source file information.
     * @param line    Source line number.
     * @param column  Source column number.
     * @param token   Offending token descriptor.
     * @return formatted string
     */
    public static String format(final String message, final Source source, final int line, final int column, final long token) {
        final String        eoln     = System.lineSeparator();
        final int           position = Token.descPosition(token);
        final StringBuilder sb       = new StringBuilder();

        // Source description and message.
        sb.append(source.getName()).
            append(':').
            append(line).
            append(':').
            append(column).
            append(' ').
            append(message).
            append(eoln);

        // Source content.
        final String sourceLine = source.getSourceLine(position);
        sb.append(sourceLine).append(eoln);

        // Pointer to column.
        for (int i = 0; i < column; i++) {
            if (sourceLine.charAt(i) == '\t') {
                sb.append('\t');
            } else {
                sb.append(' ');
            }
        }

        sb.append('^');
        // Use will append eoln.
        // buffer.append(eoln);

        return sb.toString();
    }

    /**
     * Report an error using information provided by the ParserException
     *
     * @param e ParserException object
     */

    public void error(final ParserException e) {
        error(e.getMessage());
    }

    /**
     * Report an error message provided
     *
     * @param message Error message string.
     */
    public void error(final String message) {
        writer.println(message);
        writer.flush();
        errors++;
        checkLimit();
    }

    /**
     * Report a warning using information provided by the ParserException
     *
     * @param e ParserException object
     */
    public void warning(final ParserException e) {
        warning(e.getMessage());
    }

    /**
     * Report a warning message provided
     *
     * @param message Error message string.
     */
    public void warning(final String message) {
        writer.println(message);
        writer.flush();
        warnings++;
        checkLimit();
    }

    /**
     * Test to see if errors have occurred.
     * @return True if errors.
     */
    public boolean hasErrors() {
        return errors != 0;
    }

    /**
     * Get the message limit
     * @return max number of messages
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Set the message limit
     * @param limit max number of messages
     */
    public void setLimit(final int limit) {
        this.limit = limit;
    }

    /**
     * Check whether warnings should be treated like errors
     * @return true if warnings should be treated like errors
     */
    public boolean isWarningsAsErrors() {
        return warningsAsErrors;
    }

    /**
     * Set warnings to be treated as errors
     * @param warningsAsErrors true if warnings should be treated as errors, false otherwise
     */
    public void setWarningsAsErrors(final boolean warningsAsErrors) {
        this.warningsAsErrors = warningsAsErrors;
    }

    /**
     * Get the number of errors
     * @return number of errors
     */
    public int getNumberOfErrors() {
        return errors;
    }

    /**
     * Get number of warnings
     * @return number of warnings
     */
    public int getNumberOfWarnings() {
        return warnings;
    }

    /**
     * Clear warnings and error count.
     */
    void reset() {
        warnings = 0;
        errors = 0;
    }
}

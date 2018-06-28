/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.tree;

/**
 * Interface for diagnostics from tools.  A diagnostic usually reports
 * a problem at a specific position in a source file.  However, not
 * all diagnostics are associated with a position or a file.
 *
 * <p>A position is a zero-based character offset from the beginning of
 * a file.  Negative values (except {@link #NOPOS}) are not valid
 * positions.
 *
 * <p>Line and column numbers begin at 1.  Negative values (except
 * {@link #NOPOS}) and 0 are not valid line or column numbers.
 *
 * <p>Line terminator is as defined in ECMAScript specification which is one
 * of { &#92;u000A, &#92;u000B, &#92;u2028, &#92;u2029 }.
 *
 * @deprecated Nashorn JavaScript script engine and APIs, and the jjs tool
 * are deprecated with the intent to remove them in a future release.
 *
 * @since 9
 */
@Deprecated(since="11", forRemoval=true)
public interface Diagnostic {

    /**
     * Kinds of diagnostics, for example, error or warning.
     *
     * The kind of a diagnostic can be used to determine how the
     * diagnostic should be presented to the user. For example,
     * errors might be colored red or prefixed with the word "Error",
     * while warnings might be colored yellow or prefixed with the
     * word "Warning". There is no requirement that the Kind
     * should imply any inherent semantic meaning to the message
     * of the diagnostic: for example, a tool might provide an
     * option to report all warnings as errors.
     *
     * @deprecated Nashorn JavaScript script engine and APIs, and the jjs tool
     * are deprecated with the intent to remove them in a future release.
     */
    @Deprecated(since="11", forRemoval=true)
    enum Kind {
        /**
         * Problem which prevents the tool's normal completion.
         */
        ERROR,
        /**
         * Problem which does not usually prevent the tool from
         * completing normally.
         */
        WARNING,
        /**
         * Problem similar to a warning, but is mandated by the tool's
         * specification.  For example, the Java&trade; Language
         * Specification mandates warnings on certain
         * unchecked operations and the use of deprecated methods.
         */
        MANDATORY_WARNING,
        /**
         * Informative message from the tool.
         */
        NOTE,
        /**
         * Diagnostic which does not fit within the other kinds.
         */
        OTHER,
    }

    /**
     * Used to signal that no position is available.
     */
    public final static long NOPOS = -1;

    /**
     * Gets the kind of this diagnostic, for example, error or
     * warning.
     * @return the kind of this diagnostic
     */
    Kind getKind();

    /**
     * Gets a character offset from the beginning of the source object
     * associated with this diagnostic that indicates the location of
     * the problem.  In addition, the following must be true:
     *
     * <p>{@code getStartPostion() <= getPosition()}
     * <p>{@code getPosition() <= getEndPosition()}
     *
     * @return character offset from beginning of source; {@link
     * #NOPOS} if no location is suitable
     */
    long getPosition();

    /**
     * Gets the source file name.
     *
     * @return the file name or null if not available
     */
    String getFileName();

    /**
     * Gets the line number of the character offset returned by
     * {@linkplain #getPosition()}.
     *
     * @return a line number or {@link #NOPOS} if and only if {@link
     * #getPosition()} returns {@link #NOPOS}
     */
    long getLineNumber();

    /**
     * Gets the column number of the character offset returned by
     * {@linkplain #getPosition()}.
     *
     * @return a column number or {@link #NOPOS} if and only if {@link
     * #getPosition()} returns {@link #NOPOS}
     */
    long getColumnNumber();

    /**
     * Gets a diagnostic code indicating the type of diagnostic.  The
     * code is implementation-dependent and might be {@code null}.
     *
     * @return a diagnostic code
     */
    String getCode();

    /**
     * Gets a message for this diagnostic.
     *
     * @return a message
     */
    String getMessage();
}

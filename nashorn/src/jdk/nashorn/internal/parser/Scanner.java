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

package jdk.nashorn.internal.parser;

/**
 * Utility for scanning thru a char array.
 *
 */
public class Scanner {
    /** Characters to scan. */
    protected final char[] content;

    /** Position in content. */
    protected int position;

    /** Scan limit. */
    protected final int limit;

    /** Current line number. */
    protected int line;

    /** Current character in stream */
    protected char ch0;
    /** 1 character lookahead */
    protected char ch1;
    /** 2 character lookahead */
    protected char ch2;
    /** 3 character lookahead */
    protected char ch3;

    /**
     * Constructor
     *
     * @param content content to scan
     * @param line    start line number
     * @param start   position index in content where to start
     * @param length  length of input
     */
    protected Scanner(final char[] content, final int line, final int start, final int length) {
        this.content  = content;
        this.position = start;
        this.limit    = start + length;
        this.line     = line;

        reset(position);
    }

    /**
     * Constructor
     *
     * Scan content from beginning to end. Content given as a string
     *
     * @param content content to scan
     */
    protected Scanner(final String content) {
        this(content.toCharArray(), 0, 0, content.length());
    }

    /**
     * Copy constructor
     *
     * @param scanner  scanner
     * @param state    state, the state is a tuple {position, limit, line} only visible internally
     */
    Scanner(final Scanner scanner, final State state) {
        content  = scanner.content;
        position = state.position;
        limit    = state.limit;
        line     = state.line;

        reset(position);
   }

    /**
     * Information needed to restore previous state.
     */
    static class State {
        /** Position in content. */
        public final int position;

        /** Scan limit. */
        public int limit;

        /** Current line number. */
        public final int line;

        State(final int position, final int limit, final int line) {
            this.position = position;
            this.limit    = limit;
            this.line     = line;
        }

        /**
         * Change the limit for a new scanner.
         * @param limit New limit.
         */
        void setLimit(final int limit) {
            this.limit = limit;
        }

        boolean isEmpty() {
            return position == limit;
        }
    }

    /**
     * Save the state of the scan.
     * @return Captured state.
     */
    State saveState() {
        return new State(position, limit, line);
    }

    /**
     * Restore the state of the scan.
     * @param state Captured state.
     */
    void restoreState(final State state) {
        position = state.position;
        line     = state.line;

        reset(position);
    }

    /**
     * Returns true of scanner is at end of input
     * @return true if no more input
     */
    protected final boolean atEOF() {
        return position == limit;
    }

    /**
     * Get the ith character from the content.
     * @param i Index of character.
     * @return ith character or '\0' if beyond limit.
     */
    protected final char charAt(final int i) {
        // Get a character from the content, '\0' if beyond the end of file.
        return i < limit ? content[i] : '\0';
    }

    /**
     * Reset to a character position.
     * @param i Position in content.
     */
    protected final void reset(final int i) {
        ch0 = charAt(i);
        ch1 = charAt(i + 1);
        ch2 = charAt(i + 2);
        ch3 = charAt(i + 3);
        position = i < limit? i : limit;
    }

    /**
     * Skip ahead a number of characters.
     * @param n Number of characters to skip.
     */
    protected final void skip(final int n) {
        if (n == 1 && !atEOF()) {
            ch0 = ch1;
            ch1 = ch2;
            ch2 = ch3;
            ch3 = charAt(position + 4);
            position++;
        } else if (n != 0) {
            reset(position + n);
        }
    }
}

/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jshell;

import java.util.List;

/**
 * Provides analysis utilities for source code input.
 * Optional functionality that provides for a richer interactive experience.
 * Includes completion analysis:
 * Is the input a complete snippet of code?
 * Do I need to prompt for more input?
 * Would adding a semicolon make it complete?
 * Is there more than one snippet?
 * etc.
 * Also includes completion suggestions, as might be used in tab-completion.
 *
 */
public abstract class SourceCodeAnalysis {

    /**
     * Given an input string, find the first snippet of code (one statement,
     * definition, import, or expression) and evaluate if it is complete.
     * @param input the input source string
     * @return a CompletionInfo instance with location and completeness info
     */
    public abstract CompletionInfo analyzeCompletion(String input);

    /**
     * Compute possible follow-ups for the given input.
     * Uses information from the current <code>JShell</code> state, including
     * type information, to filter the suggestions.
     * @param input the user input, so far
     * @param cursor the current position of the cursors in the given {@code input} text
     * @param anchor outgoing parameter - when an option will be completed, the text between
     *               the anchor and cursor will be deleted and replaced with the given option
     * @return list of candidate continuations of the given input.
     */
    public abstract List<Suggestion> completionSuggestions(String input, int cursor, int[] anchor);

    /**
     * Compute a description/help string for the given user's input.
     * @param input the snippet the user wrote so far
     * @param cursor the current position of the cursors in the given {@code input} text
     * @return description/help string for the given user's input
     */
    public abstract String documentation(String input, int cursor);

    /**
     * Internal only constructor
     */
    SourceCodeAnalysis() {}

    /**
     * The result of <code>analyzeCompletion(String input)</code>.
     * Describes the completeness and position of the first snippet in the given input.
     */
    public static class CompletionInfo {

        public CompletionInfo(Completeness completeness, int unitEndPos, String source, String remaining) {
            this.completeness = completeness;
            this.unitEndPos = unitEndPos;
            this.source = source;
            this.remaining = remaining;
        }

        /**
         * The analyzed completeness of the input.
         */
        public final Completeness completeness;

        /**
         * The end of the first unit of source.
         */
        public final int unitEndPos;

        /**
         * Source code for the first unit of code input.  For example, first
         * statement, or first method declaration.  Trailing semicolons will
         * be added, as needed
         */
        public final String source;

        /**
         * Input remaining after the source
         */
        public final String remaining;
    }

    /**
     * Describes the completeness of the given input.
     */
    public enum Completeness {
        /**
         * The input is a complete source snippet (declaration or statement) as is.
         */
        COMPLETE(true),

        /**
         * With this addition of a semicolon the input is a complete source snippet.
         * This will only be returned when the end of input is encountered.
         */
        COMPLETE_WITH_SEMI(true),

        /**
         * There must be further source beyond the given input in order for it
         * to be complete.  A semicolon would not complete it.
         * This will only be returned when the end of input is encountered.
         */
        DEFINITELY_INCOMPLETE(false),

        /**
         * A statement with a trailing (non-terminated) empty statement.
         * Though technically it would be a complete statement
         * with the addition of a semicolon, it is rare
         * that that assumption is the desired behavior.
         * The input is considered incomplete.  Comments and white-space are
         * still considered empty.
         */
        CONSIDERED_INCOMPLETE(false),


        /**
         * An empty input.
         * The input is considered incomplete.  Comments and white-space are
         * still considered empty.
         */
        EMPTY(false),

        /**
         * The completeness of the input could not be determined because it
         * contains errors. Error detection is not a goal of completeness
         * analysis, however errors interfered with determining its completeness.
         * The input is considered complete because evaluating is the best
         * mechanism to get error information.
         */
        UNKNOWN(true);

        /**
         * Is the first snippet of source complete. For example, "x=" is not
         * complete, but "x=2" is complete, even though a subsequent line could
         * make it "x=2+2". Already erroneous code is marked complete.
         */
        public final boolean isComplete;

        Completeness(boolean isComplete) {
            this.isComplete = isComplete;
        }
    }

    /**
     * A candidate for continuation of the given user's input.
     */
    public static class Suggestion {

        /**
         * Create a {@code Suggestion} instance.
         * @param continuation a candidate continuation of the user's input
         * @param isSmart is the candidate "smart"
         */
        public Suggestion(String continuation, boolean isSmart) {
            this.continuation = continuation;
            this.isSmart = isSmart;
        }

        /**
         * The candidate continuation of the given user's input.
         */
        public final String continuation;

        /**
         * Is it an input continuation that matches the target type and is thus more
         * likely to be the desired continuation. A smart continuation
         * is preferred.
         */
        public final boolean isSmart;
    }
}

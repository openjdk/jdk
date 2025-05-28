/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.parser;

import com.sun.tools.javac.parser.Tokens.Comment;
import com.sun.tools.javac.parser.Tokens.Comment.CommentStyle;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Position;

import java.nio.CharBuffer;
import java.util.Arrays;

/**
 * An extension to the base lexical analyzer (JavaTokenizer) that
 * captures and processes the contents of doc comments. It does
 * so by stripping the leading whitespace and comment stars from
 * each line of the Javadoc comment.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JavadocTokenizer extends JavaTokenizer {
    /**
     * The factory that created this Scanner.
     */
    final ScannerFactory fac;

    /**
     * Create a tokenizer from the input character buffer. The input buffer
     * content would typically be a Javadoc comment extracted by
     * JavaTokenizer.
     *
     * @param fac  the factory which created this Scanner.
     * @param cb   the input character buffer.
     */
    protected JavadocTokenizer(ScannerFactory fac, CharBuffer cb) {
        super(fac, cb);
        this.fac = fac;
    }

    /**
     * Create a tokenizer from the input array. The input buffer
     * content would typically be a Javadoc comment extracted by
     * JavaTokenizer.
     *
     * @param fac     factory which created this Scanner
     * @param array   input character array.
     * @param length  length of the meaningful content in the array.
     */
    protected JavadocTokenizer(ScannerFactory fac, char[] array, int length) {
        super(fac, array, length);
        this.fac = fac;
    }

    @Override
    protected Comment processComment(int pos, int endPos, CommentStyle style) {
        return new JavadocComment(style, this, pos, endPos);
    }

    /**
     * An extension of BasicComment used to extract the relevant portion
     * of a Javadoc comment.
     */
    protected static class JavadocComment extends BasicComment {
        /**
         * The relevant portion of the comment that is of interest to Javadoc.
         * Produced by invoking scanDocComment.
         */
        private String docComment = null;

        /**
         * StringBuilder used to extract the relevant portion of the Javadoc comment.
         */
        private StringBuilder sb;

        /**
         * Indicates if newline is required.
         */
        private boolean firstLine = true;

        /**
         * Map used to map the extracted Javadoc comment's character positions back to
         * the original source.
         */
        OffsetMap offsetMap = new OffsetMap();

        JavadocComment(CommentStyle cs, UnicodeReader reader, int pos, int endPos) {
            super(cs, reader, pos, endPos);
            this.sb = new StringBuilder();
        }

        /**
         * Add current character or code point from line to the extraction buffer.
         *
         * @param line line reader
         */
        protected void putLine(UnicodeReader line) {
            if (firstLine) {
                firstLine = false;
            } else {
                sb.append('\n');
                offsetMap.add(sb.length(), line.position());
            }
            while (line.isAvailable()) {
                offsetMap.add(sb.length(), line.position());

                if (line.isSurrogate()) {
                    sb.appendCodePoint(line.getCodepoint());
                } else {
                    sb.append(line.get());
                }

                line.next();
            }
        }

        @Override
        public String getText() {
            if (!scanned) {
                scanDocComment();
            }
            return docComment;
        }

        @Override
        public int getSourcePos(int pos) {
            if (pos == Position.NOPOS) {
                return Position.NOPOS;
            }

            if (pos < 0 || pos > docComment.length()) {
                throw new StringIndexOutOfBoundsException(String.valueOf(pos));
            }

            return offsetMap.getSourcePos(pos);
        }

        @Override
        protected void scanDocComment() {
            try {
                super.scanDocComment();
            } finally {
                docComment = sb.toString();
                sb = null;
                offsetMap.trim();
            }
        }

        @Override
        public Comment stripIndent() {
            return StrippedComment.of(this);
        }
    }

    /**
     * Build a map for translating between line numbers and positions in the input.
     * Overridden to expand tabs.
     *
     * @return a LineMap
     */
    @Override
    public Position.LineMap getLineMap() {
        char[] buf = getRawCharacters();
        return Position.makeLineMap(buf, buf.length, true);
    }

    /**
     * Build an int table to mapping positions in extracted Javadoc comment
     * to positions in the JavaTokenizer source buffer.
     *
     * The array is organized as a series of pairs of integers: the first
     * number in each pair specifies a position in the comment text,
     * the second number in each pair specifies the corresponding position
     * in the source buffer. The pairs are sorted in ascending order.
     *
     * Since the mapping function is generally continuous, with successive
     * positions in the string corresponding to successive positions in the
     * source buffer, the table only needs to record discontinuities in
     * the mapping. The values of intermediate positions can be inferred.
     *
     * Discontinuities may occur in a number of places: when a newline
     * is followed by whitespace and asterisks (which are ignored),
     * when a tab is expanded into spaces, and when unicode escapes
     * are used in the source buffer.
     *
     * Thus, to find the source position of any position, p, in the comment
     * string, find the index, i, of the pair whose string offset
     * ({@code map[i * NOFFSETS + SB_OFFSET] }) is closest to but not greater
     * than p. Then, {@code sourcePos(p) = map[i * NOFFSETS + POS_OFFSET] +
     *                                (p - map[i * NOFFSETS + SB_OFFSET]) }.
     */
    static class OffsetMap {
        /**
         * map entry offset for comment offset member of pair.
         */
        private static final int SB_OFFSET = 0;

        /**
         * map entry offset of input offset member of pair.
         */
        private static final int POS_OFFSET = 1;

        /**
         * Number of elements in each entry.
         */
        private static final int NOFFSETS = 2;

        /**
         * Array storing entries in map.
         */
        private int[] map;

        /**
         * Logical size of map.
         * This is the number of occupied positions in {@code map},
         * and equals {@code NOFFSETS} multiplied by the number of entries.
         */
        private int size;

        /**
         * Constructor.
         */
        OffsetMap() {
            this.map = new int[128];
            this.size = 0;
        }

        /**
         * Returns true if it is worthwhile adding the entry pair to the map. That is
         * if there is a change in relative offset.
         *
         * @param sbOffset  comment offset member of pair.
         * @param posOffset  input offset member of pair.
         *
         * @return true if it is worthwhile adding the entry pair.
         */
        boolean shouldAdd(int sbOffset, int posOffset) {
            return sbOffset - lastSBOffset() != posOffset - lastPosOffset();
        }

        /**
         * Adds entry pair if worthwhile.
         *
         * @param sbOffset  comment offset member of pair.
         * @param posOffset  input offset member of pair.
         */
        void add(int sbOffset, int posOffset) {
            if (size == 0 || shouldAdd(sbOffset, posOffset)) {
                ensure(NOFFSETS);
                map[size + SB_OFFSET] = sbOffset;
                map[size + POS_OFFSET] = posOffset;
                size += NOFFSETS;
            }
        }

        /**
         * Returns the previous comment offset.
         *
         * @return the previous comment offset.
         */
        private int lastSBOffset() {
            return size == 0 ? 0 : map[size - NOFFSETS + SB_OFFSET];
        }

        /**
         * Returns the previous input offset.
         *
         * @return the previous input offset.
         */
        private int lastPosOffset() {
            return size == 0 ? 0 : map[size - NOFFSETS + POS_OFFSET];
        }

        /**
         * Ensures there is enough space for a new entry.
         *
         * @param need  number of array slots needed.
         */
        private void ensure(int need) {
            need += size;
            int grow = map.length;

            while (need > grow) {
                grow <<= 1;
                // Handle overflow.
                if (grow <= 0) {
                    throw new IndexOutOfBoundsException();
                }
            }

            if (grow != map.length) {
                map = Arrays.copyOf(map, grow);
            }
        }

        /**
         * Reduce map to minimum size.
         */
        void trim() {
            map = Arrays.copyOf(map, size);
        }

        /**
         * Binary search to find the entry for which the string index is less
         * than pos. Since the map is a list of pairs of integers we must make
         * sure the index is always NOFFSETS scaled. If we find an exact match
         * for pos, the other item in the pair gives the source pos; otherwise,
         * compute the source position relative to the best match found in the
         * array.
         */
        int getSourcePos(int pos) {
            if (size == 0) {
                return Position.NOPOS;
            }

            int start = 0;
            int end = size / NOFFSETS;

            while (start < end - 1) {
                // find an index midway between start and end
                int index = (start + end) / 2;
                int indexScaled = index * NOFFSETS;

                if (map[indexScaled + SB_OFFSET] < pos) {
                    start = index;
                } else if (map[indexScaled + SB_OFFSET] == pos) {
                    return map[indexScaled + POS_OFFSET];
                } else {
                    end = index;
                }
            }

            int startScaled = start * NOFFSETS;

            return map[startScaled + POS_OFFSET] + (pos - map[startScaled + SB_OFFSET]);
        }
    }

    /**
     * A Comment derived from a JavadocComment with leading whitespace removed from all lines.
     * A new OffsetMap is used in combination with the OffsetMap of the original comment to
     * translate comment locations to positions in the source file.
     *
     * Note: This class assumes new lines are encoded as {@code '\n'}, which is the case
     * for comments created by {@code JavadocTokenizer}.
     */
    static class StrippedComment implements Comment {
        String text;
        final OffsetMap strippedMap;
        final OffsetMap sourceMap;
        // Copy these fields to not hold a reference to the original comment with its text
        final JCDiagnostic.DiagnosticPosition diagPos;
        final CommentStyle style;
        final boolean deprecated;

        /**
         * Returns a stripped version of the comment, or the comment itself if there is no
         * whitespace that can be stripped.
         *
         * @param comment the original comment
         * @return stripped or original comment
         */
        static Comment of(JavadocComment comment) {
            if (comment.getStyle() != CommentStyle.JAVADOC_BLOCK) {
                return comment;
            }
            int indent = getIndent(comment);
            return indent > 0 ? new StrippedComment(comment, indent) : comment;
        }

        private StrippedComment(JavadocComment comment, int indent) {
            this.diagPos = comment.getPos();
            this.style = comment.getStyle();
            this.deprecated = comment.isDeprecated();
            this.strippedMap = new OffsetMap();
            this.sourceMap = comment.offsetMap;
            stripComment(comment, indent);
        }

        /**
         * Determines the number of leading whitespace characters that can be removed from
         * all non-blank lines of the original comment.
         *
         * @param comment the original comment
         * @return number of leading whitespace characters that can be reomved
         */
        static int getIndent(Comment comment) {
            String txt = comment.getText();
            int len = txt.length();
            int indent = Integer.MAX_VALUE;

            for (int i = 0; i < len; ) {
                int next;
                boolean inIndent = true;
                for (next = i; next < len && txt.charAt(next) != '\n'; next++) {
                    if (inIndent && !Character.isWhitespace(txt.charAt(next))) {
                        indent = Math.min(indent, next - i);
                        inIndent = false;
                    }
                }
                i = next + 1;
            }

            return indent == Integer.MAX_VALUE ? 0 : indent;
        }

        /**
         * Strips {@code indent} whitespace characters from every line of the original comment
         * and initializes an OffsetMap to translate positions to the original comment's OffsetMap.
         * This method does not distinguish between blank and non-blank lines except for the fact
         * that blank lines are not required to contain the number of leading whitespace indicated
         * by {@indent}.
         *
         * @param comment the original comment
         * @param indent number of whitespace characters to remove from each non-blank line
         */
        private void stripComment(JavadocComment comment, int indent) {
            String txt = comment.getText();
            int len = txt.length();
            StringBuilder sb = new StringBuilder(len);

            for (int i = 0; i < len; ) {
                int startOfLine = i;
                // Advance till start of stripped line, or \n if line is blank
                while (startOfLine < len
                        && startOfLine < i + indent
                        && txt.charAt(startOfLine) != '\n') {
                    assert(Character.isWhitespace(txt.charAt(startOfLine)));
                    startOfLine++;
                }
                if (startOfLine == len) {
                    break;
                }

                // Copy stripped line (terminated by \n or end of input)
                i = startOfLine + 1;
                while (i < len && txt.charAt(i - 1) != '\n') {
                    i++;
                }
                // Add new offset if necessary
                strippedMap.add(sb.length(), startOfLine);
                sb.append(txt, startOfLine, i);
            }

            text = sb.toString();
            strippedMap.trim();
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public Comment stripIndent() {
            return this;
        }

        @Override
        public int getSourcePos(int pos) {
            if (pos == Position.NOPOS) {
                return Position.NOPOS;
            }

            if (pos < 0 || pos > text.length()) {
                throw new StringIndexOutOfBoundsException(String.valueOf(pos));
            }

            return sourceMap.getSourcePos(strippedMap.getSourcePos(pos));
        }

        @Override
        public JCDiagnostic.DiagnosticPosition getPos() {
            return diagPos;
        }

        @Override
        public CommentStyle getStyle() {
            return style;
        }

        @Override
        public boolean isDeprecated() {
            return deprecated;
        }
    }

}

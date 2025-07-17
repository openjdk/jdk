/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, a notice that is now available elsewhere in this distribution
 * accompanied the original version of this file, and, per its terms,
 * should not be removed.
 */

package jdk.internal.org.commonmark.parser.block;

import jdk.internal.org.commonmark.parser.SourceLine;

/**
 * State of the parser that is used in block parsers.
 * <p><em>This interface is not intended to be implemented by clients.</em></p>
 */
public interface ParserState {

    /**
     * @return the current source line being parsed (full line)
     */
    SourceLine getLine();

    /**
     * @return the current index within the line (0-based)
     */
    int getIndex();

    /**
     * @return the index of the next non-space character starting from {@link #getIndex()} (may be the same) (0-based)
     */
    int getNextNonSpaceIndex();

    /**
     * The column is the position within the line after tab characters have been processed as 4-space tab stops.
     * If the line doesn't contain any tabs, it's the same as the {@link #getIndex()}. If the line starts with a tab,
     * followed by text, then the column for the first character of the text is 4 (the index is 1).
     *
     * @return the current column within the line (0-based)
     */
    int getColumn();

    /**
     * @return the indentation in columns (either by spaces or tab stop of 4), starting from {@link #getColumn()}
     */
    int getIndent();

    /**
     * @return true if the current line is blank starting from the index
     */
    boolean isBlank();

    /**
     * @return the deepest open block parser
     */
    BlockParser getActiveBlockParser();

}

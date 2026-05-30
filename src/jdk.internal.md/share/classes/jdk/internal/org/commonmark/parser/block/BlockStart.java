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

import jdk.internal.org.commonmark.internal.BlockStartImpl;

/**
 * Result object for starting parsing of a block, see static methods for constructors.
 */
public abstract class BlockStart {

    protected BlockStart() {
    }

    /**
     * Result for when there is no block start.
     */
    public static BlockStart none() {
        return null;
    }

    /**
     * Start block(s) with the specified parser(s).
     */
    public static BlockStart of(BlockParser... blockParsers) {
        return new BlockStartImpl(blockParsers);
    }

    /**
     * Continue parsing at the specified index.
     *
     * @param newIndex the new index, see {@link ParserState#getIndex()}
     */
    public abstract BlockStart atIndex(int newIndex);

    /**
     * Continue parsing at the specified column (for tab handling).
     *
     * @param newColumn the new column, see {@link ParserState#getColumn()}
     */
    public abstract BlockStart atColumn(int newColumn);

    /**
     * @deprecated use {@link #replaceParagraphLines(int)} instead; please raise an issue if that doesn't work for you
     * for some reason.
     */
    @Deprecated
    public abstract BlockStart replaceActiveBlockParser();

    /**
     * Replace a number of lines from the current paragraph (as returned by
     * {@link MatchedBlockParser#getParagraphLines()}) with the new block.
     * <p>
     * This is useful for parsing blocks that start with normal paragraphs and only have special marker syntax in later
     * lines, e.g. in this:
     * <pre>
     * Foo
     * ===
     * </pre>
     * The <code>Foo</code> line is initially parsed as a normal paragraph, then <code>===</code> is parsed as a heading
     * marker, replacing the 1 paragraph line before. The end result is a single Heading block.
     * <p>
     * Note that source spans from the replaced lines are automatically added to the new block.
     *
     * @param lines the number of lines to replace (at least 1); use {@link Integer#MAX_VALUE} to replace the whole
     *              paragraph
     */
    public abstract BlockStart replaceParagraphLines(int lines);

}

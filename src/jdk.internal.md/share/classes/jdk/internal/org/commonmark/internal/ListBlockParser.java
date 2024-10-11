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

package jdk.internal.org.commonmark.internal;

import jdk.internal.org.commonmark.internal.util.Parsing;
import jdk.internal.org.commonmark.node.*;
import jdk.internal.org.commonmark.parser.block.*;

import java.util.Objects;

public class ListBlockParser extends AbstractBlockParser {

    private final ListBlock block;

    private boolean hadBlankLine;
    private int linesAfterBlank;

    public ListBlockParser(ListBlock block) {
        this.block = block;
    }

    @Override
    public boolean isContainer() {
        return true;
    }

    @Override
    public boolean canContain(Block childBlock) {
        if (childBlock instanceof ListItem) {
            // Another list item is added to this list block. If the previous line was blank, that means this list block
            // is "loose" (not tight).
            //
            // spec: A list is loose if any of its constituent list items are separated by blank lines
            if (hadBlankLine && linesAfterBlank == 1) {
                block.setTight(false);
                hadBlankLine = false;
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public BlockContinue tryContinue(ParserState state) {
        if (state.isBlank()) {
            hadBlankLine = true;
            linesAfterBlank = 0;
        } else if (hadBlankLine) {
            linesAfterBlank++;
        }
        // List blocks themselves don't have any markers, only list items. So try to stay in the list.
        // If there is a block start other than list item, canContain makes sure that this list is closed.
        return BlockContinue.atIndex(state.getIndex());
    }

    /**
     * Parse a list marker and return data on the marker or null.
     */
    private static ListData parseList(CharSequence line, final int markerIndex, final int markerColumn,
                                      final boolean inParagraph) {
        ListMarkerData listMarker = parseListMarker(line, markerIndex);
        if (listMarker == null) {
            return null;
        }
        ListBlock listBlock = listMarker.listBlock;

        int indexAfterMarker = listMarker.indexAfterMarker;
        int markerLength = indexAfterMarker - markerIndex;
        // marker doesn't include tabs, so counting them as columns directly is ok
        int columnAfterMarker = markerColumn + markerLength;
        // the column within the line where the content starts
        int contentColumn = columnAfterMarker;

        // See at which column the content starts if there is content
        boolean hasContent = false;
        int length = line.length();
        for (int i = indexAfterMarker; i < length; i++) {
            char c = line.charAt(i);
            if (c == '\t') {
                contentColumn += Parsing.columnsToNextTabStop(contentColumn);
            } else if (c == ' ') {
                contentColumn++;
            } else {
                hasContent = true;
                break;
            }
        }

        if (inParagraph) {
            // If the list item is ordered, the start number must be 1 to interrupt a paragraph.
            if (listBlock instanceof OrderedList && ((OrderedList) listBlock).getMarkerStartNumber() != 1) {
                return null;
            }
            // Empty list item can not interrupt a paragraph.
            if (!hasContent) {
                return null;
            }
        }

        if (!hasContent || (contentColumn - columnAfterMarker) > Parsing.CODE_BLOCK_INDENT) {
            // If this line is blank or has a code block, default to 1 space after marker
            contentColumn = columnAfterMarker + 1;
        }

        return new ListData(listBlock, contentColumn);
    }

    private static ListMarkerData parseListMarker(CharSequence line, int index) {
        char c = line.charAt(index);
        switch (c) {
            // spec: A bullet list marker is a -, +, or * character.
            case '-':
            case '+':
            case '*':
                if (isSpaceTabOrEnd(line, index + 1)) {
                    BulletList bulletList = new BulletList();
                    bulletList.setMarker(String.valueOf(c));
                    return new ListMarkerData(bulletList, index + 1);
                } else {
                    return null;
                }
            default:
                return parseOrderedList(line, index);
        }
    }

    // spec: An ordered list marker is a sequence of 1\u20139 arabic digits (0-9), followed by either a `.` character or a
    // `)` character.
    private static ListMarkerData parseOrderedList(CharSequence line, int index) {
        int digits = 0;
        int length = line.length();
        for (int i = index; i < length; i++) {
            char c = line.charAt(i);
            switch (c) {
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                    digits++;
                    if (digits > 9) {
                        return null;
                    }
                    break;
                case '.':
                case ')':
                    if (digits >= 1 && isSpaceTabOrEnd(line, i + 1)) {
                        String number = line.subSequence(index, i).toString();
                        OrderedList orderedList = new OrderedList();
                        orderedList.setMarkerStartNumber(Integer.parseInt(number));
                        orderedList.setMarkerDelimiter(String.valueOf(c));
                        return new ListMarkerData(orderedList, i + 1);
                    } else {
                        return null;
                    }
                default:
                    return null;
            }
        }
        return null;
    }

    private static boolean isSpaceTabOrEnd(CharSequence line, int index) {
        if (index < line.length()) {
            switch (line.charAt(index)) {
                case ' ':
                case '\t':
                    return true;
                default:
                    return false;
            }
        } else {
            return true;
        }
    }

    /**
     * Returns true if the two list items are of the same type,
     * with the same delimiter and bullet character. This is used
     * in agglomerating list items into lists.
     */
    private static boolean listsMatch(ListBlock a, ListBlock b) {
        if (a instanceof BulletList && b instanceof BulletList) {
            return Objects.equals(((BulletList) a).getMarker(), ((BulletList) b).getMarker());
        } else if (a instanceof OrderedList && b instanceof OrderedList) {
            return Objects.equals(((OrderedList) a).getMarkerDelimiter(), ((OrderedList) b).getMarkerDelimiter());
        }
        return false;
    }

    public static class Factory extends AbstractBlockParserFactory {

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
            BlockParser matched = matchedBlockParser.getMatchedBlockParser();

            if (state.getIndent() >= Parsing.CODE_BLOCK_INDENT) {
                return BlockStart.none();
            }
            int markerIndex = state.getNextNonSpaceIndex();
            int markerColumn = state.getColumn() + state.getIndent();
            boolean inParagraph = !matchedBlockParser.getParagraphLines().isEmpty();
            ListData listData = parseList(state.getLine().getContent(), markerIndex, markerColumn, inParagraph);
            if (listData == null) {
                return BlockStart.none();
            }

            int newColumn = listData.contentColumn;
            ListItemParser listItemParser = new ListItemParser(state.getIndent(), newColumn - state.getColumn());

            // prepend the list block if needed
            if (!(matched instanceof ListBlockParser) ||
                    !(listsMatch((ListBlock) matched.getBlock(), listData.listBlock))) {

                ListBlockParser listBlockParser = new ListBlockParser(listData.listBlock);
                // We start out with assuming a list is tight. If we find a blank line, we set it to loose later.
                listData.listBlock.setTight(true);

                return BlockStart.of(listBlockParser, listItemParser).atColumn(newColumn);
            } else {
                return BlockStart.of(listItemParser).atColumn(newColumn);
            }
        }
    }

    private static class ListData {
        final ListBlock listBlock;
        final int contentColumn;

        ListData(ListBlock listBlock, int contentColumn) {
            this.listBlock = listBlock;
            this.contentColumn = contentColumn;
        }
    }

    private static class ListMarkerData {
        final ListBlock listBlock;
        final int indexAfterMarker;

        ListMarkerData(ListBlock listBlock, int indexAfterMarker) {
            this.listBlock = listBlock;
            this.indexAfterMarker = indexAfterMarker;
        }
    }
}

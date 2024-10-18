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
import jdk.internal.org.commonmark.node.Block;
import jdk.internal.org.commonmark.node.IndentedCodeBlock;
import jdk.internal.org.commonmark.node.Paragraph;
import jdk.internal.org.commonmark.parser.SourceLine;
import jdk.internal.org.commonmark.parser.block.*;
import jdk.internal.org.commonmark.text.Characters;

import java.util.ArrayList;
import java.util.List;

public class IndentedCodeBlockParser extends AbstractBlockParser {

    private final IndentedCodeBlock block = new IndentedCodeBlock();
    private final List<CharSequence> lines = new ArrayList<>();

    @Override
    public Block getBlock() {
        return block;
    }

    @Override
    public BlockContinue tryContinue(ParserState state) {
        if (state.getIndent() >= Parsing.CODE_BLOCK_INDENT) {
            return BlockContinue.atColumn(state.getColumn() + Parsing.CODE_BLOCK_INDENT);
        } else if (state.isBlank()) {
            return BlockContinue.atIndex(state.getNextNonSpaceIndex());
        } else {
            return BlockContinue.none();
        }
    }

    @Override
    public void addLine(SourceLine line) {
        lines.add(line.getContent());
    }

    @Override
    public void closeBlock() {
        int lastNonBlank = lines.size() - 1;
        while (lastNonBlank >= 0) {
            if (!Characters.isBlank(lines.get(lastNonBlank))) {
                break;
            }
            lastNonBlank--;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lastNonBlank + 1; i++) {
            sb.append(lines.get(i));
            sb.append('\n');
        }

        String literal = sb.toString();
        block.setLiteral(literal);
    }

    public static class Factory extends AbstractBlockParserFactory {

        @Override
        public BlockStart tryStart(ParserState state, MatchedBlockParser matchedBlockParser) {
            // An indented code block cannot interrupt a paragraph.
            if (state.getIndent() >= Parsing.CODE_BLOCK_INDENT && !state.isBlank() && !(state.getActiveBlockParser().getBlock() instanceof Paragraph)) {
                return BlockStart.of(new IndentedCodeBlockParser()).atColumn(state.getColumn() + Parsing.CODE_BLOCK_INDENT);
            } else {
                return BlockStart.none();
            }
        }
    }
}


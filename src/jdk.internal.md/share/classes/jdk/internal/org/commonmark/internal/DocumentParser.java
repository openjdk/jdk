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
import jdk.internal.org.commonmark.parser.*;
import jdk.internal.org.commonmark.parser.block.*;
import jdk.internal.org.commonmark.parser.delimiter.DelimiterProcessor;
import jdk.internal.org.commonmark.text.Characters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

public class DocumentParser implements ParserState {

    private static final Set<Class<? extends Block>> CORE_FACTORY_TYPES = new LinkedHashSet<>(Arrays.asList(
            BlockQuote.class,
            Heading.class,
            FencedCodeBlock.class,
            HtmlBlock.class,
            ThematicBreak.class,
            ListBlock.class,
            IndentedCodeBlock.class));

    private static final Map<Class<? extends Block>, BlockParserFactory> NODES_TO_CORE_FACTORIES;

    static {
        Map<Class<? extends Block>, BlockParserFactory> map = new HashMap<>();
        map.put(BlockQuote.class, new BlockQuoteParser.Factory());
        map.put(Heading.class, new HeadingParser.Factory());
        map.put(FencedCodeBlock.class, new FencedCodeBlockParser.Factory());
        map.put(HtmlBlock.class, new HtmlBlockParser.Factory());
        map.put(ThematicBreak.class, new ThematicBreakParser.Factory());
        map.put(ListBlock.class, new ListBlockParser.Factory());
        map.put(IndentedCodeBlock.class, new IndentedCodeBlockParser.Factory());
        NODES_TO_CORE_FACTORIES = Collections.unmodifiableMap(map);
    }

    private SourceLine line;

    /**
     * Line index (0-based)
     */
    private int lineIndex = -1;

    /**
     * current index (offset) in input line (0-based)
     */
    private int index = 0;

    /**
     * current column of input line (tab causes column to go to next 4-space tab stop) (0-based)
     */
    private int column = 0;

    /**
     * if the current column is within a tab character (partially consumed tab)
     */
    private boolean columnIsInTab;

    private int nextNonSpace = 0;
    private int nextNonSpaceColumn = 0;
    private int indent = 0;
    private boolean blank;

    private final List<BlockParserFactory> blockParserFactories;
    private final InlineParserFactory inlineParserFactory;
    private final List<DelimiterProcessor> delimiterProcessors;
    private final IncludeSourceSpans includeSourceSpans;
    private final DocumentBlockParser documentBlockParser;
    private final LinkReferenceDefinitions definitions = new LinkReferenceDefinitions();

    private final List<OpenBlockParser> openBlockParsers = new ArrayList<>();
    private final List<BlockParser> allBlockParsers = new ArrayList<>();

    public DocumentParser(List<BlockParserFactory> blockParserFactories, InlineParserFactory inlineParserFactory,
                          List<DelimiterProcessor> delimiterProcessors, IncludeSourceSpans includeSourceSpans) {
        this.blockParserFactories = blockParserFactories;
        this.inlineParserFactory = inlineParserFactory;
        this.delimiterProcessors = delimiterProcessors;
        this.includeSourceSpans = includeSourceSpans;

        this.documentBlockParser = new DocumentBlockParser();
        activateBlockParser(new OpenBlockParser(documentBlockParser, 0));
    }

    public static Set<Class<? extends Block>> getDefaultBlockParserTypes() {
        return CORE_FACTORY_TYPES;
    }

    public static List<BlockParserFactory> calculateBlockParserFactories(List<BlockParserFactory> customBlockParserFactories, Set<Class<? extends Block>> enabledBlockTypes) {
        List<BlockParserFactory> list = new ArrayList<>();
        // By having the custom factories come first, extensions are able to change behavior of core syntax.
        list.addAll(customBlockParserFactories);
        for (Class<? extends Block> blockType : enabledBlockTypes) {
            list.add(NODES_TO_CORE_FACTORIES.get(blockType));
        }
        return list;
    }

    public static void checkEnabledBlockTypes(Set<Class<? extends Block>> enabledBlockTypes) {
        for (Class<? extends Block> enabledBlockType : enabledBlockTypes) {
            if (!NODES_TO_CORE_FACTORIES.containsKey(enabledBlockType)) {
                throw new IllegalArgumentException("Can't enable block type " + enabledBlockType + ", possible options are: " + NODES_TO_CORE_FACTORIES.keySet());
            }
        }
    }

    /**
     * The main parsing function. Returns a parsed document AST.
     */
    public Document parse(String input) {
        int lineStart = 0;
        int lineBreak;
        while ((lineBreak = Characters.findLineBreak(input, lineStart)) != -1) {
            String line = input.substring(lineStart, lineBreak);
            parseLine(line);
            if (lineBreak + 1 < input.length() && input.charAt(lineBreak) == '\r' && input.charAt(lineBreak + 1) == '\n') {
                lineStart = lineBreak + 2;
            } else {
                lineStart = lineBreak + 1;
            }
        }
        if (input.length() > 0 && (lineStart == 0 || lineStart < input.length())) {
            String line = input.substring(lineStart);
            parseLine(line);
        }

        return finalizeAndProcess();
    }

    public Document parse(Reader input) throws IOException {
        BufferedReader bufferedReader;
        if (input instanceof BufferedReader) {
            bufferedReader = (BufferedReader) input;
        } else {
            bufferedReader = new BufferedReader(input);
        }

        String line;
        while ((line = bufferedReader.readLine()) != null) {
            parseLine(line);
        }

        return finalizeAndProcess();
    }

    @Override
    public SourceLine getLine() {
        return line;
    }

    @Override
    public int getIndex() {
        return index;
    }

    @Override
    public int getNextNonSpaceIndex() {
        return nextNonSpace;
    }

    @Override
    public int getColumn() {
        return column;
    }

    @Override
    public int getIndent() {
        return indent;
    }

    @Override
    public boolean isBlank() {
        return blank;
    }

    @Override
    public BlockParser getActiveBlockParser() {
        return openBlockParsers.get(openBlockParsers.size() - 1).blockParser;
    }

    /**
     * Analyze a line of text and update the document appropriately. We parse markdown text by calling this on each
     * line of input, then finalizing the document.
     */
    private void parseLine(CharSequence ln) {
        setLine(ln);

        // For each containing block, try to parse the associated line start.
        // The document will always match, so we can skip the first block parser and start at 1 matches
        int matches = 1;
        for (int i = 1; i < openBlockParsers.size(); i++) {
            OpenBlockParser openBlockParser = openBlockParsers.get(i);
            BlockParser blockParser = openBlockParser.blockParser;
            findNextNonSpace();

            BlockContinue result = blockParser.tryContinue(this);
            if (result instanceof BlockContinueImpl) {
                BlockContinueImpl blockContinue = (BlockContinueImpl) result;
                openBlockParser.sourceIndex = getIndex();
                if (blockContinue.isFinalize()) {
                    addSourceSpans();
                    closeBlockParsers(openBlockParsers.size() - i);
                    return;
                } else {
                    if (blockContinue.getNewIndex() != -1) {
                        setNewIndex(blockContinue.getNewIndex());
                    } else if (blockContinue.getNewColumn() != -1) {
                        setNewColumn(blockContinue.getNewColumn());
                    }
                    matches++;
                }
            } else {
                break;
            }
        }

        int unmatchedBlocks = openBlockParsers.size() - matches;
        BlockParser blockParser = openBlockParsers.get(matches - 1).blockParser;
        boolean startedNewBlock = false;

        int lastIndex = index;

        // Unless last matched container is a code block, try new container starts,
        // adding children to the last matched container:
        boolean tryBlockStarts = blockParser.getBlock() instanceof Paragraph || blockParser.isContainer();
        while (tryBlockStarts) {
            lastIndex = index;
            findNextNonSpace();

            // this is a little performance optimization:
            if (isBlank() || (indent < Parsing.CODE_BLOCK_INDENT && Characters.isLetter(this.line.getContent(), nextNonSpace))) {
                setNewIndex(nextNonSpace);
                break;
            }

            BlockStartImpl blockStart = findBlockStart(blockParser);
            if (blockStart == null) {
                setNewIndex(nextNonSpace);
                break;
            }

            startedNewBlock = true;
            int sourceIndex = getIndex();

            // We're starting a new block. If we have any previous blocks that need to be closed, we need to do it now.
            if (unmatchedBlocks > 0) {
                closeBlockParsers(unmatchedBlocks);
                unmatchedBlocks = 0;
            }

            if (blockStart.getNewIndex() != -1) {
                setNewIndex(blockStart.getNewIndex());
            } else if (blockStart.getNewColumn() != -1) {
                setNewColumn(blockStart.getNewColumn());
            }

            List<SourceSpan> replacedSourceSpans = null;
            if (blockStart.isReplaceActiveBlockParser()) {
                Block replacedBlock = prepareActiveBlockParserForReplacement();
                replacedSourceSpans = replacedBlock.getSourceSpans();
            }

            for (BlockParser newBlockParser : blockStart.getBlockParsers()) {
                addChild(new OpenBlockParser(newBlockParser, sourceIndex));
                if (replacedSourceSpans != null) {
                    newBlockParser.getBlock().setSourceSpans(replacedSourceSpans);
                }
                blockParser = newBlockParser;
                tryBlockStarts = newBlockParser.isContainer();
            }
        }

        // What remains at the offset is a text line. Add the text to the
        // appropriate block.

        // First check for a lazy paragraph continuation:
        if (!startedNewBlock && !isBlank() &&
                getActiveBlockParser().canHaveLazyContinuationLines()) {
            openBlockParsers.get(openBlockParsers.size() - 1).sourceIndex = lastIndex;
            // lazy paragraph continuation
            addLine();

        } else {

            // finalize any blocks not matched
            if (unmatchedBlocks > 0) {
                closeBlockParsers(unmatchedBlocks);
            }

            if (!blockParser.isContainer()) {
                addLine();
            } else if (!isBlank()) {
                // create paragraph container for line
                ParagraphParser paragraphParser = new ParagraphParser();
                addChild(new OpenBlockParser(paragraphParser, lastIndex));
                addLine();
            } else {
                // This can happen for a list item like this:
                // ```
                // *
                // list item
                // ```
                //
                // The first line does not start a paragraph yet, but we still want to record source positions.
                addSourceSpans();
            }
        }
    }

    private void setLine(CharSequence ln) {
        lineIndex++;
        index = 0;
        column = 0;
        columnIsInTab = false;

        CharSequence lineContent = prepareLine(ln);
        SourceSpan sourceSpan = null;
        if (includeSourceSpans != IncludeSourceSpans.NONE) {
            sourceSpan = SourceSpan.of(lineIndex, 0, lineContent.length());
        }
        this.line = SourceLine.of(lineContent, sourceSpan);
    }

    private void findNextNonSpace() {
        int i = index;
        int cols = column;

        blank = true;
        int length = line.getContent().length();
        while (i < length) {
            char c = line.getContent().charAt(i);
            switch (c) {
                case ' ':
                    i++;
                    cols++;
                    continue;
                case '\t':
                    i++;
                    cols += (4 - (cols % 4));
                    continue;
            }
            blank = false;
            break;
        }

        nextNonSpace = i;
        nextNonSpaceColumn = cols;
        indent = nextNonSpaceColumn - column;
    }

    private void setNewIndex(int newIndex) {
        if (newIndex >= nextNonSpace) {
            // We can start from here, no need to calculate tab stops again
            index = nextNonSpace;
            column = nextNonSpaceColumn;
        }
        int length = line.getContent().length();
        while (index < newIndex && index != length) {
            advance();
        }
        // If we're going to an index as opposed to a column, we're never within a tab
        columnIsInTab = false;
    }

    private void setNewColumn(int newColumn) {
        if (newColumn >= nextNonSpaceColumn) {
            // We can start from here, no need to calculate tab stops again
            index = nextNonSpace;
            column = nextNonSpaceColumn;
        }
        int length = line.getContent().length();
        while (column < newColumn && index != length) {
            advance();
        }
        if (column > newColumn) {
            // Last character was a tab and we overshot our target
            index--;
            column = newColumn;
            columnIsInTab = true;
        } else {
            columnIsInTab = false;
        }
    }

    private void advance() {
        char c = line.getContent().charAt(index);
        index++;
        if (c == '\t') {
            column += Parsing.columnsToNextTabStop(column);
        } else {
            column++;
        }
    }

    /**
     * Add line content to the active block parser. We assume it can accept lines -- that check should be done before
     * calling this.
     */
    private void addLine() {
        CharSequence content;
        if (columnIsInTab) {
            // Our column is in a partially consumed tab. Expand the remaining columns (to the next tab stop) to spaces.
            int afterTab = index + 1;
            CharSequence rest = line.getContent().subSequence(afterTab, line.getContent().length());
            int spaces = Parsing.columnsToNextTabStop(column);
            StringBuilder sb = new StringBuilder(spaces + rest.length());
            for (int i = 0; i < spaces; i++) {
                sb.append(' ');
            }
            sb.append(rest);
            content = sb.toString();
        } else if (index == 0) {
            content = line.getContent();
        } else {
            content = line.getContent().subSequence(index, line.getContent().length());
        }
        SourceSpan sourceSpan = null;
        if (includeSourceSpans == IncludeSourceSpans.BLOCKS_AND_INLINES) {
            // Note that if we're in a partially-consumed tab, the length here corresponds to the content but not to the
            // actual source length. That sounds like a problem, but I haven't found a test case where it matters (yet).
            sourceSpan = SourceSpan.of(lineIndex, index, content.length());
        }
        getActiveBlockParser().addLine(SourceLine.of(content, sourceSpan));
        addSourceSpans();
    }

    private void addSourceSpans() {
        if (includeSourceSpans != IncludeSourceSpans.NONE) {
            // Don't add source spans for Document itself (it would get the whole source text)
            for (int i = 1; i < openBlockParsers.size(); i++) {
                OpenBlockParser openBlockParser = openBlockParsers.get(i);
                int blockIndex = openBlockParser.sourceIndex;
                int length = line.getContent().length() - blockIndex;
                if (length != 0) {
                    openBlockParser.blockParser.addSourceSpan(SourceSpan.of(lineIndex, blockIndex, length));
                }
            }
        }
    }

    private BlockStartImpl findBlockStart(BlockParser blockParser) {
        MatchedBlockParser matchedBlockParser = new MatchedBlockParserImpl(blockParser);
        for (BlockParserFactory blockParserFactory : blockParserFactories) {
            BlockStart result = blockParserFactory.tryStart(this, matchedBlockParser);
            if (result instanceof BlockStartImpl) {
                return (BlockStartImpl) result;
            }
        }
        return null;
    }

    /**
     * Finalize a block. Close it and do any necessary postprocessing, e.g. setting the content of blocks and
     * collecting link reference definitions from paragraphs.
     */
    private void finalize(BlockParser blockParser) {
        if (blockParser instanceof ParagraphParser) {
            addDefinitionsFrom((ParagraphParser) blockParser);
        }

        blockParser.closeBlock();
    }

    private void addDefinitionsFrom(ParagraphParser paragraphParser) {
        for (LinkReferenceDefinition definition : paragraphParser.getDefinitions()) {
            // Add nodes into document before paragraph.
            paragraphParser.getBlock().insertBefore(definition);

            definitions.add(definition);
        }
    }

    /**
     * Walk through a block & children recursively, parsing string content into inline content where appropriate.
     */
    private void processInlines() {
        InlineParserContextImpl context = new InlineParserContextImpl(delimiterProcessors, definitions);
        InlineParser inlineParser = inlineParserFactory.create(context);

        for (BlockParser blockParser : allBlockParsers) {
            blockParser.parseInlines(inlineParser);
        }
    }

    /**
     * Add block of type tag as a child of the tip. If the tip can't accept children, close and finalize it and try
     * its parent, and so on until we find a block that can accept children.
     */
    private void addChild(OpenBlockParser openBlockParser) {
        while (!getActiveBlockParser().canContain(openBlockParser.blockParser.getBlock())) {
            closeBlockParsers(1);
        }

        getActiveBlockParser().getBlock().appendChild(openBlockParser.blockParser.getBlock());
        activateBlockParser(openBlockParser);
    }

    private void activateBlockParser(OpenBlockParser openBlockParser) {
        openBlockParsers.add(openBlockParser);
    }

    private OpenBlockParser deactivateBlockParser() {
        return openBlockParsers.remove(openBlockParsers.size() - 1);
    }

    private Block prepareActiveBlockParserForReplacement() {
        // Note that we don't want to parse inlines, as it's getting replaced.
        BlockParser old = deactivateBlockParser().blockParser;

        if (old instanceof ParagraphParser) {
            ParagraphParser paragraphParser = (ParagraphParser) old;
            // Collect any link reference definitions. Note that replacing the active block parser is done after a
            // block parser got the current paragraph content using MatchedBlockParser#getContentString. In case the
            // paragraph started with link reference definitions, we parse and strip them before the block parser gets
            // the content. We want to keep them.
            // If no replacement happens, we collect the definitions as part of finalizing paragraph blocks.
            addDefinitionsFrom(paragraphParser);
        }

        // Do this so that source positions are calculated, which we will carry over to the replacing block.
        old.closeBlock();
        old.getBlock().unlink();
        return old.getBlock();
    }

    private Document finalizeAndProcess() {
        closeBlockParsers(openBlockParsers.size());
        processInlines();
        return documentBlockParser.getBlock();
    }

    private void closeBlockParsers(int count) {
        for (int i = 0; i < count; i++) {
            BlockParser blockParser = deactivateBlockParser().blockParser;
            finalize(blockParser);
            // Remember for inline parsing. Note that a lot of blocks don't need inline parsing. We could have a
            // separate interface (e.g. BlockParserWithInlines) so that we only have to remember those that actually
            // have inlines to parse.
            allBlockParsers.add(blockParser);
        }
    }

    /**
     * Prepares the input line replacing {@code \0}
     */
    private static CharSequence prepareLine(CharSequence line) {
        // Avoid building a new string in the majority of cases (no \0)
        StringBuilder sb = null;
        int length = line.length();
        for (int i = 0; i < length; i++) {
            char c = line.charAt(i);
            if (c == '\0') {
                if (sb == null) {
                    sb = new StringBuilder(length);
                    sb.append(line, 0, i);
                }
                sb.append('\uFFFD');
            } else {
                if (sb != null) {
                    sb.append(c);
                }
            }
        }

        if (sb != null) {
            return sb.toString();
        } else {
            return line;
        }
    }

    private static class MatchedBlockParserImpl implements MatchedBlockParser {

        private final BlockParser matchedBlockParser;

        public MatchedBlockParserImpl(BlockParser matchedBlockParser) {
            this.matchedBlockParser = matchedBlockParser;
        }

        @Override
        public BlockParser getMatchedBlockParser() {
            return matchedBlockParser;
        }

        @Override
        public SourceLines getParagraphLines() {
            if (matchedBlockParser instanceof ParagraphParser) {
                ParagraphParser paragraphParser = (ParagraphParser) matchedBlockParser;
                return paragraphParser.getParagraphLines();
            }
            return SourceLines.empty();
        }
    }

    private static class OpenBlockParser {
        private final BlockParser blockParser;
        private int sourceIndex;

        OpenBlockParser(BlockParser blockParser, int sourceIndex) {
            this.blockParser = blockParser;
            this.sourceIndex = sourceIndex;
        }
    }
}

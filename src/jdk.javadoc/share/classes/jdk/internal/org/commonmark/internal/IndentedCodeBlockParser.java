package jdk.internal.org.commonmark.internal;

import jdk.internal.org.commonmark.internal.util.Parsing;
import jdk.internal.org.commonmark.node.Block;
import jdk.internal.org.commonmark.node.IndentedCodeBlock;
import jdk.internal.org.commonmark.node.Paragraph;
import jdk.internal.org.commonmark.parser.SourceLine;
import jdk.internal.org.commonmark.parser.block.*;

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
            if (!Parsing.isBlank(lines.get(lastNonBlank))) {
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


package jdk.internal.org.commonmark.internal;

import jdk.internal.org.commonmark.node.Block;
import jdk.internal.org.commonmark.node.Document;
import jdk.internal.org.commonmark.parser.SourceLine;
import jdk.internal.org.commonmark.parser.block.AbstractBlockParser;
import jdk.internal.org.commonmark.parser.block.BlockContinue;
import jdk.internal.org.commonmark.parser.block.ParserState;

public class DocumentBlockParser extends AbstractBlockParser {

    private final Document document = new Document();

    @Override
    public boolean isContainer() {
        return true;
    }

    @Override
    public boolean canContain(Block block) {
        return true;
    }

    @Override
    public Document getBlock() {
        return document;
    }

    @Override
    public BlockContinue tryContinue(ParserState state) {
        return BlockContinue.atIndex(state.getIndex());
    }

    @Override
    public void addLine(SourceLine line) {
    }

}

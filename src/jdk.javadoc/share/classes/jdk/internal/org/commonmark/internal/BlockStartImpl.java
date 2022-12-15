package jdk.internal.org.commonmark.internal;

import jdk.internal.org.commonmark.parser.block.BlockParser;
import jdk.internal.org.commonmark.parser.block.BlockStart;

public class BlockStartImpl extends BlockStart {

    private final BlockParser[] blockParsers;
    private int newIndex = -1;
    private int newColumn = -1;
    private boolean replaceActiveBlockParser = false;

    public BlockStartImpl(BlockParser... blockParsers) {
        this.blockParsers = blockParsers;
    }

    public BlockParser[] getBlockParsers() {
        return blockParsers;
    }

    public int getNewIndex() {
        return newIndex;
    }

    public int getNewColumn() {
        return newColumn;
    }

    public boolean isReplaceActiveBlockParser() {
        return replaceActiveBlockParser;
    }

    @Override
    public BlockStart atIndex(int newIndex) {
        this.newIndex = newIndex;
        return this;
    }

    @Override
    public BlockStart atColumn(int newColumn) {
        this.newColumn = newColumn;
        return this;
    }

    @Override
    public BlockStart replaceActiveBlockParser() {
        this.replaceActiveBlockParser = true;
        return this;
    }

}

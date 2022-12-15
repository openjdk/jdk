package jdk.internal.org.commonmark.internal;

import jdk.internal.org.commonmark.parser.block.BlockContinue;

public class BlockContinueImpl extends BlockContinue {

    private final int newIndex;
    private final int newColumn;
    private final boolean finalize;

    public BlockContinueImpl(int newIndex, int newColumn, boolean finalize) {
        this.newIndex = newIndex;
        this.newColumn = newColumn;
        this.finalize = finalize;
    }

    public int getNewIndex() {
        return newIndex;
    }

    public int getNewColumn() {
        return newColumn;
    }

    public boolean isFinalize() {
        return finalize;
    }

}

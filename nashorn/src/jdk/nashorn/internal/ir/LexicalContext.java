package jdk.nashorn.internal.ir;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A class that tracks the current lexical context of node visitation as a stack of {@link Block} nodes. Has special
 * methods to retrieve useful subsets of the context.
 */
public class LexicalContext implements Cloneable {
    private final Deque<Block> lexicalContext;

    /**
     * Creates a new empty lexical context.
     */
    public LexicalContext() {
        lexicalContext = new ArrayDeque<>();
    }

    /**
     * Pushes a new block on top of the context, making it the innermost open block.
     * @param block the new block
     */
    public void push(Block block) {
        //new Exception(block.toString()).printStackTrace();
        lexicalContext.push(block);
    }

    /**
     * Pops the innermost block off the context.
     * @param the block expected to be popped, used to detect unbalanced pushes/pops
     */
    public void pop(Block block) {
        final Block popped = lexicalContext.pop();
        assert popped == block;
    }

    /**
     * Returns an iterator over all blocks in the context, with the top block (innermost lexical context) first.
     * @return an iterator over all blocks in the context.
     */
    public Iterator<Block> getBlocks() {
        return lexicalContext.iterator();
    }

    /**
     * Returns an iterator over all functions in the context, with the top (innermost open) function first.
     * @return an iterator over all functions in the context.
     */
    public Iterator<FunctionNode> getFunctions() {
        return new FunctionIterator(getBlocks());
    }

    private static final class FunctionIterator implements Iterator<FunctionNode> {
        private final Iterator<Block> it;
        private FunctionNode next;

        FunctionIterator(Iterator<Block> it) {
            this.it = it;
            next = findNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public FunctionNode next() {
            if(next == null) {
                throw new NoSuchElementException();
            }
            FunctionNode lnext = next;
            next = findNext();
            return lnext;
        }

        private FunctionNode findNext() {
            while(it.hasNext()) {
                final Block block = it.next();
                if(block instanceof FunctionNode) {
                    return ((FunctionNode)block);
                }
            }
            return null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns an iterator over all ancestors block of the given block, with its parent block first.
     * @param block the block whose ancestors are returned
     * @return an iterator over all ancestors block of the given block.
     */
    public Iterator<Block> getAncestorBlocks(Block block) {
        final Iterator<Block> it = getBlocks();
        while(it.hasNext()) {
            final Block b = it.next();
            if(block == b) {
                return it;
            }
        }
        throw new AssertionError("Block is not on the current lexical context stack");
    }

    /**
     * Returns an iterator over a block and all its ancestors blocks, with the block first.
     * @param block the block that is the starting point of the iteration.
     * @return an iterator over a block and all its ancestors.
     */
    public Iterator<Block> getBlocks(final Block block) {
        final Iterator<Block> it = getAncestorBlocks(block);
        return new Iterator<Block>() {
            boolean blockReturned = false;
            @Override
            public boolean hasNext() {
                return it.hasNext() || !blockReturned;
            }
            @Override
            public Block next() {
                if(blockReturned) {
                    return it.next();
                }
                blockReturned = true;
                return block;
            }
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    /**
     * Returns the closest function node to the block. If the block is itself a function, it is returned.
     * @param block the block
     * @return the function closest to the block.
     * @see #getParentFunction(Block)
     */
    public FunctionNode getFunction(Block block) {
        if(block instanceof FunctionNode) {
            return (FunctionNode)block;
        }
        return getParentFunction(block);
    }

    /**
     * Returns the closest function node to the block and all its ancestor functions. If the block is itself a function,
     * it is returned too.
     * @param block the block
     * @return the closest function node to the block and all its ancestor functions.
     */
    public Iterator<FunctionNode> getFunctions(final Block block) {
        return new FunctionIterator(getBlocks(block));
    }

    /**
     * Returns the containing function of the block. If the block is itself a function, its parent function is returned.
     * @param block the block
     * @return the containing function of the block.
     * @see #getFunction(Block)
     */
    public FunctionNode getParentFunction(Block block) {
        return getFirstFunction(getAncestorBlocks(block));
    }

    private static FunctionNode getFirstFunction(Iterator<Block> it) {
        while(it.hasNext()) {
            final Block ancestor = it.next();
            if(ancestor instanceof FunctionNode) {
                return (FunctionNode)ancestor;
            }
        }
        return null;
    }

    /**
     * Returns the innermost block in the context.
     * @return the innermost block in the context.
     */
    public Block getCurrentBlock() {
        return lexicalContext.element();
    }

    /**
     * Returns the innermost function in the context.
     * @return the innermost function in the context.
     */
    public FunctionNode getCurrentFunction() {
        return getFirstFunction(getBlocks());
    }
}

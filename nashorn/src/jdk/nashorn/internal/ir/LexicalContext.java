/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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
package jdk.nashorn.internal.ir;

import java.io.File;
import java.util.Iterator;
import java.util.NoSuchElementException;
import jdk.nashorn.internal.codegen.Label;
import jdk.nashorn.internal.runtime.Debug;
import jdk.nashorn.internal.runtime.Source;

/**
 * A class that tracks the current lexical context of node visitation as a stack of {@link Block} nodes. Has special
 * methods to retrieve useful subsets of the context.
 *
 * This is implemented with a primitive array and a stack pointer, because it really makes a difference
 * performance wise. None of the collection classes were optimal
 */
public class LexicalContext {
    private LexicalContextNode[] stack;

    private int[] flags;
    private int sp;

    /**
     * Creates a new empty lexical context.
     */
    public LexicalContext() {
        stack = new LexicalContextNode[16];
        flags = new int[16];
    }

    /**
     * Set the flags for a lexical context node on the stack. Does not
     * replace the flags, but rather adds to them.
     *
     * @param node  node
     * @param flag  new flag to set
     */
    public void setFlag(final LexicalContextNode node, final int flag) {
        if (flag != 0) {
            // Use setBlockNeedsScope() instead
            assert !(flag == Block.NEEDS_SCOPE && node instanceof Block);

            for (int i = sp - 1; i >= 0; i--) {
                if (stack[i] == node) {
                    flags[i] |= flag;
                    return;
                }
            }
        }
        assert false;
    }

    /**
     * Marks the block as one that creates a scope. Note that this method must
     * be used instead of {@link #setFlag(LexicalContextNode, int)} with
     * {@link Block#NEEDS_SCOPE} because it atomically also sets the
     * {@link FunctionNode#HAS_SCOPE_BLOCK} flag on the block's containing
     * function.
     * @param block the block that needs to be marked as creating a scope.
     */
    public void setBlockNeedsScope(final Block block) {
        for (int i = sp - 1; i >= 0; i--) {
            if (stack[i] == block) {
                flags[i] |= Block.NEEDS_SCOPE;
                for(int j = i - 1; j >=0; j --) {
                    if(stack[j] instanceof FunctionNode) {
                        flags[j] |= FunctionNode.HAS_SCOPE_BLOCK;
                        return;
                    }
                }
            }
        }
        assert false;
    }

    /**
     * Get the flags for a lexical context node on the stack
     * @param node node
     * @return the flags for the node
     */
    public int getFlags(final LexicalContextNode node) {
        for (int i = sp - 1; i >= 0; i--) {
            if (stack[i] == node) {
                return flags[i];
            }
        }
        throw new AssertionError("flag node not on context stack");
    }

    /**
     * Get the function body of a function node on the lexical context
     * stack. This will trigger an assertion if node isn't present
     * @param functionNode function node
     * @return body of function node
     */
    public Block getFunctionBody(final FunctionNode functionNode) {
        for (int i = sp - 1; i >= 0 ; i--) {
            if (stack[i] == functionNode) {
                return (Block)stack[i + 1];
            }
        }
        throw new AssertionError(functionNode.getName() + " not on context stack");
    }

    /**
     * Return all nodes in the LexicalContext
     * @return all nodes
     */
    public Iterator<LexicalContextNode> getAllNodes() {
        return new NodeIterator<>(LexicalContextNode.class);
    }

    /**
     * Returns the outermost function in this context. It is either the program, or a lazily compiled function.
     * @return the outermost function in this context.
     */
    public FunctionNode getOutermostFunction() {
        return (FunctionNode)stack[0];
    }

    /**
     * Pushes a new block on top of the context, making it the innermost open block.
     * @param node the new node
     * @return the node that was pushed
     */
    public <T extends LexicalContextNode> T push(final T node) {
        if (sp == stack.length) {
            final LexicalContextNode[] newStack = new LexicalContextNode[sp * 2];
            System.arraycopy(stack, 0, newStack, 0, sp);
            stack = newStack;

            final int[] newFlags = new int[sp * 2];
            System.arraycopy(flags, 0, newFlags, 0, sp);
            flags = newFlags;

        }
        stack[sp] = node;
        flags[sp] = 0;

        sp++;

        return node;
    }

    /**
     * Is the context empty?
     * @return true if empty
     */
    public boolean isEmpty() {
        return sp == 0;
    }

    /**
     * The depth of the lexical context
     * @return depth
     */
    public int size() {
        return sp;
    }

    /**
     * Pops the innermost block off the context and all nodes that has been contributed
     * since it was put there
     *
     * @param node the node expected to be popped, used to detect unbalanced pushes/pops
     * @return the node that was popped
     */
    @SuppressWarnings("unchecked")
    public <T extends LexicalContextNode> T pop(final T node) {
        --sp;
        final LexicalContextNode popped = stack[sp];
        stack[sp] = null;
        if (popped instanceof Flags) {
            return (T)((Flags<?>)popped).setFlag(this, flags[sp]);
        }

        return (T)popped;
    }


    /**
     * Return the top element in the context
     * @return the node that was pushed last
     */
    public LexicalContextNode peek() {
        return stack[sp - 1];
    }

    /**
     * Check if a node is in the lexical context
     * @param node node to check for
     * @return true if in the context
     */
    public boolean contains(final LexicalContextNode node) {
        for (int i = 0; i < sp; i++) {
            if (stack[i] == node) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replace a node on the lexical context with a new one. Normally
     * you should try to engineer IR traversals so this isn't needed
     *
     * @param oldNode old node
     * @param newNode new node
     * @return the new node
     */
    public LexicalContextNode replace(final LexicalContextNode oldNode, final LexicalContextNode newNode) {
       //System.err.println("REPLACE old=" + Debug.id(oldNode) + " new=" + Debug.id(newNode));
        for (int i = sp - 1; i >= 0; i--) {
            if (stack[i] == oldNode) {
                assert i == (sp - 1) : "violation of contract - we always expect to find the replacement node on top of the lexical context stack: " + newNode + " has " + stack[i + 1].getClass() + " above it";
                stack[i] = newNode;
                break;
            }
         }
        return newNode;
    }

    /**
     * Returns an iterator over all blocks in the context, with the top block (innermost lexical context) first.
     * @return an iterator over all blocks in the context.
     */
    public Iterator<Block> getBlocks() {
        return new NodeIterator<>(Block.class);
    }

    /**
     * Returns an iterator over all functions in the context, with the top (innermost open) function first.
     * @return an iterator over all functions in the context.
     */
    public Iterator<FunctionNode> getFunctions() {
        return new NodeIterator<>(FunctionNode.class);
    }

    /**
     * Get the parent block for the current lexical context block
     * @return parent block
     */
    public Block getParentBlock() {
        final Iterator<Block> iter = new NodeIterator<>(Block.class, getCurrentFunction());
        iter.next();
        return iter.hasNext() ? iter.next() : null;
    }

    /**
     * Returns an iterator over all ancestors block of the given block, with its parent block first.
     * @param block the block whose ancestors are returned
     * @return an iterator over all ancestors block of the given block.
     */
    public Iterator<Block> getAncestorBlocks(final Block block) {
        final Iterator<Block> iter = getBlocks();
        while (iter.hasNext()) {
            final Block b = iter.next();
            if (block == b) {
                return iter;
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
        final Iterator<Block> iter = getAncestorBlocks(block);
        return new Iterator<Block>() {
            boolean blockReturned = false;
            @Override
            public boolean hasNext() {
                return iter.hasNext() || !blockReturned;
            }
            @Override
            public Block next() {
                if (blockReturned) {
                    return iter.next();
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
     * Get the function for this block. If the block is itself a function
     * this returns identity
     * @param block block for which to get function
     * @return function for block
     */
    public FunctionNode getFunction(final Block block) {
        final Iterator<LexicalContextNode> iter = new NodeIterator<>(LexicalContextNode.class);
        while (iter.hasNext()) {
            final LexicalContextNode next = iter.next();
            if (next == block) {
                while (iter.hasNext()) {
                    final LexicalContextNode next2 = iter.next();
                    if (next2 instanceof FunctionNode) {
                        return (FunctionNode)next2;
                    }
                }
            }
        }
        assert false;
        return null;
    }

    /**
     * Returns the innermost block in the context.
     * @return the innermost block in the context.
     */
    public Block getCurrentBlock() {
        return getBlocks().next();
    }

    /**
     * Returns the innermost function in the context.
     * @return the innermost function in the context.
     */
    public FunctionNode getCurrentFunction() {
        for (int i = sp - 1; i >= 0; i--) {
            if (stack[i] instanceof FunctionNode) {
                return (FunctionNode) stack[i];
            }
        }
        return null;
    }

    /**
     * Get the block in which a symbol is defined
     * @param symbol symbol
     * @return block in which the symbol is defined, assert if no such block in context
     */
    public Block getDefiningBlock(final Symbol symbol) {
        if (symbol.isTemp()) {
            return null;
        }
        final String name = symbol.getName();
        for (final Iterator<Block> it = getBlocks(); it.hasNext();) {
            final Block next = it.next();
            if (next.getExistingSymbol(name) == symbol) {
                return next;
            }
        }
        throw new AssertionError("Couldn't find symbol " + name + " in the context");
    }

    /**
     * Get the function in which a symbol is defined
     * @param symbol symbol
     * @return function node in which this symbol is defined, assert if no such symbol exists in context
     */
    public FunctionNode getDefiningFunction(Symbol symbol) {
        if (symbol.isTemp()) {
            return null;
        }
        final String name = symbol.getName();
        for (final Iterator<LexicalContextNode> iter = new NodeIterator<>(LexicalContextNode.class); iter.hasNext();) {
            final LexicalContextNode next = iter.next();
            if (next instanceof Block && ((Block)next).getExistingSymbol(name) == symbol) {
                while (iter.hasNext()) {
                    final LexicalContextNode next2 = iter.next();
                    if (next2 instanceof FunctionNode) {
                        return ((FunctionNode)next2);
                    }
                }
                throw new AssertionError("Defining block for symbol " + name + " has no function in the context");
            }
        }
        throw new AssertionError("Couldn't find symbol " + name + " in the context");
    }

    /**
     * Is the topmost lexical context element a function body?
     * @return true if function body
     */
    public boolean isFunctionBody() {
        return getParentBlock() == null;
    }

    /**
     * Returns true if the expression defining the function is a callee of a CallNode that should be the second
     * element on the stack, e.g. <code>(function(){})()</code>. That is, if the stack ends with
     * {@code [..., CallNode, FunctionNode]} then {@code callNode.getFunction()} should be equal to
     * {@code functionNode}, and the top of the stack should itself be a variant of {@code functionNode}.
     * @param functionNode the function node being tested
     * @return true if the expression defining the current function is a callee of a call expression.
     */
    public boolean isFunctionDefinedInCurrentCall(FunctionNode functionNode) {
        final LexicalContextNode parent = stack[sp - 2];
        if (parent instanceof CallNode && ((CallNode)parent).getFunction() == functionNode) {
            return true;
        }
        return false;
    }

    /**
     * Get the parent function for a function in the lexical context
     * @param functionNode function for which to get parent
     * @return parent function of functionNode or null if none (e.g. if functionNode is the program)
     */
    public FunctionNode getParentFunction(final FunctionNode functionNode) {
        final Iterator<FunctionNode> iter = new NodeIterator<>(FunctionNode.class);
        while (iter.hasNext()) {
            final FunctionNode next = iter.next();
            if (next == functionNode) {
                return iter.hasNext() ? iter.next() : null;
            }
        }
        assert false;
        return null;
    }

    /**
     * Count the number of with scopes until a given node
     * @param until node to stop counting at, or null if all nodes should be counted
     * @return number of with scopes encountered in the context
     */
    public int getScopeNestingLevelTo(final LexicalContextNode until) {
        //count the number of with nodes until "until" is hit
        int n = 0;
        for (final Iterator<WithNode> iter = new NodeIterator<>(WithNode.class, until); iter.hasNext(); iter.next()) {
            n++;
        }
        return n;
    }

    private BreakableNode getBreakable() {
        for (final NodeIterator<BreakableNode> iter = new NodeIterator<>(BreakableNode.class, getCurrentFunction()); iter.hasNext(); ) {
            final BreakableNode next = iter.next();
            if (next.isBreakableWithoutLabel()) {
                return next;
            }
        }
        return null;
    }

    /**
     * Check whether the lexical context is currently inside a loop
     * @return true if inside a loop
     */
    public boolean inLoop() {
        return getCurrentLoop() != null;
    }

    /**
     * Returns the loop header of the current loop, or null if not inside a loop
     * @return loop header
     */
    public LoopNode getCurrentLoop() {
        final Iterator<LoopNode> iter = new NodeIterator<>(LoopNode.class, getCurrentFunction());
        return iter.hasNext() ? iter.next() : null;
    }

    /**
     * Find the breakable node corresponding to this label.
     * @param label label to search for, if null the closest breakable node will be returned unconditionally, e.g. a while loop with no label
     * @return closest breakable node
     */
    public BreakableNode getBreakable(final IdentNode label) {
        if (label != null) {
            final LabelNode foundLabel = findLabel(label.getName());
            if (foundLabel != null) {
                // iterate to the nearest breakable to the foundLabel
                BreakableNode breakable = null;
                for (final NodeIterator<BreakableNode> iter = new NodeIterator<>(BreakableNode.class, foundLabel); iter.hasNext(); ) {
                    breakable = iter.next();
                }
                return breakable;
            }
            return null;
        }
        return getBreakable();
    }

    private LoopNode getContinueTo() {
        return getCurrentLoop();
    }

    /**
     * Find the continue target node corresponding to this label.
     * @param label label to search for, if null the closest loop node will be returned unconditionally, e.g. a while loop with no label
     * @return closest continue target node
     */
    public LoopNode getContinueTo(final IdentNode label) {
        if (label != null) {
            final LabelNode foundLabel = findLabel(label.getName());
            if (foundLabel != null) {
                // iterate to the nearest loop to the foundLabel
                LoopNode loop = null;
                for (final NodeIterator<LoopNode> iter = new NodeIterator<>(LoopNode.class, foundLabel); iter.hasNext(); ) {
                    loop = iter.next();
                }
                return loop;
            }
            return null;
        }
        return getContinueTo();
    }

    /**
     * Check the lexical context for a given label node by name
     * @param name name of the label
     * @return LabelNode if found, null otherwise
     */
    public LabelNode findLabel(final String name) {
        for (final Iterator<LabelNode> iter = new NodeIterator<>(LabelNode.class, getCurrentFunction()); iter.hasNext(); ) {
            final LabelNode next = iter.next();
            if (next.getLabel().getName().equals(name)) {
                return next;
            }
        }
        return null;
    }

    /**
     * Checks whether a given label is a jump destination that lies outside a given
     * split node
     * @param splitNode the split node
     * @param label     the label
     * @return true if label resides outside the split node
     */
    public boolean isExternalTarget(final SplitNode splitNode, final Label label) {
        boolean targetFound = false;
        for (int i = sp - 1; i >= 0; i--) {
            final LexicalContextNode next = stack[i];
            if (next == splitNode) {
                return !targetFound;
            }

            if (next instanceof BreakableNode) {
                for (final Label l : ((BreakableNode)next).getLabels()) {
                    if (l == label) {
                        targetFound = true;
                        break;
                    }
                }
            }
        }
        assert false : label + " was expected in lexical context " + LexicalContext.this + " but wasn't";
        return false;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer();
        sb.append("[ ");
        for (int i = 0; i < sp; i++) {
            final Object node = stack[i];
            sb.append(node.getClass().getSimpleName());
            sb.append('@');
            sb.append(Debug.id(node));
            sb.append(':');
            if (node instanceof FunctionNode) {
                final FunctionNode fn = (FunctionNode)node;
                final Source source = fn.getSource();
                String src = source.toString();
                if (src.contains(File.pathSeparator)) {
                    src = src.substring(src.lastIndexOf(File.pathSeparator));
                }
                src += ' ';
                src += fn.getLineNumber();
                sb.append(src);
            }
            sb.append(' ');
        }
        sb.append(" ==> ]");
        return sb.toString();
    }

    private class NodeIterator <T extends LexicalContextNode> implements Iterator<T> {
        private int index;
        private T next;
        private final Class<T> clazz;
        private LexicalContextNode until;

        NodeIterator(final Class<T> clazz) {
            this(clazz, null);
        }

        NodeIterator(final Class<T> clazz, final LexicalContextNode until) {
            this.index = sp - 1;
            this.clazz = clazz;
            this.until = until;
            this.next  = findNext();
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public T next() {
            if (next == null) {
                throw new NoSuchElementException();
            }
            T lnext = next;
            next = findNext();
            return lnext;
        }

        private T findNext() {
            for (int i = index; i >= 0; i--) {
                final Object node = stack[i];
                if (node == until) {
                    return null;
                }
                if (clazz.isAssignableFrom(node.getClass())) {
                    index = i - 1;
                    return clazz.cast(node);
                }
            }
            return null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}

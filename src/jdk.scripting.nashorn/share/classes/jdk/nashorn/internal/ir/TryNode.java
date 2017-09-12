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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation of a TRY statement.
 */
@Immutable
public final class TryNode extends LexicalContextStatement implements JoinPredecessor {
    private static final long serialVersionUID = 1L;

    /** Try statements. */
    private final Block body;

    /** List of catch clauses. */
    private final List<Block> catchBlocks;

    /** Finally clause. */
    private final Block finallyBody;

    /**
     * List of inlined finally blocks. The structure of every inlined finally is:
     * Block(LabelNode(label, Block(finally-statements, (JumpStatement|ReturnNode)?))).
     * That is, the block has a single LabelNode statement with the label and a block containing the
     * statements of the inlined finally block with the jump or return statement appended (if the finally
     * block was not terminal; the original jump/return is simply ignored if the finally block itself
     * terminates). The reason for this somewhat strange arrangement is that we didn't want to create a
     * separate class for the (label, BlockStatement pair) but rather reused the already available LabelNode.
     * However, if we simply used List&lt;LabelNode&gt; without wrapping the label nodes in an additional Block,
     * that would've thrown off visitors relying on BlockLexicalContext -- same reason why we never use
     * Statement as the type of bodies of e.g. IfNode, WhileNode etc. but rather blockify them even when they're
     * single statements.
     */
    private final List<Block> inlinedFinallies;

    /** Exception symbol. */
    private final Symbol exception;

    private final LocalVariableConversion conversion;

    /**
     * Constructor
     *
     * @param lineNumber  lineNumber
     * @param token       token
     * @param finish      finish
     * @param body        try node body
     * @param catchBlocks list of catch blocks in order
     * @param finallyBody body of finally block or null if none
     */
    public TryNode(final int lineNumber, final long token, final int finish, final Block body, final List<Block> catchBlocks, final Block finallyBody) {
        super(lineNumber, token, finish);
        this.body        = body;
        this.catchBlocks = catchBlocks;
        this.finallyBody = finallyBody;
        this.conversion  = null;
        this.inlinedFinallies = Collections.emptyList();
        this.exception = null;
    }

    private TryNode(final TryNode tryNode, final Block body, final List<Block> catchBlocks, final Block finallyBody, final LocalVariableConversion conversion, final List<Block> inlinedFinallies, final Symbol exception) {
        super(tryNode);
        this.body        = body;
        this.catchBlocks = catchBlocks;
        this.finallyBody = finallyBody;
        this.conversion  = conversion;
        this.inlinedFinallies = inlinedFinallies;
        this.exception = exception;
    }

    @Override
    public Node ensureUniqueLabels(final LexicalContext lc) {
        //try nodes are never in lex context
        return new TryNode(this, body, catchBlocks, finallyBody, conversion, inlinedFinallies, exception);
    }

    @Override
    public boolean isTerminal() {
        if (body.isTerminal()) {
            for (final Block catchBlock : getCatchBlocks()) {
                if (!catchBlock.isTerminal()) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Assist in IR navigation.
     * @param visitor IR navigating visitor.
     */
    @Override
    public Node accept(final LexicalContext lc, final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterTryNode(this)) {
            // Need to do finallybody first for termination analysis. TODO still necessary?
            final Block newFinallyBody = finallyBody == null ? null : (Block)finallyBody.accept(visitor);
            final Block newBody        = (Block)body.accept(visitor);
            return visitor.leaveTryNode(
                setBody(lc, newBody).
                setFinallyBody(lc, newFinallyBody).
                setCatchBlocks(lc, Node.accept(visitor, catchBlocks)).
                setInlinedFinallies(lc, Node.accept(visitor, inlinedFinallies)));
        }

        return this;
    }

    @Override
    public void toString(final StringBuilder sb, final boolean printType) {
        sb.append("try ");
    }

    /**
     * Get the body for this try block
     * @return body
     */
    public Block getBody() {
        return body;
    }

    /**
     * Reset the body of this try block
     * @param lc current lexical context
     * @param body new body
     * @return new TryNode or same if unchanged
     */
    public TryNode setBody(final LexicalContext lc, final Block body) {
        if (this.body == body) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new TryNode(this,  body, catchBlocks, finallyBody, conversion, inlinedFinallies, exception));
    }

    /**
     * Get the catches for this try block
     * @return a list of catch nodes
     */
    public List<CatchNode> getCatches() {
        final List<CatchNode> catches = new ArrayList<>(catchBlocks.size());
        for (final Block catchBlock : catchBlocks) {
            catches.add(getCatchNodeFromBlock(catchBlock));
        }
        return Collections.unmodifiableList(catches);
    }

    private static CatchNode getCatchNodeFromBlock(final Block catchBlock) {
        return (CatchNode)catchBlock.getStatements().get(0);
    }

    /**
     * Get the catch blocks for this try block
     * @return a list of blocks
     */
    public List<Block> getCatchBlocks() {
        return Collections.unmodifiableList(catchBlocks);
    }

    /**
     * Set the catch blocks of this try
     * @param lc current lexical context
     * @param catchBlocks list of catch blocks
     * @return new TryNode or same if unchanged
     */
    public TryNode setCatchBlocks(final LexicalContext lc, final List<Block> catchBlocks) {
        if (this.catchBlocks == catchBlocks) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new TryNode(this, body, catchBlocks, finallyBody, conversion, inlinedFinallies, exception));
    }

    /**
     * Get the exception symbol for this try block
     * @return a symbol for the compiler to store the exception in
     */
    public Symbol getException() {
        return exception;
    }
    /**
     * Set the exception symbol for this try block
     * @param lc lexical context
     * @param exception a symbol for the compiler to store the exception in
     * @return new TryNode or same if unchanged
     */
    public TryNode setException(final LexicalContext lc, final Symbol exception) {
        if (this.exception == exception) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new TryNode(this, body, catchBlocks, finallyBody, conversion, inlinedFinallies, exception));
    }

    /**
     * Get the body of the finally clause for this try
     * @return finally body, or null if no finally
     */
    public Block getFinallyBody() {
        return finallyBody;
    }

    /**
     * Get the inlined finally block with the given label name. This returns the actual finally block in the
     * {@link LabelNode}, not the outer wrapper block for the {@link LabelNode}.
     * @param labelName the name of the inlined finally's label
     * @return the requested finally block, or null if no finally block's label matches the name.
     */
    public Block getInlinedFinally(final String labelName) {
        for(final Block inlinedFinally: inlinedFinallies) {
            final LabelNode labelNode = getInlinedFinallyLabelNode(inlinedFinally);
            if (labelNode.getLabelName().equals(labelName)) {
                return labelNode.getBody();
            }
        }
        return null;
    }

    private static LabelNode getInlinedFinallyLabelNode(final Block inlinedFinally) {
        return (LabelNode)inlinedFinally.getStatements().get(0);
    }

    /**
     * Given an outer wrapper block for the {@link LabelNode} as returned by {@link #getInlinedFinallies()},
     * returns its actual inlined finally block.
     * @param inlinedFinally the outer block for inlined finally, as returned as an element of
     * {@link #getInlinedFinallies()}.
     * @return the block contained in the {@link LabelNode} contained in the passed block.
     */
    public static Block getLabelledInlinedFinallyBlock(final Block inlinedFinally) {
        return getInlinedFinallyLabelNode(inlinedFinally).getBody();
    }

    /**
     * Returns a list of inlined finally blocks. Note that this returns a list of {@link Block}s such that each one of
     * them has a single {@link LabelNode}, which in turn contains the label name for the finally block and the
     * actual finally block. To safely extract the actual finally block, use
     * {@link #getLabelledInlinedFinallyBlock(Block)}.
     * @return a list of inlined finally blocks.
     */
    public List<Block> getInlinedFinallies() {
        return Collections.unmodifiableList(inlinedFinallies);
    }

    /**
     * Set the finally body of this try
     * @param lc current lexical context
     * @param finallyBody new finally body
     * @return new TryNode or same if unchanged
     */
    public TryNode setFinallyBody(final LexicalContext lc, final Block finallyBody) {
        if (this.finallyBody == finallyBody) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new TryNode(this, body, catchBlocks, finallyBody, conversion, inlinedFinallies, exception));
    }

    /**
     * Set the inlined finally blocks of this try. Each element should be a block with a single statement that is a
     * {@link LabelNode} with a unique label, and the block within the label node should contain the actual inlined
     * finally block.
     * @param lc current lexical context
     * @param inlinedFinallies list of inlined finally blocks
     * @return new TryNode or same if unchanged
     */
    public TryNode setInlinedFinallies(final LexicalContext lc, final List<Block> inlinedFinallies) {
        if (this.inlinedFinallies == inlinedFinallies) {
            return this;
        }
        assert checkInlinedFinallies(inlinedFinallies);
        return Node.replaceInLexicalContext(lc, this, new TryNode(this, body, catchBlocks, finallyBody, conversion, inlinedFinallies, exception));
    }

    private static boolean checkInlinedFinallies(final List<Block> inlinedFinallies) {
        if (!inlinedFinallies.isEmpty()) {
            final Set<String> labels = new HashSet<>();
            for (final Block inlinedFinally : inlinedFinallies) {
                final List<Statement> stmts = inlinedFinally.getStatements();
                assert stmts.size() == 1;
                final LabelNode ln = getInlinedFinallyLabelNode(inlinedFinally);
                assert labels.add(ln.getLabelName()); // unique label
            }
        }
        return true;
    }

    @Override
    public JoinPredecessor setLocalVariableConversion(final LexicalContext lc, final LocalVariableConversion conversion) {
        if(this.conversion == conversion) {
            return this;
        }
        return new TryNode(this, body, catchBlocks, finallyBody, conversion, inlinedFinallies, exception);
    }

    @Override
    public LocalVariableConversion getLocalVariableConversion() {
        return conversion;
    }
}

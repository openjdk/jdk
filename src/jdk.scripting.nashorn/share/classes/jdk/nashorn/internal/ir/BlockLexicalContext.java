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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * This is a subclass of lexical context used for filling
 * blocks (and function nodes) with statements. When popping
 * a block from the lexical context, any statements that have
 * been generated in it are committed to the block. This saves
 * unnecessary object mutations and lexical context replacement
 */
public class BlockLexicalContext extends LexicalContext {
    /** statement stack, each block on the lexical context maintains one of these, which is
     *  committed to the block on pop */
    private final Deque<List<Statement>> sstack = new ArrayDeque<>();

    /** Last non debug statement emitted in this context */
    protected Statement lastStatement;

    @Override
    public <T extends LexicalContextNode> T push(final T node) {
        final T pushed = super.push(node);
        if (node instanceof Block) {
            sstack.push(new ArrayList<Statement>());
        }
        return pushed;
    }

    /**
     * Get the statement list from the stack, possibly filtered
     * @return statement list
     */
    protected List<Statement> popStatements() {
        return sstack.pop();
    }

    /**
     * Override this method to perform some additional processing on the block after its statements have been set. By
     * default does nothing and returns the original block.
     * @param block the block to operate on
     * @return a modified block.
     */
    protected Block afterSetStatements(final Block block) {
        return block;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Node> T pop(final T node) {
        T expected = node;
        if (node instanceof Block) {
            final List<Statement> newStatements = popStatements();
            expected = (T)((Block)node).setStatements(this, newStatements);
            expected = (T)afterSetStatements((Block)expected);
            if (!sstack.isEmpty()) {
                lastStatement = lastStatement(sstack.peek());
            }
        }
        return super.pop(expected);
    }

    /**
     * Append a statement to the block being generated
     * @param statement statement to add
     */
    public void appendStatement(final Statement statement) {
        assert statement != null;
        sstack.peek().add(statement);
        lastStatement = statement;
    }

    /**
     * Prepend a statement to the block being generated
     * @param statement statement to prepend
     * @return the prepended statement
     */
    public Node prependStatement(final Statement statement) {
        assert statement != null;
        sstack.peek().add(0, statement);
        return statement;
    }

    /**
     * Prepend a list of statement to the block being generated
     * @param statements a list of statements to prepend
     */
    public void prependStatements(final List<Statement> statements) {
        assert statements != null;
        sstack.peek().addAll(0, statements);
    }


    /**
     * Get the last statement that was emitted into a block
     * @return the last statement emitted
     */
    public Statement getLastStatement() {
        return lastStatement;
    }

    private static Statement lastStatement(final List<Statement> statements) {
        final int s = statements.size();
        return s == 0 ? null : statements.get(s - 1);
    }
}

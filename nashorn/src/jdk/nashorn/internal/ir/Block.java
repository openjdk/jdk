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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import jdk.nashorn.internal.codegen.Frame;
import jdk.nashorn.internal.codegen.Label;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for a list of statements and functions. All provides the
 * basis for script body.
 */
public class Block extends Node {
    /** List of statements */
    protected List<Node> statements;

    /** Symbol table. */
    protected final HashMap<String, Symbol> symbols;

    /** Variable frame. */
    protected Frame frame;

    /** Entry label. */
    protected final Label entryLabel;

    /** Break label. */
    protected final Label breakLabel;

    /** Does the block/function need a new scope? */
    protected boolean needsScope;

    /**
     * Constructor
     *
     * @param source   source code
     * @param token    token
     * @param finish   finish
     */
    public Block(final Source source, final long token, final int finish) {
        super(source, token, finish);

        this.statements = new ArrayList<>();
        this.symbols    = new HashMap<>();
        this.entryLabel = new Label("block_entry");
        this.breakLabel = new Label("block_break");
    }

    /**
     * Internal copy constructor
     *
     * @param block the source block
     * @param cs    the copy state
     */
    protected Block(final Block block, final CopyState cs) {
        super(block);

        this.statements = new ArrayList<>();
        for (final Node statement : block.getStatements()) {
            statements.add(cs.existingOrCopy(statement));
        }
        this.symbols    = new HashMap<>();
        this.frame      = block.frame == null ? null : block.frame.copy();
        this.entryLabel = new Label(block.entryLabel);
        this.breakLabel = new Label(block.breakLabel);

        assert block.symbols.isEmpty() : "must not clone with symbols";
    }

    @Override
    protected Node copy(final CopyState cs) {
        return new Block(this, cs);
    }

    /**
     * Add a new statement to the statement list.
     *
     * @param statement Statement node to add.
     */
    public void addStatement(final Node statement) {
        if (statement != null) {
            statements.add(statement);
            if (getFinish() < statement.getFinish()) {
                setFinish(statement.getFinish());
            }
        }
    }

    /**
     * Prepend statements to the statement list
     *
     * @param prepended statement to add
     */
    public void prependStatements(final List<Node> prepended) {
        statements.addAll(0, prepended);
    }

    /**
     * Add a list of statements to the statement list.
     *
     * @param statementList Statement nodes to add.
     */
    public void addStatements(final List<Node> statementList) {
        statements.addAll(statementList);
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     * @return new or same node
     */
    @Override
    public Node accept(final NodeVisitor visitor) {
        final Block saveBlock = visitor.getCurrentBlock();
        visitor.setCurrentBlock(this);

        try {
            // Ignore parent to avoid recursion.

            if (visitor.enterBlock(this) != null) {
                visitStatements(visitor);
                return visitor.leaveBlock(this);
            }
        } finally {
            visitor.setCurrentBlock(saveBlock);
        }

        return this;
    }

    /**
     * Get an iterator for all the symbols defined in this block
     * @return symbol iterator
     */
    public Iterator<Symbol> symbolIterator() {
        return symbols.values().iterator();
    }

    /**
     * Retrieves an existing symbol defined in the current block.
     * @param name the name of the symbol
     * @return an existing symbol with the specified name defined in the current block, or null if this block doesn't
     * define a symbol with this name.
     */
    public Symbol getExistingSymbol(final String name) {
        return symbols.get(name);
    }

    /**
     * Test if this block represents a <tt>catch</tt> block in a <tt>try</tt> statement.
     * This is used by the Splitter as catch blocks are not be subject to splitting.
     *
     * @return true if this block represents a catch block in a try statement.
     */
    public boolean isCatchBlock() {
        return statements.size() == 1 && statements.get(0) instanceof CatchNode;
    }

    @Override
    public void toString(final StringBuilder sb) {
        for (final Node statement : statements) {
            statement.toString(sb);
            sb.append(';');
        }
    }

    /**
     * Print symbols in block in alphabetical order, sorted on name
     * Used for debugging, see the --print-symbols flag
     *
     * @param stream print writer to output symbols to
     *
     * @return true if symbols were found
     */
    public boolean printSymbols(final PrintWriter stream) {
        final List<Symbol> values = new ArrayList<>(symbols.values());

        Collections.sort(values, new Comparator<Symbol>() {
            @Override
            public int compare(final Symbol s0, final Symbol s1) {
                return s0.getName().compareTo(s1.getName());
            }
        });

        for (final Symbol symbol : values) {
            symbol.print(stream);
        }

        return !values.isEmpty();
    }

    /**
     * Get the break label for this block
     * @return the break label
     */
    public Label getBreakLabel() {
        return breakLabel;
    }

    /**
     * Get the entry label for this block
     * @return the entry label
     */
    public Label getEntryLabel() {
        return entryLabel;
    }

    /**
     * Get the frame for this block
     * @return the frame
     */
    public Frame getFrame() {
        return frame;
    }

    /**
     * Reset the frame for this block
     *
     * @param frame  the new frame
     */
    public void setFrame(final Frame frame) {
        this.frame = frame;
    }

    /**
     * Get the list of statements in this block
     *
     * @return a list of statements
     */
    public List<Node> getStatements() {
        return Collections.unmodifiableList(statements);
    }

    /**
     * Applies the specified visitor to all statements in the block.
     * @param visitor the visitor.
     */
    public void visitStatements(NodeVisitor visitor) {
        for (ListIterator<Node> stmts = statements.listIterator(); stmts.hasNext();) {
            stmts.set(stmts.next().accept(visitor));
        }
    }
    /**
     * Reset the statement list for this block
     *
     * @param statements  new statement list
     */
    public void setStatements(final List<Node> statements) {
        this.statements = statements;
    }

    /**
     * Add or overwrite an existing symbol in the block
     *
     * @param name   name of symbol
     * @param symbol symbol
     */
    public void putSymbol(final String name, final Symbol symbol) {
        symbols.put(name, symbol);
    }

    /**
     * Check whether scope is necessary for this Block
     *
     * @return true if this function needs a scope
     */
    public boolean needsScope() {
        return needsScope;
    }

    /**
     * Set the needs scope flag.
     */
    public void setNeedsScope() {
        needsScope = true;
    }

    /**
     * Marks this block as using a specified scoped symbol. The block and its parent blocks up to but not
     * including the block defining the symbol will be marked as needing parent scope. The block defining the symbol
     * will be marked as one that needs to have its own scope.
     * @param symbol the symbol being used.
     * @param ancestors the iterator over block's containing lexical context
     */
    public void setUsesScopeSymbol(final Symbol symbol, Iterator<Block> ancestors) {
        if(symbol.getBlock() == this) {
            setNeedsScope();
        } else {
            setUsesParentScopeSymbol(symbol, ancestors);
        }
    }

    /**
     * Invoked when this block uses a scope symbol defined in one of its ancestors.
     * @param symbol the scope symbol being used
     * @param ancestors iterator over ancestor blocks
     */
    void setUsesParentScopeSymbol(final Symbol symbol, Iterator<Block> ancestors) {
        if(ancestors.hasNext()) {
            ancestors.next().setUsesScopeSymbol(symbol, ancestors);
        }
    }
}

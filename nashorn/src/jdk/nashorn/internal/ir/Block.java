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

import static jdk.nashorn.internal.ir.Symbol.IS_GLOBAL;
import static jdk.nashorn.internal.ir.Symbol.IS_INTERNAL;
import static jdk.nashorn.internal.ir.Symbol.IS_LET;
import static jdk.nashorn.internal.ir.Symbol.IS_PARAM;
import static jdk.nashorn.internal.ir.Symbol.IS_SCOPE;
import static jdk.nashorn.internal.ir.Symbol.IS_VAR;
import static jdk.nashorn.internal.ir.Symbol.KINDMASK;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import jdk.nashorn.internal.codegen.Frame;
import jdk.nashorn.internal.codegen.MethodEmitter.Label;
import jdk.nashorn.internal.ir.annotations.Ignore;
import jdk.nashorn.internal.ir.annotations.ParentNode;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.runtime.Source;

/**
 * IR representation for a list of statements and functions. All provides the
 * basis for script body.
 *
 */
public class Block extends Node {
    /** Parent context */
    @ParentNode @Ignore
    private Block parent;

    /** Owning function. */
    @Ignore //don't print it, it is apparent in the tree
    protected FunctionNode function;

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
     * @param parent   reference to parent block
     * @param function function node this block is in
     */
    public Block(final Source source, final long token, final int finish, final Block parent, final FunctionNode function) {
        super(source, token, finish);

        this.parent     = parent;
        this.function   = function;
        this.statements = new ArrayList<>();
        this.symbols    = new HashMap<>();
        this.frame      = null;
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

        parent     = block.parent;
        function   = block.function;
        statements = new ArrayList<>();
        for (final Node statement : block.getStatements()) {
            statements.add(cs.existingOrCopy(statement));
        }
        symbols    = new HashMap<>();
        frame      = block.frame == null ? null : block.frame.copy();
        entryLabel = new Label(block.entryLabel);
        breakLabel = new Label(block.breakLabel);

        assert block.symbols.isEmpty() : "must not clone with symbols";
    }

    @Override
    protected Node copy(final CopyState cs) {
        return fixBlockChain(new Block(this, cs));
    }

    /**
     * Whenever a clone that contains a hierarchy of blocks is created,
     * this function has to be called to ensure that the parents point
     * to the correct parent blocks or two different ASTs would not
     * be completely separated.
     *
     * @return the argument
     */
    static Block fixBlockChain(final Block root) {
        root.accept(new NodeVisitor() {
            private Block        parent   = root.getParent();
            private final FunctionNode function = root.getFunction();

            @Override
            public Node enter(final Block block) {
                assert block.getFunction() == function;
                block.setParent(parent);
                parent = block;

                return block;
            }

            @Override
            public Node leave(final Block block) {
                parent = block.getParent();

                return block;
            }

            @Override
            public Node enter(final FunctionNode functionNode) {
                assert functionNode.getFunction() == function;

                return enter((Block)functionNode);
            }

            @Override
            public Node leave(final FunctionNode functionNode) {
                assert functionNode.getFunction() == function;

                return leave((Block)functionNode);
            }

        });

        return root;
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
     * Prepend a statement to the statement list
     *
     * @param statement Statement node to add
     */
    public void prependStatement(final Node statement) {
        if (statement != null) {
            final List<Node> newStatements = new ArrayList<>();
            newStatements.add(statement);
            newStatements.addAll(statements);
            setStatements(newStatements);
        }
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
     * Add a new function to the function list.
     *
     * @param functionNode Function node to add.
     */
    public void addFunction(final FunctionNode functionNode) {
        assert parent != null : "Parent context missing.";

        parent.addFunction(functionNode);
    }

    /**
     * Add a list of functions to the function list.
     *
     * @param functionNodes Function nodes to add.
     */
    public void addFunctions(final List<FunctionNode> functionNodes) {
        assert parent != null : "Parent context missing.";

        parent.addFunctions(functionNodes);
    }

    /**
     * Set the function list to a new one
     *
     * @param functionNodes the nodes to set
     */
    public void setFunctions(final List<FunctionNode> functionNodes) {
        assert parent != null : "Parent context missing.";

        parent.setFunctions(functionNodes);
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

            if (visitor.enter(this) != null) {
                for (int i = 0, count = statements.size(); i < count; i++) {
                    final Node statement = statements.get(i);
                    statements.set(i, statement.accept(visitor));
                }

                return visitor.leave(this);
            }
        } finally {
            visitor.setCurrentBlock(saveBlock);
        }

        return this;
    }

    /**
     * Search for symbol.
     *
     * @param name Symbol name.
     *
     * @return Found symbol or null if not found.
     */
    public Symbol findSymbol(final String name) {
        // Search up block chain to locate symbol.

        for (Block block = this; block != null; block = block.getParent()) {
            // Find name.
            final Symbol symbol = block.symbols.get(name);
            // If found then we are good.
            if (symbol != null) {
                return symbol;
            }
        }
        return null;
    }

    /**
     * Search for symbol in current function.
     *
     * @param name Symbol name.
     *
     * @return Found symbol or null if not found.
     */
    public Symbol findLocalSymbol(final String name) {
        // Search up block chain to locate symbol.
        for (Block block = this; block != null; block = block.getParent()) {
            // Find name.
            final Symbol symbol = block.symbols.get(name);
            // If found then we are good.
            if (symbol != null) {
                return symbol;
            }

            // If searched function then we are done.
            if (block == block.function) {
                break;
            }
        }

        // Not found.
        return null;
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

    /**
     * Test to see if a symbol is local to the function.
     *
     * @param symbol Symbol to test.
     * @return True if a local symbol.
     */
    public boolean isLocal(final Symbol symbol) {
        // some temp symbols have no block, so can be assumed local
        final Block block = symbol.getBlock();
        return block == null || block.getFunction() == function;
    }

    /**
     * Declare the definition of a new symbol.
     *
     * @param name         Name of symbol.
     * @param symbolFlags  Symbol flags.
     * @param node         Defining Node.
     *
     * @return Symbol for given name or null for redefinition.
     */
    public Symbol defineSymbol(final String name, final int symbolFlags, final Node node) {
        int    flags  = symbolFlags;
        Symbol symbol = findSymbol(name); // Locate symbol.

        if ((flags & KINDMASK) == IS_GLOBAL) {
            flags |= IS_SCOPE;
        }

        if (symbol != null) {
            // Symbol was already defined. Check if it needs to be redefined.
            if ((flags & KINDMASK) == IS_PARAM) {
                if (!function.isLocal(symbol)) {
                    // Not defined in this function. Create a new definition.
                    symbol = null;
                } else if (symbol.isParam()) {
                    // Duplicate parameter. Null return will force an error.
                    assert false : "duplicate parameter";
                    return null;
                }
            } else if ((flags & KINDMASK) == IS_VAR) {
                if ((flags & IS_INTERNAL) == IS_INTERNAL || (flags & Symbol.IS_LET) == Symbol.IS_LET) {
                    assert !((flags & IS_LET) == IS_LET && symbol.getBlock() == this) : "duplicate let variable in block";
                    // Always create a new definition.
                    symbol = null;
                } else {
                    // Not defined in this function. Create a new definition.
                    if (!function.isLocal(symbol) || symbol.less(IS_VAR)) {
                        symbol = null;
                    }
                }
            }
        }

        if (symbol == null) {
            // If not found, then create a new one.
            Block symbolBlock;

            // Determine where to create it.
            if ((flags & Symbol.KINDMASK) == IS_VAR && ((flags & IS_INTERNAL) == IS_INTERNAL || (flags & IS_LET) == IS_LET)) {
                symbolBlock = this;
            } else {
                symbolBlock = getFunction();
            }

            // Create and add to appropriate block.
            symbol = new Symbol(name, flags, node, symbolBlock);
            symbolBlock.putSymbol(name, symbol);

            if ((flags & Symbol.KINDMASK) != IS_GLOBAL) {
                symbolBlock.getFrame().addSymbol(symbol);
                symbol.setNeedsSlot(true);
            }
        } else if (symbol.less(flags)) {
            symbol.setFlags(flags);
        }

        if (node != null) {
            node.setSymbol(symbol);
        }

        return symbol;
    }

    /**
     * Declare the use of a symbol.
     *
     * @param name Name of symbol.
     * @param node Using node
     *
     * @return Symbol for given name.
     */
    public Symbol useSymbol(final String name, final Node node) {
        Symbol symbol = findSymbol(name);

        if (symbol == null) {
            // If not found, declare as a free var.
            symbol = defineSymbol(name, IS_GLOBAL, node);
        } else {
            node.setSymbol(symbol);
        }

        return symbol;
    }

    /**
     * Add parent name to the builder.
     *
     * @param sb String bulder.
     */
    public void addParentName(final StringBuilder sb) {
        if (parent != null) {
            parent.addParentName(sb);
        }
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
     * Get the FunctionNode for this block, i.e. the function it
     * belongs to
     *
     * @return the function node
     */
    public FunctionNode getFunction() {
        return function;
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
     * Get the parent block
     *
     * @return parent block, or null if none exists
     */
    public Block getParent() {
        return parent;
    }

    /**
     * Set the parent block
     *
     * @param parent the new parent block
     */
    public void setParent(final Block parent) {
        this.parent = parent;
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

}

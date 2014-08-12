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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jdk.nashorn.internal.codegen.Label;
import jdk.nashorn.internal.ir.annotations.Immutable;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * IR representation for a list of statements.
 */
@Immutable
public class Block extends Node implements BreakableNode, Terminal, Flags<Block> {
    /** List of statements */
    protected final List<Statement> statements;

    /** Symbol table - keys must be returned in the order they were put in. */
    protected final Map<String, Symbol> symbols;

    /** Entry label. */
    private final Label entryLabel;

    /** Break label. */
    private final Label breakLabel;

    /** Does the block/function need a new scope? */
    protected final int flags;

    /**
     * @see JoinPredecessor
     */
    private final LocalVariableConversion conversion;

    /** Flag indicating that this block needs scope */
    public static final int NEEDS_SCOPE = 1 << 0;

    /**
     * Is this block tagged as terminal based on its contents
     * (usually the last statement)
     */
    public static final int IS_TERMINAL = 1 << 2;

    /**
     * Is this block the eager global scope - i.e. the original program. This isn't true for the
     * outermost level of recompiles
     */
    public static final int IS_GLOBAL_SCOPE = 1 << 3;

    /**
     * Constructor
     *
     * @param token      token
     * @param finish     finish
     * @param statements statements
     */
    public Block(final long token, final int finish, final Statement... statements) {
        super(token, finish);

        this.statements = Arrays.asList(statements);
        this.symbols    = new LinkedHashMap<>();
        this.entryLabel = new Label("block_entry");
        this.breakLabel = new Label("block_break");
        final int len = statements.length;
        this.flags = len > 0 && statements[len - 1].hasTerminalFlags() ? IS_TERMINAL : 0;
        this.conversion = null;
    }

    /**
     * Constructor
     *
     * @param token      token
     * @param finish     finish
     * @param statements statements
     */
    public Block(final long token, final int finish, final List<Statement> statements) {
        this(token, finish, statements.toArray(new Statement[statements.size()]));
    }

    private Block(final Block block, final int finish, final List<Statement> statements, final int flags, final Map<String, Symbol> symbols, final LocalVariableConversion conversion) {
        super(block);
        this.statements = statements;
        this.flags      = flags;
        this.symbols    = new LinkedHashMap<>(symbols); //todo - symbols have no dependencies on any IR node and can as far as we understand it be shallow copied now
        this.entryLabel = new Label(block.entryLabel);
        this.breakLabel = new Label(block.breakLabel);
        this.finish     = finish;
        this.conversion = conversion;
    }

    /**
     * Is this block the outermost eager global scope - i.e. the primordial program?
     * Used for global anchor point for scope depth computation for recompilation code
     * @return true if outermost eager global scope
     */
    public boolean isGlobalScope() {
        return getFlag(IS_GLOBAL_SCOPE);
    }

    /**
     * Clear the symbols in the block.
     * TODO: make this immutable.
     */
    public void clearSymbols() {
        symbols.clear();
    }

    @Override
    public Node ensureUniqueLabels(final LexicalContext lc) {
        return Node.replaceInLexicalContext(lc, this, new Block(this, finish, statements, flags, symbols, conversion));
    }

    /**
     * Assist in IR navigation.
     *
     * @param visitor IR navigating visitor.
     * @return new or same node
     */
    @Override
    public Node accept(final LexicalContext lc, final NodeVisitor<? extends LexicalContext> visitor) {
        if (visitor.enterBlock(this)) {
            return visitor.leaveBlock(setStatements(lc, Node.accept(visitor, statements)));
        }

        return this;
    }

    /**
     * Get a copy of the list for all the symbols defined in this block
     * @return symbol iterator
     */
    public List<Symbol> getSymbols() {
        return Collections.unmodifiableList(new ArrayList<>(symbols.values()));
    }

    /**
     * Retrieves an existing symbol defined in the current block.
     * @param name the name of the symbol
     * @return an existing symbol with the specified name defined in the current block, or null if this block doesn't
     * define a symbol with this name.T
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
    public void toString(final StringBuilder sb, final boolean printType) {
        for (final Node statement : statements) {
            statement.toString(sb, printType);
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
     * Tag block as terminal or non terminal
     * @param lc          lexical context
     * @param isTerminal is block terminal
     * @return same block, or new if flag changed
     */
    public Block setIsTerminal(final LexicalContext lc, final boolean isTerminal) {
        return isTerminal ? setFlag(lc, IS_TERMINAL) : clearFlag(lc, IS_TERMINAL);
    }

    @Override
    public int getFlags() {
        return flags;
    }

    /**
     * Is this a terminal block, i.e. does it end control flow like ending with a throw or return?
     *
     * @return true if this node statement is terminal
     */
    @Override
    public boolean isTerminal() {
        return getFlag(IS_TERMINAL);
    }

    /**
     * Get the entry label for this block
     * @return the entry label
     */
    public Label getEntryLabel() {
        return entryLabel;
    }

    @Override
    public Label getBreakLabel() {
        return breakLabel;
    }

    @Override
    public Block setLocalVariableConversion(final LexicalContext lc, final LocalVariableConversion conversion) {
        if(this.conversion == conversion) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new Block(this, finish, statements, flags, symbols, conversion));
    }

    @Override
    public LocalVariableConversion getLocalVariableConversion() {
        return conversion;
    }

    /**
     * Get the list of statements in this block
     *
     * @return a list of statements
     */
    public List<Statement> getStatements() {
        return Collections.unmodifiableList(statements);
    }

    /**
     * Returns the line number of the first statement in the block.
     * @return the line number of the first statement in the block, or -1 if the block has no statements.
     */
    public int getFirstStatementLineNumber() {
        if(statements == null || statements.isEmpty()) {
            return -1;
        }
        return statements.get(0).getLineNumber();
    }

    /**
     * Reset the statement list for this block
     *
     * @param lc lexical context
     * @param statements new statement list
     * @return new block if statements changed, identity of statements == block.statements
     */
    public Block setStatements(final LexicalContext lc, final List<Statement> statements) {
        if (this.statements == statements) {
            return this;
        }
        int lastFinish = 0;
        if (!statements.isEmpty()) {
            lastFinish = statements.get(statements.size() - 1).getFinish();
        }
        return Node.replaceInLexicalContext(lc, this, new Block(this, Math.max(finish, lastFinish), statements, flags, symbols, conversion));
    }

    /**
     * Add or overwrite an existing symbol in the block
     *
     * @param lc     get lexical context
     * @param symbol symbol
     */
    public void putSymbol(final LexicalContext lc, final Symbol symbol) {
        symbols.put(symbol.getName(), symbol);
    }

    /**
     * Check whether scope is necessary for this Block
     *
     * @return true if this function needs a scope
     */
    public boolean needsScope() {
        return (flags & NEEDS_SCOPE) == NEEDS_SCOPE;
    }

    @Override
    public Block setFlags(final LexicalContext lc, final int flags) {
        if (this.flags == flags) {
            return this;
        }
        return Node.replaceInLexicalContext(lc, this, new Block(this, finish, statements, flags, symbols, conversion));
    }

    @Override
    public Block clearFlag(final LexicalContext lc, final int flag) {
        return setFlags(lc, flags & ~flag);
    }

    @Override
    public Block setFlag(final LexicalContext lc, final int flag) {
        return setFlags(lc, flags | flag);
    }

    @Override
    public boolean getFlag(final int flag) {
        return (flags & flag) == flag;
    }

    /**
     * Set the needs scope flag.
     * @param lc lexicalContext
     * @return new block if state changed, otherwise this
     */
    public Block setNeedsScope(final LexicalContext lc) {
        if (needsScope()) {
            return this;
        }

        return Node.replaceInLexicalContext(lc, this, new Block(this, finish, statements, flags | NEEDS_SCOPE, symbols, conversion));
    }

    /**
     * Computationally determine the next slot for this block,
     * indexed from 0. Use this as a relative base when computing
     * frames
     * @return next slot
     */
    public int nextSlot() {
        int next = 0;
        for (final Symbol symbol : getSymbols()) {
            if (symbol.hasSlot()) {
                next += symbol.slotCount();
            }
        }
        return next;
    }

    @Override
    public boolean isBreakableWithoutLabel() {
        return false;
    }

    @Override
    public List<Label> getLabels() {
        return Collections.unmodifiableList(Arrays.asList(entryLabel, breakLabel));
    }

    @Override
    public Node accept(final NodeVisitor<? extends LexicalContext> visitor) {
        return Acceptor.accept(this, visitor);
    }
}

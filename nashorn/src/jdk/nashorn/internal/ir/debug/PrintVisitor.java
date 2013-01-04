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

package jdk.nashorn.internal.ir.debug;

import java.util.List;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.DoWhileNode;
import jdk.nashorn.internal.ir.ExecuteNode;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LineNumberNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ReferenceNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;

/**
 * Print out the AST as human readable source code.
 * This works both on lowered and unlowered ASTs
 *
 * see the flags --print-parse and --print-lower-parse
 */
public final class PrintVisitor extends NodeVisitor {
    /** Tab width */
    private static final int TABWIDTH = 1;

    /** Composing buffer. */
    private final StringBuilder sb;

    /** Indentation factor. */
    private int indent;

    /** Line separator. */
    private final String EOLN;

    /** Print line numbers */
    private final boolean printLineNumbers;

    /**
     * Constructor.
     */
    public PrintVisitor() {
        this(true);
    }

    /**
     * Constructor
     *
     * @param printLineNumbers  should line number nodes be included in the output?
     */
    public PrintVisitor(final boolean printLineNumbers) {
        this.EOLN             = System.lineSeparator();
        this.sb               = new StringBuilder();
        this.printLineNumbers = printLineNumbers;
    }

    /**
     * Constructor
     *
     * @param root  a node from which to start printing code
     */
    public PrintVisitor(final Node root) {
        this(root, true);
    }

    /**
     * Constructor
     *
     * @param root              a node from which to start printing code
     * @param printLineNumbers  should line numbers nodes be included in the output?
     */
    public PrintVisitor(final Node root, final boolean printLineNumbers) {
        this(printLineNumbers);
        visit(root);
    }

    private void visit(final Node root) {
        root.accept(this);
    }

    @Override
    public String toString() {
        return sb.append(EOLN).toString();
    }

    /**
     * Insert spaces before a statement.
     */
    private void indent() {
        for (int i = indent; i > 0; i--) {
            sb.append(' ');
        }
    }

    /*
     * Visits.
     */
    @Override
    public Node enter(final AccessNode accessNode) {
        accessNode.toString(sb);
        return null;
    }

    @Override
    public Node enter(final Block block) {
        sb.append(' ');
        sb.append('{');

        indent += TABWIDTH;

        final boolean isFunction = block instanceof FunctionNode;

        if (isFunction) {
            final FunctionNode       function  = (FunctionNode)block;
            final List<FunctionNode> functions = function.getFunctions();

            for (final FunctionNode f : functions) {
                sb.append(EOLN);
                indent();
                f.accept(this);
            }

            if (!functions.isEmpty()) {
                sb.append(EOLN);
            }
        }

        final List<Node> statements = block.getStatements();

        boolean lastLineNumber = false;

        for (final Node statement : statements) {
            if (printLineNumbers || !lastLineNumber) {
                sb.append(EOLN);
                indent();
            }

            if (statement instanceof UnaryNode) {
                statement.toString(sb);
            } else {
                statement.accept(this);
            }

            lastLineNumber = statement instanceof LineNumberNode;

            final Symbol symbol = statement.getSymbol();

            if (symbol != null) {
                sb.append("  [");
                sb.append(symbol.toString());
                sb.append(']');
            }

            final char lastChar = sb.charAt(sb.length() - 1);

            if (lastChar != '}' && lastChar != ';') {
                if (printLineNumbers || !lastLineNumber) {
                    sb.append(';');
                }
            }

            if (statement.hasGoto()) {
                sb.append(" [GOTO]");
            }

            if (statement.isTerminal()) {
                sb.append(" [TERMINAL]");
            }
        }

        indent -= TABWIDTH;

        sb.append(EOLN);
        indent();
        sb.append("}");

        if (isFunction) {
            sb.append(EOLN);
        }

        return null;
    }

    @Override
    public Node enter(final BreakNode breakNode) {
        breakNode.toString(sb);
        return null;
    }

    @Override
    public Node enter(final CallNode callNode) {
        callNode.toString(sb);
        return null;
    }

    @Override
    public Node enter(final ContinueNode continueNode) {
        continueNode.toString(sb);
        return null;
    }

    @Override
    public Node enter(final DoWhileNode doWhileNode) {
        sb.append("do");
        doWhileNode.getBody().accept(this);
        sb.append(' ');
        doWhileNode.toString(sb);

        return null;
    }

    @Override
    public Node enter(final ExecuteNode executeNode) {
        final Node expression = executeNode.getExpression();

        if (expression instanceof UnaryNode) {
            expression.toString(sb);
        } else {
            expression.accept(this);
        }

        return null;
    }

    @Override
    public Node enter(final ForNode forNode) {
        forNode.toString(sb);
        forNode.getBody().accept(this);

        return null;
    }

    @Override
    public Node enter(final FunctionNode functionNode) {
        functionNode.toString(sb);
        enter((Block)functionNode);

        return null;
    }

    @Override
    public Node enter(final IfNode ifNode) {
        ifNode.toString(sb);
        ifNode.getPass().accept(this);

        final Block fail = ifNode.getFail();

        if (fail != null) {
            sb.append(" else ");
            fail.accept(this);
        }

        return null;
    }

    @Override
    public Node enter(final IndexNode indexNode) {
        indexNode.toString(sb);
        return null;
    }

    @Override
    public Node enter(final LabelNode labeledNode) {
        indent -= TABWIDTH;
        indent();
        indent += TABWIDTH;
        labeledNode.toString(sb);
        labeledNode.getBody().accept(this);

        return null;
    }

    @Override
    public Node enter(final LineNumberNode lineNumberNode) {
        if (printLineNumbers) {
            lineNumberNode.toString(sb);
        }

        return null;
    }


    @Override
    public Node enter(final ReferenceNode referenceNode) {
        referenceNode.toString(sb);
        return null;
    }

    @Override
    public Node enter(final ReturnNode returnNode) {
        returnNode.toString(sb);
        return null;
    }

    @Override
    public Node enter(final RuntimeNode runtimeNode) {
        runtimeNode.toString(sb);
        return null;
    }

    @Override
    public Node enter(final SplitNode splitNode) {
        splitNode.toString(sb);
        sb.append(EOLN);
        indent += TABWIDTH;
        indent();
        return splitNode;
    }

    @Override
    public Node leave(final SplitNode splitNode) {
        sb.append("</split>");
        sb.append(EOLN);
        indent -= TABWIDTH;
        indent();
        return splitNode;
    }

    @Override
    public Node enter(final SwitchNode switchNode) {
        switchNode.toString(sb);
        sb.append(" {");

        final List<CaseNode> cases = switchNode.getCases();

        for (final CaseNode caseNode : cases) {
            sb.append(EOLN);
            indent();
            caseNode.toString(sb);
            indent += TABWIDTH;
            caseNode.getBody().accept(this);
            indent -= TABWIDTH;
            sb.append(EOLN);
        }

        sb.append(EOLN);
        indent();
        sb.append("}");

        return null;
   }

    @Override
    public Node enter(final ThrowNode throwNode) {
        throwNode.toString(sb);
        return null;
    }

    @Override
    public Node enter(final TryNode tryNode) {
        tryNode.toString(sb);
        tryNode.getBody().accept(this);

        final List<Block> catchBlocks = tryNode.getCatchBlocks();

        for (final Block catchBlock : catchBlocks) {
            final CatchNode catchNode = (CatchNode) catchBlock.getStatements().get(0);
            catchNode.toString(sb);
            catchNode.getBody().accept(this);
        }

        final Block finallyBody = tryNode.getFinallyBody();

        if (finallyBody != null) {
            sb.append(" finally ");
            finallyBody.accept(this);
        }

        return null;
    }

    @Override
    public Node enter(final VarNode varNode) {
        varNode.toString(sb);
        return null;
    }

    @Override
    public Node enter(final WhileNode whileNode) {
        whileNode.toString(sb);
        whileNode.getBody().accept(this);

        return null;
    }

    @Override
    public Node enter(final WithNode withNode) {
        withNode.toString(sb);
        withNode.getBody().accept(this);

        return null;
    }

}

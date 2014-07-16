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
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BlockStatement;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.JoinPredecessor;
import jdk.nashorn.internal.ir.JoinPredecessorExpression;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LocalVariableConversion;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.SplitNode;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
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
public final class PrintVisitor extends NodeVisitor<LexicalContext> {
    /** Tab width */
    private static final int TABWIDTH = 4;

    /** Composing buffer. */
    private final StringBuilder sb;

    /** Indentation factor. */
    private int indent;

    /** Line separator. */
    private final String EOLN;

    /** Print line numbers */
    private final boolean printLineNumbers;

    /** Print inferred and optimistic types */
    private final boolean printTypes;

    private int lastLineNumber = -1;

    /**
     * Constructor.
     */
    public PrintVisitor() {
        this(true, true);
    }

    /**
     * Constructor
     *
     * @param printLineNumbers  should line number nodes be included in the output?
     * @param printTypes        should we print optimistic and inferred types?
     */
    public PrintVisitor(final boolean printLineNumbers, final boolean printTypes) {
        super(new LexicalContext());
        this.EOLN             = System.lineSeparator();
        this.sb               = new StringBuilder();
        this.printLineNumbers = printLineNumbers;
        this.printTypes       = printTypes;
    }

    /**
     * Constructor
     *
     * @param root  a node from which to start printing code
     */
    public PrintVisitor(final Node root) {
        this(root, true, true);
    }

    /**
     * Constructor
     *
     * @param root              a node from which to start printing code
     * @param printLineNumbers  should line numbers nodes be included in the output?
     * @param printTypes        should we print optimistic and inferred types?
     */
    public PrintVisitor(final Node root, final boolean printLineNumbers, final boolean printTypes) {
        this(printLineNumbers, printTypes);
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
    public boolean enterDefault(final Node node) {
        node.toString(sb, printTypes);
        return false;
    }

    @Override
    public boolean enterContinueNode(final ContinueNode node) {
        node.toString(sb, printTypes);
        printLocalVariableConversion(node);
        return false;
    }

    @Override
    public boolean enterBreakNode(final BreakNode node) {
        node.toString(sb, printTypes);
        printLocalVariableConversion(node);
        return false;
    }

    @Override
    public boolean enterThrowNode(final ThrowNode node) {
        node.toString(sb, printTypes);
        printLocalVariableConversion(node);
        return false;
    }

    @Override
    public boolean enterBlock(final Block block) {
        sb.append(' ');
        sb.append('{');

        indent += TABWIDTH;

        final List<Statement> statements = block.getStatements();

        for (final Statement statement : statements) {
            if (printLineNumbers) {
                final int lineNumber = statement.getLineNumber();
                sb.append('\n');
                if (lineNumber != lastLineNumber) {
                    indent();
                    sb.append("[|").append(lineNumber).append("|];").append('\n');
                }
                lastLineNumber = lineNumber;
            }
            indent();

            statement.accept(this);

            int  lastIndex = sb.length() - 1;
            char lastChar  = sb.charAt(lastIndex);
            while (Character.isWhitespace(lastChar) && lastIndex >= 0) {
                lastChar = sb.charAt(--lastIndex);
            }

            if (lastChar != '}' && lastChar != ';') {
                sb.append(';');
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
        sb.append('}');
        printLocalVariableConversion(block);

        return false;
    }

    @Override
    public boolean enterBlockStatement(final BlockStatement statement) {
        statement.getBlock().accept(this);
        return false;
    }

    @Override
    public boolean enterBinaryNode(final BinaryNode binaryNode) {
        binaryNode.lhs().accept(this);
        sb.append(' ');
        sb.append(binaryNode.tokenType());
        sb.append(' ');
        binaryNode.rhs().accept(this);
        return false;
    }

    @Override
    public boolean enterJoinPredecessorExpression(final JoinPredecessorExpression expr) {
        expr.getExpression().accept(this);
        printLocalVariableConversion(expr);
        return false;
    }

    @Override
    public boolean enterIdentNode(final IdentNode identNode) {
        identNode.toString(sb, printTypes);
        printLocalVariableConversion(identNode);
        return true;
    }

    private void printLocalVariableConversion(final JoinPredecessor joinPredecessor) {
        LocalVariableConversion.toString(joinPredecessor.getLocalVariableConversion(), sb);
    }

    @Override
    public boolean enterUnaryNode(final UnaryNode unaryNode) {
        unaryNode.toString(sb, new Runnable() {
            @Override
            public void run() {
                unaryNode.getExpression().accept(PrintVisitor.this);
            }
        }, printTypes);
        return false;
    }

    @Override
    public boolean enterExpressionStatement(final ExpressionStatement expressionStatement) {
        expressionStatement.getExpression().accept(this);
        return false;
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        forNode.toString(sb, printTypes);
        forNode.getBody().accept(this);
        return false;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        functionNode.toString(sb, printTypes);
        enterBlock(functionNode.getBody());
        return false;
    }

    @Override
    public boolean enterIfNode(final IfNode ifNode) {
        ifNode.toString(sb, printTypes);
        ifNode.getPass().accept(this);

        final Block fail = ifNode.getFail();

        if (fail != null) {
            sb.append(" else ");
            fail.accept(this);
        }
        if(ifNode.getLocalVariableConversion() != null) {
            assert fail == null;
            sb.append(" else ");
            printLocalVariableConversion(ifNode);
            sb.append(";");
        }
        return false;
    }

    @Override
    public boolean enterLabelNode(final LabelNode labeledNode) {
        indent -= TABWIDTH;
        indent();
        indent += TABWIDTH;
        labeledNode.toString(sb, printTypes);
        labeledNode.getBody().accept(this);
        printLocalVariableConversion(labeledNode);
        return false;
    }

    @Override
    public boolean enterSplitNode(final SplitNode splitNode) {
        splitNode.toString(sb, printTypes);
        sb.append(EOLN);
        indent += TABWIDTH;
        indent();
        return true;
    }

    @Override
    public Node leaveSplitNode(final SplitNode splitNode) {
        sb.append("</split>");
        sb.append(EOLN);
        indent -= TABWIDTH;
        indent();
        return splitNode;
    }

    @Override
    public boolean enterSwitchNode(final SwitchNode switchNode) {
        switchNode.toString(sb, printTypes);
        sb.append(" {");

        final List<CaseNode> cases = switchNode.getCases();

        for (final CaseNode caseNode : cases) {
            sb.append(EOLN);
            indent();
            caseNode.toString(sb, printTypes);
            printLocalVariableConversion(caseNode);
            indent += TABWIDTH;
            caseNode.getBody().accept(this);
            indent -= TABWIDTH;
            sb.append(EOLN);
        }
        if(switchNode.getLocalVariableConversion() != null) {
            sb.append(EOLN);
            indent();
            sb.append("default: ");
            printLocalVariableConversion(switchNode);
            sb.append("{}");
        }
        sb.append(EOLN);
        indent();
        sb.append("}");

        return false;
    }

    @Override
    public boolean enterTryNode(final TryNode tryNode) {
        tryNode.toString(sb, printTypes);
        printLocalVariableConversion(tryNode);
        tryNode.getBody().accept(this);

        final List<Block> catchBlocks = tryNode.getCatchBlocks();

        for (final Block catchBlock : catchBlocks) {
            final CatchNode catchNode = (CatchNode)catchBlock.getStatements().get(0);
            catchNode.toString(sb, printTypes);
            catchNode.getBody().accept(this);
        }

        final Block finallyBody = tryNode.getFinallyBody();

        if (finallyBody != null) {
            sb.append(" finally ");
            finallyBody.accept(this);
        }

        return false;
    }

    @Override
    public boolean enterVarNode(final VarNode varNode) {
        sb.append("var ");
        varNode.getName().toString(sb, printTypes);
        printLocalVariableConversion(varNode.getName());
        final Node init = varNode.getInit();
        if (init != null) {
            sb.append(" = ");
            init.accept(this);
        }

        return false;
    }

    @Override
    public boolean enterWhileNode(final WhileNode whileNode) {
        printLocalVariableConversion(whileNode);
        if (whileNode.isDoWhile()) {
            sb.append("do");
            whileNode.getBody().accept(this);
            sb.append(' ');
            whileNode.toString(sb, printTypes);
        } else {
            whileNode.toString(sb, printTypes);
            whileNode.getBody().accept(this);
        }

        return false;
    }

    @Override
    public boolean enterWithNode(final WithNode withNode) {
        withNode.toString(sb, printTypes);
        withNode.getBody().accept(this);

        return false;
    }

}

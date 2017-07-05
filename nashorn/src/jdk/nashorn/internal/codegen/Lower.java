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

package jdk.nashorn.internal.codegen;

import static jdk.nashorn.internal.codegen.CompilerConstants.EVAL;
import static jdk.nashorn.internal.codegen.CompilerConstants.RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import jdk.nashorn.internal.ir.BaseNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BlockLexicalContext;
import jdk.nashorn.internal.ir.BlockStatement;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LoopNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.CodeInstaller;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Source;

/**
 * Lower to more primitive operations. After lowering, an AST still has no symbols
 * and types, but several nodes have been turned into more low level constructs
 * and control flow termination criteria have been computed.
 *
 * We do things like code copying/inlining of finallies here, as it is much
 * harder and context dependent to do any code copying after symbols have been
 * finalized.
 */

final class Lower extends NodeOperatorVisitor<BlockLexicalContext> {

    private static final DebugLogger LOG = new DebugLogger("lower");

    // needed only to get unique eval id
    private final CodeInstaller installer;

    /**
     * Constructor.
     */
    Lower(final CodeInstaller installer) {
        super(new BlockLexicalContext() {

            @Override
            public List<Statement> popStatements() {
                final List<Statement> newStatements = new ArrayList<>();
                boolean terminated = false;

                final List<Statement> statements = super.popStatements();
                for (final Statement statement : statements) {
                    if (!terminated) {
                        newStatements.add(statement);
                        if (statement.isTerminal() || statement instanceof BreakNode || statement instanceof ContinueNode) { //TODO hasGoto? But some Loops are hasGoto too - why?
                            terminated = true;
                        }
                    } else {
                        statement.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                            @Override
                            public boolean enterVarNode(final VarNode varNode) {
                                newStatements.add(varNode.setInit(null));
                                return false;
                            }
                        });
                    }
                }
                return newStatements;
            }

            @Override
            protected Block afterSetStatements(final Block block) {
                final List<Statement> stmts = block.getStatements();
                for(final ListIterator<Statement> li = stmts.listIterator(stmts.size()); li.hasPrevious();) {
                    final Statement stmt = li.previous();
                    // popStatements() guarantees that the only thing after a terminal statement are uninitialized
                    // VarNodes. We skip past those, and set the terminal state of the block to the value of the
                    // terminal state of the first statement that is not an uninitialized VarNode.
                    if(!(stmt instanceof VarNode && ((VarNode)stmt).getInit() == null)) {
                        return block.setIsTerminal(this, stmt.isTerminal());
                    }
                }
                return block.setIsTerminal(this, false);
            }
        });
        this.installer = installer;
    }

    @Override
    public boolean enterBlock(final Block block) {
        final FunctionNode   function = lc.getCurrentFunction();
        if (lc.isFunctionBody() && function.isProgram() && !function.hasDeclaredFunctions()) {
            new ExpressionStatement(function.getLineNumber(), block.getToken(), block.getFinish(), LiteralNode.newInstance(block, ScriptRuntime.UNDEFINED)).accept(this);
        }
        return true;
    }

    @Override
    public Node leaveBlock(final Block block) {
        //now we have committed the entire statement list to the block, but we need to truncate
        //whatever is after the last terminal. block append won't append past it


        if (lc.isFunctionBody()) {
            final FunctionNode currentFunction = lc.getCurrentFunction();
            final boolean isProgram = currentFunction.isProgram();
            final Statement last = lc.getLastStatement();
            final ReturnNode returnNode = new ReturnNode(
                last == null ? currentFunction.getLineNumber() : last.getLineNumber(), //TODO?
                currentFunction.getToken(),
                currentFunction.getFinish(),
                isProgram ?
                    compilerConstant(RETURN) :
                    LiteralNode.newInstance(block, ScriptRuntime.UNDEFINED));

            returnNode.accept(this);
        }

        return block;
    }

    @Override
    public boolean enterBreakNode(final BreakNode breakNode) {
        addStatement(breakNode);
        return false;
    }

    @Override
    public Node leaveCallNode(final CallNode callNode) {
        return checkEval(callNode.setFunction(markerFunction(callNode.getFunction())));
    }

    @Override
    public Node leaveCatchNode(final CatchNode catchNode) {
        return addStatement(catchNode);
    }

    @Override
    public boolean enterContinueNode(final ContinueNode continueNode) {
        addStatement(continueNode);
        return false;
    }

    @Override
    public boolean enterEmptyNode(final EmptyNode emptyNode) {
        return false;
    }

    @Override
    public Node leaveExpressionStatement(final ExpressionStatement expressionStatement) {
        final Expression expr = expressionStatement.getExpression();
        ExpressionStatement node = expressionStatement;

        final FunctionNode currentFunction = lc.getCurrentFunction();

        if (currentFunction.isProgram()) {
            if (!isInternalExpression(expr) && !isEvalResultAssignment(expr)) {
                node = expressionStatement.setExpression(
                    new BinaryNode(
                        Token.recast(
                            expressionStatement.getToken(),
                            TokenType.ASSIGN),
                        compilerConstant(RETURN),
                    expr));
            }
        }

        return addStatement(node);
    }

    @Override
    public Node leaveBlockStatement(BlockStatement blockStatement) {
        return addStatement(blockStatement);
    }

    @Override
    public Node leaveForNode(final ForNode forNode) {
        ForNode newForNode = forNode;

        final Node  test = forNode.getTest();
        if (!forNode.isForIn() && conservativeAlwaysTrue(test)) {
            newForNode = forNode.setTest(lc, null);
        }

        return addStatement(checkEscape(newForNode));
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        return !functionNode.isLazy();
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        LOG.info("END FunctionNode: ", functionNode.getName());
        return functionNode.setState(lc, CompilationState.LOWERED);
    }

    @Override
    public Node leaveIfNode(final IfNode ifNode) {
        return addStatement(ifNode);
    }

    @Override
    public Node leaveLabelNode(final LabelNode labelNode) {
        return addStatement(labelNode);
    }

    @Override
    public Node leaveReturnNode(final ReturnNode returnNode) {
        addStatement(returnNode); //ReturnNodes are always terminal, marked as such in constructor
        return returnNode;
    }


    @Override
    public Node leaveSwitchNode(final SwitchNode switchNode) {
        return addStatement(switchNode);
    }

    @Override
    public Node leaveThrowNode(final ThrowNode throwNode) {
        addStatement(throwNode); //ThrowNodes are always terminal, marked as such in constructor
        return throwNode;
    }

    private static Node ensureUniqueNamesIn(final Node node) {
        return node.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public Node leaveFunctionNode(final FunctionNode functionNode) {
                final String name = functionNode.getName();
                return functionNode.setName(lc, lc.getCurrentFunction().uniqueName(name));
            }

            @Override
            public Node leaveDefault(final Node labelledNode) {
                return labelledNode.ensureUniqueLabels(lc);
            }
        });
    }

    private static List<Statement> copyFinally(final Block finallyBody) {
        final List<Statement> newStatements = new ArrayList<>();
        for (final Statement statement : finallyBody.getStatements()) {
            newStatements.add((Statement)ensureUniqueNamesIn(statement));
            if (statement.hasTerminalFlags()) {
                return newStatements;
            }
        }
        return newStatements;
    }

    private Block catchAllBlock(final TryNode tryNode) {
        final int  lineNumber = tryNode.getLineNumber();
        final long token      = tryNode.getToken();
        final int  finish     = tryNode.getFinish();

        final IdentNode exception = new IdentNode(token, finish, lc.getCurrentFunction().uniqueName("catch_all"));

        final Block catchBody = new Block(token, finish, new ThrowNode(lineNumber, token, finish, new IdentNode(exception), ThrowNode.IS_SYNTHETIC_RETHROW));
        assert catchBody.isTerminal(); //ends with throw, so terminal

        final CatchNode catchAllNode  = new CatchNode(lineNumber, token, finish, new IdentNode(exception), null, catchBody, CatchNode.IS_SYNTHETIC_RETHROW);
        final Block     catchAllBlock = new Block(token, finish, catchAllNode);

        //catchallblock -> catchallnode (catchnode) -> exception -> throw

        return (Block)catchAllBlock.accept(this); //not accepted. has to be accepted by lower
    }

    private IdentNode compilerConstant(final CompilerConstants cc) {
        final FunctionNode functionNode = lc.getCurrentFunction();
        return new IdentNode(functionNode.getToken(), functionNode.getFinish(), cc.symbolName());
    }

    private static boolean isTerminal(final List<Statement> statements) {
        return !statements.isEmpty() && statements.get(statements.size() - 1).hasTerminalFlags();
    }

    /**
     * Splice finally code into all endpoints of a trynode
     * @param tryNode the try node
     * @param rethrows list of rethrowing throw nodes from synthetic catch blocks
     * @param finallyBody the code in the original finally block
     * @return new try node after splicing finally code (same if nop)
     */
    private Node spliceFinally(final TryNode tryNode, final List<ThrowNode> rethrows, final Block finallyBody) {
        assert tryNode.getFinallyBody() == null;

        final TryNode newTryNode = (TryNode)tryNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            final List<Node> insideTry = new ArrayList<>();

            @Override
            public boolean enterDefault(final Node node) {
                insideTry.add(node);
                return true;
            }

            @Override
            public boolean enterFunctionNode(final FunctionNode functionNode) {
                // do not enter function nodes - finally code should not be inlined into them
                return false;
            }

            @Override
            public Node leaveThrowNode(final ThrowNode throwNode) {
                if (rethrows.contains(throwNode)) {
                    final List<Statement> newStatements = copyFinally(finallyBody);
                    if (!isTerminal(newStatements)) {
                        newStatements.add(throwNode);
                    }
                    return BlockStatement.createReplacement(throwNode, newStatements);
                }
                return throwNode;
            }

            @Override
            public Node leaveBreakNode(final BreakNode breakNode) {
                return copy(breakNode, (Node)Lower.this.lc.getBreakable(breakNode.getLabel()));
            }

            @Override
            public Node leaveContinueNode(final ContinueNode continueNode) {
                return copy(continueNode, Lower.this.lc.getContinueTo(continueNode.getLabel()));
            }

            @Override
            public Node leaveReturnNode(final ReturnNode returnNode) {
                final Expression expr  = returnNode.getExpression();
                final List<Statement> newStatements = new ArrayList<>();

                final Expression resultNode;
                if (expr != null) {
                    //we need to evaluate the result of the return in case it is complex while
                    //still in the try block, store it in a result value and return it afterwards
                    resultNode = new IdentNode(Lower.this.compilerConstant(RETURN));
                    newStatements.add(new ExpressionStatement(returnNode.getLineNumber(), returnNode.getToken(), returnNode.getFinish(), new BinaryNode(Token.recast(returnNode.getToken(), TokenType.ASSIGN), resultNode, expr)));
                } else {
                    resultNode = null;
                }

                newStatements.addAll(copyFinally(finallyBody));
                if (!isTerminal(newStatements)) {
                    newStatements.add(expr == null ? returnNode : returnNode.setExpression(resultNode));
                }

                return BlockStatement.createReplacement(returnNode, lc.getCurrentBlock().getFinish(), newStatements);
            }

            private Node copy(final Statement endpoint, final Node targetNode) {
                if (!insideTry.contains(targetNode)) {
                    final List<Statement> newStatements = copyFinally(finallyBody);
                    if (!isTerminal(newStatements)) {
                        newStatements.add(endpoint);
                    }
                    return BlockStatement.createReplacement(endpoint, tryNode.getFinish(), newStatements);
                }
                return endpoint;
            }
        });

        addStatement(newTryNode);
        for (final Node statement : finallyBody.getStatements()) {
            addStatement((Statement)statement);
        }

        return newTryNode;
    }

    @Override
    public Node leaveTryNode(final TryNode tryNode) {
        final Block finallyBody = tryNode.getFinallyBody();

        if (finallyBody == null) {
            return addStatement(tryNode);
        }

        /*
         * create a new trynode
         *    if we have catches:
         *
         *    try            try
         *       x              try
         *    catch               x
         *       y              catch
         *    finally z           y
         *                   catchall
         *                        rethrow
         *
         *   otheriwse
         *
         *   try              try
         *      x               x
         *   finally          catchall
         *      y               rethrow
         *
         *
         *   now splice in finally code wherever needed
         *
         */
        TryNode newTryNode;

        final Block catchAll = catchAllBlock(tryNode);

        final List<ThrowNode> rethrows = new ArrayList<>();
        catchAll.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public boolean enterThrowNode(final ThrowNode throwNode) {
                rethrows.add(throwNode);
                return true;
            }
        });
        assert rethrows.size() == 1;

        if (tryNode.getCatchBlocks().isEmpty()) {
            newTryNode = tryNode.setFinallyBody(null);
        } else {
            Block outerBody = new Block(tryNode.getToken(), tryNode.getFinish(), tryNode.setFinallyBody(null));
            newTryNode = tryNode.setBody(outerBody).setCatchBlocks(null);
        }

        newTryNode = newTryNode.setCatchBlocks(Arrays.asList(catchAll)).setFinallyBody(null);

        /*
         * Now that the transform is done, we have to go into the try and splice
         * the finally block in front of any statement that is outside the try
         */
        return spliceFinally(newTryNode, rethrows, finallyBody);
    }

    @Override
    public Node leaveVarNode(final VarNode varNode) {
        addStatement(varNode);
        if (varNode.getFlag(VarNode.IS_LAST_FUNCTION_DECLARATION) && lc.getCurrentFunction().isProgram()) {
            new ExpressionStatement(varNode.getLineNumber(), varNode.getToken(), varNode.getFinish(), new IdentNode(varNode.getName())).accept(this);
        }
        return varNode;
    }

    @Override
    public Node leaveWhileNode(final WhileNode whileNode) {
        final Node test  = whileNode.getTest();
        final Block body = whileNode.getBody();

        if (conservativeAlwaysTrue(test)) {
            //turn it into a for node without a test.
            final ForNode forNode = (ForNode)new ForNode(whileNode.getLineNumber(), whileNode.getToken(), whileNode.getFinish(), null, null, body, null, ForNode.IS_FOR).accept(this);
            lc.replace(whileNode, forNode);
            return forNode;
        }

         return addStatement(checkEscape(whileNode));
    }

    @Override
    public Node leaveWithNode(final WithNode withNode) {
        return addStatement(withNode);
    }

    /**
     * Given a function node that is a callee in a CallNode, replace it with
     * the appropriate marker function. This is used by {@link CodeGenerator}
     * for fast scope calls
     *
     * @param function function called by a CallNode
     * @return transformed node to marker function or identity if not ident/access/indexnode
     */
    private static Expression markerFunction(final Expression function) {
        if (function instanceof IdentNode) {
            return ((IdentNode)function).setIsFunction();
        } else if (function instanceof BaseNode) {
            return ((BaseNode)function).setIsFunction();
        }
        return function;
    }

    /**
     * Calculate a synthetic eval location for a node for the stacktrace, for example src#17<eval>
     * @param node a node
     * @return eval location
     */
    private String evalLocation(final IdentNode node) {
        final Source source = lc.getCurrentFunction().getSource();
        final int pos = node.position();
        // Code installer is null when running with --compile-only, use 0 as id in that case
        final long id = installer == null ? 0 : installer.getUniqueEvalId();
        return new StringBuilder().
            append(source.getName()).
            append('#').
            append(source.getLine(pos)).
            append(':').
            append(source.getColumn(pos)).
            append("<eval>@").
            append(id).
            toString();
    }

    /**
     * Check whether a call node may be a call to eval. In that case we
     * clone the args in order to create the following construct in
     * {@link CodeGenerator}
     *
     * <pre>
     * if (calledFuntion == buildInEval) {
     *    eval(cloned arg);
     * } else {
     *    cloned arg;
     * }
     * </pre>
     *
     * @param callNode call node to check if it's an eval
     */
    private CallNode checkEval(final CallNode callNode) {
        if (callNode.getFunction() instanceof IdentNode) {

            final List<Expression> args = callNode.getArgs();
            final IdentNode callee = (IdentNode)callNode.getFunction();

            // 'eval' call with at least one argument
            if (args.size() >= 1 && EVAL.symbolName().equals(callee.getName())) {
                final FunctionNode currentFunction = lc.getCurrentFunction();
                return callNode.setEvalArgs(
                    new CallNode.EvalArgs(
                        (Expression)ensureUniqueNamesIn(args.get(0)).accept(this),
                        compilerConstant(THIS),
                        evalLocation(callee),
                        currentFunction.isStrict()));
            }
        }

        return callNode;
    }

    private static boolean conservativeAlwaysTrue(final Node node) {
        return node == null || ((node instanceof LiteralNode) && Boolean.TRUE.equals(((LiteralNode<?>)node).getValue()));
    }

    /**
     * Helper that given a loop body makes sure that it is not terminal if it
     * has a continue that leads to the loop header or to outer loops' loop
     * headers. This means that, even if the body ends with a terminal
     * statement, we cannot tag it as terminal
     *
     * @param loopBody the loop body to check
     * @return true if control flow may escape the loop
     */
    private static boolean controlFlowEscapes(final LexicalContext lex, final Block loopBody) {
        final List<Node> escapes = new ArrayList<>();

        loopBody.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public Node leaveBreakNode(final BreakNode node) {
                escapes.add(node);
                return node;
            }

            @Override
            public Node leaveContinueNode(final ContinueNode node) {
                // all inner loops have been popped.
                if (lex.contains(lex.getContinueTo(node.getLabel()))) {
                    escapes.add(node);
                }
                return node;
            }
        });

        return !escapes.isEmpty();
    }

    private LoopNode checkEscape(final LoopNode loopNode) {
        final boolean escapes = controlFlowEscapes(lc, loopNode.getBody());
        if (escapes) {
            return loopNode.
                setBody(lc, loopNode.getBody().setIsTerminal(lc, false)).
                setControlFlowEscapes(lc, escapes);
        }
        return loopNode;
    }


    private Node addStatement(final Statement statement) {
        lc.appendStatement(statement);
        return statement;
    }

    /**
     * An internal expression has a symbol that is tagged internal. Check if
     * this is such a node
     *
     * @param expression expression to check for internal symbol
     * @return true if internal, false otherwise
     */
    private static boolean isInternalExpression(final Expression expression) {
        final Symbol symbol = expression.getSymbol();
        return symbol != null && symbol.isInternal();
    }

    /**
     * Is this an assignment to the special variable that hosts scripting eval
     * results, i.e. __return__?
     *
     * @param expression expression to check whether it is $evalresult = X
     * @return true if an assignment to eval result, false otherwise
     */
    private static boolean isEvalResultAssignment(final Node expression) {
        Node e = expression;
        assert e.tokenType() != TokenType.DISCARD; //there are no discards this early anymore
        if (e instanceof BinaryNode) {
            final Node lhs = ((BinaryNode)e).lhs();
            if (lhs instanceof IdentNode) {
                return ((IdentNode)lhs).getName().equals(RETURN.symbolName());
            }
        }
        return false;
    }

}

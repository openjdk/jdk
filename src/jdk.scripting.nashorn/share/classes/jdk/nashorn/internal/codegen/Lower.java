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
import static jdk.nashorn.internal.ir.Expression.isAlwaysTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Pattern;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BaseNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BlockLexicalContext;
import jdk.nashorn.internal.ir.BlockStatement;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ClassNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.DebuggerNode;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.ExpressionStatement;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.JumpStatement;
import jdk.nashorn.internal.ir.JumpToInlinedFinally;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.PrimitiveLiteralNode;
import jdk.nashorn.internal.ir.LoopNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.Statement;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.SimpleNodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.ECMAErrors;
import jdk.nashorn.internal.runtime.ErrorManager;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;

/**
 * Lower to more primitive operations. After lowering, an AST still has no symbols
 * and types, but several nodes have been turned into more low level constructs
 * and control flow termination criteria have been computed.
 *
 * We do things like code copying/inlining of finallies here, as it is much
 * harder and context dependent to do any code copying after symbols have been
 * finalized.
 */
@Logger(name="lower")
final class Lower extends NodeOperatorVisitor<BlockLexicalContext> implements Loggable {

    private final DebugLogger log;
    private final boolean es6;
    private final Source source;

    // Conservative pattern to test if element names consist of characters valid for identifiers.
    // This matches any non-zero length alphanumeric string including _ and $ and not starting with a digit.
    private static final Pattern SAFE_PROPERTY_NAME = Pattern.compile("[a-zA-Z_$][\\w$]*");

    /**
     * Constructor.
     */
    Lower(final Compiler compiler) {
        super(new BlockLexicalContext() {

            @Override
            public List<Statement> popStatements() {
                final List<Statement> newStatements = new ArrayList<>();
                boolean terminated = false;

                final List<Statement> statements = super.popStatements();
                for (final Statement statement : statements) {
                    if (!terminated) {
                        newStatements.add(statement);
                        if (statement.isTerminal() || statement instanceof JumpStatement) { //TODO hasGoto? But some Loops are hasGoto too - why?
                            terminated = true;
                        }
                    } else {
                        FoldConstants.extractVarNodesFromDeadCode(statement, newStatements);
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

        this.log = initLogger(compiler.getContext());
        this.es6 = compiler.getScriptEnvironment()._es6;
        this.source = compiler.getSource();
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
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
    public boolean enterCatchNode(final CatchNode catchNode) {
        Expression exception = catchNode.getException();
        if ((exception != null) && !(exception instanceof IdentNode)) {
            throwNotImplementedYet("es6.destructuring", exception);
        }
        return true;
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
    public boolean enterDebuggerNode(final DebuggerNode debuggerNode) {
        final int line = debuggerNode.getLineNumber();
        final long token = debuggerNode.getToken();
        final int finish = debuggerNode.getFinish();
        addStatement(new ExpressionStatement(line, token, finish, new RuntimeNode(token, finish, RuntimeNode.Request.DEBUGGER, new ArrayList<Expression>())));
        return false;
    }

    @Override
    public boolean enterJumpToInlinedFinally(final JumpToInlinedFinally jumpToInlinedFinally) {
        addStatement(jumpToInlinedFinally);
        return false;
    }

    @Override
    public boolean enterEmptyNode(final EmptyNode emptyNode) {
        return false;
    }

    @Override
    public Node leaveIndexNode(final IndexNode indexNode) {
        final String name = getConstantPropertyName(indexNode.getIndex());
        if (name != null) {
            // If index node is a constant property name convert index node to access node.
            assert indexNode.isIndex();
            return new AccessNode(indexNode.getToken(), indexNode.getFinish(), indexNode.getBase(), name);
        }
        return super.leaveIndexNode(indexNode);
    }

    @Override
    public Node leaveDELETE(final UnaryNode delete) {
        final Expression expression = delete.getExpression();
        if (expression instanceof IdentNode || expression instanceof BaseNode) {
            return delete;
        }
        return new BinaryNode(Token.recast(delete.getToken(), TokenType.COMMARIGHT), expression,
                LiteralNode.newInstance(delete.getToken(), delete.getFinish(), true));
    }

    // If expression is a primitive literal that is not an array index and does return its string value. Else return null.
    private static String getConstantPropertyName(final Expression expression) {
        if (expression instanceof LiteralNode.PrimitiveLiteralNode) {
            final Object value = ((LiteralNode) expression).getValue();
            if (value instanceof String && SAFE_PROPERTY_NAME.matcher((String) value).matches()) {
                return (String) value;
            }
        }
        return null;
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

        if (es6 && expressionStatement.destructuringDeclarationType() != null) {
            throwNotImplementedYet("es6.destructuring", expressionStatement);
        }

        return addStatement(node);
    }

    @Override
    public Node leaveBlockStatement(final BlockStatement blockStatement) {
        return addStatement(blockStatement);
    }

    @Override
    public boolean enterForNode(final ForNode forNode) {
        if (es6 && (forNode.getInit() instanceof ObjectNode || forNode.getInit() instanceof ArrayLiteralNode)) {
            throwNotImplementedYet("es6.destructuring", forNode);
        }
        return super.enterForNode(forNode);
    }

    @Override
    public Node leaveForNode(final ForNode forNode) {
        ForNode newForNode = forNode;

        final Expression test = forNode.getTest();
        if (!forNode.isForInOrOf() && isAlwaysTrue(test)) {
            newForNode = forNode.setTest(lc, null);
        }

        newForNode = checkEscape(newForNode);
        if(!es6 && newForNode.isForInOrOf()) {
            // Wrap it in a block so its internally created iterator is restricted in scope, unless we are running
            // in ES6 mode, in which case the parser already created a block to capture let/const declarations.
            addStatementEnclosedInBlock(newForNode);
        } else {
            addStatement(newForNode);
        }
        return newForNode;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        if (es6) {
            if (functionNode.getKind() == FunctionNode.Kind.MODULE) {
                throwNotImplementedYet("es6.module", functionNode);
            }

            if (functionNode.getKind() == FunctionNode.Kind.GENERATOR) {
                throwNotImplementedYet("es6.generator", functionNode);
            }
            if (functionNode.usesSuper()) {
                throwNotImplementedYet("es6.super", functionNode);
            }

            final int numParams = functionNode.getNumOfParams();
            if (numParams > 0) {
                final IdentNode lastParam = functionNode.getParameter(numParams - 1);
                if (lastParam.isRestParameter()) {
                    throwNotImplementedYet("es6.rest.param", lastParam);
                }
            }
            for (final IdentNode param : functionNode.getParameters()) {
                if (param.isDestructuredParameter()) {
                    throwNotImplementedYet("es6.destructuring", functionNode);
                }
            }
        }

        return super.enterFunctionNode(functionNode);
    }

    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        log.info("END FunctionNode: ", functionNode.getName());
        return functionNode;
    }

    @Override
    public Node leaveIfNode(final IfNode ifNode) {
        return addStatement(ifNode);
    }

    @Override
    public Node leaveIN(final BinaryNode binaryNode) {
        return new RuntimeNode(binaryNode);
    }

    @Override
    public Node leaveINSTANCEOF(final BinaryNode binaryNode) {
        return new RuntimeNode(binaryNode);
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
    public Node leaveCaseNode(final CaseNode caseNode) {
        // Try to represent the case test as an integer
        final Node test = caseNode.getTest();
        if (test instanceof LiteralNode) {
            final LiteralNode<?> lit = (LiteralNode<?>)test;
            if (lit.isNumeric() && !(lit.getValue() instanceof Integer)) {
                if (JSType.isRepresentableAsInt(lit.getNumber())) {
                    return caseNode.setTest((Expression)LiteralNode.newInstance(lit, lit.getInt32()).accept(this));
                }
            }
        }
        return caseNode;
    }

    @Override
    public Node leaveSwitchNode(final SwitchNode switchNode) {
        if(!switchNode.isUniqueInteger()) {
            // Wrap it in a block so its internally created tag is restricted in scope
            addStatementEnclosedInBlock(switchNode);
        } else {
            addStatement(switchNode);
        }
        return switchNode;
    }

    @Override
    public Node leaveThrowNode(final ThrowNode throwNode) {
        return addStatement(throwNode); //ThrowNodes are always terminal, marked as such in constructor
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T ensureUniqueNamesIn(final T node) {
        return (T)node.accept(new SimpleNodeVisitor() {
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

    private static Block createFinallyBlock(final Block finallyBody) {
        final List<Statement> newStatements = new ArrayList<>();
        for (final Statement statement : finallyBody.getStatements()) {
            newStatements.add(statement);
            if (statement.hasTerminalFlags()) {
                break;
            }
        }
        return finallyBody.setStatements(null, newStatements);
    }

    private Block catchAllBlock(final TryNode tryNode) {
        final int  lineNumber = tryNode.getLineNumber();
        final long token      = tryNode.getToken();
        final int  finish     = tryNode.getFinish();

        final IdentNode exception = new IdentNode(token, finish, lc.getCurrentFunction().uniqueName(CompilerConstants.EXCEPTION_PREFIX.symbolName()));

        final Block catchBody = new Block(token, finish, new ThrowNode(lineNumber, token, finish, new IdentNode(exception), true));
        assert catchBody.isTerminal(); //ends with throw, so terminal

        final CatchNode catchAllNode  = new CatchNode(lineNumber, token, finish, new IdentNode(exception), null, catchBody, true);
        final Block     catchAllBlock = new Block(token, finish, catchAllNode);

        //catchallblock -> catchallnode (catchnode) -> exception -> throw

        return (Block)catchAllBlock.accept(this); //not accepted. has to be accepted by lower
    }

    private IdentNode compilerConstant(final CompilerConstants cc) {
        final FunctionNode functionNode = lc.getCurrentFunction();
        return new IdentNode(functionNode.getToken(), functionNode.getFinish(), cc.symbolName());
    }

    private static boolean isTerminalFinally(final Block finallyBlock) {
        return finallyBlock.getLastStatement().hasTerminalFlags();
    }

    /**
     * Splice finally code into all endpoints of a trynode
     * @param tryNode the try node
     * @param rethrow the rethrowing throw nodes from the synthetic catch block
     * @param finallyBody the code in the original finally block
     * @return new try node after splicing finally code (same if nop)
     */
    private TryNode spliceFinally(final TryNode tryNode, final ThrowNode rethrow, final Block finallyBody) {
        assert tryNode.getFinallyBody() == null;

        final Block finallyBlock = createFinallyBlock(finallyBody);
        final ArrayList<Block> inlinedFinallies = new ArrayList<>();
        final FunctionNode fn = lc.getCurrentFunction();
        final TryNode newTryNode = (TryNode)tryNode.accept(new SimpleNodeVisitor() {

            @Override
            public boolean enterFunctionNode(final FunctionNode functionNode) {
                // do not enter function nodes - finally code should not be inlined into them
                return false;
            }

            @Override
            public Node leaveThrowNode(final ThrowNode throwNode) {
                if (rethrow == throwNode) {
                    return new BlockStatement(prependFinally(finallyBlock, throwNode));
                }
                return throwNode;
            }

            @Override
            public Node leaveBreakNode(final BreakNode breakNode) {
                return leaveJumpStatement(breakNode);
            }

            @Override
            public Node leaveContinueNode(final ContinueNode continueNode) {
                return leaveJumpStatement(continueNode);
            }

            private Node leaveJumpStatement(final JumpStatement jump) {
                // NOTE: leaveJumpToInlinedFinally deliberately does not delegate to this method, only break and
                // continue are edited. JTIF nodes should not be changed, rather the surroundings of
                // break/continue/return that were moved into the inlined finally block itself will be changed.

                // If this visitor's lc doesn't find the target of the jump, it means it's external to the try block.
                if (jump.getTarget(lc) == null) {
                    return createJumpToInlinedFinally(fn, inlinedFinallies, prependFinally(finallyBlock, jump));
                }
                return jump;
            }

            @Override
            public Node leaveReturnNode(final ReturnNode returnNode) {
                final Expression expr = returnNode.getExpression();
                if (isTerminalFinally(finallyBlock)) {
                    if (expr == null) {
                        // Terminal finally; no return expression.
                        return createJumpToInlinedFinally(fn, inlinedFinallies, ensureUniqueNamesIn(finallyBlock));
                    }
                    // Terminal finally; has a return expression.
                    final List<Statement> newStatements = new ArrayList<>(2);
                    final int retLineNumber = returnNode.getLineNumber();
                    final long retToken = returnNode.getToken();
                    // Expression is evaluated for side effects.
                    newStatements.add(new ExpressionStatement(retLineNumber, retToken, returnNode.getFinish(), expr));
                    newStatements.add(createJumpToInlinedFinally(fn, inlinedFinallies, ensureUniqueNamesIn(finallyBlock)));
                    return new BlockStatement(retLineNumber, new Block(retToken, finallyBlock.getFinish(), newStatements));
                } else if (expr == null || expr instanceof PrimitiveLiteralNode<?> || (expr instanceof IdentNode && RETURN.symbolName().equals(((IdentNode)expr).getName()))) {
                    // Nonterminal finally; no return expression, or returns a primitive literal, or returns :return.
                    // Just move the return expression into the finally block.
                    return createJumpToInlinedFinally(fn, inlinedFinallies, prependFinally(finallyBlock, returnNode));
                } else {
                    // We need to evaluate the result of the return in case it is complex while still in the try block,
                    // store it in :return, and return it afterwards.
                    final List<Statement> newStatements = new ArrayList<>();
                    final int retLineNumber = returnNode.getLineNumber();
                    final long retToken = returnNode.getToken();
                    final int retFinish = returnNode.getFinish();
                    final Expression resultNode = new IdentNode(expr.getToken(), expr.getFinish(), RETURN.symbolName());
                    // ":return = <expr>;"
                    newStatements.add(new ExpressionStatement(retLineNumber, retToken, retFinish, new BinaryNode(Token.recast(returnNode.getToken(), TokenType.ASSIGN), resultNode, expr)));
                    // inline finally and end it with "return :return;"
                    newStatements.add(createJumpToInlinedFinally(fn, inlinedFinallies, prependFinally(finallyBlock, returnNode.setExpression(resultNode))));
                    return new BlockStatement(retLineNumber, new Block(retToken, retFinish, newStatements));
                }
            }
        });
        addStatement(inlinedFinallies.isEmpty() ? newTryNode : newTryNode.setInlinedFinallies(lc, inlinedFinallies));
        // TODO: if finallyStatement is terminal, we could just have sites of inlined finallies jump here.
        addStatement(new BlockStatement(finallyBlock));

        return newTryNode;
    }

    private static JumpToInlinedFinally createJumpToInlinedFinally(final FunctionNode fn, final List<Block> inlinedFinallies, final Block finallyBlock) {
        final String labelName = fn.uniqueName(":finally");
        final long token = finallyBlock.getToken();
        final int finish = finallyBlock.getFinish();
        inlinedFinallies.add(new Block(token, finish, new LabelNode(finallyBlock.getFirstStatementLineNumber(),
                token, finish, labelName, finallyBlock)));
        return new JumpToInlinedFinally(labelName);
    }

    private static Block prependFinally(final Block finallyBlock, final Statement statement) {
        final Block inlinedFinally = ensureUniqueNamesIn(finallyBlock);
        if (isTerminalFinally(finallyBlock)) {
            return inlinedFinally;
        }
        final List<Statement> stmts = inlinedFinally.getStatements();
        final List<Statement> newStmts = new ArrayList<>(stmts.size() + 1);
        newStmts.addAll(stmts);
        newStmts.add(statement);
        return new Block(inlinedFinally.getToken(), statement.getFinish(), newStmts);
    }

    @Override
    public Node leaveTryNode(final TryNode tryNode) {
        final Block finallyBody = tryNode.getFinallyBody();
        TryNode newTryNode = tryNode.setFinallyBody(lc, null);

        // No finally or empty finally
        if (finallyBody == null || finallyBody.getStatementCount() == 0) {
            final List<CatchNode> catches = newTryNode.getCatches();
            if (catches == null || catches.isEmpty()) {
                // A completely degenerate try block: empty finally, no catches. Replace it with try body.
                return addStatement(new BlockStatement(tryNode.getBody()));
            }
            return addStatement(ensureUnconditionalCatch(newTryNode));
        }

        /*
         * create a new try node
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
         *   otherwise
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
        final Block catchAll = catchAllBlock(tryNode);

        final List<ThrowNode> rethrows = new ArrayList<>(1);
        catchAll.accept(new SimpleNodeVisitor() {
            @Override
            public boolean enterThrowNode(final ThrowNode throwNode) {
                rethrows.add(throwNode);
                return true;
            }
        });
        assert rethrows.size() == 1;

        if (!tryNode.getCatchBlocks().isEmpty()) {
            final Block outerBody = new Block(newTryNode.getToken(), newTryNode.getFinish(), ensureUnconditionalCatch(newTryNode));
            newTryNode = newTryNode.setBody(lc, outerBody).setCatchBlocks(lc, null);
        }

        newTryNode = newTryNode.setCatchBlocks(lc, Arrays.asList(catchAll));

        /*
         * Now that the transform is done, we have to go into the try and splice
         * the finally block in front of any statement that is outside the try
         */
        return (TryNode)lc.replace(tryNode, spliceFinally(newTryNode, rethrows.get(0), finallyBody));
    }

    private TryNode ensureUnconditionalCatch(final TryNode tryNode) {
        final List<CatchNode> catches = tryNode.getCatches();
        if(catches == null || catches.isEmpty() || catches.get(catches.size() - 1).getExceptionCondition() == null) {
            return tryNode;
        }
        // If the last catch block is conditional, add an unconditional rethrow block
        final List<Block> newCatchBlocks = new ArrayList<>(tryNode.getCatchBlocks());

        newCatchBlocks.add(catchAllBlock(tryNode));
        return tryNode.setCatchBlocks(lc, newCatchBlocks);
    }

    @Override
    public boolean enterUnaryNode(final UnaryNode unaryNode) {
        if (es6) {
            if (unaryNode.isTokenType(TokenType.YIELD) ||
                unaryNode.isTokenType(TokenType.YIELD_STAR)) {
                throwNotImplementedYet("es6.yield", unaryNode);
            } else if (unaryNode.isTokenType(TokenType.SPREAD_ARGUMENT) ||
                       unaryNode.isTokenType(TokenType.SPREAD_ARRAY)) {
                throwNotImplementedYet("es6.spread", unaryNode);
            }
        }

        return super.enterUnaryNode(unaryNode);
    }

    @Override
    public boolean enterASSIGN(BinaryNode binaryNode) {
        if (es6 && (binaryNode.lhs() instanceof ObjectNode || binaryNode.lhs() instanceof ArrayLiteralNode)) {
            throwNotImplementedYet("es6.destructuring", binaryNode);
        }
        return super.enterASSIGN(binaryNode);
    }

    @Override
    public Node leaveVarNode(final VarNode varNode) {
        addStatement(varNode);
        if (varNode.getFlag(VarNode.IS_LAST_FUNCTION_DECLARATION)
                && lc.getCurrentFunction().isProgram()
                && ((FunctionNode) varNode.getInit()).isAnonymous()) {
            new ExpressionStatement(varNode.getLineNumber(), varNode.getToken(), varNode.getFinish(), new IdentNode(varNode.getName())).accept(this);
        }
        return varNode;
    }

    @Override
    public Node leaveWhileNode(final WhileNode whileNode) {
        final Expression test = whileNode.getTest();
        final Block body = whileNode.getBody();

        if (isAlwaysTrue(test)) {
            //turn it into a for node without a test.
            final ForNode forNode = (ForNode)new ForNode(whileNode.getLineNumber(), whileNode.getToken(), whileNode.getFinish(), body, 0).accept(this);
            lc.replace(whileNode, forNode);
            return forNode;
        }

         return addStatement(checkEscape(whileNode));
    }

    @Override
    public Node leaveWithNode(final WithNode withNode) {
        return addStatement(withNode);
    }

    @Override
    public boolean enterClassNode(final ClassNode classNode) {
        throwNotImplementedYet("es6.class", classNode);
        return super.enterClassNode(classNode);
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
        return new StringBuilder().
            append(source.getName()).
            append('#').
            append(source.getLine(pos)).
            append(':').
            append(source.getColumn(pos)).
            append("<eval>").
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
                final List<Expression> evalArgs = new ArrayList<>(args.size());
                for(final Expression arg: args) {
                    evalArgs.add((Expression)ensureUniqueNamesIn(arg).accept(this));
                }
                return callNode.setEvalArgs(new CallNode.EvalArgs(evalArgs, evalLocation(callee)));
            }
        }

        return callNode;
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

        loopBody.accept(new SimpleNodeVisitor() {
            @Override
            public Node leaveBreakNode(final BreakNode node) {
                escapes.add(node);
                return node;
            }

            @Override
            public Node leaveContinueNode(final ContinueNode node) {
                // all inner loops have been popped.
                if (lex.contains(node.getTarget(lex))) {
                    escapes.add(node);
                }
                return node;
            }
        });

        return !escapes.isEmpty();
    }

    @SuppressWarnings("unchecked")
    private <T extends LoopNode> T checkEscape(final T loopNode) {
        final boolean escapes = controlFlowEscapes(lc, loopNode.getBody());
        if (escapes) {
            return (T)loopNode.
                setBody(lc, loopNode.getBody().setIsTerminal(lc, false)).
                setControlFlowEscapes(lc, escapes);
        }
        return loopNode;
    }


    private Node addStatement(final Statement statement) {
        lc.appendStatement(statement);
        return statement;
    }

    private void addStatementEnclosedInBlock(final Statement stmt) {
        BlockStatement b = BlockStatement.createReplacement(stmt, Collections.<Statement>singletonList(stmt));
        if(stmt.isTerminal()) {
            b = b.setBlock(b.getBlock().setIsTerminal(null, true));
        }
        addStatement(b);
    }

    /**
     * An internal expression has a symbol that is tagged internal. Check if
     * this is such a node
     *
     * @param expression expression to check for internal symbol
     * @return true if internal, false otherwise
     */
    private static boolean isInternalExpression(final Expression expression) {
        if (!(expression instanceof IdentNode)) {
            return false;
        }
        final Symbol symbol = ((IdentNode)expression).getSymbol();
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
        final Node e = expression;
        if (e instanceof BinaryNode) {
            final Node lhs = ((BinaryNode)e).lhs();
            if (lhs instanceof IdentNode) {
                return ((IdentNode)lhs).getName().equals(RETURN.symbolName());
            }
        }
        return false;
    }

    private void throwNotImplementedYet(final String msgId, final Node node) {
        final long token = node.getToken();
        final int line = source.getLine(node.getStart());
        final int column = source.getColumn(node.getStart());
        final String message = ECMAErrors.getMessage("unimplemented." + msgId);
        final String formatted = ErrorManager.format(message, source, line, column, token);
        throw new RuntimeException(formatted);
    }
}

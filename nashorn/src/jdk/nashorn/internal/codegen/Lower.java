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

import static jdk.nashorn.internal.codegen.CompilerConstants.ARGUMENTS;
import static jdk.nashorn.internal.codegen.CompilerConstants.CALLEE;
import static jdk.nashorn.internal.codegen.CompilerConstants.EVAL;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCRIPT_RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.VARARGS;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.BaseNode;
import jdk.nashorn.internal.ir.BinaryNode;
import jdk.nashorn.internal.ir.Block;
import jdk.nashorn.internal.ir.BreakNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.CaseNode;
import jdk.nashorn.internal.ir.CatchNode;
import jdk.nashorn.internal.ir.ContinueNode;
import jdk.nashorn.internal.ir.DoWhileNode;
import jdk.nashorn.internal.ir.EmptyNode;
import jdk.nashorn.internal.ir.ExecuteNode;
import jdk.nashorn.internal.ir.ForNode;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.IfNode;
import jdk.nashorn.internal.ir.IndexNode;
import jdk.nashorn.internal.ir.LabelNode;
import jdk.nashorn.internal.ir.LabeledNode;
import jdk.nashorn.internal.ir.LineNumberNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.ThrowNode;
import jdk.nashorn.internal.ir.TryNode;
import jdk.nashorn.internal.ir.UnaryNode;
import jdk.nashorn.internal.ir.VarNode;
import jdk.nashorn.internal.ir.WhileNode;
import jdk.nashorn.internal.ir.WithNode;
import jdk.nashorn.internal.ir.visitor.NodeOperatorVisitor;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.parser.Token;
import jdk.nashorn.internal.parser.TokenType;
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

final class Lower extends NodeOperatorVisitor {

    /**
     * Nesting level stack. Currently just used for loops to avoid the problem
     * with terminal bodies that end with throw/return but still do continues to
     * outer loops or same loop.
     */
    private final Deque<Node> nesting;

    private static final DebugLogger LOG = new DebugLogger("lower");

    private Node lastStatement;

    private List<Node> statements;

    /**
     * Constructor.
     *
     * @param compiler the compiler
     */
    Lower() {
        this.nesting    = new ArrayDeque<>();
        this.statements = new ArrayList<>();
    }

    @Override
    public Node enter(final Block block) {
        final Node       savedLastStatement = lastStatement;
        final List<Node> savedStatements    = statements;

        try {
            this.statements = new ArrayList<>();
            for (final Node statement : block.getStatements()) {
                statement.accept(this);
                /*
                 * This is slightly unsound, for example if we have a loop with
                 * a guarded statement like if (x) continue in the body and the
                 * body ends with TERMINAL, e.g. return; we removed the continue
                 * before we had the loop stack, as all we cared about was a
                 * return last in the loop.
                 *
                 * @see NASHORN-285
                 */
                if (lastStatement != null && lastStatement.isTerminal()) {
                    copyTerminal(block, lastStatement);
                    break;
                }
            }
            block.setStatements(statements);

        } finally {
            this.statements = savedStatements;
            this.lastStatement = savedLastStatement;
        }

        return null;
    }

    @Override
    public Node enter(final BreakNode breakNode) {
        return enterBreakOrContinue(breakNode);
    }

    @Override
    public Node enter(final CallNode callNode) {
        final Node function = markerFunction(callNode.getFunction());
        callNode.setFunction(function);
        checkEval(callNode); //check if this is an eval call and store the information
        return callNode;
    }

    @Override
    public Node leave(final CaseNode caseNode) {
        caseNode.copyTerminalFlags(caseNode.getBody());
        return caseNode;
    }

    @Override
    public Node leave(final CatchNode catchNode) {
        catchNode.copyTerminalFlags(catchNode.getBody());
        addStatement(catchNode);
        return catchNode;
    }

    @Override
    public Node enter(final ContinueNode continueNode) {
        return enterBreakOrContinue(continueNode);
    }

    @Override
    public Node enter(final DoWhileNode doWhileNode) {
        return enter((WhileNode)doWhileNode);
    }

    @Override
    public Node leave(final DoWhileNode doWhileNode) {
        return leave((WhileNode)doWhileNode);
    }

    @Override
    public Node enter(final EmptyNode emptyNode) {
        return null;
    }

    @Override
    public Node leave(final ExecuteNode executeNode) {
        final Node expr = executeNode.getExpression();

        if (getCurrentFunctionNode().isScript()) {
            if (!(expr instanceof Block)) {
                if (!isInternalExpression(expr) && !isEvalResultAssignment(expr)) {
                    executeNode.setExpression(new BinaryNode(executeNode.getSource(), Token.recast(executeNode.getToken(), TokenType.ASSIGN),
                            getCurrentFunctionNode().getResultNode(),
                            expr));
                }
            }
        }

        copyTerminal(executeNode, executeNode.getExpression());
        addStatement(executeNode);

        return executeNode;
    }

    @Override
    public Node enter(final ForNode forNode) {
        nest(forNode);
        return forNode;
    }

    @Override
    public Node leave(final ForNode forNode) {
        final Node  test = forNode.getTest();
        final Block body = forNode.getBody();

        if (!forNode.isForIn() && test == null) {
            setHasGoto(forNode);
        }

        final boolean escapes = controlFlowEscapes(body);
        if (escapes) {
            setTerminal(body, false);
        }

        // pop the loop from the loop context
        unnest(forNode);

        if (!forNode.isForIn() && conservativeAlwaysTrue(test)) {
            forNode.setTest(null);
            setTerminal(forNode, !escapes);
        }

        addStatement(forNode);

        return forNode;
    }

    @Override
    public Node enter(final FunctionNode functionNode) {
        LOG.info("START FunctionNode: " + functionNode.getName());

        if (functionNode.isLazy()) {
            LOG.info("LAZY: " + functionNode.getName());
            return null;
        }

        initFunctionNode(functionNode);

        Node initialEvalResult = LiteralNode.newInstance(functionNode, ScriptRuntime.UNDEFINED);

        nest(functionNode);

        /*
         * As we are evaluating a nested structure, we need to store the
         * statement list for the surrounding block and restore it when the
         * function is done
         */
        final List<Node> savedStatements = statements;
        final Node savedLastStatement = lastStatement;

        statements    = new ArrayList<>();
        lastStatement = null;

        // for initial eval result is the last declared function
        for (final FunctionNode nestedFunction : functionNode.getFunctions()) {
            final IdentNode ident = nestedFunction.getIdent();
            if (ident != null && nestedFunction.isStatement()) {
                initialEvalResult = new IdentNode(ident);
            }
        }

        if (functionNode.needsSelfSymbol()) {
            //function needs to start with var funcIdent = __callee_;
            statements.add(functionNode.getSelfSymbolInit().accept(this));
        }

        try {
            // Every nested function needs a definition in the outer function with its name. Add these.
            for (final FunctionNode nestedFunction : functionNode.getFunctions()) {
                final VarNode varNode = nestedFunction.getFunctionVarNode();
                if (varNode != null) {
                    final LineNumberNode lineNumberNode = nestedFunction.getFunctionVarLineNumberNode();
                    if (lineNumberNode != null) {
                        lineNumberNode.accept(this);
                    }
                    varNode.accept(this);
                    varNode.setIsFunctionVarNode();
                }
            }

            if (functionNode.isScript()) {
                new ExecuteNode(functionNode.getSource(), functionNode.getFirstToken(), functionNode.getFinish(), initialEvalResult).accept(this);
            }

            //do the statements - this fills the block with code
            for (final Node statement : functionNode.getStatements()) {
                statement.accept(this);
                //If there are unused terminated endpoints in the function, we need
                // to add a "return undefined" in those places for correct semantics
                LOG.info("Checking lastStatement="+lastStatement+" for terminal flags");
                if (lastStatement != null && lastStatement.hasTerminalFlags()) {
                    copyTerminal(functionNode, lastStatement);
                    break;
                }
            }

            functionNode.setStatements(statements);

            if (!functionNode.isTerminal()) {
                guaranteeReturn(functionNode);
            }

            //lower all nested functions
            for (final FunctionNode nestedFunction : functionNode.getFunctions()) {
                nestedFunction.accept(this);
            }

        } finally {
            statements    = savedStatements;
            lastStatement = savedLastStatement;
        }

        LOG.info("END FunctionNode: " + functionNode.getName());
        unnest(functionNode);

        return null;
    }

    @Override
    public Node enter(final IfNode ifNode) {
        return nest(ifNode);
    }

    @Override
    public Node leave(final IfNode ifNode) {
        final Node pass = ifNode.getPass();
        final Node fail = ifNode.getFail();

        if (pass.isTerminal() && fail != null && fail.isTerminal()) {
            setTerminal(ifNode,  true);
        }

        addStatement(ifNode);
        unnest(ifNode);

        return ifNode;
    }

    @Override
    public Node enter(LabelNode labelNode) {
        final Block body = labelNode.getBody();
        body.accept(this);
        copyTerminal(labelNode, body);
        addStatement(labelNode);
        return null;
    }

    @Override
    public Node enter(final LineNumberNode lineNumberNode) {
        addStatement(lineNumberNode, false); // don't put it in lastStatement cache
        return null;
    }

    @Override
    public Node enter(final ReturnNode returnNode) {
        final TryNode tryNode = returnNode.getTryChain();
        final Node    expr    = returnNode.getExpression();

        if (tryNode != null) {
            //we are inside a try block - we don't necessarily have a result node yet. attr will do that.
            if (expr != null) {
                final Source source = getCurrentFunctionNode().getSource();

                //we need to evaluate the result of the return in case it is complex while
                //still in the try block, store it in a result value and return it afterwards
                final long token        = returnNode.getToken();
                final Node resultNode   = new IdentNode(getCurrentFunctionNode().getResultNode());
                final Node assignResult = new BinaryNode(source, Token.recast(token, TokenType.ASSIGN), resultNode, expr);

                //add return_in_try = expr; to try block
                new ExecuteNode(source, token, Token.descPosition(token), assignResult).accept(this);

                //splice in the finally code, inlining it here
                if (copyFinally(tryNode, null)) {
                    return null;
                }

                //make sure that the return node now returns 'return_in_try'
                returnNode.setExpression(resultNode);
            } else if (copyFinally(tryNode, null)) {
                return null;
            }
        } else if (expr != null) {
            returnNode.setExpression(expr.accept(this));
        }

        addStatement(returnNode);

        return null;
    }

    @Override
    public Node leave(final ReturnNode returnNode) {
        addStatement(returnNode); //ReturnNodes are always terminal, marked as such in constructor
        return returnNode;
    }

    @Override
    public Node enter(final SwitchNode switchNode) {
        nest(switchNode);
        return switchNode;
    }

    @Override
    public Node leave(final SwitchNode switchNode) {
        unnest(switchNode);

        final List<CaseNode> cases       = switchNode.getCases();
        final CaseNode       defaultCase = switchNode.getDefaultCase();

        boolean allTerminal = !cases.isEmpty();
        for (final CaseNode caseNode : switchNode.getCases()) {
            allTerminal &= caseNode.isTerminal();
        }

        if (allTerminal && defaultCase != null && defaultCase.isTerminal()) {
            setTerminal(switchNode, true);
        }

        addStatement(switchNode);

        return switchNode;
    }

    @Override
    public Node leave(final ThrowNode throwNode) {
        addStatement(throwNode); //ThrowNodes are always terminal, marked as such in constructor
        return throwNode;
    }

    @Override
    public Node enter(final TryNode tryNode) {
        final Block  finallyBody = tryNode.getFinallyBody();
        final long   token       = tryNode.getToken();
        final int    finish      = tryNode.getFinish();

        nest(tryNode);

        if (finallyBody == null) {
            //do nothing if no finally exists
            return tryNode;
        }

        /*
         * We have a finally clause.
         *
         * Transform to do finally tail duplication as follows:
         *
         * <pre>
         *  try {
         *    try_body
         *  } catch e1 {
         *    catchbody_1
         *  }
         *  ...
         *  } catch en {
         *    catchbody_n
         *  } finally {
         *    finally_body
         *  }
         *
         *  (where e1 ... en are optional)
         *
         *  turns into
         *
         *  try {
         *    try {
         *      try_body
         *    } catch e1 {
         *      catchbody1
         *      //nothing inlined explicitly here, return, break other
         *      //terminals may inline the finally body
         *      ...
         *    } catch en {
         *      catchbody2
         *      //nothing inlined explicitly here, return, break other
         *      //terminals may inline the finally body
         *    }
         *  } catch all ex {
         *      finally_body_inlined
         *      rethrow ex
         *  }
         *  finally_body_inlined
         * </pre>
         *
         * If tries are catches are terminal, visitors for return, break &
         * continue will handle the tail duplications. Throw needs to be
         * treated specially with the catchall as described in the above
         * ASCII art.
         *
         * If the try isn't terminal we do the finally_body_inlined at the
         * end. If the try is terminated with continue/break/return the
         * existing visitor logic will inline the finally before that
         * operation. if the try is terminated with a throw, the catches e1
         * ... en will have a chance to process the exception. If the
         * appropriate catch e1..en is non terminal we fall through to the
         * last finally_body_inlined. if the catch e1...en IS terminal with
         * continue/break/return existing visitor logic will fix it. If they
         * are terminal with another throw it goes to the catchall and the
         * finally_body_inlined marked (*) will fix it before rethrowing
         * whatever problem there was for identical semantic.
         */
        final Source source = getCurrentFunctionNode().getSource();

        // if try node does not contain a catch we can skip creation of a new
        // try node and just append our synthetic catch to the existing try node.
        if (!tryNode.getCatchBlocks().isEmpty()) {
            // insert an intermediate try-catch* node, where we move the body and all catch blocks.
            // the original try node become a try-finally container for the new try-catch* node.
            // because we don't clone (to avoid deep copy), we have to fix the block chain in the end.
            final TryNode innerTryNode;
            innerTryNode = new TryNode(source, token, finish, tryNode.getNext());
            innerTryNode.setBody(tryNode.getBody());
            innerTryNode.setCatchBlocks(tryNode.getCatchBlocks());

            // set outer tryNode's body to innerTryNode
            final Block outerBody;
            outerBody = new Block(source, token, finish, tryNode.getBody().getParent(), getCurrentFunctionNode());
            outerBody.setStatements(new ArrayList<Node>(Arrays.asList(innerTryNode)));
            tryNode.setBody(outerBody);
            tryNode.setCatchBlocks(null);

            // now before we go on, we have to fix the block parents
            // (we repair the block tree after the insertion so that all references are intact)
            innerTryNode.getBody().setParent(tryNode.getBody());
            for (final Block block : innerTryNode.getCatchBlocks()) {
                block.setParent(tryNode.getBody());
            }
        }

        // create a catch-all that inlines finally and rethrows

        final Block catchBlock      = new Block(source, token, finish, getCurrentBlock(), getCurrentFunctionNode());
        //this catch block should get define symbol

        final Block catchBody       = new Block(source, token, finish, catchBlock, getCurrentFunctionNode());
        final Node  catchAllFinally = finallyBody.clone();

        catchBody.addStatement(new ExecuteNode(source, finallyBody.getToken(), finallyBody.getFinish(), catchAllFinally));
        setTerminal(catchBody, true);

        final CatchNode catchAllNode;
        final IdentNode exception;

        exception    = new IdentNode(source, token, finish, getCurrentFunctionNode().uniqueName("catch_all"));
        catchAllNode = new CatchNode(source, token, finish, new IdentNode(exception), null, catchBody);
        catchAllNode.setIsSyntheticRethrow();

        catchBlock.addStatement(catchAllNode);

        // replace all catches of outer tryNode with the catch-all
        tryNode.setCatchBlocks(new ArrayList<>(Arrays.asList(catchBlock)));

        /*
         * We leave the finally block for the original try in place for now
         * so that children visitations will work. It is removed and placed
         * afterwards in the else case below, after all children are visited
         */

        return tryNode;
    }

    @Override
    public Node leave(final TryNode tryNode) {
        final Block finallyBody   = tryNode.getFinallyBody();

        boolean allTerminal = tryNode.getBody().isTerminal() && (finallyBody == null || finallyBody.isTerminal());

        for (final Block catchBlock : tryNode.getCatchBlocks()) {
            allTerminal &= catchBlock.isTerminal();
        }

        tryNode.setIsTerminal(allTerminal);

        addStatement(tryNode);
        unnest(tryNode);

        // if finally body is present, place it after the tryNode
        if (finallyBody != null) {
            tryNode.setFinallyBody(null);
            addStatement(finallyBody);
        }

        return tryNode;
    }

    @Override
    public Node leave(final VarNode varNode) {
        addStatement(varNode);
        return varNode;
    }

    @Override
    public Node enter(final WhileNode whileNode) {
        return nest(whileNode);
    }

    @Override
    public Node leave(final WhileNode whileNode) {
        final Node test = whileNode.getTest();

        if (test == null) {
            setHasGoto(whileNode);
        }

        final Block   body    = whileNode.getBody();
        final boolean escapes = controlFlowEscapes(body);
        if (escapes) {
            setTerminal(body, false);
        }

        Node node = whileNode;

        if (body.isTerminal()) {
            if (whileNode instanceof DoWhileNode) {
                setTerminal(whileNode, true);
            } else if (conservativeAlwaysTrue(test)) {
                node = new ForNode(whileNode.getSource(), whileNode.getToken(), whileNode.getFinish());
                ((ForNode)node).setBody(body);
                ((ForNode)node).accept(this);
                setTerminal(node, !escapes);
            }
        }

        // pop the loop from the loop context
        unnest(whileNode);
        addStatement(node);

        return node;
    }

    @Override
    public Node leave(final WithNode withNode) {
        if (withNode.getBody().isTerminal()) {
            setTerminal(withNode,  true);
        }
        addStatement(withNode);

        return withNode;
    }

    @Override
    public Node leaveDELETE(final UnaryNode unaryNode) {
        final Node rhs = unaryNode.rhs();
        if (rhs instanceof IdentNode || rhs instanceof BaseNode) {
            return unaryNode;
        }
        addStatement(new ExecuteNode(rhs));
        return LiteralNode.newInstance(unaryNode, true);
    }

    /**
     * Given a function node that is a callee in a CallNode, replace it with
     * the appropriate marker function. This is used by {@link CodeGenerator}
     * for fast scope calls
     *
     * @param function function called by a CallNode
     * @return transformed node to marker function or identity if not ident/access/indexnode
     */
    private static Node markerFunction(final Node function) {
        if (function instanceof IdentNode) {
            return new IdentNode((IdentNode)function) {
                @Override
                public boolean isFunction() {
                    return true;
                }
            };
        } else if (function instanceof AccessNode) {
            return new AccessNode((AccessNode)function) {
                @Override
                public boolean isFunction() {
                    return true;
                }
            };
        } else if (function instanceof IndexNode) {
            return new IndexNode((IndexNode)function) {
                @Override
                public boolean isFunction() {
                    return true;
                }
            };
        }

        return function;
    }

    /**
     * Calculate a synthetic eval location for a node for the stacktrace, for example src#17<eval>
     * @param node a node
     * @return eval location
     */
    private static String evalLocation(final IdentNode node) {
        return new StringBuilder().
            append(node.getSource().getName()).
            append('#').
            append(node.getSource().getLine(node.position())).
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
    private void checkEval(final CallNode callNode) {
        if (callNode.getFunction() instanceof IdentNode) {

            final List<Node> args   = callNode.getArgs();
            final IdentNode  callee = (IdentNode)callNode.getFunction();

            // 'eval' call with at least one argument
            if (args.size() >= 1 && EVAL.tag().equals(callee.getName())) {
                final CallNode.EvalArgs evalArgs =
                    new CallNode.EvalArgs(
                        args.get(0).clone().accept(this), //clone as we use this for the "is eval case". original evaluated separately for "is not eval case"
                        getCurrentFunctionNode().getThisNode(),
                        evalLocation(callee),
                        getCurrentFunctionNode().isStrictMode());
                callNode.setEvalArgs(evalArgs);
            }
        }
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
    private boolean controlFlowEscapes(final Node loopBody) {
        final List<Node> escapes = new ArrayList<>();

        loopBody.accept(new NodeVisitor() {
            @Override
            public Node leave(final BreakNode node) {
                escapes.add(node);
                return node;
            }

            @Override
            public Node leave(final ContinueNode node) {
                // all inner loops have been popped.
                if (nesting.contains(node.getTargetNode())) {
                    escapes.add(node);
                }
                return node;
            }
        });

        return !escapes.isEmpty();
    }

    private void guaranteeReturn(final FunctionNode functionNode) {
        Node resultNode;

        if (functionNode.isScript()) {
            resultNode = functionNode.getResultNode(); // the eval result, symbol assigned in Attr
        } else {
            if (lastStatement != null && lastStatement.isTerminal() || lastStatement instanceof ReturnNode) {
                return; //already in place or not needed, as it should be for a non-undefined returning function
            }
            resultNode = LiteralNode.newInstance(functionNode, ScriptRuntime.UNDEFINED);
        }

        //create a return statement
        final Node returnNode = new ReturnNode(functionNode.getSource(), functionNode.getLastToken(), functionNode.getFinish(), resultNode, null);
        returnNode.accept(this);
    }


    private Node nest(final Node node) {
        LOG.info("Nesting: " + node);
        LOG.indent();
        nesting.push(node);
        return node;
    }

    private void unnest(final Node node) {
        LOG.unindent();
        assert nesting.getFirst() == node : "inconsistent nesting order : " + nesting.getFirst() + " != " + node;
        LOG.info("Unnesting: " + nesting);
        nesting.pop();
    }

    private static void setTerminal(final Node node, final boolean isTerminal) {
        LOG.info("terminal = " + isTerminal + " for " + node);
        node.setIsTerminal(isTerminal);
    }

    private static void setHasGoto(final Node node) { //, final boolean hasGoto) {
        LOG.info("hasGoto = true for " + node);
        node.setHasGoto();
    }

    private static void copyTerminal(final Node node, final Node sourceNode) {
        LOG.info("copy terminal flags " + sourceNode + " -> " + node);
        node.copyTerminalFlags(sourceNode);
    }

    private void addStatement(final Node statement, final boolean storeInLastStatement) {
        LOG.info("add statement = " + statement + " (lastStatement = " + lastStatement + ")");
        statements.add(statement);
        if (storeInLastStatement) {
            lastStatement = statement;
        }
    }

    private void addStatement(final Node statement) {
        addStatement(statement, true);
    }

    /**
     * Determine if Try block is inside target block.
     *
     * @param tryNode Try node to test.
     * @param target  Target block.
     *
     * @return true if try block is inside the target, false otherwise.
     */
    private boolean isNestedTry(final TryNode tryNode, final Block target) {
        for (Block current = getCurrentBlock(); current != target; current = current.getParent()) {
            if (tryNode.getBody() == current) {
                return true;
            }

            for (final Block catchBlock : tryNode.getCatchBlocks()) {
                if (catchBlock == current) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Clones the body of the try finallys up to the target block.
     *
     * @param node       first try node in the chain.
     * @param targetNode target block of the break/continue statement or null for return
     *
     * @return true if terminates.
     */
    private boolean copyFinally(final TryNode node, final Node targetNode) {
        Block target = null;

        if (targetNode instanceof Block) {
            target = (Block)targetNode;
        }

        for (TryNode tryNode = node; tryNode != null; tryNode = tryNode.getNext()) {
            if (target != null && !isNestedTry(tryNode, target)) {
                return false;
            }

            Block finallyBody = tryNode.getFinallyBody();
            if (finallyBody == null) {
                continue;
            }

            finallyBody = (Block)finallyBody.clone();
            final boolean hasTerminalFlags = finallyBody.hasTerminalFlags();

            new ExecuteNode(finallyBody.getSource(), finallyBody.getToken(), finallyBody.getFinish(), finallyBody).accept(this);

            if (hasTerminalFlags) {
                getCurrentBlock().copyTerminalFlags(finallyBody);
                return true;
            }
        }

        return false;
    }

    private Node enterBreakOrContinue(final LabeledNode labeledNode) {
        final TryNode tryNode = labeledNode.getTryChain();
        if (tryNode != null && copyFinally(tryNode, labeledNode.getTargetNode())) {
            return null;
        }
        addStatement(labeledNode);
        return null;
    }


    /**
     * An internal expression has a symbol that is tagged internal. Check if
     * this is such a node
     *
     * @param expression expression to check for internal symbol
     * @return true if internal, false otherwise
     */
    private static boolean isInternalExpression(final Node expression) {
        final Symbol symbol = expression.getSymbol();
        return symbol != null && symbol.isInternal();
    }

    /**
     * Is this an assignment to the special variable that hosts scripting eval
     * results?
     *
     * @param expression expression to check whether it is $evalresult = X
     * @return true if an assignment to eval result, false otherwise
     */
    private boolean isEvalResultAssignment(final Node expression) {
        Node e = expression;
        if (e.tokenType() == TokenType.DISCARD) {
            e = ((UnaryNode)expression).rhs();
        }
        final Node resultNode = getCurrentFunctionNode().getResultNode();
        return e instanceof BinaryNode && ((BinaryNode)e).lhs().equals(resultNode);
    }

    /**
     * Prepare special function nodes.
     * TODO : only create those that are needed.
     * TODO : make sure slot numbering is not hardcoded in {@link CompilerConstants} - now creation order is significant
     */
    private static void initFunctionNode(final FunctionNode functionNode) {
        final Source source = functionNode.getSource();
        final long token    = functionNode.getToken();
        final int  finish   = functionNode.getFinish();

        functionNode.setThisNode(new IdentNode(source, token, finish, THIS.tag()));
        functionNode.setScopeNode(new IdentNode(source, token, finish, SCOPE.tag()));
        functionNode.setResultNode(new IdentNode(source, token, finish, SCRIPT_RETURN.tag()));
        functionNode.setCalleeNode(new IdentNode(source, token, finish, CALLEE.tag()));
        if (functionNode.isVarArg()) {
            functionNode.setVarArgsNode(new IdentNode(source, token, finish, VARARGS.tag()));
            if (functionNode.needsArguments()) {
                functionNode.setArgumentsNode(new IdentNode(source, token, finish, ARGUMENTS.tag()));
            }
        }
    }

}




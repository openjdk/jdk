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
import static jdk.nashorn.internal.codegen.CompilerConstants.EXCEPTION_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.ITERATOR_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCOPE;
import static jdk.nashorn.internal.codegen.CompilerConstants.SCRIPT_RETURN;
import static jdk.nashorn.internal.codegen.CompilerConstants.SWITCH_TAG_PREFIX;
import static jdk.nashorn.internal.codegen.CompilerConstants.THIS;
import static jdk.nashorn.internal.codegen.CompilerConstants.VARARGS;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.ADD;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.DELETE;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.EQ;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.EQ_STRICT;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.FAIL_DELETE;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.GE;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.GT;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.IN;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.INSTANCEOF;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.LE;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.LT;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.NE;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.NE_STRICT;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.TYPEOF;
import static jdk.nashorn.internal.ir.RuntimeNode.Request.VOID;
import static jdk.nashorn.internal.ir.Symbol.IS_GLOBAL;
import static jdk.nashorn.internal.ir.Symbol.IS_INTERNAL;
import static jdk.nashorn.internal.ir.Symbol.IS_LET;
import static jdk.nashorn.internal.ir.Symbol.IS_PARAM;
import static jdk.nashorn.internal.ir.Symbol.IS_THIS;
import static jdk.nashorn.internal.ir.Symbol.IS_VAR;
import static jdk.nashorn.internal.runtime.ScriptRuntime.UNDEFINED;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import jdk.nashorn.internal.codegen.types.Type;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.Assignment;
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
import jdk.nashorn.internal.ir.LineNumberNode;
import jdk.nashorn.internal.ir.LiteralNode;
import jdk.nashorn.internal.ir.LiteralNode.ArrayLiteralNode;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.ObjectNode;
import jdk.nashorn.internal.ir.PropertyNode;
import jdk.nashorn.internal.ir.ReferenceNode;
import jdk.nashorn.internal.ir.ReturnNode;
import jdk.nashorn.internal.ir.RuntimeNode;
import jdk.nashorn.internal.ir.RuntimeNode.Request;
import jdk.nashorn.internal.ir.SwitchNode;
import jdk.nashorn.internal.ir.Symbol;
import jdk.nashorn.internal.ir.TernaryNode;
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
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.ECMAException;
import jdk.nashorn.internal.runtime.JSType;
import jdk.nashorn.internal.runtime.Property;
import jdk.nashorn.internal.runtime.PropertyMap;
import jdk.nashorn.internal.runtime.ScriptFunction;
import jdk.nashorn.internal.runtime.ScriptObject;
import jdk.nashorn.internal.runtime.ScriptRuntime;
import jdk.nashorn.internal.runtime.Source;
import jdk.nashorn.internal.runtime.Undefined;

/**
 * Lower to more primitive operations. After lowering, an AST has symbols and
 * types. Lowering may also add specialized versions of methods to the script if
 * the optimizer is turned on.
 *
 * Any expression that requires temporary storage as part of computation will
 * also be detected here and give a temporary symbol
 */

final class Lower extends NodeOperatorVisitor {
    /** Current compiler. */
    private final Compiler compiler;

    /** Current source. */
    private final Source source;

    /** List of lowered statements */
    private List<Node> statements;

    /** All symbols that are declared locally in a function node */
    private List<Symbol> declaredSymbolsLocal;

    /**
     * Local definitions in current block (to discriminate from function
     * declarations always defined in the function scope. This is for
     * "can be undefined" analysis.
     */
    private Set<String> localDefs;

    /**
     * Local definitions in current block to guard against cases like
     * NASHORN-467 when things can be undefined as they are used before
     * their local var definition. *sigh* JavaScript...
     */
    private Set<String> localUses;

    /**
     * Nesting level stack. Currently just used for loops to avoid the problem
     * with terminal bodies that end with throw/return but still do continues to
     * outer loops or same loop.
     */
    private final Deque<Node> nesting;

    private static final DebugLogger LOG   = new DebugLogger("lower");
    private static final boolean     DEBUG = LOG.isEnabled();

    /**
     * Constructor.
     *
     * @param compiler the compiler
     */
    Lower(final Compiler compiler) {
        this.compiler   = compiler;
        this.source     = compiler.getSource();
        this.statements = new ArrayList<>();
        this.nesting    = new ArrayDeque<>();
    }

    private void nest(final Node node) {
        nesting.push(node);
    }

    private void unnest() {
        nesting.pop();
    }

    static void debug(final String str) {
        if (DEBUG) {
            LOG.info(str);
        }
    }

    @Override
    public Node leave(final AccessNode accessNode) {
        //accessNode.setBase(convert(accessNode.getBase(), Type.OBJECT));
        getCurrentFunctionNode().newTemporary(Type.OBJECT, accessNode); //This is not always an object per se, but currently the narrowing logic resides in AccessSpecializer. @see AccessSpecializer!

        return accessNode;
    }

    @Override
    public Node enter(final Block block) {
        /*
         * Save the statement list from the outer construct we are currently
         * generating and push frame
         */
        final List<Node>  savedStatements = statements;
        final Set<String> savedDefs       = localDefs;
        final Set<String> savedUses       = localUses;

        block.setFrame(getCurrentFunctionNode().pushFrame());

        try {
            /*
             * Reset the statement instance var, new block
             */
            statements = new ArrayList<>();
            localDefs  = new HashSet<>(savedDefs);
            localUses  = new HashSet<>(savedUses);

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
                final Node lastStatement = Node.lastStatement(statements);
                if (lastStatement != null && lastStatement.isTerminal()) {
                    block.copyTerminalFlags(lastStatement);
                    break;
                }
            }

            block.setStatements(statements);
        } finally {
            /*
             * Restore the saved statements after block ends and pop frame
             */
            statements = savedStatements;
            localDefs  = savedDefs;
            localUses  = savedUses;

            getCurrentFunctionNode().popFrame();
        }

        return null;
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

            new ExecuteNode(source, finallyBody.getToken(), finallyBody.getFinish(), finallyBody).accept(this);

            if (hasTerminalFlags) {
                getCurrentBlock().copyTerminalFlags(finallyBody);
                return true;
            }
        }

        return false;
    }

    @Override
    public Node enter(final BreakNode breakNode) {
        final TryNode tryNode = breakNode.getTryChain();

        if (tryNode != null && copyFinally(tryNode, breakNode.getTargetNode())) {
            return null;
        }

        statements.add(breakNode);

        return null;
    }

    /**
     * Blesses nodes with an expectant type, converting if necessary.
     *
     * @param node Node to be blest.
     * @param type Type class expected.
     *
     * @return Converted node or original node if no conversion is needed.
     */
    private Node convert(final Node node, final Type type) {

        final Symbol       symbol       = node.getSymbol();
        final FunctionNode functionNode = getCurrentFunctionNode();

        assert !type.isUnknown() : "unknown";

        /*
         * Conversions are now mandatory, have to be placed at code time and
         * cannot be removed as there might be cases like:
         *
         * var x = 17; if (x + 1) { ... }
         *
         * x = Number(4711); if (x + 1) { ... }
         *
         * In the old world this behaved as follows:
         *
         * Here, x is originally inferred type to a number, then the if does a
         * double add without a cast. However, the next statement turns x into
         * an object, and this messes up the original. If we would now suddenly
         * need an explicit cast for the first addition, given that we don't do
         * fancy stuff like splitting live range.
         *
         * Even worse is the case where we have an eval that modifies local
         * variables in the middle of a function and may widen a type suspected
         * to be at most e.g. NUMBER to e.g. OBJECT.
         *
         * There are a few local optimizations we can do. If we want to do an
         * OBJECT to OBJECT cast, for example, we can skip it as already is
         * maximally wide, except if we are in a method with an eval where
         * everything is possible...
         *
         * @see test/test262/test/suite/ch07/7.2/S7.2_A1.4_T2.js for an example
         * of this.
         */

        assert symbol != null : "no symbol for " + node;

        /* check object to object cast */
        if (!functionNode.hasEval() && node.getType().isEquivalentTo(Type.OBJECT) && type.isEquivalentTo(Type.OBJECT)) {
            return node;
        }

        Node resultNode = node;

        // Literal nodes may be converted directly

        if (node instanceof LiteralNode) {
            final LiteralNode<?> convertedLiteral = new LiteralNodeConstantEvaluator((LiteralNode<?>)node, type).eval();
            if (convertedLiteral != null) {
                resultNode = newLiteral(convertedLiteral);
            }
            // object literals still need the cast
            if (type.isObject()) {
                resultNode = new UnaryNode(source, Token.recast(node.getToken(), TokenType.CONVERT), node);
            }
        } else {
            if (resultNode.getSymbol().isParam()) {
                resultNode.getSymbol().setType(type);
            }
             resultNode = new UnaryNode(source, Token.recast(node.getToken(), TokenType.CONVERT), resultNode);
        }

        functionNode.newTemporary(type, resultNode);
        resultNode.copyTerminalFlags(node);

        return resultNode;
    }

    /**
     * Accept and convert all arguments to type Object. If we have a
     * specialization profile for this function, we instead try to specialize
     * the arguments before the casts based on their current types and values.
     *
     * @param callNode function call
     * @return return type for call
     */
    private Type acceptArgs(final CallNode callNode) {
        final List<Node> oldArgs = callNode.getArgs();
        final List<Node> acceptedArgs = new ArrayList<>(oldArgs.size());

        for (final Node arg : oldArgs) {
            //acceptedArgs.add(convert(arg.accept(this), OBJECT));
            acceptedArgs.add(arg.accept(this));
        }
        callNode.setArgs(acceptedArgs);

        return Type.OBJECT;
    }

    private static String evalLocation(final IdentNode node) {
        final StringBuilder sb = new StringBuilder(node.getSource().getName());

        sb.append('#');
        sb.append(node.getSource().getLine(node.position()));
        sb.append("<eval>");

        return sb.toString();
    }

    private void checkEval(final CallNode callNode) {
        if (callNode.getFunction() instanceof IdentNode) {

            final List<Node> args   = callNode.getArgs();
            final IdentNode  callee = (IdentNode)callNode.getFunction();

            // 'eval' call with atleast one argument
            if (args.size() >= 1 && EVAL.tag().equals(callee.getName())) {
                final CallNode.EvalArgs evalArgs = new CallNode.EvalArgs();
                // code that is evaluated
                evalArgs.code = args.get(0).clone();
                evalArgs.code.accept(this);
                // 'this' to be passed to evaluated code
                evalArgs.evalThis = new IdentNode(getCurrentFunctionNode().getThisNode());
                // location string of the eval call
                evalArgs.location = evalLocation(callee);
                // strict mode context or not?
                evalArgs.strictMode = getCurrentFunctionNode().isStrictMode();
                callNode.setEvalArgs(evalArgs);
            }
        }
    }

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

    @Override
    public Node enter(final CallNode callNode) {
        final Node function       = callNode.getFunction();
        final Node markedFunction = markerFunction(function);

        callNode.setFunction(markedFunction.accept(this));

        checkEval(callNode);

        final Type returnType = acceptArgs(callNode);
        getCurrentFunctionNode().newTemporary(returnType, callNode);
        callNode.getFunction().getSymbol().setType(returnType);

        return null;
    }

    @Override
    public Node leave(final CaseNode caseNode) {
        caseNode.copyTerminalFlags(caseNode.getBody());

        return caseNode;
    }

    @Override
    public Node enter(final CatchNode catchNode) {
        final IdentNode ident = catchNode.getException();
        final Block     block = getCurrentBlock();

        // define block-local exception variable
        block.defineSymbol(ident.getName(), IS_VAR | IS_LET, ident).setType(Type.OBJECT);
        localDefs.add(ident.getName());

        return catchNode;
    }

    @Override
    public Node leave(final CatchNode catchNode) {
        final Node exceptionCondition = catchNode.getExceptionCondition();
        if (exceptionCondition != null) {
            catchNode.setExceptionCondition(convert(exceptionCondition, Type.BOOLEAN));
        }

        catchNode.copyTerminalFlags(catchNode.getBody());

        statements.add(catchNode);

        return catchNode;
    }

    @Override
    public Node enter(final ContinueNode continueNode) {
        final TryNode tryNode = continueNode.getTryChain();
        final Node target = continueNode.getTargetNode();

        if (tryNode != null && copyFinally(tryNode, target)) {
            return null;
        }

        statements.add(continueNode);

        return null;
    }

    @Override
    public Node enter(final DoWhileNode whileNode) {
        return enter((WhileNode)whileNode);
    }

    @Override
    public Node leave(final DoWhileNode whileNode) {
        return leave((WhileNode)whileNode);
    }

    @Override
    public Node enter(final EmptyNode emptyNode) {
        return null;
    }

    /**
     * Is this an assignment to the special variable that hosts scripting eval
     * results?
     *
     * @param expression expression to check whether it is $evalresult = X
     *
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
     * Discard the result of the expression.
     *
     * @param expression Expression to discard.
     *
     * @return Discard node.
     */
    private Node discard(final Node expression) {
        expression.setDiscard(true);

        if (expression.getSymbol() != null) {
            final Node discard = new UnaryNode(source, Token.recast(expression.getToken(), TokenType.DISCARD), expression);
            discard.copyTerminalFlags(expression);

            return discard;
        }

        return expression;
    }

    /**
     * ExecuteNodes are needed to actually generate code, with a few exceptions
     * such as ReturnNodes and ThrowNodes and various control flow that can
     * standalone. Every other kind of statement needs to be wrapped in an
     * ExecuteNode in order to become code
     *
     * @param executeNode  the execute node to visit
     */
    @Override
    public Node leave(final ExecuteNode executeNode) {

        Node expression = executeNode.getExpression();

        /*
         * Handle the eval result for scripts. Every statement has to write its
         * return value to a special variable that is the result of the script.
         */
        if (getCurrentFunctionNode().isScript() && !(expression instanceof Block) && !isEvalResultAssignment(expression) && !isInternalExpression(expression)) {
            final Node resultNode = getCurrentFunctionNode().getResultNode();
            expression = new BinaryNode(source, Token.recast(executeNode.getToken(), TokenType.ASSIGN), resultNode, convert(expression, resultNode.getType()));

            getCurrentFunctionNode().newTemporary(Type.OBJECT, expression);
        }

        expression = discard(expression);
        executeNode.setExpression(expression);
        executeNode.copyTerminalFlags(expression);

        statements.add(executeNode);

        return executeNode;
    }

    /**
     * Helper that given a loop body makes sure that it is not terminal if it
     * has a continue that leads to the loop header or to outer loops' loop
     * headers. This means that, even if the body ends with a terminal
     * statement, we cannot tag it as terminal
     *
     * @param loopBody the loop body to check
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

    @Override
    public Node enter(final ForNode forNode) {
        // push the loop to the loop context
        nest(forNode);
        return forNode;
    }

    private static boolean conservativeAlwaysTrue(final Node node) {
        return node == null || ((node instanceof LiteralNode) && Boolean.TRUE.equals(((LiteralNode<?>)node).getValue()));
    }

    @Override
    public Node leave(final ForNode forNode) {
        final Node init   = forNode.getInit();
        final Node test   = forNode.getTest();
        final Node modify = forNode.getModify();

        if (forNode.isForIn()) {
            final String name = compiler.uniqueName(ITERATOR_PREFIX.tag());
            // DEFINE SYMBOL: may be any type, not local var
            final Symbol iter = getCurrentFunctionNode().defineSymbol(name, IS_VAR | IS_INTERNAL, null);
            iter.setType(Type.OBJECT); // NASHORN-73
            forNode.setIterator(iter);
            forNode.setModify(convert(forNode.getModify(), Type.OBJECT)); // NASHORN-400

            /*
             * Iterators return objects, so we need to widen the scope of the
             * init variable if it, for example, has been assigned double type
             * see NASHORN-50
             */
            forNode.getInit().getSymbol().setType(Type.OBJECT);
        } else {
            /* Normal for node, not for in */

            if (init != null) {
                forNode.setInit(discard(init));
            }

            if (test != null) {
                forNode.setTest(convert(test, Type.BOOLEAN));
            } else {
                forNode.setHasGoto();
            }

            if (modify != null) {
                forNode.setModify(discard(modify));
            }
        }

        final Block body = forNode.getBody();

        final boolean escapes = controlFlowEscapes(body);
        if (escapes) {
            body.setIsTerminal(false);
        }

        // pop the loop from the loop context
        unnest();

        if (!forNode.isForIn() && conservativeAlwaysTrue(test)) {
            forNode.setTest(null);
            forNode.setIsTerminal(!escapes);
        }

        statements.add(forNode);

        return forNode;
    }

    /**
     * Initialize this symbol and variable for function node
     *
     * @param functionNode the function node
     */
    private void initThis(final FunctionNode functionNode) {
        final long token = functionNode.getToken();
        final int finish = functionNode.getFinish();

        final Symbol thisSymbol = functionNode.defineSymbol(THIS.tag(), IS_PARAM | IS_THIS, null);
        final IdentNode thisNode = new IdentNode(source, token, finish, thisSymbol.getName());

        thisSymbol.setType(Type.OBJECT);
        thisSymbol.setNeedsSlot(true);

        thisNode.setSymbol(thisSymbol);

        functionNode.setThisNode(thisNode);
    }

    /**
     * Initialize scope symbol and variable for function node
     *
     * @param functionNode the function node
     */
    private void initScope(final FunctionNode functionNode) {
        final long token = functionNode.getToken();
        final int finish = functionNode.getFinish();

        final Symbol scopeSymbol = functionNode.defineSymbol(SCOPE.tag(), IS_VAR | IS_INTERNAL, null);
        final IdentNode scopeNode = new IdentNode(source, token, finish, scopeSymbol.getName());

        scopeSymbol.setType(ScriptObject.class);
        scopeSymbol.setNeedsSlot(true);

        scopeNode.setSymbol(scopeSymbol);

        functionNode.setScopeNode(scopeNode);
    }

    /**
     * Initialize return symbol and variable for function node
     *
     * @param functionNode the function node
     */
    private void initReturn(final FunctionNode functionNode) {
        final long token = functionNode.getToken();
        final int finish = functionNode.getFinish();

        final Symbol returnSymbol = functionNode.defineSymbol(SCRIPT_RETURN.tag(), IS_VAR | IS_INTERNAL, null);
        final IdentNode returnNode = new IdentNode(source, token, finish, returnSymbol.getName());

        returnSymbol.setType(Object.class);
        returnSymbol.setNeedsSlot(true);

        returnNode.setSymbol(returnSymbol);

        functionNode.setResultNode(returnNode);
    }

    /**
     * Initialize varargs for function node, if applicable
     *
     * @param functionNode
     */
    private void initVarArg(final FunctionNode functionNode) {
        final long token  = functionNode.getToken();
        final int  finish = functionNode.getFinish();

        assert functionNode.getCalleeNode() != null;

        final Symbol    varArgsSymbol = functionNode.defineSymbol(VARARGS.tag(), IS_PARAM | IS_INTERNAL, null);
        final IdentNode varArgsNode   = new IdentNode(source, token, finish, varArgsSymbol.getName());

        varArgsSymbol.setType(Type.OBJECT_ARRAY);
        varArgsSymbol.setNeedsSlot(true);

        varArgsNode.setSymbol(varArgsSymbol);

        functionNode.setVarArgsNode(varArgsNode);

        final String    argumentsName    = ARGUMENTS.tag();
        final String    name             = functionNode.hideArguments() ? functionNode.uniqueName("$" + argumentsName) : argumentsName;
        final Symbol    argumentsSymbol  = functionNode.defineSymbol(name, IS_PARAM | IS_INTERNAL, null);
        final IdentNode argumentsNode    = new IdentNode(source, token, finish, argumentsSymbol.getName());

        argumentsSymbol.setType(Type.OBJECT);
        argumentsSymbol.setNeedsSlot(true);

        argumentsNode.setSymbol(argumentsSymbol);

        functionNode.setArgumentsNode(argumentsNode);
    }

    private void initCallee(final FunctionNode functionNode) {
        if (functionNode.getCalleeNode() != null) {
            return;
        }

        final long token = functionNode.getToken();
        final int finish = functionNode.getFinish();

        final Symbol    calleeSymbol = functionNode.defineSymbol(CALLEE.tag(), IS_PARAM | IS_INTERNAL, null);
        final IdentNode calleeNode   = new IdentNode(source, token, finish, calleeSymbol.getName());

        calleeSymbol.setType(ScriptFunction.class);
        calleeSymbol.setNeedsSlot(true);

        calleeNode.setSymbol(calleeSymbol);

        functionNode.setCalleeNode(calleeNode);
    }

    /**
     * Initialize parameters for function node. This may require specializing
     * types if a specialization profile is known
     *
     * @param functionNode the function node
     */
    private void initParameters(final FunctionNode functionNode) {
        /*
         * If a function is specialized, we don't need to tag either it return
         * type or its parameters with the widest (OBJECT) type for safety.
         */
        functionNode.setReturnType(Type.UNKNOWN);

        for (final IdentNode ident : functionNode.getParameters()) {
            localDefs.add(ident.getName());
            final Symbol paramSymbol = functionNode.defineSymbol(ident.getName(), IS_PARAM, ident);
            if (paramSymbol != null) {
                paramSymbol.setType(Type.UNKNOWN);
            }
        }
    }

    /**
     * This has to run before fix assignment types, store any type specializations for
     * paramters, then turn then to objects for the generic version of this method
     *
     * @param functionNode functionNode
     */
    private static void fixParameters(final FunctionNode functionNode) {
        boolean nonObjectParams = false;
        List<Type> paramSpecializations = new ArrayList<>();

        for (final IdentNode ident : functionNode.getParameters()) {
            final Symbol paramSymbol = ident.getSymbol();
            if (paramSymbol != null) {
                Type type = paramSymbol.getSymbolType();
                if (type.isUnknown()) {
                    type = Type.OBJECT;
                }
                paramSpecializations.add(type);
                if (!type.isObject()) {
                    nonObjectParams = true;
                }
                paramSymbol.setType(Type.OBJECT);
            }
        }

        if (!nonObjectParams) {
            paramSpecializations = null;
            /*
             * Later, when resolving a call to this method, the linker can say "I have a double, an int and an object" as parameters
             * here. If the callee has parameter specializations, we can regenerate it with those particular types for speed.
             */
        } else {
            LOG.info("parameter specialization possible: " + functionNode.getName() + " " + paramSpecializations);
        }

        // parameters should not be slots for a vararg function, make sure this is the case
        if (functionNode.isVarArg()) {
            for (final IdentNode param : functionNode.getParameters()) {
                param.getSymbol().setNeedsSlot(false);
            }
        }
    }

    private LiteralNode<Undefined> undefined() {
        return LiteralNode.newInstance(source, 0, 0, UNDEFINED);
    }

    private void guaranteeReturn(final FunctionNode functionNode) {
        Node resultNode;

        if (functionNode.isScript()) {
            resultNode = functionNode.getResultNode();
        } else {
            final Node lastStatement = Node.lastStatement(functionNode.getStatements());
            if (lastStatement != null && (lastStatement.isTerminal() || lastStatement instanceof ReturnNode)) {
                return; // return already in place, as it should be for a non-undefined returning function
            }
            resultNode = undefined().accept(this);
        }

        new ReturnNode(source, functionNode.getLastToken(), functionNode.getFinish(), resultNode, null).accept(this);

        functionNode.setReturnType(Type.OBJECT);
    }

    /**
     * Fix return types for a node. The return type is the widest of all known
     * return expressions in the function, and has to be the same for all return
     * nodes, thus we need to traverse all of them in case some of them must be
     * upcast
     *
     * @param node function node to scan return types for
     */
    private void fixReturnTypes(final FunctionNode node) {

        if (node.getReturnType().isUnknown()) {
            node.setReturnType(Type.OBJECT); // for example, infinite loops
        }

        node.accept(new NodeVisitor() {
            @Override
            public Node enter(final FunctionNode subFunction) {
                //leave subfunctions alone. their return values are already specialized and should not
                //be touched.
                return null;
            }
            @Override
            public Node leave(final ReturnNode returnNode) {
                if (returnNode.hasExpression()) {
                    returnNode.setExpression(convert(returnNode.getExpression(), node.getReturnType()));
                }
                return returnNode;
            }
        });
    }

    /**
     * Augment assignments with casts after traversing a function node.
     * Assignment nodes are special, as they require entire scope for type
     * inference.
     *
     * Consider e.g.
     *
     * var x = 18.5; //double
     * for (...) {
     *      var y = x; //initially y is inferred to be double y = func(x) //y changes to object, but above assignment is unaware that it needs to be var y = (object)x instaed
     */

    private void fixAssignmentTypes(final FunctionNode node, final List<Symbol> declaredSymbols) {

        // Make sure all unknown symbols are OBJECT types now. No UNKNOWN may survive lower.
        for (final Symbol symbol : declaredSymbols) {
            if (symbol.getSymbolType().isUnknown()) {
                LOG.finest("fixAssignmentTypes: widening " + symbol + " to object " + symbol + " in " + getCurrentFunctionNode());
                symbol.setType(Type.OBJECT);
                symbol.setCanBeUndefined();
            }

            if (symbol.canBeUndefined()) {
                /*
                 * Ideally we'd like to only widen to the narrowest
                 * possible type that supports local variables, i.e. doubles for
                 * numerics, but
                 *
                 * var x;
                 *
                 * if (x !== undefined) do something
                 *
                 * x++;
                 *
                 * Screws this up, as x is determined to be a double even though
                 * the use is undefined This can be fixed with better use
                 * analysis in the IdentNode visitor.
                 *
                 * This actually seems to be superseded with calls to ensureTypeNotUnknown
                 */
                if (!Type.areEquivalent(symbol.getSymbolType(), Type.OBJECT)) {
                    debug(symbol + " in " + getCurrentFunctionNode() + " can be undefined. Widening to object");
                    symbol.setType(Type.OBJECT);
                }
            }
        }

        node.accept(new NodeVisitor() {

            private void fix(final Assignment<? extends Node> assignment) {
                final Node src  = assignment.getAssignmentSource();
                final Node dest = assignment.getAssignmentDest();

                if (src == null) {
                    return;
                }

                //we don't know about scope yet so this is too conservative. AccessSpecialized will remove casts that are not required

                final Type srcType  = src.getType();
                Type destType = dest.getType();

                //we can only narrow an operation type if the variable doesn't have a slot
                if (!dest.getSymbol().hasSlot() && !node.hasDeepWithOrEval()) {
                    destType = Type.narrowest(((Node)assignment).getWidestOperationType(), destType);
                }

                if (!Type.areEquivalent(destType, srcType)) {
                    LOG.finest("fixAssignment " + assignment + " type = " + src.getType() + "=>" + dest.getType());
                    assignment.setAssignmentSource(convert(src, destType));
                }
            }

            private Node checkAssignment(final Node assignNode) {
                if (!assignNode.isAssignment()) {
                    return assignNode;
                }
                fix((Assignment<?>)assignNode);
                return assignNode;
            }


            @Override
            public Node leave(final UnaryNode unaryNode) {
                return checkAssignment(unaryNode);
            }

            @Override
            public Node leave(final BinaryNode binaryNode) {
                return checkAssignment(binaryNode);
            }

            @Override
            public Node leave(final VarNode varNode) {
                return checkAssignment(varNode);
            }
        });
    }

    /*
     * For a script, add scope symbols as defined in the property map
     */
    private static void initFromPropertyMap(final FunctionNode functionNode) {
        assert functionNode.isScript();

        final PropertyMap map = Context.getGlobal().getMap();

        for (final Property property : map.getProperties()) {
            final String key = property.getKey();
            functionNode.defineSymbol(key, IS_GLOBAL, null).setType(Type.OBJECT);
        }
    }

    @Override
    public Node enter(final FunctionNode functionNode) {
        LOG.info("START FunctionNode: " + functionNode.getName());

        Node initialEvalResult = undefined();

        nest(functionNode);

        localDefs = new HashSet<>();
        localUses = new HashSet<>();

        functionNode.setFrame(functionNode.pushFrame());

        initThis(functionNode);
        initCallee(functionNode);
        if (functionNode.isVarArg()) {
            initVarArg(functionNode);
        }

        initParameters(functionNode);
        initScope(functionNode);
        initReturn(functionNode);

        /*
         * Add all nested functions as symbols in this function
         */
        for (final FunctionNode nestedFunction : functionNode.getFunctions()) {
            final IdentNode ident = nestedFunction.getIdent();

            // for initial eval result is the last declared function
            if (ident != null && nestedFunction.isStatement()) {
                final Symbol functionSymbol = functionNode.defineSymbol(ident.getName(), IS_VAR, nestedFunction);

                functionSymbol.setType(ScriptFunction.class);
                initialEvalResult = new IdentNode(ident);
            }
        }

        if (functionNode.isScript()) {
            initFromPropertyMap(functionNode);
        }

        // Add function name as local symbol
        if (!functionNode.isStatement() && !functionNode.isAnonymous() && !functionNode.isScript()) {
            final Symbol selfSymbol = functionNode.defineSymbol(functionNode.getIdent().getName(), IS_VAR, functionNode);
            selfSymbol.setType(Type.OBJECT);
            selfSymbol.setNode(functionNode);
        }

        /*
         * As we are evaluating a nested structure, we need to store the
         * statement list for the surrounding block and restore it when the
         * function is done
         */
        final List<Node> savedStatements = statements;
        statements = new ArrayList<>();

        /*
         * This pushes all declarations (except for non-statements, i.e. for
         * node temporaries) to the top of the function scope. This way we can
         * get around problems like
         *
         * while (true) {
         *   break;
         *   if (true) {
         *     var s;
         *   }
         * }
         *
         * to an arbitrary nesting depth.
         *
         * @see NASHORN-73
         */

        final List<Symbol> declaredSymbols = new ArrayList<>();
        for (final VarNode decl : functionNode.getDeclarations()) {
            final IdentNode ident = decl.getName();
            // any declared symbols that aren't visited need to be typed as
            // well, hence the list
            declaredSymbols.add(functionNode.defineSymbol(ident.getName(), IS_VAR, new IdentNode(ident)));
        }

        declaredSymbolsLocal = new ArrayList<>();

        /*
         * Main function code lowering
         */
        try {
            /*
             * Every nested function needs a definition in the outer function
             * with its name. Add these.
             */
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
                new ExecuteNode(source, functionNode.getFirstToken(), functionNode.getFinish(), initialEvalResult).accept(this);
            }

            /*
             * Now we do the statements. This fills the block with code
             */
            for (final Node statement : functionNode.getStatements()) {
                statement.accept(this);

                final Node lastStatement = Node.lastStatement(statements);
                if (lastStatement != null && lastStatement.hasTerminalFlags()) {
                    functionNode.copyTerminalFlags(lastStatement);
                    break;
                }
            }

            functionNode.setStatements(statements);

            /*
             * If there are unusedterminated enpoints in the function, we need
             * to add a "return undefined" in those places for correct semantics
             */
            if (!functionNode.isTerminal()) {
                guaranteeReturn(functionNode);
            }

            /*
             * Emit all nested functions
             */
            for (final FunctionNode nestedFunction : functionNode.getFunctions()) {
                nestedFunction.accept(this);
            }

            fixReturnTypes(functionNode);

            if (functionNode.needsSelfSymbol()) {
                final IdentNode selfSlotNode = new IdentNode(functionNode.getIdent());
                selfSlotNode.setSymbol(functionNode.findSymbol(functionNode.getIdent().getName()));
                final Node functionRef = new IdentNode(functionNode.getCalleeNode());
                statements.add(0, new VarNode(source, functionNode.getToken(), functionNode.getFinish(), selfSlotNode, functionRef, false).accept(this));
            }
        } finally {
            /*
             * Restore statement for outer block that we were working on
             */
            statements = savedStatements;
        }

        unnest();

        fixParameters(functionNode);

        /*
         * Fix assignment issues. assignments are troublesome as they can be on
         * the form "dest = dest op source" where the type is different for the
         * two dest:s: right now this is a post pass that handles putting casts
         * in the correct places, but it can be implemented as a bottom up AST
         * traversal on the fly. TODO.
         */
        fixAssignmentTypes(functionNode, declaredSymbols);

        /*
         * Set state of function to lowered and pop the frame
         */
        functionNode.setIsLowered(true);
        functionNode.popFrame();

        LOG.info("END FunctionNode: " + functionNode.getName());

        return null;
    }

    @Override
    public Node enter(final IdentNode identNode) {
        final String name = identNode.getName();

        if (identNode.isPropertyName()) {
            // assign a pseudo symbol to property name
            identNode.setSymbol(new Symbol(name, 0, Type.OBJECT));
            return null;
        }

        final Block block = getCurrentBlock();
        final Symbol oldSymbol = identNode.getSymbol();
        Symbol symbol = block.findSymbol(name);

        /*
         * If an existing symbol with the name is found, use that otherwise,
         * declare a new one
         */

        if (symbol != null) {
            if (isFunctionExpressionSelfReference(symbol)) {
                ((FunctionNode) symbol.getNode()).setNeedsSelfSymbol();
            }

            if (!identNode.isInitializedHere()) { // NASHORN-448
                // here is a use outside the local def scope
                if (!localDefs.contains(name)) {
                    symbol.setType(Type.OBJECT);
                    symbol.setCanBeUndefined();
                }
            }

            identNode.setSymbol(symbol);
            if (!getCurrentFunctionNode().isLocal(symbol)) {
                // non-local: we need to put symbol in scope (if it isn't already)
                if (!symbol.isScope()) {
                    final List<Block> lookupBlocks = findLookupBlocksHelper(getCurrentFunctionNode(), symbol.findFunction());
                    for (final Block lookupBlock : lookupBlocks) {
                        final Symbol refSymbol = lookupBlock.findSymbol(name);
                        if (refSymbol != null) { // See NASHORN-837, function declaration in lexical scope: try {} catch (x){ function f() { use(x) } } f()
                            LOG.finest("Found a ref symbol that must be scope " + refSymbol);
                            refSymbol.setIsScope();
                        }
                    }
                }
            }
        } else {
            symbol = block.useSymbol(name, identNode);
            // we have never seen this before, it can be undefined
            symbol.setType(Type.OBJECT); // TODO unknown -we have explicit casts anyway?
            symbol.setCanBeUndefined();
        }

        if (symbol != oldSymbol && !identNode.isInitializedHere()) {
            symbol.increaseUseCount();
        }
        localUses.add(identNode.getName());

        return null;
    }

    private static List<Block> findLookupBlocksHelper(final FunctionNode currentFunction, final FunctionNode topFunction) {
        if (currentFunction.findParentFunction() == topFunction) {
            final List<Block> blocks = new LinkedList<>();

            blocks.add(currentFunction.getParent());
            blocks.addAll(currentFunction.getReferencingParentBlocks());
            return blocks;
        }
        /**
         * assumption: all parent blocks of an inner function will always be in the same outer function;
         * therefore we can simply skip through intermediate functions.
         * @see FunctionNode#addReferencingParentBlock(Block)
         */
        return findLookupBlocksHelper(currentFunction.findParentFunction(), topFunction);
    }

    private static boolean isFunctionExpressionSelfReference(final Symbol symbol) {
        if (symbol.isVar() && symbol.getNode() == symbol.getBlock() && symbol.getNode() instanceof FunctionNode) {
            return ((FunctionNode) symbol.getNode()).getIdent().getName().equals(symbol.getName());
        }
        return false;
    }

    @Override
    public Node enter(final IfNode ifNode) {
        nest(ifNode);
        return ifNode;
    }

    @Override
    public Node leave(final IfNode ifNode) {

        final Node test = convert(ifNode.getTest(), Type.BOOLEAN);

        /*
         * constant if checks should short circuit into just one of the
         * pass/fail blocks
         */
        if (test.getSymbol().isConstant()) {
            assert test instanceof LiteralNode<?>;
            final Block shortCut = ((LiteralNode<?>)test).isTrue() ? ifNode.getPass() : ifNode.getFail();
            if (shortCut != null) {
                for (final Node statement : shortCut.getStatements()) {
                    statements.add(statement);
                }
            }
            return null;
        }

        final Node pass = ifNode.getPass();
        final Node fail = ifNode.getFail();

        if (pass.isTerminal() && fail != null && fail.isTerminal()) {
            ifNode.setIsTerminal(true);
        }

        ifNode.setTest(test);
        statements.add(ifNode);

        unnest();

        return ifNode;
    }

    @Override
    public Node leave(final IndexNode indexNode) {
        getCurrentFunctionNode().newTemporary(Type.OBJECT, indexNode);

        return indexNode;
    }

    @Override
    public Node enter(final LabelNode labeledNode) {
        // Don't want a symbol for label.
        final Block body = labeledNode.getBody();
        body.accept(this);
        labeledNode.copyTerminalFlags(body);
        statements.add(labeledNode);

        return null;
    }

    @Override
    public Node enter(final LineNumberNode lineNumberNode) {
        statements.add(lineNumberNode);
        return null;
    }

    /**
     * Generate a new literal symbol.
     *
     * @param literalNode LiteralNode needing symbol.
     * @return The literal node augmented with the symbol
     */
    private LiteralNode<?> newLiteral(final LiteralNode<?> literalNode) {
        return newLiteral(getCurrentFunctionNode(), literalNode);
    }

    private static LiteralNode<?> newLiteral(final FunctionNode functionNode, final LiteralNode<?> literalNode) {
        functionNode.newLiteral(literalNode);
        return literalNode;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Node enter(final LiteralNode literalNode) {
        if (literalNode.isTokenType(TokenType.THIS)) {
            literalNode.setSymbol(getCurrentFunctionNode().getThisNode().getSymbol());
            return null;
        }

        if (literalNode instanceof ArrayLiteralNode) {
            final ArrayLiteralNode arrayLiteralNode = (ArrayLiteralNode)literalNode;
            final Node[] array = arrayLiteralNode.getValue();

            for (int i = 0; i < array.length; i++) {
                final Node element = array[i];
                if (element != null) {
                    array[i] = array[i].accept(this);
                }
            }

            arrayLiteralNode.analyze();
            final Type elementType = arrayLiteralNode.getElementType();

            // we only have types after all elements are accepted
            for (int i = 0; i < array.length; i++) {
                final Node element = array[i];
                if (element != null) {
                    array[i] = convert(element, elementType);
                }
            }
        } else {
            final Object value = literalNode.getValue();

            if (value instanceof Node) {
                final Node node = ((Node)value).accept(this);
                if (node != null) {
                    return newLiteral(LiteralNode.newInstance(source, node.getToken(), node.getFinish(), node));
                }
            }
        }

        newLiteral(literalNode);

        return null;
    }

    @Override
    public Node leave(final ObjectNode objectNode) {
        getCurrentFunctionNode().newTemporary(Type.OBJECT, objectNode);
        return objectNode;
    }

    @Override
    public Node enter(final PropertyNode propertyNode) {
        // assign a pseudo symbol to property name, see NASHORN-710
        propertyNode.setSymbol(new Symbol(propertyNode.getKeyName(), 0, Type.OBJECT));
        return propertyNode;
    }

    @Override
    public Node enter(final ReferenceNode referenceNode) {
        final FunctionNode functionNode = referenceNode.getReference();
        if (functionNode != null) {
            functionNode.addReferencingParentBlock(getCurrentBlock());
        }
        return referenceNode;
    }

    @Override
    public Node leave(final ReferenceNode referenceNode) {
        getCurrentFunctionNode().newTemporary(Type.OBJECT, referenceNode);
        return referenceNode;
    }

    /**
     * For each return node we need to set a return expression of the correct
     * type. In the non-specializing world, we can always upcast to object and
     * ignore the rest, but in the specializing world we have to keep track of
     * the widest return type so far, which is the common one for this method.
     *
     * @param returnNode  the return node we are generating
     * @param expression  the expression to return from this code
     */
    private void setReturnExpression(final ReturnNode returnNode, final Node expression) {
        final FunctionNode functionNode = getCurrentFunctionNode();

        returnNode.setExpression(expression);

        final Symbol symbol = expression.getSymbol();
        if (expression.getType().isUnknown() && symbol.isParam()) {
            symbol.setType(Type.OBJECT);
        }
        functionNode.setReturnType(Type.widest(expression.getType(), functionNode.getReturnType()));
    }

    @Override
    public Node enter(final ReturnNode returnNode) {
        final TryNode tryNode = returnNode.getTryChain();
        final Node expression = returnNode.getExpression();

        if (tryNode != null) {
            if (expression != null) {
                final long token = returnNode.getToken();
                final Node resultNode = getCurrentFunctionNode().getResultNode();
                new ExecuteNode(source, token, Token.descPosition(token), new BinaryNode(source, Token.recast(token, TokenType.ASSIGN), resultNode, expression)).accept(this);

                if (copyFinally(tryNode, null)) {
                    return null;
                }

                setReturnExpression(returnNode, resultNode);
            } else if (copyFinally(tryNode, null)) {
                return null;
            }
        } else if (expression != null) {
            setReturnExpression(returnNode, expression.accept(this));
        }

        statements.add(returnNode);

        return null;
    }

    @Override
    public Node leave(final RuntimeNode runtimeNode) {
        for (final Node arg : runtimeNode.getArgs()) {
            ensureTypeNotUnknown(arg);
        }

        getCurrentFunctionNode().newTemporary(runtimeNode.getRequest().getReturnType(), runtimeNode);

        return runtimeNode;
    }

    @Override
    public Node enter(final SwitchNode switchNode) {
        nest(switchNode);
        return switchNode;
    }

    @Override
    public Node leave(final SwitchNode switchNode) {
        unnest();

        final Node           expression  = switchNode.getExpression();
        final List<CaseNode> cases       = switchNode.getCases();
        final CaseNode       defaultCase = switchNode.getDefaultCase();
        final boolean        hasDefault  = defaultCase != null;
        final int            n           =  cases.size() + (hasDefault ? -1 : 0);

        boolean allTerminal = !cases.isEmpty();
        boolean allInteger  = n > 1;

        for (final CaseNode caseNode : cases) {
            allTerminal &= caseNode.isTerminal();
            final Node test = caseNode.getTest();

            // Skip default.
            if (test == null) {
                continue;
            }

            // If not a number.
            if (!(test instanceof LiteralNode) || !test.getType().isNumeric()) {
                allInteger = false;
                continue;
            }

            final LiteralNode<?> testLiteral = (LiteralNode<?>)test;

            // If a potential integer.
            if (!(testLiteral.getValue() instanceof Integer)) {
                // If not an integer value.
                if (!JSType.isRepresentableAsInt(testLiteral.getNumber())) {
                    allInteger = false;
                    continue;
                }

                // Guarantee all case literals are Integers (simplifies sorting in code gen.)
                caseNode.setTest(newLiteral(LiteralNode.newInstance(source, testLiteral.getToken(), testLiteral.getFinish(), testLiteral.getInt32())));
            }
        }

        if (allTerminal && defaultCase != null && defaultCase.isTerminal()) {
            switchNode.setIsTerminal(true);
        }

        if (!allInteger) {
            switchNode.setExpression(convert(expression, Type.OBJECT));

            for (final CaseNode caseNode : cases) {
                final Node test = caseNode.getTest();

                if (test != null) {
                    caseNode.setTest(convert(test, Type.OBJECT));
                }
            }
        }

        final String name = compiler.uniqueName(SWITCH_TAG_PREFIX.tag());
        final Symbol tag  = getCurrentFunctionNode().defineSymbol(name, IS_VAR | IS_INTERNAL, null);

        tag.setType(allInteger ? Type.INT : Type.OBJECT);
        switchNode.setTag(tag);

        statements.add(switchNode);

        return switchNode;
    }

    @Override
    public Node leave(final ThrowNode throwNode) {
        throwNode.setExpression(convert(throwNode.getExpression(), Type.OBJECT));
        statements.add(throwNode);

        return throwNode;
    }

    @Override
    public Node enter(final TryNode tryNode) {
        final Block  finallyBody   = tryNode.getFinallyBody();
        final Source tryNodeSource = tryNode.getSource();
        final long   token         = tryNode.getToken();
        final int    finish        = tryNode.getFinish();

        nest(tryNode);

        if (finallyBody == null) {
            return tryNode;
        }

        /**
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

        // if try node does not contain a catch we can skip creation of a new
        // try node and just append our synthetic catch to the existing try node.
        if (!tryNode.getCatchBlocks().isEmpty()) {
            // insert an intermediate try-catch* node, where we move the body and all catch blocks.
            // the original try node become a try-finally container for the new try-catch* node.
            // because we don't clone (to avoid deep copy), we have to fix the block chain in the end.
            final TryNode innerTryNode = new TryNode(tryNodeSource, token, finish, tryNode.getNext());
            innerTryNode.setBody(tryNode.getBody());
            innerTryNode.setCatchBlocks(tryNode.getCatchBlocks());

            // set outer tryNode's body to innerTryNode
            final Block outerBody = new Block(tryNodeSource, token, finish, tryNode.getBody().getParent(), getCurrentFunctionNode());
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
        final String name = compiler.uniqueName(EXCEPTION_PREFIX.tag());

        final IdentNode exception = new IdentNode(tryNodeSource, token, finish, name);
        exception.accept(this);

        final Block catchBlock      = new Block(tryNodeSource, token, finish, getCurrentBlock(), getCurrentFunctionNode());
        final Block catchBody       = new Block(tryNodeSource, token, finish, catchBlock, getCurrentFunctionNode());
        final Node  catchAllFinally = finallyBody.clone();

        catchBody.addStatement(new ExecuteNode(tryNodeSource, finallyBody.getToken(), finallyBody.getFinish(), catchAllFinally));
        catchBody.setIsTerminal(true);

        final CatchNode catchNode = new CatchNode(tryNodeSource, token, finish, new IdentNode(exception), null, catchBody);
        catchNode.setIsSyntheticRethrow();
        catchBlock.addStatement(catchNode);

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

        final String name      = compiler.uniqueName(EXCEPTION_PREFIX.tag());
        final Symbol exception = getCurrentFunctionNode().defineSymbol(name, IS_VAR | IS_INTERNAL, null);

        exception.setType(ECMAException.class);
        tryNode.setException(exception);

        statements.add(tryNode);

        unnest();

        // if finally body is present, place it after the tryNode
        if (finallyBody != null) {
            tryNode.setFinallyBody(null);
            statements.add(finallyBody);
        }

        return tryNode;
    }

    @Override
    public Node enter(final VarNode varNode) {
        final IdentNode ident = varNode.getName();
        final String    name  = ident.getName();

        final Symbol symbol = getCurrentBlock().defineSymbol(name, IS_VAR, ident);
        assert symbol != null;

        varNode.setSymbol(symbol);
        declaredSymbolsLocal.add(symbol);

        // NASHORN-467 - use before definition of vars - conservative
        if (localUses.contains(ident.getName())) {
            symbol.setType(Type.OBJECT);
            symbol.setCanBeUndefined();
        }

        return varNode;
    }

    private static boolean isScopeOrGlobal(final Symbol symbol) {
        return symbol.getBlock().getFunction().isScript(); //we can't do explicit isScope checks as early as lower, which is why AccessSpecializer is afterwards as well.
    }

    @Override
    public Node leave(final VarNode varNode) {
        final Node      init  = varNode.getInit();
        final IdentNode ident = varNode.getName();
        final String    name  = ident.getName();

        if (init != null) {
            localDefs.add(name);
        }

        if (varNode.shouldAppend()) {
            statements.add(varNode);
        }

        if (init == null) {
            // var x; with no init will be treated like a use of x by
            // visit(IdentNode) unless we remove the name
            // from the localdef list.
            localDefs.remove(name);
            return varNode;
        }

        final Symbol symbol = varNode.getSymbol();
        if ((init.getType().isNumeric() || init.getType().isBoolean()) && !isScopeOrGlobal(symbol)) { //see NASHORN-56
            // Forbid integers as local vars for now as we have no way to treat them as undefined
            final Type type = Compiler.shouldUseIntegers() ? init.getType() : Type.NUMBER;
            varNode.setInit(convert(init, type));
            symbol.setType(type);
        } else {
            symbol.setType(Type.OBJECT); // var x = obj;, x is an "object" //TODO too conservative now?
        }

        return varNode;
    }

    @Override
    public Node enter(final WhileNode whileNode) {
        // push the loop to the loop context
        nest(whileNode);

        return whileNode;
    }

    @Override
    public Node leave(final WhileNode whileNode) {
        final Node test = whileNode.getTest();

        if (test != null) {
            whileNode.setTest(convert(test, Type.BOOLEAN));
        } else {
            whileNode.setHasGoto();
        }

        Node returnNode = whileNode;

        final Block   body    = whileNode.getBody();
        final boolean escapes = controlFlowEscapes(body);
        if (escapes) {
            body.setIsTerminal(false);
        }

        if (body.isTerminal()) {
            if (whileNode instanceof DoWhileNode) {
                whileNode.setIsTerminal(true);
            } else if (conservativeAlwaysTrue(test)) {
                returnNode = new ForNode(source, whileNode.getToken(), whileNode.getFinish());
                ((ForNode)returnNode).setBody(body);
                returnNode.setIsTerminal(!escapes);
            }
        }

        // pop the loop from the loop context
        unnest();
        statements.add(returnNode);

        return returnNode;
    }

    @Override
    public Node leave(final WithNode withNode) {
        withNode.setExpression(convert(withNode.getExpression(), Type.OBJECT));

        if (withNode.getBody().isTerminal()) {
            withNode.setIsTerminal(true);
        }

        statements.add(withNode);

        return withNode;
    }

    /*
     * Unary visits.
     */

    /**
     * Visit unary node and set symbols and inferred type
     *
     * @param unaryNode  unary node to visit
     * @param type       The narrowest allowed type for this node
     *
     * @return the final node, or replacement thereof
     */
    public Node leaveUnary(final UnaryNode unaryNode, final Type type) {
        /* Try to eliminate the unary node if the expression is constant */
        final LiteralNode<?> literalNode = new UnaryNodeConstantEvaluator(unaryNode).eval();
        if (literalNode != null) {
            return newLiteral(literalNode);
        }

        /* Add explicit conversion */
        unaryNode.setRHS(convert(unaryNode.rhs(), type));
        getCurrentFunctionNode().newTemporary(type, unaryNode);

        return unaryNode;
    }

    @Override
    public Node leaveADD(final UnaryNode unaryNode) {
        return leaveUnary(unaryNode, Type.NUMBER);
    }

    @Override
    public Node leaveBIT_NOT(final UnaryNode unaryNode) {
        return leaveUnary(unaryNode, Type.INT);
    }

    @Override
    public Node leaveCONVERT(final UnaryNode unaryNode) {
        final Node rhs = unaryNode.rhs();

        if (rhs.isTokenType(TokenType.CONVERT)) {
            rhs.setSymbol(unaryNode.getSymbol());
            return rhs;
        }

        return unaryNode;
    }

    /**
     * In an assignment, recursively make sure that there are slots for
     * everything that has to be laid out as temporary storage, which is the
     * case if we are assign-op:ing a BaseNode subclass. This has to be
     * recursive to handle things like multi dimensional arrays as lhs
     *
     * see NASHORN-258
     *
     * @param functionNode   the current function node (has to be passed as it changes in the visitor below)
     * @param assignmentDest the destination node of the assignment, e.g. lhs for binary nodes
     */
    private static void ensureAssignmentSlots(final FunctionNode functionNode, final Node assignmentDest) {

        assignmentDest.accept(new NodeVisitor() {

            @Override
            public Node leave(final AccessNode accessNode) {
                final Node base = accessNode.getBase();
                if (base.getSymbol().isThis()) {
                    functionNode.addThisProperty(accessNode.getProperty().getName(), accessNode);
                }

                return accessNode;
            }

            @Override
            public Node leave(final IndexNode indexNode) {
                final Node index = indexNode.getIndex();
                index.getSymbol().setNeedsSlot(!index.getSymbol().isConstant());

                return indexNode;
            }

        });
    }

    @Override
    public Node leaveDECINC(final UnaryNode unaryNode) {
        // @see assignOffset
        ensureAssignmentSlots(getCurrentFunctionNode(), unaryNode.rhs());
        final Type type = Compiler.shouldUseIntegerArithmetic() ? Type.INT : Type.NUMBER;

        unaryNode.rhs().getSymbol().setType(type);
        getCurrentFunctionNode().newTemporary(type, unaryNode);

        return unaryNode;
    }

    /**
     * Helper class for wrapping a result
     */
    private abstract static class ResultNodeVisitor extends NodeVisitor {

        private Node resultNode;

        ResultNodeVisitor(final Node resultNode) {
            this.resultNode = resultNode;
        }

        Node getResultNode() {
            return resultNode;
        }

        void setResultNode(final Node resultNode) {
            this.resultNode = resultNode;
        }
    }

    @Override
    public Node leaveDELETE(final UnaryNode unaryNode) {
        final boolean              strictMode     = getCurrentFunctionNode().isStrictMode();
        final Node                 rhs            = unaryNode.rhs();
        final long                 token          = unaryNode.getToken();
        final int                  finish         = unaryNode.getFinish();
        final LiteralNode<Boolean> trueNode       = LiteralNode.newInstance(source, token, finish, true);
        final LiteralNode<Boolean> strictFlagNode = LiteralNode.newInstance(source, token, finish, strictMode);

        newLiteral(trueNode);
        newLiteral(strictFlagNode);

        final NodeVisitor  lower = this;
        final FunctionNode currentFunctionNode = getCurrentFunctionNode();

        final ResultNodeVisitor visitor = new ResultNodeVisitor(trueNode) {

            private void initRuntimeNode(final RuntimeNode node) {
                node.accept(lower);
                currentFunctionNode.newTemporary(Type.BOOLEAN, unaryNode);
                node.setSymbol(unaryNode.getSymbol());

                setResultNode(node);
            }

            @Override
            public Node enter(final IdentNode node) {
                // If this is a declared variable or a function parameter, delete always fails (except for globals).
                final boolean failDelete =
                        strictMode ||
                        node.getSymbol().isParam() ||
                        (node.getSymbol().isVar() && !node.getSymbol().isTopLevel());

                final Node literalNode =
                    newLiteral(
                        currentFunctionNode,
                        LiteralNode.newInstance(
                            source,
                            node.getToken(),
                            node.getFinish(),
                            node.getName()));

                if (failDelete) {
                    if (THIS.tag().equals(node.getName())) {
                        statements.add(new ExecuteNode(source, token, finish, discard(node)));
                        currentFunctionNode.newTemporary(Type.BOOLEAN, trueNode);
                        return null; //trueNode it is
                    }
                }

                final List<Node> args = new ArrayList<>();
                args.add(convert(currentFunctionNode.getScopeNode(), Type.OBJECT));
                args.add(convert(literalNode, Type.OBJECT));
                args.add(strictFlagNode);

                initRuntimeNode(
                    new RuntimeNode(
                        source,
                        token,
                        finish,
                        failDelete ? FAIL_DELETE : DELETE,
                        args));

                return null;
            }

            @Override
            public Node enter(final AccessNode node) {
                final IdentNode property = node.getProperty();

                initRuntimeNode(
                    new RuntimeNode(
                        source,
                        token,
                        finish,
                        DELETE,
                        convert(node.getBase(), Type.OBJECT),
                        convert(
                            newLiteral(
                                currentFunctionNode,
                                LiteralNode.newInstance(
                                    source,
                                    property.getToken(),
                                    property.getFinish(),
                                    property.getName())),
                            Type.OBJECT),
                        strictFlagNode));

                return null;
            }

            @Override
            public Node enter(final IndexNode node) {
                initRuntimeNode(
                    new RuntimeNode(
                        source,
                        token,
                        finish,
                        DELETE,
                        convert(node.getBase(), Type.OBJECT),
                        convert(node.getIndex(), Type.OBJECT),
                        strictFlagNode));

                return null;
            }

            @Override
            public Node enterDefault(final Node node) {
                statements.add(new ExecuteNode(source, token, finish, discard(node)));
                currentFunctionNode.newTemporary(Type.BOOLEAN, trueNode);
                //just return trueNode which is the default
                return null;
            }
        };

        rhs.accept(visitor);

        return visitor.getResultNode();
    }

    @Override
    public Node enterNEW(final UnaryNode unaryNode) {
        final CallNode callNode = (CallNode)unaryNode.rhs();

        callNode.setIsNew();

        final Node function = callNode.getFunction();
        final Node markedFunction = markerFunction(function);

        callNode.setFunction(markedFunction.accept(this));

        acceptArgs(callNode);

        getCurrentFunctionNode().newTemporary(Type.OBJECT, unaryNode);

        return null;
    }

    @Override
    public Node leaveNOT(final UnaryNode unaryNode) {
        return leaveUnary(unaryNode, Type.BOOLEAN);
    }

    /**
     * Create a null literal
     *
     * @param parent node to inherit token from.
     * @return the new null literal
     */
    private LiteralNode<?> newNullLiteral(final Node parent) {
        return newLiteral(LiteralNode.newInstance(parent.getSource(), parent.getToken(), parent.getFinish()));
    }

    @Override
    public Node leaveTYPEOF(final UnaryNode unaryNode) {
        final Node rhs    = unaryNode.rhs();
        final long token  = unaryNode.getToken();
        final int  finish = unaryNode.getFinish();

        RuntimeNode runtimeNode;

        if (rhs instanceof IdentNode) {
            final IdentNode ident = (IdentNode)rhs;

            if (ident.getSymbol().isParam() || ident.getSymbol().isVar()) {
                runtimeNode = new RuntimeNode(
                    source,
                    token,
                    finish,
                    TYPEOF,
                    convert(
                        rhs,
                        Type.OBJECT),
                    newNullLiteral(unaryNode));
            } else {
                runtimeNode = new RuntimeNode(
                    source,
                    token,
                    finish,
                    TYPEOF,
                    convert(
                        getCurrentFunctionNode().getScopeNode(),
                        Type.OBJECT),
                    convert(
                        newLiteral(
                            LiteralNode.newInstance(
                                source,
                                ident.getToken(),
                                ident.getFinish(),
                                ident.getName())),
                    Type.OBJECT));
            }
        }  else {
            runtimeNode = new RuntimeNode(
                source,
                token,
                finish,
                TYPEOF,
                convert(
                    rhs,
                    Type.OBJECT),
                newNullLiteral(unaryNode));
        }

        runtimeNode.accept(this);

        getCurrentFunctionNode().newTemporary(Type.OBJECT, unaryNode);
        runtimeNode.setSymbol(unaryNode.getSymbol());

        return runtimeNode;
    }

    @Override
    public Node leaveSUB(final UnaryNode unaryNode) {
        return leaveUnary(unaryNode, Type.NUMBER);
    }

    @Override
    public Node leaveVOID(final UnaryNode unaryNode) {
        final Node rhs = unaryNode.rhs();
        getCurrentFunctionNode().newTemporary(Type.OBJECT, unaryNode);

        // Set up request.
        final RuntimeNode runtimeNode = new RuntimeNode(source, unaryNode.getToken(), unaryNode.getFinish(), VOID, convert(rhs, Type.OBJECT));

        // Use same symbol as unary node.
        runtimeNode.setSymbol(unaryNode.getSymbol());

        return runtimeNode;
    }

    /**
     * Compute the narrowest possible type for a binaryNode, based on its
     * contents.
     *
     * @param binaryNode the binary node
     * @return the narrowest possible type
     */
    private static Type binaryType(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();
        assert lhs.hasType();

        final Node rhs = binaryNode.rhs();
        assert rhs.hasType();

        // actually bitwise assignments are ok for ints. TODO
        switch (binaryNode.tokenType()) {
        case ASSIGN_BIT_AND:
        case ASSIGN_BIT_OR:
        case ASSIGN_BIT_XOR:
        case ASSIGN_SHL:
        case ASSIGN_SAR:
            return Compiler.shouldUseIntegers() ? Type.widest(lhs.getType(), rhs.getType(), Type.INT) : Type.INT;
        case ASSIGN_SHR:
            return Type.LONG;
        case ASSIGN:
            return Compiler.shouldUseIntegers() ? Type.widest(lhs.getType(), rhs.getType(), Type.NUMBER) : Type.NUMBER;
        default:
            return binaryArithType(lhs.getType(), rhs.getType());
        }
    }

    private static Type binaryArithType(final Type lhsType, final Type rhsType) {
         if (!Compiler.shouldUseIntegerArithmetic()) {
             return Type.NUMBER;
         }
         return Type.widest(lhsType, rhsType, Type.NUMBER);
     }

    /**
     * Visit binary node and set symbols and inferred type
     *
     * @param binaryNode unary node to visit
     * @param type       The narrowest allowed type for this node
     *
     * @return the final node, or replacement thereof
     */
    private Node leaveBinary(final BinaryNode binaryNode, final Type type) {
        return leaveBinary(binaryNode, type, type, type);
    }

    /**
     * Visit a binary node, specifying types for rhs, rhs and destType.
     *
     * @param binaryNode the binary node
     * @param destType destination type
     * @param lhsType type for left hand side
     * @param rhsType type for right hand side
     *
     * @return resulting binary node
     */
    private Node leaveBinary(final BinaryNode binaryNode, final Type destType, final Type lhsType, final Type rhsType) {
        /* Attempt to turn this binary expression into a constant */
        final LiteralNode<?> literalNode = new BinaryNodeConstantEvaluator(binaryNode).eval();
        if (literalNode != null) {
            return newLiteral(literalNode);
        }

        if (lhsType != null) {
            binaryNode.setLHS(convert(binaryNode.lhs(), lhsType));
        }

        if (rhsType != null) {
            binaryNode.setRHS(convert(binaryNode.rhs(), rhsType));
        }

        getCurrentFunctionNode().newTemporary(destType, binaryNode);

        return binaryNode;
    }

    /**
     * Determine if the outcome of + operator is a string.
     *
     * @param node
     *            Node to test.
     * @return true if a string result.
     */
    private boolean isAddString(final Node node) {
        if (node instanceof BinaryNode && node.isTokenType(TokenType.ADD)) {
            final BinaryNode binaryNode = (BinaryNode)node;
            final Node lhs = binaryNode.lhs();
            final Node rhs = binaryNode.rhs();

            return isAddString(lhs) || isAddString(rhs);
        }

        return node instanceof LiteralNode<?> && ((LiteralNode<?>)node).getObject() instanceof String;
    }

    /**
     * Helper for creating a new runtime node from a parent node, inheriting its
     * types and tokens
     *
     * @param parent   Parent node.
     * @param args     Runtime request arguments.
     * @param request  Runtime request type.
     * @return New {@link RuntimeNode}.
     */

    private RuntimeNode newRuntime(final Node parent, final List<Node> args, final Request request) {
        final RuntimeNode runtimeNode = new RuntimeNode(source, parent.getToken(), parent.getFinish(), request, args);
        runtimeNode.setStart(parent.getStart());
        runtimeNode.setFinish(parent.getFinish());

        // Use same symbol as parent node.
        runtimeNode.accept(this);
        runtimeNode.setSymbol(parent.getSymbol());

        return runtimeNode;
    }

    /**
     * Helper for creating a new runtime node from a binary node
     *
     * @param binaryNode {@link RuntimeNode} expression.
     * @param request    Runtime request type.
     * @return New {@link RuntimeNode}.
     */
    private RuntimeNode newRuntime(final BinaryNode binaryNode, final Request request) {
        return newRuntime(binaryNode, Arrays.asList(new Node[] { binaryNode.lhs(), binaryNode.rhs() }), request);
    }

    /**
     * Add is a special binary, as it works not only on arithmetic, but for
     * strings etc as well.
     */
    @Override
    public Node leaveADD(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        //parameters must be blown up to objects
        ensureTypeNotUnknown(lhs);
        ensureTypeNotUnknown(rhs);

        if ((lhs.getType().isNumeric() || lhs.getType().isBoolean()) && (rhs.getType().isNumeric() || rhs.getType().isBoolean())) {
            return leaveBinary(binaryNode, binaryType(binaryNode));
        } else if (isAddString(binaryNode)) {
            binaryNode.setLHS(convert(lhs, Type.OBJECT));
            binaryNode.setRHS(convert(rhs, Type.OBJECT));
            getCurrentFunctionNode().newTemporary(Type.OBJECT, binaryNode);
        } else {
            getCurrentFunctionNode().newTemporary(Type.OBJECT, binaryNode);
            return newRuntime(binaryNode, ADD);
        }

        return binaryNode;
    }

    @Override
    public Node leaveAND(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, Type.OBJECT, null, null);
    }

    /**
     * This is a helper called before an assignment.
     * @param binaryNode assignment node
     */
    private Node enterAssign(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();

        if (!(lhs instanceof IdentNode)) {
            return binaryNode;
        }

        final Block     block = getCurrentBlock();
        final IdentNode ident = (IdentNode)lhs;
        final String    name  = ident.getName();

        Symbol symbol = getCurrentBlock().findSymbol(name);

        if (symbol == null) {
            symbol = block.defineSymbol(name, IS_GLOBAL, ident);
            binaryNode.setSymbol(symbol);
        } else if (!getCurrentFunctionNode().isLocal(symbol)) {
            symbol.setIsScope();
        }

        localDefs.add(name);

        return binaryNode;
    }

    /**
     * This assign helper is called after an assignment, when all children of
     * the assign has been processed. It fixes the types and recursively makes
     * sure that everyhing has slots that should have them in the chain.
     *
     * @param binaryNode assignment node
     * @param destType   destination type of assignment
     */
    private Node leaveAssign(final BinaryNode binaryNode, final Type destType) {
        binaryNode.lhs().getSymbol().setType(destType); // lhs inherits dest type
        getCurrentFunctionNode().newTemporary(destType, binaryNode); // as does destination

        ensureAssignmentSlots(getCurrentFunctionNode(), binaryNode.lhs());
        return binaryNode;
    }

    @Override
    public Node enterASSIGN(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        if (rhs.getType().isNumeric()) {
            final Symbol lhsSymbol = lhs.getSymbol();
            final Type lhsType = Type.widest(lhs.getType(), binaryType(binaryNode));

            // for index nodes, we can set anything
            lhsSymbol.setType(lhsType);
            getCurrentFunctionNode().newTemporary(lhs.getType(), binaryNode);
            if (!(lhs instanceof IndexNode)) {
                binaryNode.setRHS(convert(rhs, lhsType));
            }
        } else {
            // Force symbol to be object if not numeric assignment.
            binaryNode.setRHS(convert(rhs, Type.OBJECT));
            getCurrentFunctionNode().newTemporary(Type.OBJECT, binaryNode);

            if (lhs instanceof IdentNode) {
                lhs.getSymbol().setType(Type.OBJECT);
            }
        }

        if (lhs instanceof AccessNode) {
            final Node baseNode = ((AccessNode)lhs).getBase();

            if (baseNode.getSymbol().isThis()) {
                final IdentNode property = ((AccessNode)lhs).getProperty();
                getCurrentFunctionNode().addThisProperty(property.getName(), property);
            }
        }

        return binaryNode;
    }

    @Override
    public Node enterASSIGN_ADD(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_ADD(final BinaryNode binaryNode) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();
        final boolean bothNumeric = lhs.getType().isNumeric() && rhs.getType().isNumeric();

        /*
         * In the case of bothNumeric,
         * compute type for lhs += rhs. Assign type to lhs. Assign type to
         * temporary for this node. Convert rhs from whatever it was to this
         * type. Legacy wise dest has always been narrowed. It should
         * actually only be the lhs that is the double, or other narrower
         * type than OBJECT
         */
        return leaveAssign(binaryNode, bothNumeric ? binaryType(binaryNode) : Type.OBJECT);
    }

    @Override
    public Node enterASSIGN_BIT_AND(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_AND(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node enterASSIGN_BIT_OR(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_OR(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node enterASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_BIT_XOR(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node enterASSIGN_DIV(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_DIV(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node enterASSIGN_MOD(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_MOD(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node enterASSIGN_MUL(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_MUL(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node enterASSIGN_SAR(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SAR(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node enterASSIGN_SHL(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SHL(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node enterASSIGN_SHR(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SHR(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node enterASSIGN_SUB(final BinaryNode binaryNode) {
        return enterAssign(binaryNode);
    }

    @Override
    public Node leaveASSIGN_SUB(final BinaryNode binaryNode) {
        return leaveAssign(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node leaveBIT_AND(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, Type.INT);
    }

    @Override
    public Node leaveBIT_OR(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, Type.INT);
    }

    @Override
    public Node leaveBIT_XOR(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, Type.INT);
    }

    @Override
    public Node leaveCOMMARIGHT(final BinaryNode binaryNode) {
        binaryNode.setLHS(discard(binaryNode.lhs()));
        getCurrentFunctionNode().newTemporary(binaryNode.rhs().getType(), binaryNode);

        return binaryNode;
    }

    @Override
    public Node leaveCOMMALEFT(final BinaryNode binaryNode) {
        binaryNode.setRHS(discard(binaryNode.rhs()));
        getCurrentFunctionNode().newTemporary(binaryNode.lhs().getType(), binaryNode);

        return binaryNode;
    }

    @Override
    public Node leaveDIV(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node leaveEQ(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, EQ);
    }

    @Override
    public Node leaveEQ_STRICT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, EQ_STRICT);
    }

    private Node leaveCmp(final BinaryNode binaryNode, final RuntimeNode.Request request) {
        final Node lhs = binaryNode.lhs();
        final Node rhs = binaryNode.rhs();

        /* Attempt to turn this comparison into a constant and collapse it */
        final LiteralNode<?> literalNode = new BinaryNodeConstantEvaluator(binaryNode).eval();
        if (literalNode != null) {
            return newLiteral(literalNode);
        }

        // another case where dest != source operand types always a boolean
        getCurrentFunctionNode().newTemporary(request.getReturnType(), binaryNode);

        ensureTypeNotUnknown(lhs);
        ensureTypeNotUnknown(rhs);
        final Type type = Type.widest(lhs.getType(), rhs.getType());

        if (type.isObject()) {
            return newRuntime(binaryNode, request);
        }

        if ((request.equals(EQ_STRICT) || request.equals(NE_STRICT)) && lhs.getType().isBoolean() != rhs.getType().isBoolean()) {
            // special case: number compared against boolean => never equal. must not convert!
            final boolean              result     = request.equals(NE_STRICT);
            final LiteralNode<Boolean> resultNode = LiteralNode.newInstance(source, 0, 0, result);
            final boolean              canSkipLHS = (lhs instanceof LiteralNode);
            final boolean              canSkipRHS = (rhs instanceof LiteralNode);

            if (canSkipLHS && canSkipRHS) {
                return resultNode.accept(this);
            }

            final Node argNode;

            if (!canSkipLHS && !canSkipRHS) {
                argNode = new BinaryNode(source, Token.recast(binaryNode.getToken(), TokenType.COMMARIGHT), lhs, rhs);
            } else {
                argNode = !canSkipLHS ? lhs : rhs;
            }

            return new BinaryNode(source, Token.recast(binaryNode.getToken(), TokenType.COMMARIGHT), argNode, resultNode).accept(this);
        }

        binaryNode.setLHS(convert(lhs, type));
        binaryNode.setRHS(convert(rhs, type));

        return binaryNode;
    }

    @Override
    public Node leaveGE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, GE);
    }

    @Override
    public Node leaveGT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, GT);
    }

    private Node exitIN_INSTANCEOF(final BinaryNode binaryNode, final Request request) {
        getCurrentFunctionNode().newTemporary(request.getReturnType(), binaryNode);
        return newRuntime(binaryNode, request);
    }

    @Override
    public Node leaveIN(final BinaryNode binaryNode) {
        return exitIN_INSTANCEOF(binaryNode, IN);
    }

    @Override
    public Node leaveINSTANCEOF(final BinaryNode binaryNode) {
        return exitIN_INSTANCEOF(binaryNode, INSTANCEOF);
    }

    @Override
    public Node leaveLE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, LE);
    }

    @Override
    public Node leaveLT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, LT);
    }

    @Override
    public Node leaveMOD(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node leaveMUL(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode,  binaryType(binaryNode));
    }

    @Override
    public Node leaveNE(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, NE);
    }

    @Override
    public Node leaveNE_STRICT(final BinaryNode binaryNode) {
        return leaveCmp(binaryNode, NE_STRICT);
    }

    @Override
    public Node leaveOR(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, Type.OBJECT, null, null);
    }

    @Override
    public Node leaveSAR(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, Type.INT);
    }

    @Override
    public Node leaveSHL(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, Type.INT);
    }

    @Override
    public Node leaveSHR(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, Type.LONG, Type.INT, Type.INT);
    }

    @Override
    public Node leaveSUB(final BinaryNode binaryNode) {
        return leaveBinary(binaryNode, binaryType(binaryNode));
    }

    @Override
    public Node leave(final TernaryNode ternaryNode) {
        final Node test = ternaryNode.lhs();
        final Node lhs  = ternaryNode.rhs();
        final Node rhs  = ternaryNode.third();

        ensureTypeNotUnknown(lhs);
        ensureTypeNotUnknown(rhs);
        final Type type = Type.widest(lhs.getType(), rhs.getType());

        ternaryNode.setRHS(convert(lhs, type));
        ternaryNode.setThird(convert(rhs, type));

        // optimize away the ternary if the test is constant.
        if (test.getSymbol().isConstant()) {
            return ((LiteralNode<?>)test).isTrue() ? lhs : rhs;
        }

        ternaryNode.setLHS(convert(test, Type.BOOLEAN));

        getCurrentFunctionNode().newTemporary(type, ternaryNode);

        return ternaryNode;
    }

    private static void ensureTypeNotUnknown(final Node node) {
        final Symbol symbol = node.getSymbol();

        /*
         * Note that not just unknowns, but params need to be blown
         * up to objects, because we can have something like
         *
         * function f(a) {
         *    var b = ~a; //b and a are inferred to be int
         *    return b;
         * }
         *
         * In this case, it would be correct to say that "if you have
         * an int at the callsite, just pass it".
         *
         * However
         *
         * function f(a) {
         *    var b = ~a;      //b and a are inferred to be int
         *    return b == 17;  //b is still inferred to be int.
         * }
         *
         * can be called with f("17") and if we assume that b is an
         * int and don't blow it up to an object in the comparison, we
         * are screwed. I hate JavaScript.
         *
         * This check has to be done for any operation that might take
         * objects as parameters, for example +, but not *, which is known
         * to coerce types into doubles
         */
        if (node.getType().isUnknown() || symbol.isParam()) {
            symbol.setType(Type.OBJECT);
            symbol.setCanBeUndefined();
         }
    }

    /**
     * A simple node visitor that ensure that scope and slot information is correct.
     * This is run as a post pass after we know all scope changing information about
     * the Lowering. This is also called after potential mutations like splitting
     * have taken place, as splitting forces scope.
     *
     * This was previously done on a per functionNode basis in {@link CodeGenerator},
     * but this is too late for type information to be used in {@link AccessSpecializer}
     */
    static class FinalizeSymbols extends NodeVisitor {
        @Override
        public Node leave(final Block block) {
            return updateSymbols(block);
        }

        @Override
        public Node leave(final FunctionNode function) {
            return updateSymbols(function);
        }

        private static void updateSymbolsLog(final FunctionNode functionNode, final Symbol symbol, final boolean loseSlot) {
            if (!symbol.isScope()) {
                LOG.finest("updateSymbols: " + symbol + " => scope, because all vars in " + functionNode.getName() + " are in scope");
            }
            if (loseSlot && symbol.hasSlot()) {
                LOG.finest("updateSymbols: " + symbol + " => no slot, because all vars in " + functionNode.getName() + " are in scope");
            }
        }

        // called after a block or function node (subclass of block) is finished
        // to correct scope and slot assignment for variables
        private static Block updateSymbols(final Block block) {

            if (!block.needsScope()) {
                return block; // nothing to do
            }

            assert !(block instanceof FunctionNode) || block.getFunction() == block;

            final FunctionNode functionNode   = block.getFunction();
            final List<Symbol> symbols        = block.getFrame().getSymbols();
            final boolean      allVarsInScope = functionNode.varsInScope();
            final boolean      isVarArg       = functionNode.isVarArg();

            for (final Symbol symbol : symbols) {
                if (symbol.isInternal() || symbol.isThis()) {
                    continue;
                }

                if (symbol.isVar()) {
                    if (allVarsInScope || symbol.isScope()) {
                        updateSymbolsLog(functionNode, symbol, true);
                        symbol.setIsScope();
                        symbol.setNeedsSlot(false);
                    } else {
                        assert symbol.hasSlot() : symbol + " should have a slot only, no scope";
                    }
                } else if (symbol.isParam() && (allVarsInScope || isVarArg || symbol.isScope())) {
                    updateSymbolsLog(functionNode, symbol, isVarArg);
                    symbol.setIsScope();
                    symbol.setNeedsSlot(!isVarArg);
                }
            }

            return block;
        }
    }

    /**
     * Helper class to evaluate constant expressions at compile time This is
     * also a simplifier used by BinaryNode visits, UnaryNode visits and
     * conversions.
     */
    private abstract static class ConstantEvaluator<T extends Node> {
        protected T            parent;
        protected final Source source;
        protected final long   token;
        protected final int    finish;

        protected ConstantEvaluator(final T parent) {
            this.parent  = parent;
            this.source  = parent.getSource();
            this.token   = parent.getToken();
            this.finish  = parent.getFinish();
        }

        /**
         * Returns a literal node that replaces the given parent node, or null if replacement
         * is impossible
         * @return the literal node
         */
        protected abstract LiteralNode<?> eval();
    }

    static class LiteralNodeConstantEvaluator extends ConstantEvaluator<LiteralNode<?>> {
        private final Type type;

        LiteralNodeConstantEvaluator(final LiteralNode<?> parent, final Type type) {
            super(parent);
            this.type = type;
        }

        @Override
        protected LiteralNode<?> eval() {
            final Object value = ((LiteralNode<?>)parent).getValue();

            LiteralNode<?> literalNode = null;

            if (type.isString()) {
                literalNode = LiteralNode.newInstance(source, token, finish, JSType.toString(value));
            } else if (type.isBoolean()) {
                literalNode = LiteralNode.newInstance(source, token, finish, JSType.toBoolean(value));
            } else if (type.isInteger()) {
                literalNode = LiteralNode.newInstance(source, token, finish, JSType.toInt32(value));
            } else if (type.isLong()) {
                literalNode = LiteralNode.newInstance(source, token, finish, JSType.toLong(value));
            } else if (type.isNumber() || parent.getType().isNumeric() && !parent.getType().isNumber()) {
                literalNode = LiteralNode.newInstance(source, token, finish, JSType.toNumber(value));
            }

            return literalNode;
        }
    }

    private static class UnaryNodeConstantEvaluator extends ConstantEvaluator<UnaryNode> {
        UnaryNodeConstantEvaluator(final UnaryNode parent) {
            super(parent);
        }

        @Override
        protected LiteralNode<?> eval() {
            final Node rhsNode = parent.rhs();

            if (!rhsNode.getSymbol().isConstant()) {
                return null;
            }

            final LiteralNode<?> rhs = (LiteralNode<?>)rhsNode;
            final boolean rhsInteger = rhs.getType().isInteger();

            LiteralNode<?> literalNode;

            switch (parent.tokenType()) {
            case ADD:
                if (rhsInteger) {
                    literalNode = LiteralNode.newInstance(source, token, finish, rhs.getInt32());
                } else {
                    literalNode = LiteralNode.newInstance(source, token, finish, rhs.getNumber());
                }
                break;
            case SUB:
                if (rhsInteger && rhs.getInt32() != 0) { // @see test/script/basic/minuszero.js
                    literalNode = LiteralNode.newInstance(source, token, finish, -rhs.getInt32());
                } else {
                    literalNode = LiteralNode.newInstance(source, token, finish, -rhs.getNumber());
                }
                break;
            case NOT:
                literalNode = LiteralNode.newInstance(source, token, finish, !rhs.getBoolean());
                break;
            case BIT_NOT:
                literalNode = LiteralNode.newInstance(source, token, finish, ~rhs.getInt32());
                break;
            default:
                return null;
            }

            return literalNode;
        }
    }

    private static class BinaryNodeConstantEvaluator extends ConstantEvaluator<BinaryNode> {
        BinaryNodeConstantEvaluator(final BinaryNode parent) {
            super(parent);
        }

        @Override
        protected LiteralNode<?> eval() {

            if (!parent.lhs().getSymbol().isConstant() || !parent.rhs().getSymbol().isConstant()) {
                return null;
            }

            final LiteralNode<?> lhs = (LiteralNode<?>)parent.lhs();
            final LiteralNode<?> rhs = (LiteralNode<?>)parent.rhs();

            final Type widest = Type.widest(lhs.getType(), rhs.getType());

            boolean isInteger = widest.isInteger();
            boolean isLong    = widest.isLong();

            double value;

            switch (parent.tokenType()) {
            case AND:
                return JSType.toBoolean(lhs.getObject()) ? rhs : lhs;
            case OR:
                return JSType.toBoolean(lhs.getObject()) ? lhs : rhs;
            case DIV:
                value = lhs.getNumber() / rhs.getNumber();
                break;
            case ADD:
                value = lhs.getNumber() + rhs.getNumber();
                break;
            case MUL:
                value = lhs.getNumber() * rhs.getNumber();
                break;
            case MOD:
                value = lhs.getNumber() % rhs.getNumber();
                break;
            case SUB:
                value = lhs.getNumber() - rhs.getNumber();
                break;
            case SHR:
                return LiteralNode.newInstance(source, token, finish, (lhs.getInt32() >>> rhs.getInt32()) & 0xffff_ffffL);
            case SAR:
                return LiteralNode.newInstance(source, token, finish, lhs.getInt32() >> rhs.getInt32());
            case SHL:
                return LiteralNode.newInstance(source, token, finish, lhs.getInt32() << rhs.getInt32());
            case BIT_XOR:
                return LiteralNode.newInstance(source, token, finish, lhs.getInt32() ^ rhs.getInt32());
            case BIT_AND:
                return LiteralNode.newInstance(source, token, finish, lhs.getInt32() & rhs.getInt32());
            case BIT_OR:
                return LiteralNode.newInstance(source, token, finish, lhs.getInt32() | rhs.getInt32());
            case GE:
                return LiteralNode.newInstance(source, token, finish, ScriptRuntime.GE(lhs.getObject(), rhs.getObject()));
            case LE:
                return LiteralNode.newInstance(source, token, finish, ScriptRuntime.LE(lhs.getObject(), rhs.getObject()));
            case GT:
                return LiteralNode.newInstance(source, token, finish, ScriptRuntime.GT(lhs.getObject(), rhs.getObject()));
            case LT:
                return LiteralNode.newInstance(source, token, finish, ScriptRuntime.LT(lhs.getObject(), rhs.getObject()));
            case NE:
                return LiteralNode.newInstance(source, token, finish, ScriptRuntime.NE(lhs.getObject(), rhs.getObject()));
            case NE_STRICT:
                return LiteralNode.newInstance(source, token, finish, ScriptRuntime.NE_STRICT(lhs.getObject(), rhs.getObject()));
            case EQ:
                return LiteralNode.newInstance(source, token, finish, ScriptRuntime.EQ(lhs.getObject(), rhs.getObject()));
            case EQ_STRICT:
                return LiteralNode.newInstance(source, token, finish, ScriptRuntime.EQ_STRICT(lhs.getObject(), rhs.getObject()));
            default:
                return null;
            }

            isInteger &= value != 0.0 && JSType.isRepresentableAsInt(value);
            isLong    &= value != 0.0 && JSType.isRepresentableAsLong(value);

            if (isInteger) {
                return LiteralNode.newInstance(source, token, finish, JSType.toInt32(value));
            } else if (isLong) {
                return LiteralNode.newInstance(source, token, finish, JSType.toLong(value));
            }

            return LiteralNode.newInstance(source, token, finish, value);
        }
    }
}

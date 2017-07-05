/*
 * Copyright (c) 2010, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.nashorn.internal.codegen.CompilerConstants.ARGUMENTS_VAR;
import static jdk.nashorn.internal.codegen.CompilerConstants.EXPLODED_ARGUMENT_PREFIX;

import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jdk.nashorn.internal.ir.AccessNode;
import jdk.nashorn.internal.ir.CallNode;
import jdk.nashorn.internal.ir.Expression;
import jdk.nashorn.internal.ir.FunctionNode;
import jdk.nashorn.internal.ir.IdentNode;
import jdk.nashorn.internal.ir.LexicalContext;
import jdk.nashorn.internal.ir.Node;
import jdk.nashorn.internal.ir.visitor.NodeVisitor;
import jdk.nashorn.internal.objects.Global;
import jdk.nashorn.internal.runtime.Context;
import jdk.nashorn.internal.runtime.logging.DebugLogger;
import jdk.nashorn.internal.runtime.logging.Loggable;
import jdk.nashorn.internal.runtime.logging.Logger;
import jdk.nashorn.internal.runtime.options.Options;

/**
 * An optimization that attempts to turn applies into calls. This pattern
 * is very common for fake class instance creation, and apply
 * introduces expensive args collection and boxing
 *
 * <pre>
 * var Class = {
 *     create: function() {
 *         return function() { //vararg
 *             this.initialize.apply(this, arguments);
 *         }
 *     }
 * };
 *
 * Color = Class.create();
 *
 * Color.prototype = {
 *    red: 0, green: 0, blue: 0,
 *    initialize: function(r,g,b) {
 *        this.red = r;
 *        this.green = g;
 *        this.blue = b;
 *    }
 * }
 *
 * new Color(17, 47, 11);
 * </pre>
 */

@Logger(name="apply2call")
public final class ApplySpecialization extends NodeVisitor<LexicalContext> implements Loggable {

    private static final boolean USE_APPLY2CALL = Options.getBooleanProperty("nashorn.apply2call", true);

    private final DebugLogger log;

    private final Compiler compiler;

    private final Set<Integer> changed = new HashSet<>();

    private final Deque<List<IdentNode>> explodedArguments = new ArrayDeque<>();

    private static final String ARGUMENTS = ARGUMENTS_VAR.symbolName();

    /**
     * Apply specialization optimization. Try to explode arguments and call
     * applies as calls if they just pass on the "arguments" array and
     * "arguments" doesn't escape.
     *
     * @param compiler compiler
     */
    public ApplySpecialization(final Compiler compiler) {
        super(new LexicalContext());
        this.compiler = compiler;
        this.log = initLogger(compiler.getContext());
    }

    @Override
    public DebugLogger getLogger() {
        return log;
    }

    @Override
    public DebugLogger initLogger(final Context context) {
        return context.getLogger(this.getClass());
    }

    /**
     * Arguments may only be used as args to the apply. Everything else is disqualified
     * We cannot control arguments if they escape from the method and go into an unknown
     * scope, thus we are conservative and treat any access to arguments outside the
     * apply call as a case of "we cannot apply the optimization".
     *
     * @return true if arguments escape
     */
    private boolean argumentsEscape(final FunctionNode functionNode) {

        final Deque<Set<Expression>> stack = new ArrayDeque<>();
        //ensure that arguments is only passed as arg to apply
        try {
            functionNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                private boolean isCurrentArg(final Expression expr) {
                    return !stack.isEmpty() && stack.peek().contains(expr); //args to current apply call
                }

                private boolean isArguments(final Expression expr) {
                    return expr instanceof IdentNode && ARGUMENTS.equals(((IdentNode)expr).getName());
                }

                private boolean isParam(final String name) {
                    for (final IdentNode param : functionNode.getParameters()) {
                        if (param.getName().equals(name)) {
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public Node leaveIdentNode(final IdentNode identNode) {
                    if (isParam(identNode.getName()) || ARGUMENTS.equals(identNode.getName()) && !isCurrentArg(identNode)) {
                        throw new UnsupportedOperationException();
                    }
                    return identNode;
                }

                @Override
                public boolean enterCallNode(final CallNode callNode) {
                    final Set<Expression> callArgs = new HashSet<>();
                    if (isApply(callNode)) {
                        final List<Expression> argList = callNode.getArgs();
                        if (argList.size() != 2 || !isArguments(argList.get(argList.size() - 1))) {
                            throw new UnsupportedOperationException();
                        }
                        callArgs.addAll(callNode.getArgs());
                    }
                    stack.push(callArgs);
                    return true;
                }

                @Override
                public Node leaveCallNode(final CallNode callNode) {
                    stack.pop();
                    return callNode;
                }
            });
        } catch (final UnsupportedOperationException e) {
            log.fine("'arguments' escapes, is not used in standard call dispatch, or is reassigned in '" + functionNode.getName() + "'. Aborting");
            return true; //bad
        }

        return false;
    }

    @Override
    public boolean enterCallNode(final CallNode callNode) {
        return !explodedArguments.isEmpty();
    }

    @Override
    public Node leaveCallNode(final CallNode callNode) {
        //apply needs to be a global symbol or we don't allow it

        final List<IdentNode> newParams = explodedArguments.peek();
        if (isApply(callNode)) {
            final List<Expression> newArgs = new ArrayList<>();
            for (final Expression arg : callNode.getArgs()) {
                if (arg instanceof IdentNode && ARGUMENTS.equals(((IdentNode)arg).getName())) {
                    newArgs.addAll(newParams);
                } else {
                    newArgs.add(arg);
                }
            }

            changed.add(lc.getCurrentFunction().getId());

            final CallNode newCallNode = callNode.setArgs(newArgs).setIsApplyToCall();

            log.fine("Transformed ",
                    callNode,
                    " from apply to call => ",
                    newCallNode,
                    " in ",
                    DebugLogger.quote(lc.getCurrentFunction().getName()));

            return newCallNode;
        }

        return callNode;
    }

    private boolean pushExplodedArgs(final FunctionNode functionNode) {
        int start = 0;

        final MethodType actualCallSiteType = compiler.getCallSiteType(functionNode);
        if (actualCallSiteType == null) {
            return false;
        }
        assert actualCallSiteType.parameterType(actualCallSiteType.parameterCount() - 1) != Object[].class : "error vararg callsite passed to apply2call " + functionNode.getName() + " " + actualCallSiteType;

        final TypeMap ptm = compiler.getTypeMap();
        if (ptm.needsCallee()) {
            start++;
        }

        start++; //we always uses this

        final List<IdentNode> params    = functionNode.getParameters();
        final List<IdentNode> newParams = new ArrayList<>();
        final long to = Math.max(params.size(), actualCallSiteType.parameterCount() - start);
        for (int i = 0; i < to; i++) {
            if (i >= params.size()) {
                newParams.add(new IdentNode(functionNode.getToken(), functionNode.getFinish(), EXPLODED_ARGUMENT_PREFIX.symbolName() + (i)));
            } else {
                newParams.add(params.get(i));
            }
        }

        explodedArguments.push(newParams);
        return true;
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        if (!USE_APPLY2CALL) {
            return false;
        }

        if (!Global.instance().isSpecialNameValid("apply")) {
            log.fine("Apply transform disabled: apply/call overridden");
            assert !Global.instance().isSpecialNameValid("call") : "call and apply should have the same SwitchPoint";
            return false;
        }

        if (!compiler.isOnDemandCompilation()) {
            return false;
        }

        if (functionNode.hasEval()) {
            return false;
        }

        if (argumentsEscape(functionNode)) {
            return false;
        }

        return pushExplodedArgs(functionNode);
    }

    /**
     * Try to do the apply to call transformation
     * @return true if successful, false otherwise
     */
    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode0) {
        FunctionNode newFunctionNode = functionNode0;
        final String functionName = newFunctionNode.getName();

        if (changed.contains(newFunctionNode.getId())) {
            newFunctionNode = newFunctionNode.clearFlag(lc, FunctionNode.USES_ARGUMENTS).
                    setFlag(lc, FunctionNode.HAS_APPLY_TO_CALL_SPECIALIZATION).
                    setParameters(lc, explodedArguments.peek());

            if (log.isEnabled()) {
                log.info("Successfully specialized apply to call in '",
                        functionName,
                        " params=",
                        explodedArguments.peek(),
                        "' id=",
                        newFunctionNode.getId(),
                        " source=",
                        newFunctionNode.getSource().getURL());
            }
        }

        explodedArguments.pop();

        return newFunctionNode;
    }

    private static boolean isApply(final CallNode callNode) {
        final Expression f = callNode.getFunction();
        return f instanceof AccessNode && "apply".equals(((AccessNode)f).getProperty());
    }

}

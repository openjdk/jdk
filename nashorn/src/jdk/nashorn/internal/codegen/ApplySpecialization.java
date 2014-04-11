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
import jdk.nashorn.internal.runtime.DebugLogger;
import jdk.nashorn.internal.runtime.RecompilableScriptFunctionData;

/**
 * An optimization that attempts to turn applies into calls. This pattern
 * is very common for fake class instance creation, and apply
 * introduces expensive args collection and boxing
 *
 * <pre>
 * {@code
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
 * }
 * </pre>
 */

public class ApplySpecialization {

    private final RecompilableScriptFunctionData data;

    private FunctionNode functionNode;

    private final MethodType actualCallSiteType;

    private static final DebugLogger LOG = new DebugLogger("apply2call");

    private static final String ARGUMENTS = ARGUMENTS_VAR.symbolName();

    private boolean changed;

    private boolean finished;

    private final boolean isRestOf;

    /**
     * Return the apply to call specialization logger
     * @return the logger
     */
    public static DebugLogger getLogger() {
        return LOG;
    }

    /**
     * Apply specialization optimization. Try to explode arguments and call
     * applies as calls if they just pass on the "arguments" array and
     * "arguments" doesn't escape.
     *
     * @param data                recompilable script function data, which contains e.g. needs callee information
     * @param functionNode        functionNode
     * @param actualCallSiteType  actual call site type that we use (not Object[] varargs)
     * @param isRestOf            is this a restof method
     */
    public ApplySpecialization(final RecompilableScriptFunctionData data, final FunctionNode functionNode, final MethodType actualCallSiteType, final boolean isRestOf) {
        this.data = data;
        this.functionNode = functionNode;
        this.actualCallSiteType = actualCallSiteType;
        this.isRestOf = isRestOf;
    }

    /**
     * Return the function node, possibly after transformation
     * @return function node
     */
    public FunctionNode getFunctionNode() {
        return functionNode;
    }

    /**
     * Arguments may only be used as args to the apply. Everything else is disqualified
     * @return true if arguments escape
     */
    private boolean argumentsEscape() {

        final Deque<Set<Expression>> stack = new ArrayDeque<>();
        //ensure that arguments is only passed as arg to apply
        try {
            functionNode = (FunctionNode)functionNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                private boolean isCurrentArg(final Expression expr) {
                    return !stack.isEmpty() && stack.peek().contains(expr);
                }

                private boolean isArguments(final Expression expr) {
                    return expr instanceof IdentNode && ARGUMENTS.equals(((IdentNode)expr).getName());
                }

                @Override
                public Node leaveIdentNode(final IdentNode identNode) {
                    if (ARGUMENTS.equals(identNode.getName()) && !isCurrentArg(identNode)) {
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
            LOG.fine("'arguments' escapes, is not used in standard call dispatch, or is reassigned in '" + functionNode.getName() + "'. Aborting");
            return true; //bad
        }

        return false;
    }

    private boolean finish() {
        finished = true;
        return changed;
    }

    /**
     * Try to do the apply to call transformation
     * @return true if successful, false otherwise
     */
    public boolean transform() {
        if (finished) {
            throw new AssertionError("Can't apply transform twice");
        }

        changed = false;

        if (!Global.instance().isSpecialNameValid("apply")) {
            LOG.fine("Apply transform disabled: apply/call overridden");
            assert !Global.instance().isSpecialNameValid("call") : "call and apply should have the same SwitchPoint";
            return finish();
        }

        //eval can do anything to escape arguments so that is not ok
        if (functionNode.hasEval()) {
            return finish();
        }

        if (argumentsEscape()) {
            return finish();
        }

        int start = 0;

        if (data.needsCallee()) {
            start++;
        }

        start++; //we always uses this

        final List<IdentNode> newParams = new ArrayList<>();
        for (int i = start; i < actualCallSiteType.parameterCount(); i++) {
            newParams.add(new IdentNode(functionNode.getToken(), functionNode.getFinish(), EXPLODED_ARGUMENT_PREFIX.symbolName() + (i - start)));
        }

        // expand arguments
        // this function has to be guarded with call and apply being builtins
        functionNode = (FunctionNode)functionNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
            @Override
            public Node leaveCallNode(final CallNode callNode) {
                //apply needs to be a global symbol or we don't allow it

                if (isApply(callNode)) {
                    final List<Expression> newArgs = new ArrayList<>();
                    for (final Expression arg : callNode.getArgs()) {
                        if (arg instanceof IdentNode && ARGUMENTS.equals(((IdentNode)arg).getName())) {
                            newArgs.addAll(newParams);
                        } else {
                            newArgs.add(arg);
                        }
                    }

                    changed = true;

                    final CallNode newCallNode = callNode.setArgs(newArgs).setIsApplyToCall();
                    LOG.fine("Transformed " + callNode + " from apply to call => " + newCallNode + " in '" + functionNode.getName() + "'");
                    return newCallNode;
                }

                return callNode;
            }
        });

        if (changed) {
            functionNode = functionNode.clearFlag(null, FunctionNode.USES_ARGUMENTS).
                    setFlag(null, FunctionNode.HAS_APPLY_TO_CALL_SPECIALIZATION).
                    setParameters(null, newParams);
        }

        LOG.info("Successfully specialized apply to call in '" + functionNode.getName() + "' id=" + functionNode.getId() + " signature=" + actualCallSiteType + " isRestOf=" + isRestOf);
        return finish();
    }

    private static boolean isApply(final Node node) {
        if (node instanceof AccessNode) {
            return isApply(((AccessNode)node).getProperty());
        }
        return node instanceof IdentNode && "apply".equals(((IdentNode)node).getName());
    }

    private static boolean isApply(final CallNode callNode) {
        final Expression f = callNode.getFunction();
        return f instanceof AccessNode && isApply(f);
    }

}

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
import java.net.URL;
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
import jdk.nashorn.internal.ir.FunctionNode.CompilationState;
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

    private final Deque<MethodType> callSiteTypes = new ArrayDeque<>();

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

    @SuppressWarnings("serial")
    private static class TransformFailedException extends RuntimeException {
        TransformFailedException(final FunctionNode fn, final String message) {
            super(massageURL(fn.getSource().getURL()) + '.' + fn.getName() + " => " + message, null, false, false);
        }
    }

    @SuppressWarnings("serial")
    private static class AppliesFoundException extends RuntimeException {
        AppliesFoundException() {
            super("applies_found", null, false, false);
        }
    }

    private static final AppliesFoundException HAS_APPLIES = new AppliesFoundException();

    private boolean hasApplies(final FunctionNode functionNode) {
        try {
            functionNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {
                @Override
                public boolean enterFunctionNode(final FunctionNode fn) {
                    return fn == functionNode;
                }

                @Override
                public boolean enterCallNode(final CallNode callNode) {
                    if (isApply(callNode)) {
                        throw HAS_APPLIES;
                    }
                    return true;
                }
            });
        } catch (final AppliesFoundException e) {
            return true;
        }

        log.fine("There are no applies in ", DebugLogger.quote(functionNode.getName()), " - nothing to do.");
        return false; // no applies
    }

    /**
     * Arguments may only be used as args to the apply. Everything else is disqualified
     * We cannot control arguments if they escape from the method and go into an unknown
     * scope, thus we are conservative and treat any access to arguments outside the
     * apply call as a case of "we cannot apply the optimization".
     */
    private static void checkValidTransform(final FunctionNode functionNode) {

        final Set<Expression> argumentsFound = new HashSet<>();
        final Deque<Set<Expression>> stack = new ArrayDeque<>();

        //ensure that arguments is only passed as arg to apply
        functionNode.accept(new NodeVisitor<LexicalContext>(new LexicalContext()) {

            private boolean isCurrentArg(final Expression expr) {
                return !stack.isEmpty() && stack.peek().contains(expr); //args to current apply call
            }

            private boolean isArguments(final Expression expr) {
                if (expr instanceof IdentNode && ARGUMENTS.equals(((IdentNode)expr).getName())) {
                    argumentsFound.add(expr);
                    return true;
               }
                return false;
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
                if (isParam(identNode.getName())) {
                    throw new TransformFailedException(lc.getCurrentFunction(), "parameter: " + identNode.getName());
                }
                // it's OK if 'argument' occurs as the current argument of an apply
                if (isArguments(identNode) && !isCurrentArg(identNode)) {
                    throw new TransformFailedException(lc.getCurrentFunction(), "is 'arguments': " + identNode.getName());
                }
                return identNode;
            }

            @Override
            public boolean enterCallNode(final CallNode callNode) {
                final Set<Expression> callArgs = new HashSet<>();
                if (isApply(callNode)) {
                    final List<Expression> argList = callNode.getArgs();
                    if (argList.size() != 2 || !isArguments(argList.get(argList.size() - 1))) {
                        throw new TransformFailedException(lc.getCurrentFunction(), "argument pattern not matched: " + argList);
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

            if (log.isEnabled()) {
                log.fine("Transformed ",
                        callNode,
                        " from apply to call => ",
                        newCallNode,
                        " in ",
                        DebugLogger.quote(lc.getCurrentFunction().getName()));
            }

            return newCallNode;
        }

        return callNode;
    }

    private void pushExplodedArgs(final FunctionNode functionNode) {
        int start = 0;

        final MethodType actualCallSiteType = compiler.getCallSiteType(functionNode);
        if (actualCallSiteType == null) {
            throw new TransformFailedException(lc.getCurrentFunction(), "No callsite type");
        }
        assert actualCallSiteType.parameterType(actualCallSiteType.parameterCount() - 1) != Object[].class : "error vararg callsite passed to apply2call " + functionNode.getName() + " " + actualCallSiteType;

        final TypeMap ptm = compiler.getTypeMap();
        if (ptm.needsCallee()) {
            start++;
        }

        start++; // we always use this

        assert functionNode.getNumOfParams() == 0 : "apply2call on function with named paramaters!";
        final List<IdentNode> newParams = new ArrayList<>();
        final long to = actualCallSiteType.parameterCount() - start;
        for (int i = 0; i < to; i++) {
            newParams.add(new IdentNode(functionNode.getToken(), functionNode.getFinish(), EXPLODED_ARGUMENT_PREFIX.symbolName() + (i)));
        }

        callSiteTypes.push(actualCallSiteType);
        explodedArguments.push(newParams);
    }

    @Override
    public boolean enterFunctionNode(final FunctionNode functionNode) {
        // Cheap tests first
        if (!(
                // is the transform globally enabled?
                USE_APPLY2CALL

                // Are we compiling lazily? We can't known the number and types of the actual parameters at
                // the caller when compiling eagerly, so this only works with on-demand compilation.
                && compiler.isOnDemandCompilation()

                // Does the function even reference the "arguments" identifier (without redefining it)? If not,
                // it trivially can't have an expression of form "f.apply(self, arguments)" that this transform
                // is targeting.
                && functionNode.needsArguments()

                // Does the function have eval? If so, it can arbitrarily modify arguments so we can't touch it.
                && !functionNode.hasEval()

                // Finally, does the function declare any parameters explicitly? We don't support that. It could
                // be done, but has some complications. Therefore only a function with no explicit parameters
                // is considered.
                && functionNode.getNumOfParams() == 0))
        {
            return false;
        }

        if (!Global.isBuiltinFunctionPrototypeApply()) {
            log.fine("Apply transform disabled: apply/call overridden");
            assert !Global.isBuiltinFunctionPrototypeCall() : "call and apply should have the same SwitchPoint";
            return false;
        }

        if (!hasApplies(functionNode)) {
            return false;
        }

        if (log.isEnabled()) {
            log.info("Trying to specialize apply to call in '",
                    functionNode.getName(),
                    "' params=",
                    functionNode.getParameters(),
                    " id=",
                    functionNode.getId(),
                    " source=",
                    massageURL(functionNode.getSource().getURL()));
        }

        try {
            checkValidTransform(functionNode);
            pushExplodedArgs(functionNode);
        } catch (final TransformFailedException e) {
            log.info("Failure: ", e.getMessage());
            return false;
        }

        return true;
    }

    /**
     * Try to do the apply to call transformation
     * @return true if successful, false otherwise
     */
    @Override
    public Node leaveFunctionNode(final FunctionNode functionNode) {
        FunctionNode newFunctionNode = functionNode;
        final String functionName = newFunctionNode.getName();

        if (changed.contains(newFunctionNode.getId())) {
            newFunctionNode = newFunctionNode.clearFlag(lc, FunctionNode.USES_ARGUMENTS).
                    setFlag(lc, FunctionNode.HAS_APPLY_TO_CALL_SPECIALIZATION).
                    setParameters(lc, explodedArguments.peek());

            if (log.isEnabled()) {
                log.info("Success: ",
                        massageURL(newFunctionNode.getSource().getURL()),
                        '.',
                        functionName,
                        "' id=",
                        newFunctionNode.getId(),
                        " params=",
                        callSiteTypes.peek());
            }
        }

        callSiteTypes.pop();
        explodedArguments.pop();

        return newFunctionNode.setState(lc, CompilationState.BUILTINS_TRANSFORMED);
    }

    private static boolean isApply(final CallNode callNode) {
        final Expression f = callNode.getFunction();
        return f instanceof AccessNode && "apply".equals(((AccessNode)f).getProperty());
    }

    private static String massageURL(final URL url) {
        if (url == null) {
            return "<null>";
        }
        final String str = url.toString();
        final int slash = str.lastIndexOf('/');
        if (slash == -1) {
            return str;
        }
        return str.substring(slash + 1);
    }
}

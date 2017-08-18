/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package org.graalvm.compiler.nodes.graphbuilderconf;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.type.StampTool;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Plugin for handling a specific method invocation.
 */
public interface InvocationPlugin extends GraphBuilderPlugin {

    /**
     * The receiver in a non-static method. The class literal for this interface must be used with
     * {@link InvocationPlugins#put(InvocationPlugin, boolean, boolean, Class, String, Class...)} to
     * denote the receiver argument for such a non-static method.
     */
    public interface Receiver {
        /**
         * Gets the receiver value, null checking it first if necessary.
         *
         * @return the receiver value with a {@linkplain StampTool#isPointerNonNull(ValueNode)
         *         non-null} stamp
         */
        default ValueNode get() {
            return get(true);
        }

        /**
         * Gets the receiver value, optionally null checking it first if necessary.
         */
        ValueNode get(boolean performNullCheck);

        /**
         * Determines if the receiver is constant.
         */
        default boolean isConstant() {
            return false;
        }
    }

    /**
     * Determines if this plugin is for a method with a polymorphic signature (e.g.
     * {@link MethodHandle#invokeExact(Object...)}).
     */
    default boolean isSignaturePolymorphic() {
        return false;
    }

    /**
     * Determines if this plugin can only be used when inlining the method is it associated with.
     * That is, this plugin cannot be used when the associated method is the compilation root.
     */
    default boolean inlineOnly() {
        return isSignaturePolymorphic();
    }

    /**
     * Handles invocation of a signature polymorphic method.
     *
     * @param receiver access to the receiver, {@code null} if {@code targetMethod} is static
     * @param argsIncludingReceiver all arguments to the invocation include the raw receiver in
     *            position 0 if {@code targetMethod} is not static
     * @see #execute
     */
    default boolean applyPolymorphic(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode... argsIncludingReceiver) {
        return defaultHandler(b, targetMethod, receiver, argsIncludingReceiver);
    }

    /**
     * @see #execute
     */
    default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver) {
        return defaultHandler(b, targetMethod, receiver);
    }

    /**
     * @see #execute
     */
    default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg) {
        return defaultHandler(b, targetMethod, receiver, arg);
    }

    /**
     * @see #execute
     */
    default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2);
    }

    /**
     * @see #execute
     */
    default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3);
    }

    /**
     * @see #execute
     */
    default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4);
    }

    /**
     * @see #execute
     */
    default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5);
    }

    /**
     * @see #execute
     */
    default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    /**
     * @see #execute
     */
    default boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode arg1, ValueNode arg2, ValueNode arg3, ValueNode arg4, ValueNode arg5,
                    ValueNode arg6, ValueNode arg7) {
        return defaultHandler(b, targetMethod, receiver, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    /**
     * Executes this plugin against a set of invocation arguments.
     *
     * The default implementation in {@link InvocationPlugin} dispatches to the {@code apply(...)}
     * method that matches the number of arguments or to {@link #applyPolymorphic} if {@code plugin}
     * is {@linkplain #isSignaturePolymorphic() signature polymorphic}.
     *
     * @param targetMethod the method for which this plugin is being applied
     * @param receiver access to the receiver, {@code null} if {@code targetMethod} is static
     * @param argsIncludingReceiver all arguments to the invocation include the receiver in position
     *            0 if {@code targetMethod} is not static
     * @return {@code true} if this plugin handled the invocation of {@code targetMethod}
     *         {@code false} if the graph builder should process the invoke further (e.g., by
     *         inlining it or creating an {@link Invoke} node). A plugin that does not handle an
     *         invocation must not modify the graph being constructed.
     */
    default boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] argsIncludingReceiver) {
        if (isSignaturePolymorphic()) {
            return applyPolymorphic(b, targetMethod, receiver, argsIncludingReceiver);
        } else if (receiver != null) {
            assert !targetMethod.isStatic();
            assert argsIncludingReceiver.length > 0;
            if (argsIncludingReceiver.length == 1) {
                return apply(b, targetMethod, receiver);
            } else if (argsIncludingReceiver.length == 2) {
                return apply(b, targetMethod, receiver, argsIncludingReceiver[1]);
            } else if (argsIncludingReceiver.length == 3) {
                return apply(b, targetMethod, receiver, argsIncludingReceiver[1], argsIncludingReceiver[2]);
            } else if (argsIncludingReceiver.length == 4) {
                return apply(b, targetMethod, receiver, argsIncludingReceiver[1], argsIncludingReceiver[2], argsIncludingReceiver[3]);
            } else if (argsIncludingReceiver.length == 5) {
                return apply(b, targetMethod, receiver, argsIncludingReceiver[1], argsIncludingReceiver[2], argsIncludingReceiver[3], argsIncludingReceiver[4]);
            } else {
                return defaultHandler(b, targetMethod, receiver, argsIncludingReceiver);
            }
        } else {
            assert targetMethod.isStatic();
            if (argsIncludingReceiver.length == 0) {
                return apply(b, targetMethod, null);
            } else if (argsIncludingReceiver.length == 1) {
                return apply(b, targetMethod, null, argsIncludingReceiver[0]);
            } else if (argsIncludingReceiver.length == 2) {
                return apply(b, targetMethod, null, argsIncludingReceiver[0], argsIncludingReceiver[1]);
            } else if (argsIncludingReceiver.length == 3) {
                return apply(b, targetMethod, null, argsIncludingReceiver[0], argsIncludingReceiver[1], argsIncludingReceiver[2]);
            } else if (argsIncludingReceiver.length == 4) {
                return apply(b, targetMethod, null, argsIncludingReceiver[0], argsIncludingReceiver[1], argsIncludingReceiver[2], argsIncludingReceiver[3]);
            } else if (argsIncludingReceiver.length == 5) {
                return apply(b, targetMethod, null, argsIncludingReceiver[0], argsIncludingReceiver[1], argsIncludingReceiver[2], argsIncludingReceiver[3], argsIncludingReceiver[4]);
            } else if (argsIncludingReceiver.length == 6) {
                return apply(b, targetMethod, null, argsIncludingReceiver[0], argsIncludingReceiver[1], argsIncludingReceiver[2], argsIncludingReceiver[3], argsIncludingReceiver[4],
                                argsIncludingReceiver[5]);
            } else if (argsIncludingReceiver.length == 7) {
                return apply(b, targetMethod, null, argsIncludingReceiver[0], argsIncludingReceiver[1], argsIncludingReceiver[2], argsIncludingReceiver[3], argsIncludingReceiver[4],
                                argsIncludingReceiver[5], argsIncludingReceiver[6]);
            } else {
                return defaultHandler(b, targetMethod, receiver, argsIncludingReceiver);
            }

        }
    }

    /**
     * Handles an invocation when a specific {@code apply} method is not available.
     */
    default boolean defaultHandler(@SuppressWarnings("unused") GraphBuilderContext b, ResolvedJavaMethod targetMethod, @SuppressWarnings("unused") InvocationPlugin.Receiver receiver,
                    ValueNode... args) {
        throw new GraalError("Invocation plugin for %s does not handle invocations with %d arguments", targetMethod.format("%H.%n(%p)"), args.length);
    }

    default StackTraceElement getApplySourceLocation(MetaAccessProvider metaAccess) {
        Class<?> c = getClass();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals("apply")) {
                return metaAccess.lookupJavaMethod(m).asStackTraceElement(0);
            } else if (m.getName().equals("defaultHandler")) {
                return metaAccess.lookupJavaMethod(m).asStackTraceElement(0);
            }
        }
        throw new GraalError("could not find method named \"apply\" or \"defaultHandler\" in " + c.getName());
    }
}

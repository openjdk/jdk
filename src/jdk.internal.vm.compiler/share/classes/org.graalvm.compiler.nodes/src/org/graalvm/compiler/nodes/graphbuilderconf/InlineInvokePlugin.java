/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.ValueNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Plugin for specifying what is inlined during graph parsing. This plugin is also notified
 * {@link #notifyBeforeInline before} and {@link #notifyAfterInline} the inlining, as well as of
 * {@link #notifyNotInlined non-inlined} invocations (i.e., those for which an {@link Invoke} node
 * is created).
 */
public interface InlineInvokePlugin extends GraphBuilderPlugin {

    /**
     * Result of a {@link #shouldInlineInvoke inlining decision}.
     */
    final class InlineInfo {

        /**
         * Denotes a call site that must not be inlined and should be implemented by a node that
         * does not speculate on the call not raising an exception.
         */
        public static final InlineInfo DO_NOT_INLINE_WITH_EXCEPTION = new InlineInfo(null, null, null);

        /**
         * Denotes a call site must not be inlined and can be implemented by a node that speculates
         * the call will not throw an exception.
         */
        public static final InlineInfo DO_NOT_INLINE_NO_EXCEPTION = new InlineInfo(null, null, null);

        /**
         * Denotes a call site must not be inlined and the execution should be transferred to
         * interpreter in case of an exception.
         */
        public static final InlineInfo DO_NOT_INLINE_DEOPTIMIZE_ON_EXCEPTION = new InlineInfo(null, null, null);

        private final ResolvedJavaMethod methodToInline;
        private final ResolvedJavaMethod originalMethod;
        private final BytecodeProvider intrinsicBytecodeProvider;

        public static InlineInfo createStandardInlineInfo(ResolvedJavaMethod methodToInline) {
            return new InlineInfo(methodToInline, null, null);
        }

        public static InlineInfo createIntrinsicInlineInfo(ResolvedJavaMethod methodToInline, ResolvedJavaMethod originalMethod, BytecodeProvider intrinsicBytecodeProvider) {
            return new InlineInfo(methodToInline, originalMethod, intrinsicBytecodeProvider);
        }

        private InlineInfo(ResolvedJavaMethod methodToInline, ResolvedJavaMethod originalMethod, BytecodeProvider intrinsicBytecodeProvider) {
            this.methodToInline = methodToInline;
            this.originalMethod = originalMethod;
            this.intrinsicBytecodeProvider = intrinsicBytecodeProvider;
        }

        /**
         * Returns the method to be inlined, or {@code null} if the call site must not be inlined.
         */
        public ResolvedJavaMethod getMethodToInline() {
            return methodToInline;
        }

        public boolean allowsInlining() {
            return methodToInline != null;
        }

        /**
         * Returns the original method if this is an inline of an intrinsic, or {@code null} if the
         * call site must not be inlined.
         */
        public ResolvedJavaMethod getOriginalMethod() {
            return originalMethod;
        }

        /**
         * Gets the provider of bytecode to be parsed for {@link #getMethodToInline()} if is is an
         * intrinsic for the original method (i.e., the {@code method} passed to
         * {@link InlineInvokePlugin#shouldInlineInvoke}). A {@code null} return value indicates
         * that this is not an intrinsic inlining.
         */
        public BytecodeProvider getIntrinsicBytecodeProvider() {
            return intrinsicBytecodeProvider;
        }
    }

    /**
     * Determines whether a call to a given method is to be inlined. The return value is a
     * tri-state:
     * <p>
     * Non-null return value with a non-null {@link InlineInfo#getMethodToInline method}: That
     * {@link InlineInfo#getMethodToInline method} is inlined. Note that it can be a different
     * method than the one specified here as the parameter, which allows method substitutions.
     * <p>
     * Non-null return value with a null {@link InlineInfo#getMethodToInline method}, e.g.,
     * {@link InlineInfo#DO_NOT_INLINE_WITH_EXCEPTION}: The method is not inlined, and other plugins
     * with a lower priority cannot overwrite this decision.
     * <p>
     * Null return value: This plugin made no decision, other plugins with a lower priority are
     * asked.
     *
     * @param b the context
     * @param method the target method of an invoke
     * @param args the arguments to the invoke
     */
    default InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        return null;
    }

    /**
     * Notification that a method is about to be inlined.
     *
     * @param methodToInline the inlined method
     */
    default void notifyBeforeInline(ResolvedJavaMethod methodToInline) {
    }

    /**
     * Notification that a method was inlined.
     *
     * @param methodToInline the inlined method
     */
    default void notifyAfterInline(ResolvedJavaMethod methodToInline) {
    }

    /**
     * Notifies this plugin of the {@link Invoke} node created for a method that was not inlined per
     * {@link #shouldInlineInvoke}.
     *
     * @param b the context
     * @param method the method that was not inlined
     * @param invoke the invoke node created for the call to {@code method}
     */
    default void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
    }
}

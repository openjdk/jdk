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

import static jdk.vm.ci.code.BytecodeFrame.AFTER_BCI;
import static jdk.vm.ci.code.BytecodeFrame.AFTER_EXCEPTION_BCI;
import static jdk.vm.ci.code.BytecodeFrame.BEFORE_BCI;
import static jdk.vm.ci.code.BytecodeFrame.INVALID_FRAMESTATE_BCI;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.ROOT_COMPILATION;

import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * An intrinsic is a substitute implementation of a Java method (or a bytecode in the case of
 * snippets) that is itself implemented in Java. This interface provides information about the
 * intrinsic currently being processed by the graph builder.
 *
 * When in the scope of an intrinsic, the graph builder does not check the value kinds flowing
 * through the JVM state since intrinsics can employ non-Java kinds to represent values such as raw
 * machine words and pointers.
 */
public class IntrinsicContext {

    /**
     * Method being intrinsified.
     */
    final ResolvedJavaMethod originalMethod;

    /**
     * Method providing the intrinsic implementation.
     */
    final ResolvedJavaMethod intrinsicMethod;

    /**
     * Provider of bytecode to be parsed for a method that is part of an intrinsic.
     */
    final BytecodeProvider bytecodeProvider;

    final CompilationContext compilationContext;

    final boolean allowPartialIntrinsicArgumentMismatch;

    public IntrinsicContext(ResolvedJavaMethod method, ResolvedJavaMethod intrinsic, BytecodeProvider bytecodeProvider, CompilationContext compilationContext) {
        this(method, intrinsic, bytecodeProvider, compilationContext, false);
    }

    public IntrinsicContext(ResolvedJavaMethod method, ResolvedJavaMethod intrinsic, BytecodeProvider bytecodeProvider, CompilationContext compilationContext,
                    boolean allowPartialIntrinsicArgumentMismatch) {
        this.originalMethod = method;
        this.intrinsicMethod = intrinsic;
        this.bytecodeProvider = bytecodeProvider;
        assert bytecodeProvider != null;
        this.compilationContext = compilationContext;
        this.allowPartialIntrinsicArgumentMismatch = allowPartialIntrinsicArgumentMismatch;
        assert !isCompilationRoot() || method.hasBytecodes() : "Cannot root compile intrinsic for native or abstract method " + method.format("%H.%n(%p)");
        assert !method.equals(intrinsic) || method.getAnnotation(MethodSubstitution.class) == null : "method and intrinsic must be different: " + method + " " + intrinsic;
    }

    /**
     * A partial intrinsic exits by (effectively) calling the intrinsified method. Normally, this
     * call must use exactly the same arguments as the call that is being intrinsified. This allows
     * to override this behavior.
     */
    public boolean allowPartialIntrinsicArgumentMismatch() {
        return allowPartialIntrinsicArgumentMismatch;
    }

    /**
     * Gets the method being intrinsified.
     */
    public ResolvedJavaMethod getOriginalMethod() {
        return originalMethod;
    }

    /**
     * Gets the method providing the intrinsic implementation.
     */
    public ResolvedJavaMethod getIntrinsicMethod() {
        return intrinsicMethod;
    }

    /**
     * Gets provider of bytecode to be parsed for a method that is part of an intrinsic.
     */
    public BytecodeProvider getBytecodeProvider() {
        return bytecodeProvider;
    }

    /**
     * Determines if a call within the compilation scope of this intrinsic represents a call to the
     * {@linkplain #getOriginalMethod() original} method. This denotes the path where a partial
     * intrinsification falls back to the original method.
     */
    public boolean isCallToOriginal(ResolvedJavaMethod targetMethod) {
        return originalMethod.equals(targetMethod) || intrinsicMethod.equals(targetMethod);
    }

    private NodeSourcePosition nodeSourcePosition;

    public boolean isPostParseInlined() {
        return compilationContext.equals(INLINE_AFTER_PARSING);
    }

    public boolean isCompilationRoot() {
        return compilationContext.equals(ROOT_COMPILATION);
    }

    public NodeSourcePosition getNodeSourcePosition() {
        return nodeSourcePosition;
    }

    public void setNodeSourcePosition(NodeSourcePosition position) {
        assert nodeSourcePosition == null : "can only be set once";
        this.nodeSourcePosition = position;
    }

    /**
     * Denotes the compilation context in which an intrinsic is being parsed.
     */
    public enum CompilationContext {
        /**
         * An intrinsic is being processed when parsing an invoke bytecode that calls the
         * intrinsified method.
         */
        INLINE_DURING_PARSING,

        /**
         * An intrinsic is being processed when inlining an {@link Invoke} in an existing graph.
         */
        INLINE_AFTER_PARSING,

        /**
         * An intrinsic is the root of compilation.
         */
        ROOT_COMPILATION
    }

    /**
     * Models the state of a graph in terms of {@link StateSplit#hasSideEffect() side effects} that
     * are control flow predecessors of the current point in a graph.
     */
    public interface SideEffectsState {

        /**
         * Determines if the current program point is preceded by one or more side effects.
         */
        boolean isAfterSideEffect();

        /**
         * Gets the side effects preceding the current program point.
         */
        Iterable<StateSplit> sideEffects();

        /**
         * Records a side effect for the current program point.
         */
        void addSideEffect(StateSplit sideEffect);
    }

    public FrameState createFrameState(StructuredGraph graph, SideEffectsState sideEffects, StateSplit forStateSplit, NodeSourcePosition sourcePosition) {
        assert forStateSplit != graph.start();
        if (forStateSplit.hasSideEffect()) {
            if (sideEffects.isAfterSideEffect()) {
                // Only the last side effect on any execution path in a replacement
                // can inherit the stateAfter of the replaced node
                FrameState invalid = graph.add(new FrameState(INVALID_FRAMESTATE_BCI));
                if (graph.trackNodeSourcePosition()) {
                    invalid.setNodeSourcePosition(sourcePosition);
                }
                for (StateSplit lastSideEffect : sideEffects.sideEffects()) {
                    lastSideEffect.setStateAfter(invalid);
                }
            }
            sideEffects.addSideEffect(forStateSplit);
            FrameState frameState;
            if (forStateSplit instanceof ExceptionObjectNode) {
                frameState = graph.add(new FrameState(AFTER_EXCEPTION_BCI, (ExceptionObjectNode) forStateSplit));
            } else {
                frameState = graph.add(new FrameState(AFTER_BCI));
            }
            if (graph.trackNodeSourcePosition()) {
                frameState.setNodeSourcePosition(sourcePosition);
            }
            return frameState;
        } else {
            if (forStateSplit instanceof AbstractMergeNode || forStateSplit instanceof LoopExitNode) {
                // Merge nodes always need a frame state
                if (sideEffects.isAfterSideEffect()) {
                    // A merge after one or more side effects
                    FrameState frameState = graph.add(new FrameState(AFTER_BCI));
                    if (graph.trackNodeSourcePosition()) {
                        frameState.setNodeSourcePosition(sourcePosition);
                    }
                    return frameState;
                } else {
                    // A merge before any side effects
                    FrameState frameState = graph.add(new FrameState(BEFORE_BCI));
                    if (graph.trackNodeSourcePosition()) {
                        frameState.setNodeSourcePosition(sourcePosition);
                    }
                    return frameState;
                }
            } else {
                // Other non-side-effects do not need a state
                return null;
            }
        }
    }

    @Override
    public String toString() {
        return "Intrinsic{original: " + originalMethod.format("%H.%n(%p)") + ", intrinsic: " + intrinsicMethod.format("%H.%n(%p)") + ", context: " + compilationContext + "}";
    }
}

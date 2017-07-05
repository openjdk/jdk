/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.api.directives;

import java.nio.charset.Charset;

// JaCoCo Exclude

/**
 * Directives that influence the compilation of methods by Graal. They don't influence the semantics
 * of the code, but they are useful for unit testing and benchmarking.
 */
public final class GraalDirectives {

    public static final double LIKELY_PROBABILITY = 0.75;
    public static final double UNLIKELY_PROBABILITY = 1.0 - LIKELY_PROBABILITY;

    public static final double SLOWPATH_PROBABILITY = 0.0001;
    public static final double FASTPATH_PROBABILITY = 1.0 - SLOWPATH_PROBABILITY;

    /**
     * Directive for the compiler to fall back to the bytecode interpreter at this point.
     */
    public static void deoptimize() {
    }

    /**
     * Directive for the compiler to fall back to the bytecode interpreter at this point, invalidate
     * the compiled code and reprofile the method.
     */
    public static void deoptimizeAndInvalidate() {
    }

    /**
     * Returns a boolean value indicating whether the method is executed in Graal-compiled code.
     */
    public static boolean inCompiledCode() {
        return false;
    }

    /**
     * A call to this method will never be duplicated by control flow optimizations in the compiler.
     */
    public static void controlFlowAnchor() {
    }

    /**
     * Injects a probability for the given condition into the profiling information of a branch
     * instruction. The probability must be a value between 0.0 and 1.0 (inclusive).
     *
     * Example usage (it specifies that the likelihood for a to be greater than b is 90%):
     *
     * <code>
     * if (injectBranchProbability(0.9, a &gt; b)) {
     *    // ...
     * }
     * </code>
     *
     * There are predefined constants for commonly used probabilities (see
     * {@link #LIKELY_PROBABILITY} , {@link #UNLIKELY_PROBABILITY}, {@link #SLOWPATH_PROBABILITY},
     * {@link #FASTPATH_PROBABILITY} ).
     *
     * @param probability the probability value between 0.0 and 1.0 that should be injected
     */
    public static boolean injectBranchProbability(double probability, boolean condition) {
        assert probability >= 0.0 && probability <= 1.0;
        return condition;
    }

    /**
     * Injects an average iteration count of a loop into the probability information of a loop exit
     * condition. The iteration count specifies how often the condition is checked, i.e. in for and
     * while loops it is one more than the body iteration count, and in do-while loops it is equal
     * to the body iteration count. The iteration count must be >= 1.0.
     *
     * Example usage (it specifies that the expected iteration count of the loop condition is 500,
     * so the iteration count of the loop body is 499):
     *
     * <code>
     * for (int i = 0; injectIterationCount(500, i < array.length); i++) {
     *     // ...
     * }
     * </code>
     *
     * @param iterations the expected number of iterations that should be injected
     */
    public static boolean injectIterationCount(double iterations, boolean condition) {
        return injectBranchProbability(1. - 1. / iterations, condition);
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(boolean value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(byte value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(short value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(char value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(int value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(long value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(float value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(double value) {
    }

    /**
     * Consume a value, making sure the compiler doesn't optimize away the computation of this
     * value, even if it is otherwise unused.
     */
    @SuppressWarnings("unused")
    public static void blackhole(Object value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(boolean value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(byte value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(short value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(char value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(int value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(long value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(float value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(double value) {
    }

    /**
     * Forces a value to be kept in a register.
     */
    @SuppressWarnings("unused")
    public static void bindToRegister(Object value) {
    }

    /**
     * Spills all caller saved registers.
     */
    public static void spillRegisters() {
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static boolean opaque(boolean value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static byte opaque(byte value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static short opaque(short value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static char opaque(char value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static int opaque(int value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static long opaque(long value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static float opaque(float value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static double opaque(double value) {
        return value;
    }

    /**
     * Do nothing, but also make sure the compiler doesn't do any optimizations across this call.
     *
     * For example, the compiler will constant fold the expression 5 * 3, but the expression 5 *
     * opaque(3) will result in a real multiplication, because the compiler will not see that
     * opaque(3) is a constant.
     */
    public static <T> T opaque(T value) {
        return value;
    }

    public static <T> T guardingNonNull(T value) {
        if (value == null) {
            deoptimize();
        }
        return value;
    }

    /**
     * Ensures that the given object will be virtual (escape analyzed) at all points that are
     * dominated by the current position.
     */
    public static void ensureVirtualized(@SuppressWarnings("unused") Object object) {
    }

    /**
     * Ensures that the given object will be virtual at the current position.
     */
    public static void ensureVirtualizedHere(@SuppressWarnings("unused") Object object) {
    }

    /**
     * Marks the beginning of an instrumentation boundary. The instrumentation code will be folded
     * during compilation and will not affect inlining heuristics regarding graph size except one on
     * compiled low-level graph size (e.g., {@code GraalOptions.SmallCompiledLowLevelGraphSize}).
     */
    public static void instrumentationBegin() {
    }

    /**
     * Marks the beginning of an instrumentation boundary and associates the instrumentation with
     * the preceding bytecode. If the instrumented instruction is {@code new}, then instrumentation
     * will adapt to optimizations concerning allocation, and only be executed if allocation really
     * happens.
     *
     * Example (the instrumentation is associated with {@code new}):
     *
     * <blockquote>
     *
     * <pre>
     *  0  new java.lang.Object
     *  3  invokestatic org.graalvm.compiler.api.directives.GraalDirectives.instrumentationBeginForPredecessor() : void
     *  6  invokestatic AllocationProfiler.countActualAllocation() : void
     *  9  invokestatic org.graalvm.compiler.api.directives.GraalDirectives.instrumentationEnd() : void
     * 12  invokespecial java.lang.Object()
     * </pre>
     *
     * </blockquote>
     *
     * @see #instrumentationBegin()
     */
    public static void instrumentationBeginForPredecessor() {
    }

    /**
     * Marks the end of the instrumentation boundary.
     *
     * @see #instrumentationBegin()
     */
    public static void instrumentationEnd() {
    }

    /**
     * @return true if the enclosing method is inlined.
     */
    public static boolean isMethodInlined() {
        return false;
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    /**
     * @return the name of the root method for the current compilation task. If the enclosing method
     *         is inlined, it returns the name of the method into which it is inlined.
     */
    public static String rootName() {
        return new String(rawRootName(), UTF8);
    }

    public static byte[] rawRootName() {
        return new byte[0];
    }

}

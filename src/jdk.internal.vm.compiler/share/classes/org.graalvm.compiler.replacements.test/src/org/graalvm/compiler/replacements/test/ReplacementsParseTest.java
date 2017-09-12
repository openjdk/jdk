/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import static org.graalvm.compiler.java.BytecodeParserOptions.InlinePartialIntrinsicExitDuringParsing;

import java.util.function.Function;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.GraalGraphError;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.debug.OpaqueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin.Receiver;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.graphbuilderconf.NodeIntrinsicPluginFactory;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.common.FloatingReadPhase;
import org.graalvm.compiler.phases.common.FrameStateAssignmentPhase;
import org.graalvm.compiler.phases.common.GuardLoweringPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.word.LocationIdentity;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests for expected behavior when parsing snippets and intrinsics.
 */
public class ReplacementsParseTest extends ReplacementsTest {

    private static final String IN_COMPILED_HANDLER_MARKER = "*** in compiled handler ***";

    /**
     * Marker value to indicate an exception handler was interpreted. We cannot use a complex string
     * expression in this context without risking non-deterministic behavior dependent on whether
     * String intrinsics are applied or whether String expression evaluation hit an uncommon trap
     * when executed by C1 or C2 (and thus potentially altering the profile such that the exception
     * handler is *not* compiled by Graal even when we want it to be).
     */
    private static final String IN_INTERPRETED_HANDLER_MARKER = "*** in interpreted handler ***";

    private InlineInvokePlugin.InlineInfo inlineInvokeDecision;
    private String inlineInvokeMethodName = null;

    @SuppressWarnings("serial")
    static class CustomError extends Error {
        CustomError(String message) {
            super(message);
        }
    }

    static final Object THROW_EXCEPTION_MARKER = new Object() {
        @Override
        public String toString() {
            return "THROW_EXCEPTION_MARKER";
        }
    };

    static int copyFirstBody(byte[] left, byte[] right, boolean left2right) {
        if (left2right) {
            byte e = left[0];
            right[0] = e;
            return e;
        } else {
            byte e = right[0];
            left[0] = e;
            return e;
        }
    }

    static int copyFirstL2RBody(byte[] left, byte[] right) {
        byte e = left[0];
        right[0] = e;
        return e;
    }

    static class TestObject {
        static double next(double v) {
            return Math.nextAfter(v, 1.0);
        }

        static double next2(double v) {
            return Math.nextAfter(v, 1.0);
        }

        static double nextAfter(double x, double d) {
            return Math.nextAfter(x, d);
        }

        TestObject() {
            this(null);
        }

        TestObject(Object id) {
            this.id = id;
        }

        final Object id;

        String stringizeId() {
            Object res = id;
            if (res == THROW_EXCEPTION_MARKER) {
                // Tests exception throwing from partial intrinsification
                throw new CustomError("ex");
            }
            return String.valueOf(res);
        }

        static String stringize(Object obj) {
            Object res = obj;
            if (res == THROW_EXCEPTION_MARKER) {
                // Tests exception throwing from partial intrinsification
                throw new CustomError("ex");
            }
            return String.valueOf(res);
        }

        static String identity(String s) {
            return s;
        }

        /**
         * @see TestObjectSubstitutions#copyFirst(byte[], byte[], boolean)
         */
        static int copyFirst(byte[] left, byte[] right, boolean left2right) {
            return copyFirstBody(left, right, left2right);
        }

        /**
         * @see TestObjectSubstitutions#copyFirstL2R(byte[], byte[])
         */
        static int copyFirstL2R(byte[] left, byte[] right) {
            return copyFirstL2RBody(left, right);
        }

        static int nonVoidIntrinsicWithCall(@SuppressWarnings("unused") int x, int y) {
            return y;
        }

        static int nonVoidIntrinsicWithOptimizedSplit(int x) {
            return x;
        }
    }

    @ClassSubstitution(TestObject.class)
    static class TestObjectSubstitutions {

        @MethodSubstitution(isStatic = true)
        static double nextAfter(double x, double d) {
            double xx = (x == -0.0 ? 0.0 : x);
            return Math.nextAfter(xx, d);
        }

        /**
         * Tests conditional intrinsification of a static method.
         */
        @MethodSubstitution
        static String stringize(Object obj) {
            if (obj != null && obj.getClass() == String.class) {
                return asNonNullString(obj);
            } else {
                // A recursive call denotes exiting/deoptimizing
                // out of the partial intrinsification to the
                // slow/uncommon case.
                return stringize(obj);
            }
        }

        /**
         * Tests conditional intrinsification of a non-static method.
         */
        @MethodSubstitution(isStatic = false)
        static String stringizeId(TestObject thisObj) {
            if (thisObj.id != null && thisObj.id.getClass() == String.class) {
                return asNonNullString(thisObj.id);
            } else {
                // A recursive call denotes exiting/deoptimizing
                // out of the partial intrinsification to the
                // slow/uncommon case.
                return outOfLinePartialIntrinsification(thisObj);
            }
        }

        static String outOfLinePartialIntrinsification(TestObject thisObj) {
            return stringizeId(thisObj);
        }

        public static String asNonNullString(Object object) {
            return asNonNullStringIntrinsic(object, String.class, true, true);
        }

        @NodeIntrinsic(PiNode.class)
        private static native String asNonNullStringIntrinsic(Object object, @ConstantNodeParameter Class<?> toType, @ConstantNodeParameter boolean exactType, @ConstantNodeParameter boolean nonNull);

        /**
         * An valid intrinsic as the frame state associated with the merge should prevent the frame
         * states associated with the array stores from being associated with subsequent
         * deoptimizing nodes.
         */
        @MethodSubstitution
        static int copyFirst(byte[] left, byte[] right, boolean left2right) {
            return copyFirstBody(left, right, left2right);
        }

        /**
         * An invalid intrinsic as the frame state associated with the array assignment can leak out
         * to subsequent deoptimizing nodes.
         */
        @MethodSubstitution
        static int copyFirstL2R(byte[] left, byte[] right) {
            return copyFirstL2RBody(left, right);
        }

        /**
         * Tests that non-capturing lambdas are folded away.
         */
        @MethodSubstitution
        static String identity(String value) {
            return apply(s -> s, value);
        }

        private static String apply(Function<String, String> f, String value) {
            return f.apply(value);
        }

        @MethodSubstitution(isStatic = true)
        static int nonVoidIntrinsicWithCall(int x, int y) {
            nonVoidIntrinsicWithCallStub(x);
            return y;
        }

        @MethodSubstitution(isStatic = true)
        static int nonVoidIntrinsicWithOptimizedSplit(int x) {
            if (x == GraalDirectives.opaque(x)) {
                nonVoidIntrinsicWithCallStub(x);
            }
            return x;
        }

        public static void nonVoidIntrinsicWithCallStub(int zLen) {
            nonVoidIntrinsicWithCallStub(STUB_CALL, zLen);
        }

        static final ForeignCallDescriptor STUB_CALL = new ForeignCallDescriptor("stubCall", void.class, int.class);

        @NodeIntrinsic(ForeignCallNode.class)
        private static native void nonVoidIntrinsicWithCallStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, int zLen);

    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        BytecodeProvider replacementBytecodeProvider = getSystemClassLoaderBytecodeProvider();
        Registration r = new Registration(invocationPlugins, TestObject.class, replacementBytecodeProvider);
        NodeIntrinsicPluginFactory.InjectionProvider injections = new DummyInjectionProvider();
        new PluginFactory_ReplacementsParseTest().registerPlugins(invocationPlugins, injections);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "nextAfter", double.class, double.class);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "stringize", Object.class);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "stringizeId", Receiver.class);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "copyFirst", byte[].class, byte[].class, boolean.class);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "copyFirstL2R", byte[].class, byte[].class);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "nonVoidIntrinsicWithCall", int.class, int.class);
        r.registerMethodSubstitution(TestObjectSubstitutions.class, "nonVoidIntrinsicWithOptimizedSplit", int.class);

        if (replacementBytecodeProvider.supportsInvokedynamic()) {
            r.registerMethodSubstitution(TestObjectSubstitutions.class, "identity", String.class);
        }
        super.registerInvocationPlugins(invocationPlugins);
    }

    @BeforeClass
    public static void warmupProfiles() {
        for (int i = 0; i < 40000; i++) {
            callCopyFirst(new byte[16], new byte[16], true);
            callCopyFirstL2R(new byte[16], new byte[16]);
        }
    }

    /**
     * Ensure that calling the original method from the substitution binds correctly.
     */
    @Test
    public void test1() {
        test("test1Snippet", 1.0);
    }

    public double test1Snippet(double d) {
        return TestObject.next(d);
    }

    /**
     * Ensure that calling the substitution method binds to the original method properly.
     */
    @Test
    public void test2() {
        test("test2Snippet", 1.0);
    }

    public double test2Snippet(double d) {
        return TestObject.next2(d);
    }

    /**
     * Ensure that substitution methods with assertions in them don't complain when the exception
     * constructor is deleted.
     */

    @Test
    public void testNextAfter() {
        double[] inArray = new double[1024];
        double[] outArray = new double[1024];
        for (int i = 0; i < inArray.length; i++) {
            inArray[i] = -0.0;
        }
        test("doNextAfter", inArray, outArray);
    }

    public void doNextAfter(double[] outArray, double[] inArray) {
        for (int i = 0; i < inArray.length; i++) {
            double direction = (i & 1) == 0 ? Double.POSITIVE_INFINITY : -Double.NEGATIVE_INFINITY;
            outArray[i] = TestObject.nextAfter(inArray[i], direction);
        }
    }

    private void testWithDifferentReturnValues(OptionValues options, String standardReturnValue, String compiledReturnValue, String name, Object... args) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        Object receiver = null;

        Result expect = executeExpected(method, receiver, args);
        Assert.assertEquals(standardReturnValue, expect.returnValue);
        expect = new Result(compiledReturnValue, null);
        testAgainstExpected(options, method, expect, receiver, args);
    }

    @Override
    protected InstalledCode getCode(final ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, boolean forceCompile, boolean installAsDefault, OptionValues options) {
        return super.getCode(installedCodeOwner, graph, forceCompileOverride, installAsDefault, options);
    }

    boolean forceCompileOverride;

    @Test
    public void testCallStringize() {
        test("callStringize", "a string");
        test("callStringize", Boolean.TRUE);
        // Unset 'exception seen' bit if testCallStringizeWithoutInlinePartialIntrinsicExit
        // is executed before this test
        getResolvedJavaMethod("callStringize").reprofile();
        forceCompileOverride = true;
        String standardReturnValue = IN_INTERPRETED_HANDLER_MARKER;
        String compiledReturnValue = IN_COMPILED_HANDLER_MARKER;
        testWithDifferentReturnValues(getInitialOptions(), standardReturnValue, compiledReturnValue, "callStringize", THROW_EXCEPTION_MARKER);
    }

    @Test
    public void testCallStringizeWithoutInlinePartialIntrinsicExit() {
        OptionValues options = new OptionValues(getInitialOptions(), InlinePartialIntrinsicExitDuringParsing, false);
        test(options, "callStringize", "a string");
        test(options, "callStringize", Boolean.TRUE);
        String standardReturnValue = IN_INTERPRETED_HANDLER_MARKER;
        String compiledReturnValue = IN_COMPILED_HANDLER_MARKER;
        forceCompileOverride = true;
        inlineInvokeDecision = InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
        inlineInvokeMethodName = "stringizeId";
        try {
            testWithDifferentReturnValues(options, standardReturnValue, compiledReturnValue, "callStringize", THROW_EXCEPTION_MARKER);
        } finally {
            inlineInvokeDecision = null;
            inlineInvokeMethodName = null;
        }
    }

    @Test
    public void testCallStringizeId() {
        test("callStringizeId", new TestObject("a string"));
        test("callStringizeId", new TestObject(Boolean.TRUE));
        // Unset 'exception seen' bit if testCallStringizeIdWithoutInlinePartialIntrinsicExit
        // is executed before this test
        getResolvedJavaMethod("callStringize").reprofile();
        forceCompileOverride = true;
        String standardReturnValue = IN_INTERPRETED_HANDLER_MARKER;
        String compiledReturnValue = IN_COMPILED_HANDLER_MARKER;
        testWithDifferentReturnValues(getInitialOptions(), standardReturnValue, compiledReturnValue, "callStringizeId", new TestObject(THROW_EXCEPTION_MARKER));
    }

    @Test
    public void testCallStringizeIdWithoutInlinePartialIntrinsicExit() {
        OptionValues options = new OptionValues(getInitialOptions(), InlinePartialIntrinsicExitDuringParsing, false);
        test(options, "callStringizeId", new TestObject("a string"));
        test(options, "callStringizeId", new TestObject(Boolean.TRUE));
        TestObject exceptionTestObject = new TestObject(THROW_EXCEPTION_MARKER);
        String standardReturnValue = IN_INTERPRETED_HANDLER_MARKER;
        String compiledReturnValue = IN_COMPILED_HANDLER_MARKER;
        forceCompileOverride = true;
        inlineInvokeDecision = InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
        inlineInvokeMethodName = "stringizeId";
        try {
            testWithDifferentReturnValues(options, standardReturnValue, compiledReturnValue, "callStringizeId", exceptionTestObject);
        } finally {
            inlineInvokeDecision = null;
            inlineInvokeMethodName = null;
        }
    }

    public static Object callStringize(Object obj) {
        try {
            return TestObject.stringize(obj);
        } catch (CustomError e) {
            if (GraalDirectives.inCompiledCode()) {
                return IN_COMPILED_HANDLER_MARKER;
            }
            return IN_INTERPRETED_HANDLER_MARKER;
        }
    }

    public static Object callStringizeId(TestObject testObj) {
        try {
            return testObj.stringizeId();
        } catch (CustomError e) {
            if (GraalDirectives.inCompiledCode()) {
                return IN_COMPILED_HANDLER_MARKER;
            }
            return IN_INTERPRETED_HANDLER_MARKER;
        }
    }

    @Test
    public void testRootCompileStringize() {
        ResolvedJavaMethod method = getResolvedJavaMethod(TestObject.class, "stringize");
        test(method, null, "a string");
        test(method, null, Boolean.TRUE);
        test(method, null, THROW_EXCEPTION_MARKER);
    }

    @Test
    public void testLambda() {
        test("callLambda", (String) null);
        test("callLambda", "a string");
    }

    public static String callLambda(String value) {
        return TestObject.identity(value);
    }

    public static int callCopyFirst(byte[] in, byte[] out, boolean left2right) {
        int res = TestObject.copyFirst(in, out, left2right);
        if (res == 17) {
            // A node after the intrinsic that needs a frame state.
            GraalDirectives.deoptimize();
        }
        return res;
    }

    public static int callCopyFirstWrapper(byte[] in, byte[] out, boolean left2right) {
        return callCopyFirst(in, out, left2right);
    }

    public static int callCopyFirstL2R(byte[] in, byte[] out) {
        int res = TestObject.copyFirstL2R(in, out);
        if (res == 17) {
            // A node after the intrinsic that needs a frame state.
            GraalDirectives.deoptimize();
        }
        return res;
    }

    @Test
    public void testCallCopyFirst() {
        byte[] in = {0, 1, 2, 3, 4};
        byte[] out = new byte[in.length];
        test("callCopyFirst", in, out, true);
        test("callCopyFirst", in, out, false);
    }

    @SuppressWarnings("try")
    @Test
    public void testCallCopyFirstL2R() {
        byte[] in = {0, 1, 2, 3, 4};
        byte[] out = new byte[in.length];
        try {
            test("callCopyFirstL2R", in, out);
        } catch (GraalGraphError e) {
            assertTrue(e.getMessage().startsWith("Invalid frame state"));
        }
    }

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (inlineInvokeMethodName == null || inlineInvokeMethodName.equals(method.getName())) {
            return inlineInvokeDecision;
        }
        return null;
    }

    @Test
    public void testCallCopyFirstWithoutInlinePartialIntrinsicExit() {
        OptionValues options = new OptionValues(getInitialOptions(), InlinePartialIntrinsicExitDuringParsing, false);
        inlineInvokeDecision = InlineInvokePlugin.InlineInfo.DO_NOT_INLINE_WITH_EXCEPTION;
        try {
            byte[] in = {0, 1, 2, 3, 4};
            byte[] out = new byte[in.length];
            test(options, "callCopyFirstWrapper", in, out, true);
            test(options, "callCopyFirstWrapper", in, out, false);
        } finally {
            inlineInvokeDecision = null;
        }
    }

    public static int nonVoidIntrinsicWithCall(int x, int y) {
        if (TestObject.nonVoidIntrinsicWithCall(x, y) == x) {
            GraalDirectives.deoptimize();
        }
        return y;
    }

    /**
     * This tests the case where an intrinsic ends with a runtime call but returns some kind of
     * value. This requires that a FrameState is available after the {@link ForeignCallNode} since
     * the return value must be computed on return from the call.
     */
    @Test
    public void testNonVoidIntrinsicWithCall() {
        testGraph("nonVoidIntrinsicWithCall");
    }

    public static int nonVoidIntrinsicWithOptimizedSplit(int x) {
        if (TestObject.nonVoidIntrinsicWithOptimizedSplit(x) == x) {
            GraalDirectives.deoptimize();
        }
        return x;
    }

    /**
     * This is similar to {@link #testNonVoidIntrinsicWithCall()} but has a merge after the call
     * which would normally capture the {@link FrameState} but in this case we force the merge to be
     * optimized away.
     */
    @Test
    public void testNonVoidIntrinsicWithOptimizedSplit() {
        testGraph("nonVoidIntrinsicWithOptimizedSplit");
    }

    @SuppressWarnings("try")
    private void testGraph(String name) {
        StructuredGraph graph = parseEager(name, StructuredGraph.AllowAssumptions.YES);
        try (DebugContext.Scope s0 = graph.getDebug().scope(name, graph)) {
            for (OpaqueNode node : graph.getNodes().filter(OpaqueNode.class)) {
                node.replaceAndDelete(node.getValue());
            }
            HighTierContext context = getDefaultHighTierContext();
            CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
            new FloatingReadPhase().apply(graph);
            canonicalizer.apply(graph, context);
            new DeadCodeEliminationPhase().apply(graph);
            new GuardLoweringPhase().apply(graph, getDefaultMidTierContext());
            new FrameStateAssignmentPhase().apply(graph);
        } catch (Throwable e) {
            throw graph.getDebug().handle(e);
        }
    }

    private class DummyInjectionProvider implements NodeIntrinsicPluginFactory.InjectionProvider {
        @SuppressWarnings("unchecked")
        @Override
        public <T> T getInjectedArgument(Class<T> type) {
            if (type == ForeignCallsProvider.class) {
                return (T) new ForeignCallsProvider() {
                    @Override
                    public LIRKind getValueKind(JavaKind javaKind) {
                        return null;
                    }

                    @Override
                    public boolean isReexecutable(ForeignCallDescriptor descriptor) {
                        return false;
                    }

                    @Override
                    public LocationIdentity[] getKilledLocations(ForeignCallDescriptor descriptor) {
                        return new LocationIdentity[0];
                    }

                    @Override
                    public boolean canDeoptimize(ForeignCallDescriptor descriptor) {
                        return false;
                    }

                    @Override
                    public boolean isGuaranteedSafepoint(ForeignCallDescriptor descriptor) {
                        return false;
                    }

                    @Override
                    public ForeignCallLinkage lookupForeignCall(ForeignCallDescriptor descriptor) {
                        return null;
                    }
                };
            }
            if (type == SnippetReflectionProvider.class) {
                return (T) getSnippetReflection();
            }
            return null;
        }

        @Override
        public Stamp getInjectedStamp(Class<?> type, boolean nonNull) {
            JavaKind kind = JavaKind.fromJavaClass(type);
            return StampFactory.forKind(kind);
        }
    }
}

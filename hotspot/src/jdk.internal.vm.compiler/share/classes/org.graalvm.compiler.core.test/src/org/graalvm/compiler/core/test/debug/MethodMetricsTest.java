/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test.debug;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugConfig;
import org.graalvm.compiler.debug.DebugConfigScope;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugMethodMetrics;
import org.graalvm.compiler.debug.DebugTimer;
import org.graalvm.compiler.debug.DebugVerifyHandler;
import org.graalvm.compiler.debug.DelegatingDebugConfig;
import org.graalvm.compiler.debug.DelegatingDebugConfig.Feature;
import org.graalvm.compiler.debug.GraalDebugConfig;
import org.graalvm.compiler.debug.internal.DebugScope;
import org.graalvm.compiler.debug.internal.method.MethodMetricsImpl;
import org.graalvm.compiler.debug.internal.method.MethodMetricsImpl.CompilationData;
import org.graalvm.compiler.debug.internal.method.MethodMetricsInlineeScopeInfo;
import org.graalvm.compiler.debug.internal.method.MethodMetricsPrinter;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.FixedBinaryNode;
import org.graalvm.compiler.nodes.calc.MulNode;
import org.graalvm.compiler.nodes.calc.ShiftNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SubNode;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.Phase;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class MethodMetricsTest extends GraalCompilerTest {
    static class TestApplication {
        public static int m01(int x, int y) {
            return x + y;
        }

        public static int m02(int x, int y) {
            return x * y;
        }

        public static int m03(int x, int y) {
            return x ^ y;
        }

        public static int m04(int x, int y) {
            return x >> y;
        }

        public static int m05(int x, int y) {
            return x >>> y;
        }

        public static int m06(int x, int y) {
            return x << y;
        }

        public static int m07(int x, int y) {
            return x > y ? 0 : 1;
        }

        public static int m08(int x, int y) {
            return x % y;
        }

        public static int m09(int x, int y) {
            return x / y;
        }

        public static int m10(int x, int y) {
            return x - y;
        }

    }

    public static final Class<?>[] testSignature = new Class<?>[]{int.class, int.class};
    public static final Object[] testArgs = new Object[]{10, 10};

    static class MethodMetricPhases {
        static class CountingAddPhase extends Phase {

            // typically those global metrics would be static final, but we need new timers every
            // invocation if we override the debugvaluefactory
            private final DebugCounter globalCounter = Debug.counter("GlobalMetric");
            private final DebugTimer globalTimer = Debug.timer("GlobalTimer");

            @Override
            @SuppressWarnings("try")
            protected void run(StructuredGraph graph) {
                try (DebugCloseable d = globalTimer.start()) {
                    ResolvedJavaMethod method = graph.method();
                    DebugMethodMetrics mm = Debug.methodMetrics(method);
                    mm.addToMetric(graph.getNodes().filter(InvokeNode.class).count(), "Invokes");
                    mm.incrementMetric("PhaseRunsOnMethod");
                    globalCounter.increment();
                }
            }
        }

        static class CountingShiftPhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                Debug.methodMetrics(graph.method()).addToMetric(graph.getNodes().filter(ShiftNode.class).count(), "Shifts");
            }
        }

        static class CountingMulPhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                Debug.methodMetrics(graph.method()).addToMetric(graph.getNodes().filter(MulNode.class).count(), "Muls");
            }
        }

        static class CountingSubPhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                Debug.methodMetrics(graph.method()).addToMetric(graph.getNodes().filter(SubNode.class).count(), "Subs");
            }
        }

        static class CountingDivPhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                Debug.methodMetrics(graph.method()).addToMetric(graph.getNodes().filter(SignedDivNode.class).count(), "Divs");
            }
        }

        static class CountingBinOpPhase extends Phase {
            @Override
            protected void run(StructuredGraph graph) {
                Debug.methodMetrics(graph.method()).addToMetric(graph.getNodes().filter(x -> x instanceof BinaryNode || x instanceof FixedBinaryNode).count(), "BinOps");
            }
        }

        static class ScopeTestPhase extends Phase {
            // typically those global metrics would be static final, but we need new timers every
            // invocation if we override the debugvaluefactory
            private final DebugTimer timer = Debug.timer("GlobalTimer1");
            private final DebugTimer scopedTimer = Debug.timer("GlobalTimer2");
            private final DebugTimer scopedScopedTimer = Debug.timer("GlobalTimer3");
            private final DebugTimer scopedScopedScopeTimer = Debug.timer("GlobalTimer4");

            private final DebugTimer timer1 = Debug.timer("GlobalTimer1_WithoutInlineEnhancement");
            private final DebugTimer scopedTimer1 = Debug.timer("GlobalTimer2_WithoutInlineEnhancement");
            private final DebugTimer scopedScopedTimer1 = Debug.timer("GlobalTimer3_WithoutInlineEnhancement");
            private final DebugTimer scopedScopedScopeTimer1 = Debug.timer("GlobalTimer4_WithoutInlineEnhancement");

            @Override
            @SuppressWarnings("try")
            protected void run(StructuredGraph graph) {
                // we are in an enhanced debug scope from graal compiler
                // now we open multiple inlining scopes, record their time
                try (DebugCloseable c1 = timer.start()) {
                    try (DebugCloseable c2 = scopedTimer.start()) {
                        try (DebugCloseable c3 = scopedScopedTimer.start()) {
                            // do sth unnecessary long allocating many inlinee scopes
                            for (int i = 0; i < 50; i++) {
                                try (Debug.Scope s1 = Debug.methodMetricsScope("InlineEnhancement1", MethodMetricsInlineeScopeInfo.create(graph.method()), false)) {
                                    try (DebugCloseable c4 = scopedScopedScopeTimer.start()) {
                                        new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph);
                                        // double scoped inlinee scopes should not make problems
                                        // with the data
                                        try (Debug.Scope s2 = Debug.methodMetricsScope("InlineEnhancement2", MethodMetricsInlineeScopeInfo.create(graph.method()),
                                                        false)) {
                                            new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // now lets try different counters without the inline enhancement
                try (DebugCloseable c1 = timer1.start()) {
                    try (DebugCloseable c2 = scopedTimer1.start()) {
                        try (DebugCloseable c3 = scopedScopedTimer1.start()) {
                            // do sth unnecessary long allocating many inlinee scopes
                            for (int i = 0; i < 50; i++) {
                                try (DebugCloseable c4 = scopedScopedScopeTimer1.start()) {
                                    new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph);
                                    new SchedulePhase(SchedulingStrategy.LATEST_OUT_OF_LOOPS).apply(graph);
                                }
                            }
                        }
                    }
                }
            }

        }
    }

    static DebugConfig overrideGraalDebugConfig(PrintStream log, String methodFilter, String methodMeter) {
        List<DebugDumpHandler> dumpHandlers = new ArrayList<>();
        List<DebugVerifyHandler> verifyHandlers = new ArrayList<>();
        GraalDebugConfig debugConfig = new GraalDebugConfig(
                        GraalDebugConfig.Options.Log.getValue(),
                        GraalDebugConfig.Options.Count.getValue(),
                        GraalDebugConfig.Options.TrackMemUse.getValue(),
                        GraalDebugConfig.Options.Time.getValue(),
                        GraalDebugConfig.Options.Dump.getValue(),
                        GraalDebugConfig.Options.Verify.getValue(),
                        methodFilter,
                        methodMeter,
                        log, dumpHandlers, verifyHandlers);
        return debugConfig;
    }

    private static OverrideScope overrideMetricPrinterConfig() {
        Map<OptionValue<?>, Object> mapping = new HashMap<>();
        mapping.put(MethodMetricsPrinter.Options.MethodMeterPrintAscii, true);
        return OptionValue.override(mapping);
    }

    abstract Phase additionalPhase();

    @Override
    protected Suites createSuites() {
        Suites ret = super.createSuites();
        ListIterator<BasePhase<? super HighTierContext>> iter = ret.getHighTier().findPhase(ConvertDeoptimizeToGuardPhase.class, true);
        PhaseSuite.findNextPhase(iter, CanonicalizerPhase.class);
        iter.add(additionalPhase());
        return ret;
    }

    @Test
    @SuppressWarnings("try")
    public void test() throws Throwable {
        try (DebugConfigScope s = Debug.setConfig(getConfig()); OverrideScope o = getOScope();) {
            executeMethod(TestApplication.class.getMethod("m01", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m02", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m03", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m04", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m05", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m06", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m07", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m08", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m09", testSignature), null, testArgs);
            executeMethod(TestApplication.class.getMethod("m10", testSignature), null, testArgs);
            assertValues();
        }
    }

    @Before
    public void rememberScopeId() {
        scopeIdBeforeAccess = DebugScope.getCurrentGlobalScopeId();
    }

    @After
    public void clearMMCache() {
        MethodMetricsImpl.clearMM();
    }

    abstract DebugConfig getConfig();

    OverrideScope getOScope() {
        return overrideMetricPrinterConfig();
    }

    abstract void assertValues() throws Throwable;

    @SuppressWarnings("unchecked")
    private static Map<ResolvedJavaMethod, CompilationData> readMethodMetricsImplData() {
        Map<ResolvedJavaMethod, CompilationData> threadLocalMap = null;
        for (Field f : MethodMetricsImpl.class.getDeclaredFields()) {
            if (f.getName().equals("threadEntries")) {
                f.setAccessible(true);
                Object map;
                try {
                    map = ((ThreadLocal<?>) f.get(null)).get();
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
                threadLocalMap = (Map<ResolvedJavaMethod, CompilationData>) map;
                break;
            }
        }
        return threadLocalMap;
    }

    private long scopeIdBeforeAccess;
    private long scopeIdAfterAccess;

    protected long readValFromCurrThread(ResolvedJavaMethod method, String metricName) {

        Map<ResolvedJavaMethod, CompilationData> threadLocalMap = readMethodMetricsImplData();
        assert threadLocalMap != null;
        CompilationData compilationData = threadLocalMap.get(method);
        assert compilationData != null;
        Map<Long, Map<String, Long>> compilations = compilationData.getCompilations();
        List<Map<String, Long>> compilationEntries = new ArrayList<>();
        compilations.forEach((x, y) -> {
            if (x >= scopeIdBeforeAccess && x <= scopeIdAfterAccess) {
                compilationEntries.add(y);
            }
        });
        List<Map<String, Long>> listView = compilationEntries.stream().filter(x -> x.size() > 0).collect(Collectors.toList());
        assert listView.size() <= 1 : "There must be at most one none empty compilation data point present:" + listView.size();
        /*
         * NOTE: Using the pre-generation of compilation entries for a method has the disadvantage
         * that during testing we have different points in time when we request the metric. First,
         * properly, when we use it and then when we want to know the result, but when we check the
         * result the debug context no longer holds a correct scope with the unique id, so we return
         * the first compilation entry that is not empty.
         */
        Map<String, Long> entries = listView.size() > 0 ? listView.get(0) : null;
        Long res = entries != null ? entries.get(metricName) : null;
        return res != null ? res : 0;
    }

    @SuppressWarnings("try")
    void assertValues(String metricName, long[] vals) {
        scopeIdAfterAccess = DebugScope.getCurrentGlobalScopeId();
        try (DebugConfigScope s = Debug.setConfig(new DelegatingDebugConfig().enable(Feature.METHOD_METRICS))) {
            Assert.assertEquals(vals[0], readValFromCurrThread(asResolvedJavaMethod(TestApplication.class.getMethod("m01", testSignature)), metricName));
            Assert.assertEquals(vals[1], readValFromCurrThread(asResolvedJavaMethod(TestApplication.class.getMethod("m02", testSignature)), metricName));
            Assert.assertEquals(vals[2], readValFromCurrThread(asResolvedJavaMethod(TestApplication.class.getMethod("m03", testSignature)), metricName));
            Assert.assertEquals(vals[3], readValFromCurrThread(asResolvedJavaMethod(TestApplication.class.getMethod("m04", testSignature)), metricName));
            Assert.assertEquals(vals[4], readValFromCurrThread(asResolvedJavaMethod(TestApplication.class.getMethod("m05", testSignature)), metricName));
            Assert.assertEquals(vals[5], readValFromCurrThread(asResolvedJavaMethod(TestApplication.class.getMethod("m06", testSignature)), metricName));
            Assert.assertEquals(vals[6], readValFromCurrThread(asResolvedJavaMethod(TestApplication.class.getMethod("m07", testSignature)), metricName));
            Assert.assertEquals(vals[7], readValFromCurrThread(asResolvedJavaMethod(TestApplication.class.getMethod("m08", testSignature)), metricName));
            Assert.assertEquals(vals[8], readValFromCurrThread(asResolvedJavaMethod(TestApplication.class.getMethod("m09", testSignature)), metricName));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    void executeMethod(Method m, Object receiver, Object... args) {
        test(asResolvedJavaMethod(m), receiver, args);
    }

}

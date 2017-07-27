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
package org.graalvm.compiler.debug.internal.method;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.CSVUtil;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugCounter;
import org.graalvm.compiler.debug.DebugMethodMetrics;
import org.graalvm.compiler.debug.GraalDebugConfig;
import org.graalvm.compiler.debug.internal.DebugScope;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class MethodMetricsImpl implements DebugMethodMetrics {

    /**
     * A list capturing all method metrics data of all the compiler threads. Every thread registers
     * a reference to its thread local map of compilation metrics in this list. During metrics
     * dumping this list is globally locked and all method entries across all threads are merged to
     * a result.
     */
    private static final List<Map<ResolvedJavaMethod, CompilationData>> threadMaps = new ArrayList<>();
    /**
     * Every compiler thread carries its own map of metric data for each method and compilation it
     * compiles. This data is stored in {@link ThreadLocal} maps for each compiler thread that are
     * merged before metrics are reported. Storing compilation data thread locally reduces the
     * locking on access of a method metric object to one point for each thread, the first access
     * where the thread local is initialized.
     */
    private static final ThreadLocal<Map<ResolvedJavaMethod, CompilationData>> threadEntries = new ThreadLocal<>();
    /**
     * The lowest debug scope id that should be used during metric dumping. When a bootstrap is run
     * all compilations during bootstrap are also collected if the associated debug filters match.
     * Data collected during bootstrap should normally not be included in metrics for application
     * compilation, thus every compilation lower than this index is ignored during metric dumping.
     */
    private static long lowestCompilationDebugScopeId;

    public static class CompilationData {
        /**
         * A mapping of graph ids (unique ids used for the caching) to compilations.
         */
        private final Map<Long, Map<String, Long>> compilations;
        /**
         * A pointer to a {@code MethodMetricsImpl} object. This reference is created once for every
         * compilation of a method (and once for each thread, i.e. if method a is compiled by 8
         * compiler threads there will be 8 metrics objects for the given method, one local to every
         * thread, this avoids synchronizing on the metrics object on every access) accessing method
         * metrics for a given method.
         */
        private final MethodMetricsImpl metrics;

        CompilationData(ResolvedJavaMethod method) {
            compilations = new HashMap<>(8);
            metrics = new MethodMetricsImpl(method);
        }

        public Map<Long, Map<String, Long>> getCompilations() {
            return compilations;
        }
    }

    private static void addThreadCompilationData(Map<ResolvedJavaMethod, CompilationData> threadMap) {
        synchronized (threadMaps) {
            threadMaps.add(threadMap);
        }
    }

    /**
     * A reference to the {@link ResolvedJavaMethod} method object. This object's identity is used
     * to store metrics for each compilation.
     */
    private final ResolvedJavaMethod method;
    /**
     * A list of all recorded compilations. This is generated during metric dumping when all thread
     * local metrics are merged into one final method metrics object that is than reported
     */
    private List<Map<Long, Map<String, Long>>> collected;
    /**
     * A pointer to the current compilation data for the {@link MethodMetricsImpl#method} method
     * which allows to avoid synchronizing over the compilation data. This reference changes for
     * each compilation of the given method. It is set on the first access of this
     * {@link MethodMetricsImpl} object during the call to
     * {@link MethodMetricsImpl#getMethodMetrics(ResolvedJavaMethod)}.
     */
    private Map<String, Long> currentCompilation;

    MethodMetricsImpl(ResolvedJavaMethod method) {
        this.method = method;
    }

    private static void clearData() {
        lowestCompilationDebugScopeId = DebugScope.getCurrentGlobalScopeId();
    }

    @Override
    public void addToMetric(long value, String metricName) {
        if (!Debug.isMethodMeterEnabled() || value == 0) {
            return;
        }
        assert metricName != null;
        Long valueStored = currentCompilation.get(metricName);
        currentCompilation.put(metricName, valueStored == null ? value : value + valueStored);
    }

    @Override
    public long getCurrentMetricValue(String metricName) {
        assert metricName != null;
        Long valueStored = currentCompilation.get(metricName);
        return valueStored == null ? 0 : valueStored;
    }

    @Override
    public void addToMetric(long value, String format, Object arg1) {
        addToMetric(value, String.format(format, arg1));
    }

    @Override
    public void addToMetric(long value, String format, Object arg1, Object arg2) {
        addToMetric(value, String.format(format, arg1, arg2));
    }

    @Override
    public void addToMetric(long value, String format, Object arg1, Object arg2, Object arg3) {
        addToMetric(value, String.format(format, arg1, arg2, arg3));
    }

    @Override
    public void incrementMetric(String metricName) {
        addToMetric(1, metricName);
    }

    @Override
    public void incrementMetric(String format, Object arg1) {
        incrementMetric(String.format(format, arg1));
    }

    @Override
    public void incrementMetric(String format, Object arg1, Object arg2) {
        incrementMetric(String.format(format, arg1, arg2));
    }

    @Override
    public void incrementMetric(String format, Object arg1, Object arg2, Object arg3) {
        incrementMetric(String.format(format, arg1, arg2, arg3));
    }

    @Override
    public long getCurrentMetricValue(String format, Object arg1) {
        return getCurrentMetricValue(String.format(format, arg1));
    }

    @Override
    public long getCurrentMetricValue(String format, Object arg1, Object arg2) {
        return getCurrentMetricValue(String.format(format, arg1, arg2));
    }

    @Override
    public long getCurrentMetricValue(String format, Object arg1, Object arg2, Object arg3) {
        return getCurrentMetricValue(String.format(format, arg1, arg2, arg3));
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public static DebugMethodMetrics getMethodMetrics(ResolvedJavaMethod method) {
        assert method != null;
        Map<ResolvedJavaMethod, CompilationData> threadCache = threadEntries.get();
        if (threadCache == null) {
            // this branch will only be executed once for each compiler thread on the first request
            // of a method metric
            threadCache = new HashMap<>(GraalDebugConfig.Options.MethodFilter.getValue() == null ? 128 : 16);
            threadEntries.set(threadCache);
            addThreadCompilationData(threadCache);
        }

        CompilationData recorded = threadCache.get(method);
        if (recorded == null) {
            recorded = new CompilationData(method);
            threadCache.put(method, recorded);
        }
        // pre-generate the current compilation map to avoid doing it later every time we add to a
        // metric or read a current metric's value
        long compilationId = DebugScope.getInstance().scopeId();
        Map<String, Long> currentCompilation = recorded.compilations.get(compilationId);
        if (currentCompilation == null) {
            // this map is generated for every distinct compilation of a unique method
            currentCompilation = new HashMap<>(32);
            recorded.compilations.put(compilationId, currentCompilation);
            // we remember a reference to the current compilation to avoid the expensive lookup
            recorded.metrics.currentCompilation = currentCompilation;
        }

        return recorded.metrics;
    }

    public void dumpASCII(PrintStream p) {
        // we need to lock the threadmap as a concurrent call to #collectedMetrics can change the
        // content of this#collected
        synchronized (threadMaps) {
            String methodName = method.toString();
            int maxLen = methodName.length();
            int entrySum = 0;
            // get the longest entry
            for (Map<Long, Map<String, Long>> compilationThreadTable : collected) {
                for (Map.Entry<Long, Map<String, Long>> compilationEntry : compilationThreadTable.entrySet()) {
                    Map<String, Long> table = compilationEntry.getValue();
                    if (table != null) {
                        for (Map.Entry<String, Long> entry : table.entrySet()) {
                            maxLen = Math.max(maxLen, entry.getKey().length());
                            entrySum += entry.getValue();
                        }
                    }
                }
            }
            if (entrySum == 0) {
                // nothing to report
                return;
            }
            maxLen += 23;
            for (int j = 0; j < maxLen; j++) {
                p.print("#");
            }
            p.println();
            p.println(methodName);
            for (int j = 0; j < maxLen; j++) {
                p.print("~");
            }
            p.println();
            for (Map<Long, Map<String, Long>> compilationThreadTable : collected) {
                for (Map.Entry<Long, Map<String, Long>> compilationEntry : compilationThreadTable.entrySet()) {
                    Map<String, Long> table = compilationEntry.getValue();
                    if (table != null) {
                        if (table.values().stream().filter(x -> x > 0).count() == 0) {
                            continue;
                        }
                        Set<Map.Entry<String, Long>> entries = table.entrySet();
                        for (Map.Entry<String, Long> entry : entries.stream().sorted((x, y) -> x.getKey().compareTo(y.getKey())).collect(Collectors.toList())) {
                            long value = entry.getValue();
                            // report timers in ms and memory in mb
                            if ((entry.getKey().endsWith("Accm") || entry.getKey().endsWith("Flat")) &&
                                            !entry.getKey().toLowerCase().contains("mem")) {
                                value = value / 1000000;
                            }
                            if (value == 0) {
                                continue;
                            }
                            p.print(String.format("%-" + String.valueOf(maxLen - 23) + "s = %20d", entry.getKey(), value));
                            p.println();
                        }
                        for (int j = 0; j < maxLen; j++) {
                            p.print("~");
                        }
                        p.println();
                    }
                }
            }
            for (int j = 0; j < maxLen; j++) {
                p.print("#");
            }
            p.println();
        }
    }

    private static final String FMT = CSVUtil.buildFormatString("%s", "%s", "%d", "%d", "%s", "%d");

    public void dumpCSV(PrintStream p) {
        // we need to lock the threadmap as a concurrent call to #collectedMetrics can change
        // the content of this#collected
        synchronized (threadMaps) {
            String methodName = method.format("%H.%n(%p)%R");
            /*
             * NOTE: the caching mechanism works by caching compilation data based on the identity
             * of the resolved java method object. The identity is based on the metaspace address of
             * the resolved java method object. If the class was loaded by different class loaders
             * or e.g. loaded - unloaded - loaded the identity will be different. Therefore we also
             * need to include the identity in the reporting of the data as it is an additional
             * dimension to <method,compilationId>.
             */
            String methodIdentity = String.valueOf(System.identityHashCode(method));
            int nrOfCompilations = 0;
            for (Map<Long, Map<String, Long>> compilationThreadTable : collected) {
                for (Map.Entry<Long, Map<String, Long>> compilationEntry : compilationThreadTable.entrySet()) {
                    Map<String, Long> table = compilationEntry.getValue();
                    if (table != null) {
                        Set<Map.Entry<String, Long>> entries = table.entrySet();
                        for (Map.Entry<String, Long> entry : entries.stream().sorted((x, y) -> x.getKey().compareTo(y.getKey())).collect(Collectors.toList())) {
                            CSVUtil.Escape.println(p, FMT, methodName, methodIdentity, nrOfCompilations, compilationEntry.getKey(), entry.getKey(), entry.getValue());
                        }
                        nrOfCompilations++;
                    }
                }
            }
        }
    }

    public static Collection<DebugMethodMetrics> collectedMetrics() {
        synchronized (threadMaps) {
            // imprecise excluding all compilations that follow, we simply do not report them
            final long lastId = DebugScope.getCurrentGlobalScopeId();
            List<DebugMethodMetrics> finalMetrics = new ArrayList<>();
            Set<ResolvedJavaMethod> methods = new HashSet<>();

            // gather all methods we found
            threadMaps.stream().forEach(x -> {
                // snapshot the current compilations to only capture all methods compiled until now
                HashMap<ResolvedJavaMethod, CompilationData> snapShot = new HashMap<>(x);
                snapShot.keySet().forEach(y -> methods.add(y));
            });

            // for each method gather all metrics we want to report
            for (ResolvedJavaMethod method : methods) {
                MethodMetricsImpl impl = new MethodMetricsImpl(method);
                impl.collected = new ArrayList<>();
                for (Map<ResolvedJavaMethod, CompilationData> threadMap : threadMaps) {
                    CompilationData threadMethodData = threadMap.get(method);

                    // not every method is necessarily compiled by all threads
                    if (threadMethodData != null) {
                        Map<Long, Map<String, Long>> snapshot = new HashMap<>(threadMethodData.compilations);
                        for (Map.Entry<Long, Map<String, Long>> entry : snapshot.entrySet()) {
                            if (entry.getKey() < lowestCompilationDebugScopeId || entry.getKey() > lastId) {
                                entry.setValue(null);
                            }
                        }
                        impl.collected.add(snapshot);
                    }
                }
                finalMetrics.add(impl);
            }

            return finalMetrics;
        }
    }

    public static void clearMM() {
        clearData();
    }

    private static final String INLINEE_PREFIX = "INLINING_SCOPE_";
    private static final boolean TRACK_INLINED_SCOPES = false;

    public static void recordInlinee(ResolvedJavaMethod root, ResolvedJavaMethod caller, ResolvedJavaMethod inlinee) {
        if (TRACK_INLINED_SCOPES) {
            Debug.methodMetrics(root).addToMetric(1, "INLINED_METHOD_root: caller:%s inlinee:%s", caller, inlinee);
        }
    }

    private static final boolean COUNT_CACHE = false;
    private static final String HIT_MSG = "InterceptionCache_Hit";
    private static final String MISS_MSG = "InterceptionCache_Miss";
    private static final DebugCounter cacheHit = Debug.counter(HIT_MSG);
    private static final DebugCounter cacheMiss = Debug.counter(MISS_MSG);
    /**
     * To avoid the lookup of a method metrics through the
     * {@link MethodMetricsImpl#getMethodMetrics(ResolvedJavaMethod)} method on every global metric
     * interception we thread-locally cache the last (through metric interception)
     * {@link MethodMetricsImpl} object. This avoids additional map lookups and replaces them with a
     * {@link DebugScope#scopeId()} call and a numerical comparison in a cache hit case.
     */
    private static final ThreadLocal<Long> interceptionCache = new ThreadLocal<>();
    private static final ThreadLocal<MethodMetricsImpl> interceptionMetrics = new ThreadLocal<>();

    public static void addToCurrentScopeMethodMetrics(String metricName, long value) {
        if (COUNT_CACHE) {
            if (metricName.equals(HIT_MSG) || metricName.equals(MISS_MSG)) {
                return;
            }
        }
        final DebugScope currScope = DebugScope.getInstance();
        final DebugScope.ExtraInfo metaInfo = currScope.getExtraInfo();
        final long currScopeId = currScope.scopeId();
        if (metaInfo instanceof MethodMetricsRootScopeInfo) {
            ResolvedJavaMethod rootMethod = ((MethodMetricsRootScopeInfo) metaInfo).getRootMethod();
            if (metaInfo instanceof MethodMetricsInlineeScopeInfo) {
                /*
                 * if we make use of a method filter(s) together with interception we get a problem
                 * with inlined methods and their scopes. Inlining will put the inlinee(s) on the
                 * debug scope context thus Debug.areMethodMetricsEnabled() will yield true if an
                 * inlinee matches a method filter. Thus we must make sure the root is defined as
                 * this means the root matched a method filter and therefore the inlinee can be
                 * safely recorded.
                 */
                if (TRACK_INLINED_SCOPES) {
                    if (threadEntries.get().get(rootMethod) != null) {
                        Debug.methodMetrics(rootMethod).addToMetric(value, "%s%s", INLINEE_PREFIX, metricName);
                    }
                }
            } else {
                // when unboxing the thread local on access it must not be null
                Long cachedId = interceptionCache.get();
                if (cachedId != null && cachedId == currScopeId) {
                    interceptionMetrics.get().addToMetric(value, metricName);
                    if (COUNT_CACHE) {
                        cacheHit.increment();
                    }
                } else {
                    // avoid the lookup over Debug.methodMetrics
                    final MethodMetricsImpl impl = (MethodMetricsImpl) getMethodMetrics(rootMethod);
                    impl.addToMetric(value, metricName);
                    // cache for next access
                    interceptionCache.set(currScopeId);
                    interceptionMetrics.set(impl);
                    if (COUNT_CACHE) {
                        cacheMiss.increment();
                    }
                }
            }
        }
    }

}

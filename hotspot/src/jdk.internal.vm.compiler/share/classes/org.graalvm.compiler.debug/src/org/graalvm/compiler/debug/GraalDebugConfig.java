/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.debug;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValue;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.JavaMethod;

public class GraalDebugConfig implements DebugConfig {
    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        return assertionsEnabled;
    }

    public static class Options {
        // @formatter:off
        @Option(help = "Pattern for scope(s) in which dumping is enabled (see DebugFilter and Debug.dump)", type = OptionType.Debug)
        public static final OptionValue<String> Dump = new OptionValue<>(null);
        @Option(help = "Pattern for scope(s) in which counting is enabled (see DebugFilter and Debug.counter). " +
                       "An empty value enables all counters unconditionally.", type = OptionType.Debug)
        public static final OptionValue<String> Count = new OptionValue<>(null);
        @Option(help = "Pattern for scope(s) in which verification is enabled (see DebugFilter and Debug.verify).", type = OptionType.Debug)
        public static final OptionValue<String> Verify = new OptionValue<String>() {
            @Override
            protected String defaultValue() {
                return assertionsEnabled() ? "" : null;
            }
        };
        @Option(help = "Pattern for scope(s) in which memory use tracking is enabled (see DebugFilter and Debug.counter). " +
                       "An empty value enables all memory use trackers unconditionally.", type = OptionType.Debug)
        public static final OptionValue<String> TrackMemUse = new OptionValue<>(null);
        @Option(help = "Pattern for scope(s) in which timing is enabled (see DebugFilter and Debug.timer). " +
                       "An empty value enables all timers unconditionally.", type = OptionType.Debug)
        public static final OptionValue<String> Time = new OptionValue<>(null);
        @Option(help = "Pattern for scope(s) in which logging is enabled (see DebugFilter and Debug.log)", type = OptionType.Debug)
        public static final OptionValue<String> Log = new OptionValue<>(null);
        @Option(help = "Pattern for filtering debug scope output based on method context (see MethodFilter)", type = OptionType.Debug)
        public static final OptionValue<String> MethodFilter = new OptionValue<>(null);
        @Option(help = "Only check MethodFilter against the root method in the context if true, otherwise check all methods", type = OptionType.Debug)
        public static final OptionValue<Boolean> MethodFilterRootOnly = new OptionValue<>(false);

        @Option(help = "How to print counters and timing values:%n" +
                       "Name - aggregate by unqualified name%n" +
                       "Partial - aggregate by partially qualified name (e.g., A.B.C.D.Counter and X.Y.Z.D.Counter will be merged to D.Counter)%n" +
                       "Complete - aggregate by qualified name%n" +
                       "Thread - aggregate by qualified name and thread", type = OptionType.Debug)
        public static final OptionValue<String> DebugValueSummary = new OptionValue<>("Name");
        @Option(help = "Print counters and timers in a human readable form.", type = OptionType.Debug)
        public static final OptionValue<Boolean> DebugValueHumanReadable = new OptionValue<>(true);
        @Option(help = "Omit reporting 0-value counters", type = OptionType.Debug)
        public static final OptionValue<Boolean> SuppressZeroDebugValues = new OptionValue<>(true);
        @Option(help = "Only report debug values for maps which match the regular expression.", type = OptionType.Debug)
        public static final OptionValue<String> DebugValueThreadFilter = new OptionValue<>(null);
        @Option(help = "Write debug values into a file instead of the terminal. " +
                       "If DebugValueSummary is Thread, the thread name will be prepended.", type = OptionType.Debug)
        public static final OptionValue<String> DebugValueFile = new OptionValue<>(null);
        @Option(help = "Send Graal compiler IR to dump handlers on error", type = OptionType.Debug)
        public static final OptionValue<Boolean> DumpOnError = new OptionValue<>(false);
        @Option(help = "Intercept also bailout exceptions", type = OptionType.Debug)
        public static final OptionValue<Boolean> InterceptBailout = new OptionValue<>(false);
        @Option(help = "Enable more verbose log output when available", type = OptionType.Debug)
        public static final OptionValue<Boolean> LogVerbose = new OptionValue<>(false);

        @Option(help = "The directory where various Graal dump files are written.")
        public static final OptionValue<String> DumpPath = new OptionValue<>(".");

        @Option(help = "Enable dumping to the C1Visualizer. Enabling this option implies PrintBackendCFG.", type = OptionType.Debug)
        public static final OptionValue<Boolean> PrintCFG = new OptionValue<>(false);
        @Option(help = "Enable dumping LIR, register allocation and code generation info to the C1Visualizer.", type = OptionType.Debug)
        public static final OptionValue<Boolean> PrintBackendCFG = new OptionValue<>(true);
        @Option(help = "Base filename when dumping C1Visualizer output to files.", type = OptionType.Debug)
        public static final OptionValue<String> PrintCFGFileName = new OptionValue<>("compilations");

        @Option(help = "Output probabilities for fixed nodes during binary graph dumping", type = OptionType.Debug)
        public static final OptionValue<Boolean> PrintGraphProbabilities = new OptionValue<>(false);
        @Option(help = "Enable dumping to the IdealGraphVisualizer.", type = OptionType.Debug)
        public static final OptionValue<Boolean> PrintIdealGraph = new OptionValue<>(true);
        @Option(help = "Dump IdealGraphVisualizer output in binary format", type = OptionType.Debug)
        public static final OptionValue<Boolean> PrintBinaryGraphs = new OptionValue<>(true);
        @Option(help = "Print Ideal graphs as opposed to sending them over the network.", type = OptionType.Debug)
        public static final OptionValue<Boolean> PrintIdealGraphFile = new OptionValue<>(false);
        @Option(help = "Base filename when dumping Ideal graphs to files.", type = OptionType.Debug)
        public static final OptionValue<String> PrintIdealGraphFileName = new OptionValue<>("runtime-graphs");

        @Option(help = "", type = OptionType.Debug)
        public static final OptionValue<String> PrintIdealGraphAddress = new OptionValue<>("127.0.0.1");
        @Option(help = "", type = OptionType.Debug)
        public static final OptionValue<Integer> PrintIdealGraphPort = new OptionValue<>(4444);
        @Option(help = "", type = OptionType.Debug)
        public static final OptionValue<Integer> PrintBinaryGraphPort = new OptionValue<>(4445);
        @Option(help = "", type = OptionType.Debug)
        public static final OptionValue<Boolean> PrintIdealGraphSchedule = new OptionValue<>(false);
        @Option(help = "Enable dumping Truffle ASTs to the IdealGraphVisualizer.", type = OptionType.Debug)
        public static final OptionValue<Boolean> PrintTruffleTrees = new OptionValue<>(true);

        @Option(help = "Enable dumping canonical text from for graphs.", type = OptionType.Debug)
        public static final OptionValue<Boolean> PrintCanonicalGraphStrings = new OptionValue<>(false);
        @Option(help = "Base directory when dumping graphs strings to files.", type = OptionType.Debug)
        public static final OptionValue<String> PrintCanonicalGraphStringsDirectory = new OptionValue<>("graph-strings");
        @Option(help = "Choose format used when dumping canonical text for graphs: " +
                "0 gives a scheduled graph (better for spotting changes involving the schedule)" +
                "while 1 gives a CFG containing expressions rooted at fixed nodes (better for spotting small structure differences)", type = OptionType.Debug)
        public static final OptionValue<Integer> PrintCanonicalGraphStringFlavor = new OptionValue<>(0);
        @Option(help = "Exclude virtual nodes when dumping canonical text for graphs.", type = OptionType.Debug)
        public static final OptionValue<Boolean> CanonicalGraphStringsExcludeVirtuals = new OptionValue<>(true);
        @Option(help = "Exclude virtual nodes when dumping canonical text for graphs.", type = OptionType.Debug)
        public static final OptionValue<Boolean> CanonicalGraphStringsCheckConstants = new OptionValue<>(false);
        @Option(help = "Attempts to remove object identity hashes when dumping canonical text for graphs.", type = OptionType.Debug)
        public static final OptionValue<Boolean> CanonicalGraphStringsRemoveIdentities = new OptionValue<>(true);

        @Option(help = "Enable per method metrics that are collected across all compilations of a method." +
                       "Pattern for scope(s) in which method metering is enabled (see DebugFilter and Debug.metric).", type = OptionType.Debug)
        public static final OptionValue<String> MethodMeter = new OptionValue<>(null);
        @Option(help = "If a global metric (DebugTimer, DebugCounter or DebugMemUseTracker) is enabled in the same scope as a method metric, " +
                       "use the global metric to update the method metric for the current compilation. " +
                       "This option enables the re-use of global metrics on per-compilation basis. " +
                       "Whenever a value is added to a global metric, the value is also added to a MethodMetric under the same name " +
                       "as the global metric. " +
                       "This option incurs a small but constant overhead due to the context method lookup at each metric update. " +
                       "Format to specify GlobalMetric interception:(Timers|Counters|MemUseTrackers)(,Timers|,Counters|,MemUseTrackers)*", type = OptionType.Debug)
        public static final OptionValue<String> GlobalMetricsInterceptedByMethodMetrics = new OptionValue<>(null);
        @Option(help = "Force-enable debug code paths", type = OptionType.Debug)
        public static final OptionValue<Boolean> ForceDebugEnable = new OptionValue<>(false);
        @Option(help = "Clear the debug metrics after bootstrap.", type = OptionType.Debug)
        public static final OptionValue<Boolean> ClearMetricsAfterBootstrap = new OptionValue<>(false);
        @Option(help = "Do not compile anything on bootstrap but just initialize the compiler.", type = OptionType.Debug)
        public static final OptionValue<Boolean> BootstrapInitializeOnly = new OptionValue<>(false);
        // @formatter:on
    }

    public static boolean isNotEmpty(OptionValue<String> option) {
        return option.getValue() != null && !option.getValue().isEmpty();
    }

    public static boolean areDebugScopePatternsEnabled() {
        return Options.DumpOnError.getValue() || Options.Dump.getValue() != null || Options.Log.getValue() != null || areScopedGlobalMetricsEnabled();
    }

    public static boolean isGlobalMetricsInterceptedByMethodMetricsEnabled() {
        return isNotEmpty(Options.GlobalMetricsInterceptedByMethodMetrics);
    }

    /**
     * Determines if any of {@link Options#Count}, {@link Options#Time} or
     * {@link Options#TrackMemUse} has a non-null, non-empty value.
     */
    public static boolean areScopedGlobalMetricsEnabled() {
        return isNotEmpty(Options.Count) || isNotEmpty(Options.Time) || isNotEmpty(Options.TrackMemUse) || isNotEmpty(Options.MethodMeter);
    }

    private final DebugFilter countFilter;
    private final DebugFilter logFilter;
    private final DebugFilter methodMetricsFilter;
    private final DebugFilter trackMemUseFilter;
    private final DebugFilter timerFilter;
    private final DebugFilter dumpFilter;
    private final DebugFilter verifyFilter;
    private final MethodFilter[] methodFilter;
    private final List<DebugDumpHandler> dumpHandlers;
    private final List<DebugVerifyHandler> verifyHandlers;
    private final PrintStream output;

    // Use an identity set to handle context objects that don't support hashCode().
    private final Set<Object> extraFilters = Collections.newSetFromMap(new IdentityHashMap<>());

    public GraalDebugConfig(String logFilter, String countFilter, String trackMemUseFilter, String timerFilter, String dumpFilter, String verifyFilter, String methodFilter,
                    String methodMetricsFilter, PrintStream output, List<DebugDumpHandler> dumpHandlers, List<DebugVerifyHandler> verifyHandlers) {
        this.logFilter = DebugFilter.parse(logFilter);
        this.countFilter = DebugFilter.parse(countFilter);
        this.trackMemUseFilter = DebugFilter.parse(trackMemUseFilter);
        this.timerFilter = DebugFilter.parse(timerFilter);
        this.dumpFilter = DebugFilter.parse(dumpFilter);
        this.verifyFilter = DebugFilter.parse(verifyFilter);
        this.methodMetricsFilter = DebugFilter.parse(methodMetricsFilter);
        if (methodFilter == null || methodFilter.isEmpty()) {
            this.methodFilter = null;
        } else {
            this.methodFilter = org.graalvm.compiler.debug.MethodFilter.parse(methodFilter);
        }

        // Report the filters that have been configured so the user can verify it's what they expect
        if (logFilter != null || countFilter != null || timerFilter != null || dumpFilter != null || methodFilter != null) {
            // TTY.println(Thread.currentThread().getName() + ": " + toString());
        }
        this.dumpHandlers = dumpHandlers;
        this.verifyHandlers = verifyHandlers;
        this.output = output;
    }

    @Override
    public int getLogLevel() {
        return getLevel(logFilter);
    }

    @Override
    public boolean isLogEnabledForMethod() {
        return isEnabledForMethod(logFilter);
    }

    @Override
    public boolean isCountEnabled() {
        return isEnabled(countFilter);
    }

    @Override
    public boolean isMemUseTrackingEnabled() {
        return isEnabled(trackMemUseFilter);
    }

    @Override
    public int getDumpLevel() {
        return getLevel(dumpFilter);
    }

    @Override
    public boolean isDumpEnabledForMethod() {
        return isEnabledForMethod(dumpFilter);
    }

    @Override
    public boolean isVerifyEnabled() {
        return isEnabled(verifyFilter);
    }

    @Override
    public boolean isVerifyEnabledForMethod() {
        return isEnabledForMethod(verifyFilter);
    }

    @Override
    public boolean isMethodMeterEnabled() {
        return isEnabled(methodMetricsFilter);
    }

    @Override
    public boolean isTimeEnabled() {
        return isEnabled(timerFilter);
    }

    @Override
    public PrintStream output() {
        return output;
    }

    private boolean isEnabled(DebugFilter filter) {
        return getLevel(filter) > 0;
    }

    private int getLevel(DebugFilter filter) {
        int level;
        if (filter == null) {
            level = 0;
        } else {
            level = filter.matchLevel(Debug.currentScope());
        }
        if (level > 0 && !checkMethodFilter()) {
            level = 0;
        }
        return level;
    }

    private boolean isEnabledForMethod(DebugFilter filter) {
        return filter != null && checkMethodFilter();
    }

    /**
     * Extracts a {@link JavaMethod} from an opaque debug context.
     *
     * @return the {@link JavaMethod} represented by {@code context} or null
     */
    public static JavaMethod asJavaMethod(Object context) {
        if (context instanceof JavaMethodContext) {
            return ((JavaMethodContext) context).asJavaMethod();
        }
        if (context instanceof JavaMethod) {
            return (JavaMethod) context;
        }
        return null;
    }

    private boolean checkMethodFilter() {
        if (methodFilter == null && extraFilters.isEmpty()) {
            return true;
        } else {
            JavaMethod lastMethod = null;
            for (Object o : Debug.context()) {
                if (extraFilters.contains(o)) {
                    return true;
                } else if (methodFilter != null) {
                    JavaMethod method = asJavaMethod(o);
                    if (method != null) {
                        if (!Options.MethodFilterRootOnly.getValue()) {
                            if (org.graalvm.compiler.debug.MethodFilter.matches(methodFilter, method)) {
                                return true;
                            }
                        } else {
                            /*
                             * The context values operate as a stack so if we want MethodFilter to
                             * only apply to the root method we have to check only the last method
                             * seen.
                             */
                            lastMethod = method;
                        }
                    }
                }
            }
            if (lastMethod != null && org.graalvm.compiler.debug.MethodFilter.matches(methodFilter, lastMethod)) {
                return true;
            }
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Debug config:");
        add(sb, "Log", logFilter);
        add(sb, "Count", countFilter);
        add(sb, "MethodMeter", methodMetricsFilter);
        add(sb, "Time", timerFilter);
        add(sb, "Dump", dumpFilter);
        add(sb, "MethodFilter", methodFilter);
        return sb.toString();
    }

    private static void add(StringBuilder sb, String name, Object filter) {
        if (filter != null) {
            sb.append(' ');
            sb.append(name);
            sb.append('=');
            if (filter instanceof Object[]) {
                sb.append(Arrays.toString((Object[]) filter));
            } else {
                sb.append(String.valueOf(filter));
            }
        }
    }

    @Override
    public RuntimeException interceptException(Throwable e) {
        if (e instanceof BailoutException && !Options.InterceptBailout.getValue()) {
            return null;
        }
        Debug.setConfig(Debug.fixedConfig(Debug.BASIC_LOG_LEVEL, Debug.BASIC_LOG_LEVEL, false, false, false, false, false, dumpHandlers, verifyHandlers, output));
        Debug.log("Exception occurred in scope: %s", Debug.currentScope());
        Map<Object, Object> firstSeen = new IdentityHashMap<>();
        for (Object o : Debug.context()) {
            // Only dump a context object once.
            if (!firstSeen.containsKey(o)) {
                firstSeen.put(o, o);
                if (Options.DumpOnError.getValue()) {
                    Debug.dump(Debug.BASIC_LOG_LEVEL, o, "Exception: %s", e);
                } else {
                    Debug.log("Context obj %s", o);
                }
            }
        }
        return null;
    }

    @Override
    public Collection<DebugDumpHandler> dumpHandlers() {
        return dumpHandlers;
    }

    @Override
    public Collection<DebugVerifyHandler> verifyHandlers() {
        return verifyHandlers;
    }

    @Override
    public void addToContext(Object o) {
        extraFilters.add(o);
    }

    @Override
    public void removeFromContext(Object o) {
        extraFilters.remove(o);
    }

}

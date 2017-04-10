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
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.util.EconomicMap;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.JavaMethod;

public class GraalDebugConfig implements DebugConfig {

    public static class Options {
        // @formatter:off
        @Option(help = "Pattern for scope(s) in which dumping is enabled (see DebugFilter and Debug.dump)", type = OptionType.Debug)
        public static final OptionKey<String> Dump = new OptionKey<>(null);
        @Option(help = "Pattern for scope(s) in which counting is enabled (see DebugFilter and Debug.counter). " +
                       "An empty value enables all counters unconditionally.", type = OptionType.Debug)
        public static final OptionKey<String> Count = new OptionKey<>(null);
        @Option(help = "Pattern for scope(s) in which verification is enabled (see DebugFilter and Debug.verify).", type = OptionType.Debug)
        public static final OptionKey<String> Verify = new OptionKey<>(Assertions.ENABLED ? "" : null);
        @Option(help = "Pattern for scope(s) in which memory use tracking is enabled (see DebugFilter and Debug.counter). " +
                       "An empty value enables all memory use trackers unconditionally.", type = OptionType.Debug)
        public static final OptionKey<String> TrackMemUse = new OptionKey<>(null);
        @Option(help = "Pattern for scope(s) in which timing is enabled (see DebugFilter and Debug.timer). " +
                       "An empty value enables all timers unconditionally.", type = OptionType.Debug)
        public static final OptionKey<String> Time = new OptionKey<>(null);
        @Option(help = "Pattern for scope(s) in which logging is enabled (see DebugFilter and Debug.log)", type = OptionType.Debug)
        public static final OptionKey<String> Log = new OptionKey<>(null);
        @Option(help = "Pattern for filtering debug scope output based on method context (see MethodFilter)", type = OptionType.Debug)
        public static final OptionKey<String> MethodFilter = new OptionKey<>(null);
        @Option(help = "Only check MethodFilter against the root method in the context if true, otherwise check all methods", type = OptionType.Debug)
        public static final OptionKey<Boolean> MethodFilterRootOnly = new OptionKey<>(false);

        @Option(help = "How to print counters and timing values:%n" +
                       "Name - aggregate by unqualified name%n" +
                       "Partial - aggregate by partially qualified name (e.g., A.B.C.D.Counter and X.Y.Z.D.Counter will be merged to D.Counter)%n" +
                       "Complete - aggregate by qualified name%n" +
                       "Thread - aggregate by qualified name and thread", type = OptionType.Debug)
        public static final OptionKey<String> DebugValueSummary = new OptionKey<>("Name");
        @Option(help = "Print counters and timers in a human readable form.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DebugValueHumanReadable = new OptionKey<>(true);
        @Option(help = "Omit reporting 0-value counters", type = OptionType.Debug)
        public static final OptionKey<Boolean> SuppressZeroDebugValues = new OptionKey<>(true);
        @Option(help = "Only report debug values for maps which match the regular expression.", type = OptionType.Debug)
        public static final OptionKey<String> DebugValueThreadFilter = new OptionKey<>(null);
        @Option(help = "Write debug values into a file instead of the terminal. " +
                       "If DebugValueSummary is Thread, the thread name will be prepended.", type = OptionType.Debug)
        public static final OptionKey<String> DebugValueFile = new OptionKey<>(null);
        @Option(help = "Enable debug output for stub code generation and snippet preparation.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DebugStubsAndSnippets = new OptionKey<>(false);
        @Option(help = "Send Graal compiler IR to dump handlers on error.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DumpOnError = new OptionKey<>(false);
        @Option(help = "Intercept also bailout exceptions", type = OptionType.Debug)
        public static final OptionKey<Boolean> InterceptBailout = new OptionKey<>(false);
        @Option(help = "Enable more verbose log output when available", type = OptionType.Debug)
        public static final OptionKey<Boolean> LogVerbose = new OptionKey<>(false);

        @Option(help = "The directory where various Graal dump files are written.")
        public static final OptionKey<String> DumpPath = new OptionKey<>(".");

        @Option(help = "Enable dumping to the C1Visualizer. Enabling this option implies PrintBackendCFG.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintCFG = new OptionKey<>(false);
        @Option(help = "Enable dumping LIR, register allocation and code generation info to the C1Visualizer.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintBackendCFG = new OptionKey<>(true);
        @Option(help = "Base filename when dumping C1Visualizer output to files.", type = OptionType.Debug)
        public static final OptionKey<String> PrintCFGFileName = new OptionKey<>("compilations");

        @Option(help = "Output probabilities for fixed nodes during binary graph dumping.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintGraphProbabilities = new OptionKey<>(false);
        @Option(help = "Enable dumping to the IdealGraphVisualizer.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintGraph = new OptionKey<>(true);
        @Option(help = "Dump graphs in binary format instead of XML format.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintBinaryGraphs = new OptionKey<>(true);
        @Option(help = "Print graphs to files instead of sending them over the network.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintGraphFile = new OptionKey<>(false);
        @Option(help = "Base filename when dumping graphs to files.", type = OptionType.Debug)
        public static final OptionKey<String> PrintGraphFileName = new OptionKey<>("runtime-graphs");

        @Option(help = "Host part of the address to which graphs are dumped.", type = OptionType.Debug)
        public static final OptionKey<String> PrintGraphHost = new OptionKey<>("127.0.0.1");
        @Option(help = "Port part of the address to which graphs are dumped in XML format (ignored if PrintBinaryGraphs=true).", type = OptionType.Debug)
        public static final OptionKey<Integer> PrintXmlGraphPort = new OptionKey<>(4444);
        @Option(help = "Port part of the address to which graphs are dumped in binary format (ignored if PrintBinaryGraphs=false).", type = OptionType.Debug)
        public static final OptionKey<Integer> PrintBinaryGraphPort = new OptionKey<>(4445);
        @Option(help = "Schedule graphs as they are dumped.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintGraphWithSchedule = new OptionKey<>(false);
        @Option(help = "Enable dumping Truffle ASTs to the IdealGraphVisualizer.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintTruffleTrees = new OptionKey<>(true);

        @Option(help = "Treat any exceptions during dumping as fatal.", type = OptionType.Debug)
        public static final OptionKey<Boolean> DumpingErrorsAreFatal = new OptionKey<>(false);

        @Option(help = "Enable dumping canonical text from for graphs.", type = OptionType.Debug)
        public static final OptionKey<Boolean> PrintCanonicalGraphStrings = new OptionKey<>(false);
        @Option(help = "Base directory when dumping graphs strings to files.", type = OptionType.Debug)
        public static final OptionKey<String> PrintCanonicalGraphStringsDirectory = new OptionKey<>("graph-strings");
        @Option(help = "Choose format used when dumping canonical text for graphs: " +
                "0 gives a scheduled graph (better for spotting changes involving the schedule)" +
                "while 1 gives a CFG containing expressions rooted at fixed nodes (better for spotting small structure differences)", type = OptionType.Debug)
        public static final OptionKey<Integer> PrintCanonicalGraphStringFlavor = new OptionKey<>(0);
        @Option(help = "Exclude virtual nodes when dumping canonical text for graphs.", type = OptionType.Debug)
        public static final OptionKey<Boolean> CanonicalGraphStringsExcludeVirtuals = new OptionKey<>(true);
        @Option(help = "Exclude virtual nodes when dumping canonical text for graphs.", type = OptionType.Debug)
        public static final OptionKey<Boolean> CanonicalGraphStringsCheckConstants = new OptionKey<>(false);
        @Option(help = "Attempts to remove object identity hashes when dumping canonical text for graphs.", type = OptionType.Debug)
        public static final OptionKey<Boolean> CanonicalGraphStringsRemoveIdentities = new OptionKey<>(true);

        @Option(help = "Enable per method metrics that are collected across all compilations of a method." +
                       "Pattern for scope(s) in which method metering is enabled (see DebugFilter and Debug.metric).", type = OptionType.Debug)
        public static final OptionKey<String> MethodMeter = new OptionKey<>(null);
        @Option(help = "If a global metric (DebugTimer, DebugCounter or DebugMemUseTracker) is enabled in the same scope as a method metric, " +
                       "use the global metric to update the method metric for the current compilation. " +
                       "This option enables the re-use of global metrics on per-compilation basis. " +
                       "Whenever a value is added to a global metric, the value is also added to a MethodMetric under the same name " +
                       "as the global metric. " +
                       "This option incurs a small but constant overhead due to the context method lookup at each metric update. " +
                       "Format to specify GlobalMetric interception:(Timers|Counters|MemUseTrackers)(,Timers|,Counters|,MemUseTrackers)*", type = OptionType.Debug)
        public static final OptionKey<String> GlobalMetricsInterceptedByMethodMetrics = new OptionKey<>(null);
        @Option(help = "Force-enable debug code paths", type = OptionType.Debug)
        public static final OptionKey<Boolean> ForceDebugEnable = new OptionKey<>(false);
        @Option(help = "Clear the debug metrics after bootstrap.", type = OptionType.Debug)
        public static final OptionKey<Boolean> ClearMetricsAfterBootstrap = new OptionKey<>(false);
        @Option(help = "Do not compile anything on bootstrap but just initialize the compiler.", type = OptionType.Debug)
        public static final OptionKey<Boolean> BootstrapInitializeOnly = new OptionKey<>(false);

        // These
        @Option(help = "Deprecated - use PrintGraphHost instead.", type = OptionType.Debug)
        static final OptionKey<String> PrintIdealGraphAddress = new DeprecatedOptionKey<>(PrintGraphHost);
        @Option(help = "Deprecated - use PrintGraphWithSchedule instead.", type = OptionType.Debug)
        static final OptionKey<Boolean> PrintIdealGraphSchedule = new DeprecatedOptionKey<>(PrintGraphWithSchedule);
        @Option(help = "Deprecated - use PrintGraph instead.", type = OptionType.Debug)
        static final OptionKey<Boolean> PrintIdealGraph = new DeprecatedOptionKey<>(PrintGraph);
        @Option(help = "Deprecated - use PrintGraphFile instead.", type = OptionType.Debug)
        static final OptionKey<Boolean> PrintIdealGraphFile = new DeprecatedOptionKey<>(PrintGraphFile);
        @Option(help = "Deprecated - use PrintGraphFileName instead.", type = OptionType.Debug)
        static final OptionKey<String> PrintIdealGraphFileName = new DeprecatedOptionKey<>(PrintGraphFileName);
        @Option(help = "Deprecated - use PrintXmlGraphPort instead.", type = OptionType.Debug)
        static final OptionKey<Integer> PrintIdealGraphPort = new DeprecatedOptionKey<>(PrintXmlGraphPort);
        // @formatter:on
    }

    static class DeprecatedOptionKey<T> extends OptionKey<T> {
        private final OptionKey<T> replacement;

        DeprecatedOptionKey(OptionKey<T> replacement) {
            super(replacement.getDefaultValue());
            this.replacement = replacement;
        }

        @Override
        protected void onValueUpdate(EconomicMap<OptionKey<?>, Object> values, T oldValue, T newValue) {
            // Ideally we'd use TTY here but it may not yet be initialized.
            System.err.printf("Warning: the %s option is deprecated - use %s instead%n", getName(), replacement.getName());
            replacement.update(values, newValue);
        }
    }

    public static boolean isNotEmpty(OptionKey<String> option, OptionValues options) {
        return option.getValue(options) != null && !option.getValue(options).isEmpty();
    }

    public static boolean areDebugScopePatternsEnabled(OptionValues options) {
        return Options.DumpOnError.getValue(options) || Options.Dump.getValue(options) != null || Options.Log.getValue(options) != null || areScopedGlobalMetricsEnabled(options);
    }

    public static boolean isGlobalMetricsInterceptedByMethodMetricsEnabled(OptionValues options) {
        return isNotEmpty(Options.GlobalMetricsInterceptedByMethodMetrics, options);
    }

    /**
     * Determines if any of {@link Options#Count}, {@link Options#Time} or
     * {@link Options#TrackMemUse} has a non-null, non-empty value.
     *
     * @param options
     */
    public static boolean areScopedGlobalMetricsEnabled(OptionValues options) {
        return isNotEmpty(Options.Count, options) || isNotEmpty(Options.Time, options) || isNotEmpty(Options.TrackMemUse, options) || isNotEmpty(Options.MethodMeter, options);
    }

    private final OptionValues options;

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

    public GraalDebugConfig(OptionValues options, String logFilter, String countFilter, String trackMemUseFilter, String timerFilter, String dumpFilter, String verifyFilter, String methodFilter,
                    String methodMetricsFilter, PrintStream output, List<DebugDumpHandler> dumpHandlers, List<DebugVerifyHandler> verifyHandlers) {
        this.options = options;
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

        this.dumpHandlers = dumpHandlers;
        this.verifyHandlers = verifyHandlers;
        this.output = output;
    }

    @Override
    public OptionValues getOptions() {
        return options;
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
                        if (!Options.MethodFilterRootOnly.getValue(options)) {
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
        if (e instanceof BailoutException && !Options.InterceptBailout.getValue(options)) {
            return null;
        }
        Debug.setConfig(Debug.fixedConfig(options, Debug.BASIC_LEVEL, Debug.BASIC_LEVEL, false, false, false, false, false, dumpHandlers, verifyHandlers, output));
        Debug.log("Exception occurred in scope: %s", Debug.currentScope());
        Map<Object, Object> firstSeen = new IdentityHashMap<>();
        for (Object o : Debug.context()) {
            // Only dump a context object once.
            if (!firstSeen.containsKey(o)) {
                firstSeen.put(o, o);
                if (Options.DumpOnError.getValue(options) || Options.Dump.getValue(options) != null) {
                    Debug.dump(Debug.BASIC_LEVEL, o, "Exception: %s", e);
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

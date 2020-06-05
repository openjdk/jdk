/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static java.util.FormattableFlags.LEFT_JUSTIFY;
import static java.util.FormattableFlags.UPPERCASE;
import static org.graalvm.compiler.debug.DebugOptions.Count;
import static org.graalvm.compiler.debug.DebugOptions.Counters;
import static org.graalvm.compiler.debug.DebugOptions.DisableIntercept;
import static org.graalvm.compiler.debug.DebugOptions.Dump;
import static org.graalvm.compiler.debug.DebugOptions.DumpOnError;
import static org.graalvm.compiler.debug.DebugOptions.DumpOnPhaseChange;
import static org.graalvm.compiler.debug.DebugOptions.DumpPath;
import static org.graalvm.compiler.debug.DebugOptions.ListMetrics;
import static org.graalvm.compiler.debug.DebugOptions.Log;
import static org.graalvm.compiler.debug.DebugOptions.MemUseTrackers;
import static org.graalvm.compiler.debug.DebugOptions.ShowDumpFiles;
import static org.graalvm.compiler.debug.DebugOptions.Time;
import static org.graalvm.compiler.debug.DebugOptions.Timers;
import static org.graalvm.compiler.debug.DebugOptions.TrackMemUse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Pair;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.graphio.GraphOutput;

import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A facility for logging and dumping as well as a container for values associated with
 * {@link MetricKey}s.
 *
 * A {@code DebugContext} object must only be used on the thread that created it. This means it
 * needs to be passed around as a parameter. For convenience, it can be encapsulated in a widely
 * used object that is in scope wherever a {@code DebugContext} is needed. However, care must be
 * taken when such objects can be exposed to multiple threads (e.g., they are in a non-thread-local
 * cache).
 */
public final class DebugContext implements AutoCloseable {

    public static final Description NO_DESCRIPTION = new Description(null, "NO_DESCRIPTION");
    public static final GlobalMetrics NO_GLOBAL_METRIC_VALUES = null;
    public static final Iterable<DebugHandlersFactory> NO_CONFIG_CUSTOMIZERS = Collections.emptyList();

    /**
     * Contains the immutable parts of a debug context. This separation allows the immutable parts
     * to be shared and reduces the overhead of initialization since most immutable fields are
     * configured by parsing options.
     */
    final Immutable immutable;

    /**
     * Determines whether metrics are enabled.
     */
    boolean metricsEnabled;

    DebugConfigImpl currentConfig;
    ScopeImpl currentScope;
    CloseableCounter currentTimer;
    CloseableCounter currentMemUseTracker;
    Scope lastClosedScope;
    Throwable lastExceptionThrown;
    private IgvDumpChannel sharedChannel;
    private GraphOutput<?, ?> parentOutput;

    /**
     * Stores the {@link MetricKey} values.
     */
    private long[] metricValues;

    public static PrintStream getDefaultLogStream() {
        return TTY.out;
    }

    /**
     * Determines if dynamic scopes are enabled.
     */
    public boolean areScopesEnabled() {
        return immutable.scopesEnabled;
    }

    public <G, N, M> GraphOutput<G, M> buildOutput(GraphOutput.Builder<G, N, M> builder) throws IOException {
        if (parentOutput != null) {
            return builder.build(parentOutput);
        } else {
            if (sharedChannel == null) {
                sharedChannel = new IgvDumpChannel(() -> getDumpPath(".bgv", false), immutable.options);
            }
            builder.attr(GraphOutput.ATTR_VM_ID, GraalServices.getExecutionID());
            final GraphOutput<G, M> output = builder.build(sharedChannel);
            parentOutput = output;
            return output;
        }
    }

    /**
     * Adds version properties to the provided map. The version properties are read at a start of
     * the JVM from a JVM specific location. Each property identifiers a commit of a certain
     * component in the system. The properties added to the {@code properties} map are prefixed with
     * {@code "version."} prefix.
     *
     * @param properties map to add the version properties to or {@code null}
     * @return {@code properties} with version properties added or an unmodifiable map containing
     *         the version properties if {@code properties == null}
     */
    public static Map<Object, Object> addVersionProperties(Map<Object, Object> properties) {
        return Versions.VERSIONS.withVersions(properties);
    }

    /**
     * The immutable configuration that can be shared between {@link DebugContext} objects.
     */
    static final class Immutable {

        private static final Immutable[] CACHE = new Immutable[5];

        /**
         * The options from which this object was configured.
         */
        final OptionValues options;

        /**
         * Specifies if dynamic scopes are enabled.
         */
        final boolean scopesEnabled;

        final boolean listMetrics;

        /**
         * Names of unscoped counters. A counter is unscoped if this set is empty or contains the
         * counter's name.
         */
        final EconomicSet<String> unscopedCounters;

        /**
         * Names of unscoped timers. A timer is unscoped if this set is empty or contains the
         * timer's name.
         */
        final EconomicSet<String> unscopedTimers;

        /**
         * Names of unscoped memory usage trackers. A memory usage tracker is unscoped if this set
         * is empty or contains the memory usage tracker's name.
         */
        final EconomicSet<String> unscopedMemUseTrackers;

        private static EconomicSet<String> parseUnscopedMetricSpec(String spec, boolean unconditional, boolean accumulatedKey) {
            EconomicSet<String> res;
            if (spec == null) {
                if (!unconditional) {
                    res = null;
                } else {
                    res = EconomicSet.create();
                }
            } else {
                res = EconomicSet.create();
                if (!spec.isEmpty()) {
                    if (!accumulatedKey) {
                        res.addAll(Arrays.asList(spec.split(",")));
                    } else {
                        for (String n : spec.split(",")) {
                            res.add(n + AccumulatedKey.ACCUMULATED_KEY_SUFFIX);
                            res.add(n + AccumulatedKey.FLAT_KEY_SUFFIX);
                        }
                    }

                }
            }
            return res;
        }

        static Immutable create(OptionValues options) {
            int i = 0;
            while (i < CACHE.length) {
                Immutable immutable = CACHE[i];
                if (immutable == null) {
                    break;
                }
                if (immutable.options == options) {
                    return immutable;
                }
                i++;
            }
            Immutable immutable = new Immutable(options);
            if (i < CACHE.length) {
                CACHE[i] = immutable;
            }
            return immutable;
        }

        private static boolean isNotEmpty(OptionKey<String> option, OptionValues options) {
            return option.getValue(options) != null && !option.getValue(options).isEmpty();
        }

        private Immutable(OptionValues options) {
            this.options = options;
            String timeValue = Time.getValue(options);
            String trackMemUseValue = TrackMemUse.getValue(options);
            this.unscopedCounters = parseUnscopedMetricSpec(Counters.getValue(options), "".equals(Count.getValue(options)), false);
            this.unscopedTimers = parseUnscopedMetricSpec(Timers.getValue(options), "".equals(timeValue), true);
            this.unscopedMemUseTrackers = parseUnscopedMetricSpec(MemUseTrackers.getValue(options), "".equals(trackMemUseValue), true);

            if (unscopedMemUseTrackers != null || trackMemUseValue != null) {
                if (!GraalServices.isThreadAllocatedMemorySupported()) {
                    TTY.println("WARNING: Missing VM support for MemUseTrackers and TrackMemUse options so all reported memory usage will be 0");
                }
            }

            this.scopesEnabled = DumpOnError.getValue(options) ||
                            Dump.getValue(options) != null ||
                            Log.getValue(options) != null ||
                            isNotEmpty(DebugOptions.Count, options) ||
                            isNotEmpty(DebugOptions.Time, options) ||
                            isNotEmpty(DebugOptions.TrackMemUse, options) ||
                            DumpOnPhaseChange.getValue(options) != null;
            this.listMetrics = ListMetrics.getValue(options);
        }

        private Immutable() {
            this.options = new OptionValues(EconomicMap.create());
            this.unscopedCounters = null;
            this.unscopedTimers = null;
            this.unscopedMemUseTrackers = null;
            this.scopesEnabled = false;
            this.listMetrics = false;
        }

        public boolean hasUnscopedMetrics() {
            return unscopedCounters != null || unscopedTimers != null || unscopedMemUseTrackers != null;
        }
    }

    /**
     * Gets the options this debug context was constructed with.
     */
    public OptionValues getOptions() {
        return immutable.options;
    }

    static class Activated extends ThreadLocal<DebugContext> {
    }

    private static final Activated activated = new Activated();

    /**
     * An object used to undo the changes made by DebugContext#activate().
     */
    public static class Activation implements AutoCloseable {
        private final DebugContext parent;

        Activation(DebugContext parent) {
            this.parent = parent;
        }

        @Override
        public void close() {
            activated.set(parent);
        }
    }

    /**
     * Activates this object as the debug context {@linkplain DebugContext#forCurrentThread for the
     * current thread}. This method should be used in a try-with-resources statement.
     *
     * @return an object that will deactivate the debug context for the current thread when
     *         {@link Activation#close()} is called on it
     */
    public Activation activate() {
        Activation res = new Activation(activated.get());
        activated.set(this);
        return res;
    }

    /**
     * Singleton used to represent a disabled debug context.
     */
    private static final DebugContext DISABLED = new DebugContext(NO_DESCRIPTION, null, NO_GLOBAL_METRIC_VALUES, getDefaultLogStream(), new Immutable(), NO_CONFIG_CUSTOMIZERS);

    /**
     * Create a DebugContext with debugging disabled.
     */
    public static DebugContext disabled(OptionValues options) {
        if (options == null || options.getMap().isEmpty()) {
            return DISABLED;
        }
        return new DebugContext(NO_DESCRIPTION, null, NO_GLOBAL_METRIC_VALUES, getDefaultLogStream(), Immutable.create(options), NO_CONFIG_CUSTOMIZERS);
    }

    /**
     * Gets the debug context for the current thread. This should only be used when there is no
     * other reasonable means to get a hold of a debug context.
     */
    public static DebugContext forCurrentThread() {
        DebugContext current = activated.get();
        if (current == null) {
            return DISABLED;
        }
        return current;
    }

    private final GlobalMetrics globalMetrics;

    /**
     * Describes the computation associated with a {@link DebugContext}.
     */
    public static class Description {
        /**
         * The primary input to the computation.
         */
        final Object compilable;

        /**
         * A runtime based identifier that is most likely to be unique.
         */
        final String identifier;

        public Description(Object compilable, String identifier) {
            this.compilable = compilable;
            this.identifier = identifier;
        }

        @Override
        public String toString() {
            String compilableName = compilable instanceof JavaMethod ? ((JavaMethod) compilable).format("%H.%n(%p)%R") : String.valueOf(compilable);
            return identifier + ":" + compilableName;
        }

        final String getLabel() {
            if (compilable instanceof JavaMethod) {
                JavaMethod method = (JavaMethod) compilable;
                return method.format("%h.%n(%p)%r");
            }
            return String.valueOf(compilable);
        }
    }

    private final Description description;

    private final CompilationListener compilationListener;

    /**
     * Gets a description of the computation associated with this debug context.
     *
     * @return {@code null} if no description is available
     */
    public Description getDescription() {
        return description;
    }

    /**
     * Determines if {@link #enterCompilerPhase} and {@link #notifyInlining} do anything.
     *
     * @return {@code true} if there is a listener for compiler phase and inlining events attached
     *         to this object, {@code false} otherwise
     */
    public boolean hasCompilationListener() {
        return compilationListener != null;
    }

    private int compilerPhaseNesting = 0;

    /**
     * Scope for a compiler phase event.
     */
    public interface CompilerPhaseScope extends AutoCloseable {
        /**
         * Notifies the listener that the phase has ended.
         */
        @Override
        void close();
    }

    /**
     * Notifies this object that the compiler is entering a phase.
     *
     * It is recommended to use this method in a try-with-resource statement.
     *
     * @param phaseName name of the phase being entered
     * @return {@code null} if {@link #hasCompilationListener()} returns {@code false} otherwise an
     *         object whose {@link CompilerPhaseScope#close()} method must be called when the phase
     *         ends
     */
    public CompilerPhaseScope enterCompilerPhase(CharSequence phaseName) {
        if (compilationListener != null) {
            return enterCompilerPhase(() -> phaseName);
        }
        return null;
    }

    /**
     * Notifies this object that the compiler is entering a phase.
     *
     * It is recommended to use this method in a try-with-resource statement.
     *
     * @param phaseName name of the phase being entered
     * @return {@code null} if {@link #hasCompilationListener()} returns {@code false} otherwise an
     *         object whose {@link CompilerPhaseScope#close()} method must be called when the phase
     *         ends
     */
    public CompilerPhaseScope enterCompilerPhase(Supplier<CharSequence> phaseName) {
        CompilationListener l = compilationListener;
        if (l != null) {
            CompilerPhaseScope scope = l.enterPhase(phaseName.get(), compilerPhaseNesting++);
            return new CompilerPhaseScope() {

                @Override
                public void close() {
                    --compilerPhaseNesting;
                    scope.close();
                }
            };
        }
        return null;
    }

    /**
     * Notifies this object when the compiler considers inlining {@code callee} into {@code caller}.
     * A call to this method should be guarded with {@link #hasCompilationListener()} if
     * {@code message} is not a string literal or pre-computed value
     *
     * @param caller caller method
     * @param callee callee method considered for inlining into {@code caller}
     * @param succeeded true if {@code callee} was inlined into {@code caller}
     * @param message extra information about inlining decision
     * @param bci byte code index of call site
     */
    public void notifyInlining(ResolvedJavaMethod caller, ResolvedJavaMethod callee, boolean succeeded, CharSequence message, int bci) {
        if (compilationListener != null) {
            compilationListener.notifyInlining(caller, callee, succeeded, message, bci);
        }
    }

    /**
     * Gets the global metrics associated with this debug context.
     *
     * @return {@code null} if no global metrics are available
     */
    public GlobalMetrics getGlobalMetrics() {
        return globalMetrics;
    }

    /**
     * Object used to create a {@link DebugContext}.
     */
    public static class Builder {
        private final OptionValues options;
        private Description description = NO_DESCRIPTION;
        private CompilationListener compilationListener;
        private GlobalMetrics globalMetrics = NO_GLOBAL_METRIC_VALUES;
        private PrintStream logStream = getDefaultLogStream();
        private final Iterable<DebugHandlersFactory> factories;

        /**
         * Builder for a {@link DebugContext} based on {@code options} and
         * {@link DebugHandlersFactory#LOADER}.
         */
        public Builder(OptionValues options) {
            this.options = options;
            this.factories = DebugHandlersFactory.LOADER;
        }

        /**
         * Builder for a {@link DebugContext} based on {@code options} and {@code factories}. The
         * {@link DebugHandlersFactory#LOADER} value can be used for the latter.
         */
        public Builder(OptionValues options, Iterable<DebugHandlersFactory> factories) {
            this.options = options;
            this.factories = factories;
        }

        /**
         * Builder for a {@link DebugContext} based {@code options} and {@code factory}. The latter
         * can be null in which case {@link DebugContext#NO_CONFIG_CUSTOMIZERS} is used.
         */
        public Builder(OptionValues options, DebugHandlersFactory factory) {
            this.options = options;
            this.factories = factory == null ? NO_CONFIG_CUSTOMIZERS : Collections.singletonList(factory);
        }

        /**
         * Sets the description for the debug context. The default is for a context to have no
         * description.
         */
        public Builder description(Description desc) {
            this.description = desc;
            return this;
        }

        /**
         * Sets the compilation listener for the debug context. The default is for a context to have
         * no compilation listener.
         */
        public Builder compilationListener(CompilationListener listener) {
            this.compilationListener = listener;
            return this;
        }

        public Builder globalMetrics(GlobalMetrics metrics) {
            this.globalMetrics = metrics;
            return this;
        }

        public Builder logStream(PrintStream stream) {
            this.logStream = stream;
            return this;
        }

        public DebugContext build() {
            return new DebugContext(description,
                            compilationListener,
                            globalMetrics,
                            logStream,
                            Immutable.create(options),
                            factories);
        }
    }

    private DebugContext(Description description,
                    CompilationListener compilationListener,
                    GlobalMetrics globalMetrics,
                    PrintStream logStream,
                    Immutable immutable,
                    Iterable<DebugHandlersFactory> factories) {
        this.immutable = immutable;
        this.description = description;
        this.globalMetrics = globalMetrics;
        this.compilationListener = compilationListener;
        if (immutable.scopesEnabled) {
            OptionValues options = immutable.options;
            List<DebugDumpHandler> dumpHandlers = new ArrayList<>();
            List<DebugVerifyHandler> verifyHandlers = new ArrayList<>();
            for (DebugHandlersFactory factory : factories) {
                for (DebugHandler handler : factory.createHandlers(options)) {
                    if (handler instanceof DebugDumpHandler) {
                        dumpHandlers.add((DebugDumpHandler) handler);
                    } else {
                        assert handler instanceof DebugVerifyHandler;
                        verifyHandlers.add((DebugVerifyHandler) handler);
                    }
                }
            }
            currentConfig = new DebugConfigImpl(options, logStream, dumpHandlers, verifyHandlers);
            currentScope = new ScopeImpl(this, Thread.currentThread(), DisableIntercept.getValue(options));
            currentScope.updateFlags(currentConfig);
            metricsEnabled = true;
        } else {
            metricsEnabled = immutable.hasUnscopedMetrics() || immutable.listMetrics;
        }
    }

    public Path getDumpPath(String extension, boolean createMissingDirectory) {
        try {
            String id = description == null ? null : description.identifier;
            String label = description == null ? null : description.getLabel();
            Path result = PathUtilities.createUnique(immutable.options, DumpPath, id, label, extension, createMissingDirectory);
            if (ShowDumpFiles.getValue(immutable.options)) {
                TTY.println("Dumping debug output to %s", result.toAbsolutePath().toString());
            }
            return result;
        } catch (IOException ex) {
            throw rethrowSilently(RuntimeException.class, ex);
        }
    }

    /**
     * A special dump level that indicates the dumping machinery is enabled but no dumps will be
     * produced except through other options.
     */
    public static final int ENABLED_LEVEL = 0;

    /**
     * Basic debug level.
     *
     * For HIR dumping, only ~5 graphs per method: after parsing, after inlining, after high tier,
     * after mid tier, after low tier.
     *
     * LIR dumping: After LIR generation, after each pre-allocation, allocation and post allocation
     * stage, and after code installation.
     */
    public static final int BASIC_LEVEL = 1;

    /**
     * Informational debug level.
     *
     * HIR dumping: One graph after each applied top-level phase.
     *
     * LIR dumping: After each applied phase.
     */
    public static final int INFO_LEVEL = 2;

    /**
     * Verbose debug level.
     *
     * HIR dumping: One graph after each phase (including sub phases).
     *
     * LIR dumping: After each phase including sub phases.
     */
    public static final int VERBOSE_LEVEL = 3;

    /**
     * Detailed debug level.
     *
     * HIR dumping: Graphs within phases where interesting for a phase, max ~5 per phase.
     *
     * LIR dumping: Dump CFG within phases where interesting.
     */
    public static final int DETAILED_LEVEL = 4;

    /**
     * Very detailed debug level.
     *
     * HIR dumping: Graphs per node granularity graph change (before/after change).
     *
     * LIR dumping: Intermediate CFGs of phases where interesting.
     */
    public static final int VERY_DETAILED_LEVEL = 5;

    public boolean isDumpEnabled(int dumpLevel) {
        return currentScope != null && currentScope.isDumpEnabled(dumpLevel);
    }

    /**
     * Determines if verification is enabled for any {@link JavaMethod} in the current scope.
     *
     * @see DebugContext#verify(Object, String)
     */
    public boolean isVerifyEnabledForMethod() {
        if (currentScope == null) {
            return false;
        }
        if (currentConfig == null) {
            return false;
        }
        return currentConfig.isVerifyEnabledForMethod(currentScope);
    }

    /**
     * Determines if verification is enabled in the current scope.
     *
     * @see DebugContext#verify(Object, String)
     */
    public boolean isVerifyEnabled() {
        return currentScope != null && currentScope.isVerifyEnabled();
    }

    public boolean isCountEnabled() {
        return currentScope != null && currentScope.isCountEnabled();
    }

    public boolean isTimeEnabled() {
        return currentScope != null && currentScope.isTimeEnabled();
    }

    public boolean isMemUseTrackingEnabled() {
        return currentScope != null && currentScope.isMemUseTrackingEnabled();
    }

    public boolean isDumpEnabledForMethod() {
        if (currentConfig == null) {
            return false;
        }
        return currentConfig.isDumpEnabledForMethod(currentScope);
    }

    public boolean isLogEnabledForMethod() {
        if (currentScope == null) {
            return false;
        }
        if (currentConfig == null) {
            return false;
        }
        return currentConfig.isLogEnabledForMethod(currentScope);
    }

    public boolean isLogEnabled() {
        return currentScope != null && isLogEnabled(BASIC_LEVEL);
    }

    public boolean isLogEnabled(int logLevel) {
        return currentScope != null && currentScope.isLogEnabled(logLevel);
    }

    /**
     * Gets a string composed of the names in the current nesting of debug
     * {@linkplain #scope(Object) scopes} separated by {@code '.'}.
     */
    public String getCurrentScopeName() {
        if (currentScope != null) {
            return currentScope.getQualifiedName();
        } else {
            return "";
        }
    }

    /**
     * Creates and enters a new debug scope which will be a child of the current debug scope.
     * <p>
     * It is recommended to use the try-with-resource statement for managing entering and leaving
     * debug scopes. For example:
     *
     * <pre>
     * try (Scope s = Debug.scope(&quot;InliningGraph&quot;, inlineeGraph)) {
     *     ...
     * } catch (Throwable e) {
     *     throw Debug.handle(e);
     * }
     * </pre>
     *
     * The {@code name} argument is subject to the following type based conversion before having
     * {@link Object#toString()} called on it:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @param name the name of the new scope
     * @param contextObjects an array of object to be appended to the {@linkplain #context()
     *            current} debug context
     * @throws Throwable used to enforce a catch block.
     * @return the scope entered by this method which will be exited when its {@link Scope#close()}
     *         method is called
     */
    public DebugContext.Scope scope(Object name, Object[] contextObjects) throws Throwable {
        if (currentScope != null) {
            return enterScope(convertFormatArg(name).toString(), null, contextObjects);
        } else {
            return null;
        }
    }

    /**
     * Similar to {@link #scope(Object, Object[])} but without context objects. Therefore the catch
     * block can be omitted.
     *
     * @see #scope(Object, Object[])
     */
    public DebugContext.Scope scope(Object name) {
        if (currentScope != null) {
            return enterScope(convertFormatArg(name).toString(), null);
        } else {
            return null;
        }
    }

    /**
     * Arbitrary threads cannot be in the image so null out {@code DebugContext.invariants} which
     * holds onto a thread and is only used for assertions.
     */
    @NativeImageReinitialize private final Invariants invariants = Assertions.assertionsEnabled() ? new Invariants() : null;

    static StackTraceElement[] getStackTrace(Thread thread) {
        return thread.getStackTrace();
    }

    /**
     * Utility for enforcing {@link DebugContext} invariants via assertions.
     */
    static class Invariants {
        private final Thread thread;
        private final StackTraceElement[] origin;

        Invariants() {
            thread = Thread.currentThread();
            origin = getStackTrace(thread);
        }

        boolean checkNoConcurrentAccess() {
            Thread currentThread = Thread.currentThread();
            if (currentThread != thread) {
                Formatter buf = new Formatter();
                buf.format("Thread local %s object was created on thread %s but is being accessed by thread %s. The most likely cause is " +
                                "that the object is being retrieved from a non-thread-local cache.",
                                DebugContext.class.getName(), thread, currentThread);
                int debugContextConstructors = 0;
                boolean addedHeader = false;
                for (StackTraceElement e : origin) {
                    if (e.getMethodName().equals("<init>") && e.getClassName().equals(DebugContext.class.getName())) {
                        debugContextConstructors++;
                    } else if (debugContextConstructors != 0) {
                        if (!addedHeader) {
                            addedHeader = true;
                            buf.format(" The object was instantiated here:");
                        }
                        // Distinguish from assertion stack trace by using double indent and
                        // "in" instead of "at" prefix.
                        buf.format("%n\t\tin %s", e);
                    }
                }
                if (addedHeader) {
                    buf.format("%n");
                }

                throw new AssertionError(buf.toString());
            }
            return true;
        }
    }

    boolean checkNoConcurrentAccess() {
        assert invariants == null || invariants.checkNoConcurrentAccess();
        return true;
    }

    private DebugContext.Scope enterScope(CharSequence name, DebugConfig sandboxConfig, Object... newContextObjects) {
        assert checkNoConcurrentAccess();
        currentScope = currentScope.scope(name, sandboxConfig, newContextObjects);
        return currentScope;
    }

    /**
     * @see #scope(Object, Object[])
     * @param context an object to be appended to the {@linkplain #context() current} debug context
     */
    public DebugContext.Scope scope(Object name, Object context) throws Throwable {
        if (currentScope != null) {
            return enterScope(convertFormatArg(name).toString(), null, context);
        } else {
            return null;
        }
    }

    /**
     * @see #scope(Object, Object[])
     * @param context1 first object to be appended to the {@linkplain #context() current} debug
     *            context
     * @param context2 second object to be appended to the {@linkplain #context() current} debug
     *            context
     */
    public DebugContext.Scope scope(Object name, Object context1, Object context2) throws Throwable {
        if (currentScope != null) {
            return enterScope(convertFormatArg(name).toString(), null, context1, context2);
        } else {
            return null;
        }
    }

    /**
     * @see #scope(Object, Object[])
     * @param context1 first object to be appended to the {@linkplain #context() current} debug
     *            context
     * @param context2 second object to be appended to the {@linkplain #context() current} debug
     *            context
     * @param context3 third object to be appended to the {@linkplain #context() current} debug
     *            context
     */
    public DebugContext.Scope scope(Object name, Object context1, Object context2, Object context3) throws Throwable {
        if (currentScope != null) {
            return enterScope(convertFormatArg(name).toString(), null, context1, context2, context3);
        } else {
            return null;
        }
    }

    /**
     * Create an unnamed scope that appends some context to the current scope.
     *
     * @param context an object to be appended to the {@linkplain #context() current} debug context
     */
    public DebugContext.Scope withContext(Object context) throws Throwable {
        if (currentScope != null) {
            return enterScope("", null, context);
        } else {
            return null;
        }
    }

    /**
     * Creates and enters a new debug scope which will be disjoint from the current debug scope.
     * <p>
     * It is recommended to use the try-with-resource statement for managing entering and leaving
     * debug scopes. For example:
     *
     * <pre>
     * try (Scope s = Debug.sandbox(&quot;CompilingStub&quot;, null, stubGraph)) {
     *     ...
     * } catch (Throwable e) {
     *     throw Debug.handle(e);
     * }
     * </pre>
     *
     * @param name the name of the new scope
     * @param config the debug configuration to use for the new scope or {@code null} to disable the
     *            scoping mechanism within the sandbox scope
     * @param context objects to be appended to the {@linkplain #context() current} debug context
     * @return the scope entered by this method which will be exited when its {@link Scope#close()}
     *         method is called
     */
    public DebugContext.Scope sandbox(CharSequence name, DebugConfig config, Object... context) throws Throwable {
        if (config == null) {
            return disable();
        }
        if (currentScope != null) {
            return enterScope(name, config, context);
        } else {
            return null;
        }
    }

    /**
     * Determines if scopes are enabled and this context is in a non-top-level scope.
     */
    public boolean inNestedScope() {
        if (immutable.scopesEnabled) {
            if (currentScope == null) {
                // In an active DisabledScope
                return true;
            }
            return !currentScope.isTopLevel();
        } else {
            return false;
        }
    }

    class DisabledScope implements DebugContext.Scope {
        final boolean savedMetricsEnabled;
        final ScopeImpl savedScope;
        final DebugConfigImpl savedConfig;

        DisabledScope() {
            this.savedMetricsEnabled = metricsEnabled;
            this.savedScope = currentScope;
            this.savedConfig = currentConfig;
            metricsEnabled = false;
            currentScope = null;
            currentConfig = null;
        }

        @Override
        public String getQualifiedName() {
            return "";
        }

        @Override
        public Iterable<Object> getCurrentContext() {
            return Collections.emptyList();
        }

        @Override
        public void close() {
            metricsEnabled = savedMetricsEnabled;
            currentScope = savedScope;
            currentConfig = savedConfig;
            lastClosedScope = this;
        }
    }

    /**
     * Disables all metrics and scope related functionality until {@code close()} is called on the
     * returned object.
     */
    public DebugContext.Scope disable() {
        if (currentScope != null) {
            return new DisabledScope();
        } else {
            return null;
        }
    }

    public DebugContext.Scope forceLog() throws Throwable {
        if (currentConfig != null) {
            ArrayList<Object> context = new ArrayList<>();
            for (Object obj : context()) {
                context.add(obj);
            }
            DebugConfigImpl config = new DebugConfigImpl(new OptionValues(currentConfig.getOptions(), DebugOptions.Log, ":1000"));
            return sandbox("forceLog", config, context.toArray());
        }
        return null;
    }

    /**
     * Opens a scope in which exception
     * {@linkplain DebugConfig#interceptException(DebugContext, Throwable) interception} is
     * disabled. The current state of interception is restored when {@link DebugCloseable#close()}
     * is called on the returned object.
     *
     * This is particularly useful to suppress extraneous output in JUnit tests that are expected to
     * throw an exception.
     */
    public DebugCloseable disableIntercept() {
        if (currentScope != null) {
            return currentScope.disableIntercept();
        }
        return null;
    }

    /**
     * Handles an exception in the context of the debug scope just exited. The just exited scope
     * must have the current scope as its parent which will be the case if the try-with-resource
     * pattern recommended by {@link #scope(Object)} and
     * {@link #sandbox(CharSequence, DebugConfig, Object...)} is used
     *
     * @see #scope(Object, Object[])
     * @see #sandbox(CharSequence, DebugConfig, Object...)
     */
    public RuntimeException handle(Throwable exception) {
        if (currentScope != null) {
            return currentScope.handle(exception);
        } else {
            if (exception instanceof Error) {
                throw (Error) exception;
            }
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new RuntimeException(exception);
        }
    }

    public void log(String msg) {
        log(BASIC_LEVEL, msg);
    }

    /**
     * Prints a message to the current debug scope's logging stream if logging is enabled.
     *
     * @param msg the message to log
     */
    public void log(int logLevel, String msg) {
        if (currentScope != null) {
            currentScope.log(logLevel, msg);
        }
    }

    public void log(String format, Object arg) {
        log(BASIC_LEVEL, format, arg);
    }

    /**
     * Prints a message to the current debug scope's logging stream if logging is enabled.
     *
     * @param format a format string
     * @param arg the argument referenced by the format specifiers in {@code format}
     */
    public void log(int logLevel, String format, Object arg) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg);
        }
    }

    public void log(String format, int arg) {
        log(BASIC_LEVEL, format, arg);
    }

    /**
     * Prints a message to the current debug scope's logging stream if logging is enabled.
     *
     * @param format a format string
     * @param arg the argument referenced by the format specifiers in {@code format}
     */
    public void log(int logLevel, String format, int arg) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg);
        }
    }

    public void log(String format, Object arg1, Object arg2) {
        log(BASIC_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #log(int, String, Object)
     */
    public void log(int logLevel, String format, Object arg1, Object arg2) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2);
        }
    }

    public void log(String format, int arg1, Object arg2) {
        log(BASIC_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #log(int, String, Object)
     */
    public void log(int logLevel, String format, int arg1, Object arg2) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2);
        }
    }

    public void log(String format, Object arg1, int arg2) {
        log(BASIC_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #log(int, String, Object)
     */
    public void log(int logLevel, String format, Object arg1, int arg2) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2);
        }
    }

    public void log(String format, int arg1, int arg2) {
        log(BASIC_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #log(int, String, Object)
     */
    public void log(int logLevel, String format, int arg1, int arg2) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2);
        }
    }

    public void log(String format, Object arg1, Object arg2, Object arg3) {
        log(BASIC_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #log(int, String, Object)
     */
    public void log(int logLevel, String format, Object arg1, Object arg2, Object arg3) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2, arg3);
        }
    }

    public void log(String format, int arg1, int arg2, int arg3) {
        log(BASIC_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #log(int, String, Object)
     */
    public void log(int logLevel, String format, int arg1, int arg2, int arg3) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2, arg3);
        }
    }

    public void log(String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        log(BASIC_LEVEL, format, arg1, arg2, arg3, arg4);
    }

    /**
     * @see #log(int, String, Object)
     */
    public void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2, arg3, arg4);
        }
    }

    public void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        log(BASIC_LEVEL, format, arg1, arg2, arg3, arg4, arg5);
    }

    /**
     * @see #log(int, String, Object)
     */
    public void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2, arg3, arg4, arg5);
        }
    }

    public void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        log(BASIC_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    /**
     * @see #log(int, String, Object)
     */
    public void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6);
        }
    }

    public void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        log(BASIC_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
    }

    public void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
        log(BASIC_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
    }

    /**
     * @see #log(int, String, Object)
     */
    public void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7);
        }
    }

    public void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
        }
    }

    public void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
        log(BASIC_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
    }

    public void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
        }
    }

    public void log(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
        log(BASIC_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
    }

    public void log(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6, Object arg7, Object arg8, Object arg9, Object arg10) {
        if (currentScope != null) {
            currentScope.log(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
        }
    }

    public void logv(String format, Object... args) {
        logv(BASIC_LEVEL, format, args);
    }

    /**
     * Prints a message to the current debug scope's logging stream. This method must only be called
     * if debugging scopes are {@linkplain DebugContext#areScopesEnabled() enabled} as it incurs
     * allocation at the call site. If possible, call one of the other {@code log()} methods in this
     * class that take a fixed number of parameters.
     *
     * @param format a format string
     * @param args the arguments referenced by the format specifiers in {@code format}
     */
    public void logv(int logLevel, String format, Object... args) {
        if (currentScope == null) {
            throw new InternalError("Use of Debug.logv() must be guarded by a test of Debug.isEnabled()");
        }
        currentScope.log(logLevel, format, args);
    }

    /**
     * This override exists to catch cases when {@link #log(String, Object)} is called with one
     * argument bound to a varargs method parameter. It will bind to this method instead of the
     * single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public void log(String format, Object[] args) {
        assert false : "shouldn't use this";
        log(BASIC_LEVEL, format, args);
    }

    /**
     * This override exists to catch cases when {@link #log(int, String, Object)} is called with one
     * argument bound to a varargs method parameter. It will bind to this method instead of the
     * single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public void log(int logLevel, String format, Object[] args) {
        assert false : "shouldn't use this";
        logv(logLevel, format, args);
    }

    /**
     * Forces an unconditional dump. This method exists mainly for debugging. It can also be used to
     * force a graph dump from IDEs that support invoking a Java method while at a breakpoint.
     */
    public void forceDump(Object object, String format, Object... args) {
        DebugConfig config = currentConfig;
        Collection<DebugDumpHandler> dumpHandlers;
        boolean closeAfterDump;
        if (config != null) {
            dumpHandlers = config.dumpHandlers();
            closeAfterDump = false;
        } else {
            OptionValues options = getOptions();
            dumpHandlers = new ArrayList<>();
            for (DebugHandlersFactory factory : DebugHandlersFactory.LOADER) {
                for (DebugHandler handler : factory.createHandlers(options)) {
                    if (handler instanceof DebugDumpHandler) {
                        dumpHandlers.add((DebugDumpHandler) handler);
                    }
                }
            }
            closeAfterDump = true;
        }
        for (DebugDumpHandler dumpHandler : dumpHandlers) {
            dumpHandler.dump(this, object, format, args);
            if (closeAfterDump) {
                dumpHandler.close();
            }
        }
    }

    public void dump(int dumpLevel, Object object, String msg) {
        if (currentScope != null && currentScope.isDumpEnabled(dumpLevel)) {
            currentScope.dump(dumpLevel, object, msg);
        }
    }

    public void dump(int dumpLevel, Object object, String format, Object arg) {
        if (currentScope != null && currentScope.isDumpEnabled(dumpLevel)) {
            currentScope.dump(dumpLevel, object, format, arg);
        }
    }

    public void dump(int dumpLevel, Object object, String format, Object arg1, Object arg2) {
        if (currentScope != null && currentScope.isDumpEnabled(dumpLevel)) {
            currentScope.dump(dumpLevel, object, format, arg1, arg2);
        }
    }

    public void dump(int dumpLevel, Object object, String format, Object arg1, Object arg2, Object arg3) {
        if (currentScope != null && currentScope.isDumpEnabled(dumpLevel)) {
            currentScope.dump(dumpLevel, object, format, arg1, arg2, arg3);
        }
    }

    /**
     * This override exists to catch cases when {@link #dump(int, Object, String, Object)} is called
     * with one argument bound to a varargs method parameter. It will bind to this method instead of
     * the single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public void dump(int dumpLevel, Object object, String format, Object[] args) {
        assert false : "shouldn't use this";
        if (currentScope != null && currentScope.isDumpEnabled(dumpLevel)) {
            currentScope.dump(dumpLevel, object, format, args);
        }
    }

    /**
     * Calls all {@link DebugVerifyHandler}s in the current {@linkplain #getConfig() config} to
     * perform verification on a given object.
     *
     * @param object object to verify
     * @param message description of verification context
     *
     * @see DebugVerifyHandler#verify
     */
    public void verify(Object object, String message) {
        if (currentScope != null && currentScope.isVerifyEnabled()) {
            currentScope.verify(object, message);
        }
    }

    /**
     * Calls all {@link DebugVerifyHandler}s in the current {@linkplain #getConfig() config} to
     * perform verification on a given object.
     *
     * @param object object to verify
     * @param format a format string for the description of the verification context
     * @param arg the argument referenced by the format specifiers in {@code format}
     *
     * @see DebugVerifyHandler#verify
     */
    public void verify(Object object, String format, Object arg) {
        if (currentScope != null && currentScope.isVerifyEnabled()) {
            currentScope.verify(object, format, arg);
        }
    }

    /**
     * This override exists to catch cases when {@link #verify(Object, String, Object)} is called
     * with one argument bound to a varargs method parameter. It will bind to this method instead of
     * the single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public void verify(Object object, String format, Object[] args) {
        assert false : "shouldn't use this";
        if (currentScope != null && currentScope.isVerifyEnabled()) {
            currentScope.verify(object, format, args);
        }
    }

    /**
     * Opens a new indentation level (by adding some spaces) based on the current indentation level.
     * This should be used in a {@linkplain Indent try-with-resources} pattern.
     *
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     * @see #logAndIndent(int, String)
     * @see #logAndIndent(int, String, Object)
     */
    public Indent indent() {
        if (currentScope != null) {
            return currentScope.pushIndentLogger();
        }
        return null;
    }

    public Indent logAndIndent(String msg) {
        return logAndIndent(BASIC_LEVEL, msg);
    }

    /**
     * A convenience function which combines {@link #log(String)} and {@link #indent()}.
     *
     * @param msg the message to log
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public Indent logAndIndent(int logLevel, String msg) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, msg);
        }
        return null;
    }

    public Indent logAndIndent(String format, Object arg) {
        return logAndIndent(BASIC_LEVEL, format, arg);
    }

    /**
     * A convenience function which combines {@link #log(String, Object)} and {@link #indent()}.
     *
     * @param format a format string
     * @param arg the argument referenced by the format specifiers in {@code format}
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public Indent logAndIndent(int logLevel, String format, Object arg) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg);
        }
        return null;
    }

    public Indent logAndIndent(String format, int arg) {
        return logAndIndent(BASIC_LEVEL, format, arg);
    }

    /**
     * A convenience function which combines {@link #log(String, Object)} and {@link #indent()}.
     *
     * @param format a format string
     * @param arg the argument referenced by the format specifiers in {@code format}
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public Indent logAndIndent(int logLevel, String format, int arg) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg);
        }
        return null;
    }

    public Indent logAndIndent(String format, int arg1, Object arg2) {
        return logAndIndent(BASIC_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public Indent logAndIndent(int logLevel, String format, int arg1, Object arg2) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2);
        }
        return null;
    }

    public Indent logAndIndent(String format, Object arg1, int arg2) {
        return logAndIndent(BASIC_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public Indent logAndIndent(int logLevel, String format, Object arg1, int arg2) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2);
        }
        return null;
    }

    public Indent logAndIndent(String format, int arg1, int arg2) {
        return logAndIndent(BASIC_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public Indent logAndIndent(int logLevel, String format, int arg1, int arg2) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2);
        }
        return null;
    }

    public Indent logAndIndent(String format, Object arg1, Object arg2) {
        return logAndIndent(BASIC_LEVEL, format, arg1, arg2);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2);
        }
        return null;
    }

    public Indent logAndIndent(String format, Object arg1, Object arg2, Object arg3) {
        return logAndIndent(BASIC_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2, Object arg3) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3);
        }
        return null;
    }

    public Indent logAndIndent(String format, int arg1, int arg2, int arg3) {
        return logAndIndent(BASIC_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public Indent logAndIndent(int logLevel, String format, int arg1, int arg2, int arg3) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3);
        }
        return null;
    }

    public Indent logAndIndent(String format, Object arg1, int arg2, int arg3) {
        return logAndIndent(BASIC_LEVEL, format, arg1, arg2, arg3);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public Indent logAndIndent(int logLevel, String format, Object arg1, int arg2, int arg3) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3);
        }
        return null;
    }

    public Indent logAndIndent(String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        return logAndIndent(BASIC_LEVEL, format, arg1, arg2, arg3, arg4);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3, arg4);
        }
        return null;
    }

    public Indent logAndIndent(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        return logAndIndent(BASIC_LEVEL, format, arg1, arg2, arg3, arg4, arg5);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3, arg4, arg5);
        }
        return null;
    }

    public Indent logAndIndent(String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        return logAndIndent(BASIC_LEVEL, format, arg1, arg2, arg3, arg4, arg5, arg6);
    }

    /**
     * @see #logAndIndent(int, String, Object)
     */
    public Indent logAndIndent(int logLevel, String format, Object arg1, Object arg2, Object arg3, Object arg4, Object arg5, Object arg6) {
        if (currentScope != null && isLogEnabled(logLevel)) {
            return logvAndIndentInternal(logLevel, format, arg1, arg2, arg3, arg4, arg5, arg6);
        }
        return null;
    }

    /**
     * A convenience function which combines {@link #logv(int, String, Object...)} and
     * {@link #indent()}.
     *
     * @param format a format string
     * @param args the arguments referenced by the format specifiers in {@code format}
     * @return an object that reverts to the current indentation level when
     *         {@linkplain Indent#close() closed} or null if debugging is disabled
     */
    public Indent logvAndIndent(int logLevel, String format, Object... args) {
        if (currentScope != null) {
            if (isLogEnabled(logLevel)) {
                return logvAndIndentInternal(logLevel, format, args);
            }
            return null;
        }
        throw new InternalError("Use of Debug.logvAndIndent() must be guarded by a test of Debug.isEnabled()");
    }

    private Indent logvAndIndentInternal(int logLevel, String format, Object... args) {
        assert currentScope != null && isLogEnabled(logLevel) : "must have checked Debug.isLogEnabled()";
        currentScope.log(logLevel, format, args);
        return currentScope.pushIndentLogger();
    }

    /**
     * This override exists to catch cases when {@link #logAndIndent(String, Object)} is called with
     * one argument bound to a varargs method parameter. It will bind to this method instead of the
     * single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public void logAndIndent(String format, Object[] args) {
        assert false : "shouldn't use this";
        logAndIndent(BASIC_LEVEL, format, args);
    }

    /**
     * This override exists to catch cases when {@link #logAndIndent(int, String, Object)} is called
     * with one argument bound to a varargs method parameter. It will bind to this method instead of
     * the single arg variant and produce a deprecation warning instead of silently wrapping the
     * Object[] inside of another Object[].
     */
    @Deprecated
    public void logAndIndent(int logLevel, String format, Object[] args) {
        assert false : "shouldn't use this";
        logvAndIndent(logLevel, format, args);
    }

    public Iterable<Object> context() {
        if (currentScope != null) {
            return currentScope.getCurrentContext();
        } else {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    public <T> List<T> contextSnapshot(Class<T> clazz) {
        if (currentScope != null) {
            List<T> result = new ArrayList<>();
            for (Object o : context()) {
                if (clazz.isInstance(o)) {
                    result.add((T) o);
                }
            }
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Searches the current debug scope, bottom up, for a context object that is an instance of a
     * given type. The first such object found is returned.
     */
    @SuppressWarnings("unchecked")
    public <T> T contextLookup(Class<T> clazz) {
        if (currentScope != null) {
            for (Object o : context()) {
                if (clazz.isInstance(o)) {
                    return ((T) o);
                }
            }
        }
        return null;
    }

    /**
     * Searches the current debug scope, top down, for a context object that is an instance of a
     * given type. The first such object found is returned.
     */
    @SuppressWarnings("unchecked")
    public <T> T contextLookupTopdown(Class<T> clazz) {
        if (currentScope != null) {
            T found = null;
            for (Object o : context()) {
                if (clazz.isInstance(o)) {
                    found = (T) o;
                }
            }
            return found;
        }
        return null;
    }

    /**
     * Creates a {@linkplain MemUseTrackerKey memory use tracker}.
     */
    public static MemUseTrackerKey memUseTracker(CharSequence name) {
        return createMemUseTracker("%s", name, null);
    }

    /**
     * Creates a debug memory use tracker. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.memUseTracker(format, arg, null)
     * </pre>
     *
     * except that the string formatting only happens if mem tracking is enabled.
     *
     * @see #counter(String, Object, Object)
     */
    public static MemUseTrackerKey memUseTracker(String format, Object arg) {
        return createMemUseTracker(format, arg, null);
    }

    /**
     * Creates a debug memory use tracker. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.memUseTracker(String.format(format, arg1, arg2))
     * </pre>
     *
     * except that the string formatting only happens if memory use tracking is enabled. In
     * addition, each argument is subject to the following type based conversion before being passed
     * as an argument to {@link String#format(String, Object...)}:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @see #memUseTracker(CharSequence)
     */
    public static MemUseTrackerKey memUseTracker(String format, Object arg1, Object arg2) {
        return createMemUseTracker(format, arg1, arg2);
    }

    private static MemUseTrackerKey createMemUseTracker(String format, Object arg1, Object arg2) {
        return new MemUseTrackerKeyImpl(format, arg1, arg2);
    }

    /**
     * Creates a {@linkplain CounterKey counter}.
     */
    public static CounterKey counter(CharSequence name) {
        return createCounter("%s", name, null);
    }

    /**
     * Gets a tally of the metric values in this context and a given tally.
     *
     * @param tally the tally to which the metrics should be added
     * @return a tally of the metric values in this context and {@code tally}. This will be
     *         {@code tally} if this context has no metric values or {@code tally} is wide enough to
     *         hold all the metric values in this context otherwise it will be a new array.
     */
    public long[] addValuesTo(long[] tally) {
        if (metricValues == null) {
            return tally;
        }
        if (tally == null) {
            return metricValues.clone();
        } else if (metricValues.length >= tally.length) {
            long[] newTally = metricValues.clone();
            for (int i = 0; i < tally.length; i++) {
                newTally[i] += tally[i];
            }
            return newTally;
        } else {
            for (int i = 0; i < metricValues.length; i++) {
                tally[i] += metricValues[i];
            }
            return tally;
        }
    }

    /**
     * Creates and returns a sorted map from metric names to their values in {@code values}.
     *
     * @param values values for metrics in the {@link KeyRegistry}.
     */
    public static EconomicMap<MetricKey, Long> convertValuesToKeyValueMap(long[] values) {
        List<MetricKey> keys = KeyRegistry.getKeys();
        Collections.sort(keys, MetricKey.NAME_COMPARATOR);
        EconomicMap<MetricKey, Long> res = EconomicMap.create(keys.size());
        for (MetricKey key : keys) {
            int index = ((AbstractKey) key).getIndex();
            if (index >= values.length) {
                res.put(key, 0L);
            } else {
                res.put(key, values[index]);
            }
        }
        return res;
    }

    void setMetricValue(int keyIndex, long l) {
        ensureMetricValuesSize(keyIndex);
        metricValues[keyIndex] = l;
    }

    long getMetricValue(int keyIndex) {
        if (metricValues == null || metricValues.length <= keyIndex) {
            return 0L;
        }
        return metricValues[keyIndex];
    }

    private void ensureMetricValuesSize(int index) {
        if (metricValues == null) {
            metricValues = new long[index + 1];
        }
        if (metricValues.length <= index) {
            metricValues = Arrays.copyOf(metricValues, index + 1);
        }
    }

    public static String applyFormattingFlagsAndWidth(String s, int flags, int width) {
        if (flags == 0 && width < 0) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);

        // apply width and justification
        int len = sb.length();
        if (len < width) {
            for (int i = 0; i < width - len; i++) {
                if ((flags & LEFT_JUSTIFY) == LEFT_JUSTIFY) {
                    sb.append(' ');
                } else {
                    sb.insert(0, ' ');
                }
            }
        }

        String res = sb.toString();
        if ((flags & UPPERCASE) == UPPERCASE) {
            res = res.toUpperCase();
        }
        return res;
    }

    /**
     * Creates a debug counter. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.counter(format, arg, null)
     * </pre>
     *
     * except that the string formatting only happens if count is enabled.
     *
     * @see #counter(String, Object, Object)
     */
    public static CounterKey counter(String format, Object arg) {
        return createCounter(format, arg, null);
    }

    /**
     * Creates a debug counter. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.counter(String.format(format, arg1, arg2))
     * </pre>
     *
     * except that the string formatting only happens if count is enabled. In addition, each
     * argument is subject to the following type based conversion before being passed as an argument
     * to {@link String#format(String, Object...)}:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @see #counter(CharSequence)
     */
    public static CounterKey counter(String format, Object arg1, Object arg2) {
        return createCounter(format, arg1, arg2);
    }

    private static CounterKey createCounter(String format, Object arg1, Object arg2) {
        return new CounterKeyImpl(format, arg1, arg2);
    }

    public DebugConfig getConfig() {
        return currentConfig;
    }

    /**
     * Creates a {@linkplain TimerKey timer}.
     * <p>
     * A disabled timer has virtually no overhead.
     */
    public static TimerKey timer(CharSequence name) {
        return createTimer("%s", name, null);
    }

    /**
     * Creates a debug timer. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.timer(format, arg, null)
     * </pre>
     *
     * except that the string formatting only happens if timing is enabled.
     *
     * @see #timer(String, Object, Object)
     */
    public static TimerKey timer(String format, Object arg) {
        return createTimer(format, arg, null);
    }

    /**
     * Creates a debug timer. Invoking this method is equivalent to:
     *
     * <pre>
     * Debug.timer(String.format(format, arg1, arg2))
     * </pre>
     *
     * except that the string formatting only happens if timing is enabled. In addition, each
     * argument is subject to the following type based conversion before being passed as an argument
     * to {@link String#format(String, Object...)}:
     *
     * <pre>
     *     Type          | Conversion
     * ------------------+-----------------
     *  java.lang.Class  | arg.getSimpleName()
     *                   |
     * </pre>
     *
     * @see #timer(CharSequence)
     */
    public static TimerKey timer(String format, Object arg1, Object arg2) {
        return createTimer(format, arg1, arg2);
    }

    /**
     * Gets the name to use for a class based on whether it appears to be an obfuscated name. The
     * heuristic for an obfuscated name is that it is less than 6 characters in length and consists
     * only of lower case letters.
     */
    private static String getBaseName(Class<?> c) {
        String simpleName = c.getSimpleName();
        if (simpleName.length() < 6) {
            for (int i = 0; i < simpleName.length(); i++) {
                if (!Character.isLowerCase(simpleName.charAt(0))) {
                    return simpleName;
                }
            }
            // Looks like an obfuscated simple class name so use qualified class name
            return c.getName();
        }
        return simpleName;
    }

    /**
     * There are paths where construction of formatted class names are common and the code below is
     * surprisingly expensive, so compute it once and cache it.
     */
    private static final ClassValue<String> formattedClassName = new ClassValue<String>() {
        @Override
        protected String computeValue(Class<?> c) {
            String baseName = getBaseName(c);
            if (Character.isLowerCase(baseName.charAt(0))) {
                // Looks like an obfuscated simple class name so use qualified class name
                baseName = c.getName();
            }
            Class<?> enclosingClass = c.getEnclosingClass();
            if (enclosingClass != null) {
                String prefix = "";
                while (enclosingClass != null) {
                    prefix = getBaseName(enclosingClass) + "_" + prefix;
                    enclosingClass = enclosingClass.getEnclosingClass();
                }
                return prefix + baseName;
            } else {
                return baseName;
            }
        }
    };

    public static Object convertFormatArg(Object arg) {
        if (arg instanceof Class) {
            return formattedClassName.get((Class<?>) arg);
        }
        return arg;
    }

    static String formatDebugName(String format, Object arg1, Object arg2) {
        return String.format(format, convertFormatArg(arg1), convertFormatArg(arg2));
    }

    private static TimerKey createTimer(String format, Object arg1, Object arg2) {
        return new TimerKeyImpl(format, arg1, arg2);
    }

    /**
     * Represents a debug scope entered by {@link DebugContext#scope(Object)} or
     * {@link DebugContext#sandbox(CharSequence, DebugConfig, Object...)}. Leaving the scope is
     * achieved via {@link #close()}.
     */
    public interface Scope extends AutoCloseable {
        /**
         * Gets the names of this scope and its ancestors separated by {@code '.'}.
         */
        String getQualifiedName();

        Iterable<Object> getCurrentContext();

        @Override
        void close();
    }

    boolean isTimerEnabled(TimerKeyImpl key) {
        if (!metricsEnabled) {
            // Pulling this common case out of `isTimerEnabledSlow`
            // gives C1 a better chance to inline this method.
            return false;
        }
        return isTimerEnabledSlow(key);
    }

    private boolean isTimerEnabledSlow(AbstractKey key) {
        if (currentScope != null && currentScope.isTimeEnabled()) {
            return true;
        }
        if (immutable.listMetrics) {
            key.ensureInitialized();
        }
        assert checkNoConcurrentAccess();
        EconomicSet<String> unscoped = immutable.unscopedTimers;
        return unscoped != null && (unscoped.isEmpty() || unscoped.contains(key.getName()));
    }

    /**
     * Determines if a given timer is enabled in the current scope.
     */
    boolean isCounterEnabled(CounterKeyImpl key) {
        if (!metricsEnabled) {
            // Pulling this common case out of `isCounterEnabledSlow`
            // gives C1 a better chance to inline this method.
            return false;
        }
        return isCounterEnabledSlow(key);
    }

    private boolean isCounterEnabledSlow(AbstractKey key) {
        if (currentScope != null && currentScope.isCountEnabled()) {
            return true;
        }
        if (immutable.listMetrics) {
            key.ensureInitialized();
        }
        assert checkNoConcurrentAccess();
        EconomicSet<String> unscoped = immutable.unscopedCounters;
        return unscoped != null && (unscoped.isEmpty() || unscoped.contains(key.getName()));
    }

    boolean isMemUseTrackerEnabled(MemUseTrackerKeyImpl key) {
        if (!metricsEnabled) {
            // Pulling this common case out of `isMemUseTrackerEnabledSlow`
            // gives C1 a better chance to inline this method.
            return false;
        }
        return isMemUseTrackerEnabledSlow(key);
    }

    private boolean isMemUseTrackerEnabledSlow(AbstractKey key) {
        if (currentScope != null && currentScope.isMemUseTrackingEnabled()) {
            return true;
        }
        if (immutable.listMetrics) {
            key.ensureInitialized();
        }
        assert checkNoConcurrentAccess();
        EconomicSet<String> unscoped = immutable.unscopedMemUseTrackers;
        return unscoped != null && (unscoped.isEmpty() || unscoped.contains(key.getName()));
    }

    public boolean areMetricsEnabled() {
        return metricsEnabled;
    }

    @Override
    public void close() {
        closeDumpHandlers(false);
        if (description != null) {
            printMetrics(description);
        }
        if (metricsEnabled && metricValues != null && globalMetrics != null) {
            globalMetrics.add(this);
        }
        metricValues = null;
        if (sharedChannel != null) {
            try {
                sharedChannel.realClose();
            } catch (IOException ex) {
                // ignore.
            }
        }
    }

    public void closeDumpHandlers(boolean ignoreErrors) {
        if (currentConfig != null) {
            currentConfig.closeDumpHandlers(ignoreErrors);
        }
    }

    /**
     * Records how many times a given method has been compiled.
     */
    private static EconomicMap<Integer, Integer> compilations;

    /**
     * Maintains maximum buffer size used by {@link #printMetrics(Description)} to minimize buffer
     * resizing during subsequent calls to this method.
     */
    private static int metricsBufSize = 50_000;

    /**
     * Flag that allows the first call to {@link #printMetrics(Description)} to delete the file that
     * will be appended to.
     */
    private static boolean metricsFileDeleteCheckPerformed;

    /**
     * Prints metric values in this object to the file (if any) specified by
     * {@link DebugOptions#MetricsFile}.
     */
    public void printMetrics(Description desc) {
        if (metricValues == null) {
            return;
        }
        String metricsFile = DebugOptions.MetricsFile.getValue(getOptions());
        if (metricsFile != null) {
            // Use identity to distinguish methods that have been redefined
            // or loaded by different class loaders.
            Object compilable = desc.compilable;
            Integer identity = System.identityHashCode(compilable);
            int compilationNr;
            synchronized (PRINT_METRICS_LOCK) {
                if (!metricsFileDeleteCheckPerformed) {
                    metricsFileDeleteCheckPerformed = true;
                    File file = new File(metricsFile);
                    if (file.exists()) {
                        // This can return false in case something like /dev/stdout
                        // is specified. If the file is unwriteable, the file open
                        // below will fail.
                        file.delete();
                    }
                }
                if (compilations == null) {
                    compilationNr = 0;
                    compilations = EconomicMap.create();
                } else {
                    Integer value = compilations.get(identity);
                    compilationNr = value == null ? 0 : value + 1;
                }
                compilations.put(identity, compilationNr);
            }

            // Release the lock while generating the content to reduce contention.
            // This means `compilationNr` fields may show up out of order in the file.
            ByteArrayOutputStream baos = new ByteArrayOutputStream(metricsBufSize);
            PrintStream out = new PrintStream(baos);
            if (metricsFile.endsWith(".csv") || metricsFile.endsWith(".CSV")) {
                printMetricsCSV(out, compilable, identity, compilationNr, desc.identifier);
            } else {
                printMetrics(out, compilable, identity, compilationNr, desc.identifier);
            }

            byte[] content = baos.toByteArray();
            Path path = Paths.get(metricsFile);
            synchronized (PRINT_METRICS_LOCK) {
                metricsBufSize = Math.max(metricsBufSize, content.length);
                try {
                    Files.write(path, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                }
            }
        }
    }

    /**
     * Lock to serialize writes to {@link DebugOptions#MetricsFile}.
     */
    private static final Object PRINT_METRICS_LOCK = new Object();

    /**
     * Appends metrics in CSV format to {@code out} for a single method compilation.
     *
     * @param identity the identity hash code of {@code compilable}
     * @param compilationNr where this compilation lies in the ordered sequence of all compilations
     *            identified by {@code identity}
     * @param compilationId the runtime issued identifier for the compilation
     */
    private void printMetricsCSV(PrintStream out, Object compilable, Integer identity, int compilationNr, String compilationId) {
        String compilableName = compilable instanceof JavaMethod ? ((JavaMethod) compilable).format("%H.%n(%p)%R") : String.valueOf(compilable);
        String csvFormat = CSVUtil.buildFormatString("%s", "%s", "%d", "%s");
        String format = String.format(csvFormat, CSVUtil.Escape.escapeArgs(compilableName, identity, compilationNr, compilationId));
        char sep = CSVUtil.SEPARATOR;
        format += sep + "%s" + sep + "%s" + sep + "%s";
        for (MetricKey key : KeyRegistry.getKeys()) {
            int index = ((AbstractKey) key).getIndex();
            if (index < metricValues.length) {
                Pair<String, String> valueAndUnit = key.toCSVFormat(metricValues[index]);
                CSVUtil.Escape.println(out, format, CSVUtil.Escape.escape(key.getName()), valueAndUnit.getLeft(), valueAndUnit.getRight());
            }
        }
    }

    /**
     * Appends metrics in a human readable format to {@code out} for a single method compilation.
     *
     * @param identity the identity hash code of {@code compilable}
     * @param compilationNr where this compilation lies in the ordered sequence of all compilations
     *            identified by {@code identity}
     * @param compilationId the runtime issued identifier for the compilation
     */
    private void printMetrics(PrintStream out, Object compilable, Integer identity, int compilationNr, String compilationId) {
        String compilableName = compilable instanceof JavaMethod ? ((JavaMethod) compilable).format("%H.%n(%p)%R") : String.valueOf(compilable);
        int maxKeyWidth = compilableName.length();
        SortedMap<String, String> res = new TreeMap<>();
        for (MetricKey key : KeyRegistry.getKeys()) {
            int index = ((AbstractKey) key).getIndex();
            if (index < metricValues.length && metricValues[index] != 0) {
                String name = key.getName();
                long value = metricValues[index];
                String valueString;
                if (key instanceof TimerKey) {
                    // Report timers in ms
                    TimerKey timer = (TimerKey) key;
                    long ms = timer.getTimeUnit().toMillis(value);
                    if (ms == 0) {
                        continue;
                    }
                    valueString = ms + "ms";
                } else {
                    valueString = String.valueOf(value);
                }
                res.put(name, valueString);
                maxKeyWidth = Math.max(maxKeyWidth, name.length());
            }
        }

        String title = String.format("%s [id:%s compilation:%d compilation_id:%s]", compilableName, identity, compilationNr, compilationId);
        out.println(new String(new char[title.length()]).replace('\0', '#'));
        out.printf("%s%n", title);
        out.println(new String(new char[title.length()]).replace('\0', '~'));

        for (Map.Entry<String, String> e : res.entrySet()) {
            out.printf("%-" + String.valueOf(maxKeyWidth) + "s = %20s%n", e.getKey(), e.getValue());
        }
        out.println();
    }

    public Map<MetricKey, Long> getMetricsSnapshot() {
        Map<MetricKey, Long> res = new HashMap<>();
        for (MetricKey key : KeyRegistry.getKeys()) {
            int index = ((AbstractKey) key).getIndex();
            if (index < metricValues.length && metricValues[index] != 0) {
                long value = metricValues[index];
                res.put(key, value);
            }
        }
        return res;
    }

    @SuppressWarnings({"unused", "unchecked"})
    private static <E extends Exception> E rethrowSilently(Class<E> type, Throwable ex) throws E {
        throw (E) ex;
    }
}

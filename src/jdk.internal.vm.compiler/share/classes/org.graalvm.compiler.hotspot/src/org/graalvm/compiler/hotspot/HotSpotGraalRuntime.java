/*
 * Copyright (c) 2011, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.services.Services;
import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.Equivalence;
import jdk.internal.vm.compiler.collections.UnmodifiableMapCursor;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompilationListenerProfiler;
import org.graalvm.compiler.core.common.CompilerProfiler;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.Assertions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.GlobalMetrics;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.CompilationStatistics.Options;
import org.graalvm.compiler.hotspot.CompilerConfigurationFactory.BackendMap;
import org.graalvm.compiler.hotspot.debug.BenchmarkCounters;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.EnumOptionKey;
import org.graalvm.compiler.options.OptionDescriptor;
import org.graalvm.compiler.options.OptionDescriptors;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.options.OptionsParser;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.serviceprovider.GraalServices;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static jdk.vm.ci.common.InitTimer.timer;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.common.GraalOptions.HotSpotPrintInlining;
import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfigAccess.JDK;

//JaCoCo Exclude

/**
 * Singleton class holding the instance of the {@link GraalRuntime}.
 */
public final class HotSpotGraalRuntime implements HotSpotGraalRuntimeProvider {

    private static final boolean IS_AOT = Boolean.parseBoolean(Services.getSavedProperties().get("com.oracle.graalvm.isaot"));

    /**
     * A factory for a {@link HotSpotGraalManagementRegistration} injected by
     * {@code Target_org_graalvm_compiler_hotspot_HotSpotGraalRuntime}.
     */
    private static final Supplier<HotSpotGraalManagementRegistration> AOT_INJECTED_MANAGEMENT = null;

    private static boolean checkArrayIndexScaleInvariants(MetaAccessProvider metaAccess) {
        assert metaAccess.getArrayIndexScale(JavaKind.Byte) == 1;
        assert metaAccess.getArrayIndexScale(JavaKind.Boolean) == 1;
        assert metaAccess.getArrayIndexScale(JavaKind.Char) == 2;
        assert metaAccess.getArrayIndexScale(JavaKind.Short) == 2;
        assert metaAccess.getArrayIndexScale(JavaKind.Int) == 4;
        assert metaAccess.getArrayIndexScale(JavaKind.Long) == 8;
        assert metaAccess.getArrayIndexScale(JavaKind.Float) == 4;
        assert metaAccess.getArrayIndexScale(JavaKind.Double) == 8;
        return true;
    }

    private final String runtimeName;
    private final String compilerConfigurationName;
    private final HotSpotBackend hostBackend;

    public GlobalMetrics getMetricValues() {
        return metricValues;
    }

    private final GlobalMetrics metricValues = new GlobalMetrics();
    private final List<SnippetCounter.Group> snippetCounterGroups;
    private final HotSpotGC garbageCollector;

    private final EconomicMap<Class<? extends Architecture>, HotSpotBackend> backends = EconomicMap.create(Equivalence.IDENTITY);

    private final GraalHotSpotVMConfig config;

    private final Instrumentation instrumentation;

    /**
     * The options can be {@linkplain #setOptionValues(String[], String[]) updated} by external
     * interfaces such as JMX. This comes with the risk that inconsistencies can arise as an
     * {@link OptionValues} object can be cached by various parts of Graal instead of always
     * obtaining them from this object. However, concurrent updates are never lost.
     */
    private AtomicReference<OptionValues> optionsRef = new AtomicReference<>();

    private final DiagnosticsOutputDirectory outputDirectory;
    private final Map<ExceptionAction, Integer> compilationProblemsPerAction;

    private final CompilerProfiler compilerProfiler;

    /**
     * @param nameQualifier a qualifier to be added to this runtime's {@linkplain #getName() name}
     * @param compilerConfigurationFactory factory for the compiler configuration
     *            {@link CompilerConfigurationFactory#selectFactory(String, OptionValues)}
     */
    @SuppressWarnings("try")
    HotSpotGraalRuntime(String nameQualifier, HotSpotJVMCIRuntime jvmciRuntime, CompilerConfigurationFactory compilerConfigurationFactory, OptionValues initialOptions) {
        this.runtimeName = getClass().getSimpleName() + ":" + nameQualifier;
        HotSpotVMConfigStore store = jvmciRuntime.getConfigStore();
        config = GeneratePIC.getValue(initialOptions) ? new AOTGraalHotSpotVMConfig(store) : new GraalHotSpotVMConfig(store);

        // Only set HotSpotPrintInlining if it still has its default value (false).
        if (GraalOptions.HotSpotPrintInlining.getValue(initialOptions) == false && config.printInlining) {
            optionsRef.set(new OptionValues(initialOptions, HotSpotPrintInlining, true));
        } else {
            optionsRef.set(initialOptions);
        }
        OptionValues options = optionsRef.get();

        garbageCollector = getSelectedGC();

        outputDirectory = new DiagnosticsOutputDirectory(options);
        compilationProblemsPerAction = new EnumMap<>(ExceptionAction.class);
        snippetCounterGroups = GraalOptions.SnippetCounters.getValue(options) ? new ArrayList<>() : null;
        CompilerConfiguration compilerConfiguration = compilerConfigurationFactory.createCompilerConfiguration();
        compilerConfigurationName = compilerConfigurationFactory.getName();

        this.instrumentation = compilerConfigurationFactory.createInstrumentation(options);

        if (IS_AOT) {
            management = AOT_INJECTED_MANAGEMENT == null ? null : AOT_INJECTED_MANAGEMENT.get();
        } else {
            management = GraalServices.loadSingle(HotSpotGraalManagementRegistration.class, false);
        }
        if (management != null) {
            try {
                management.initialize(this, config);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable error) {
                TTY.println("Cannot install GraalVM MBean due to " + error.getMessage());
                management = null;
            }
        }

        BackendMap backendMap = compilerConfigurationFactory.createBackendMap();

        JVMCIBackend hostJvmciBackend = jvmciRuntime.getHostJVMCIBackend();
        Architecture hostArchitecture = hostJvmciBackend.getTarget().arch;
        try (InitTimer t = timer("create backend:", hostArchitecture)) {
            HotSpotBackendFactory factory = backendMap.getBackendFactory(hostArchitecture);
            if (factory == null) {
                throw new GraalError("No backend available for host architecture \"%s\"", hostArchitecture);
            }
            hostBackend = registerBackend(factory.createBackend(this, compilerConfiguration, jvmciRuntime, null));
        }

        for (JVMCIBackend jvmciBackend : jvmciRuntime.getJVMCIBackends().values()) {
            if (jvmciBackend == hostJvmciBackend) {
                continue;
            }

            Architecture gpuArchitecture = jvmciBackend.getTarget().arch;
            HotSpotBackendFactory factory = backendMap.getBackendFactory(gpuArchitecture);
            if (factory == null) {
                throw new GraalError("No backend available for specified GPU architecture \"%s\"", gpuArchitecture);
            }
            try (InitTimer t = timer("create backend:", gpuArchitecture)) {
                registerBackend(factory.createBackend(this, compilerConfiguration, null, hostBackend));
            }
        }

        // Complete initialization of backends
        try (InitTimer st = timer(hostBackend.getTarget().arch.getName(), ".completeInitialization")) {
            hostBackend.completeInitialization(jvmciRuntime, options);
        }
        for (HotSpotBackend backend : backends.getValues()) {
            if (backend != hostBackend) {
                try (InitTimer st = timer(backend.getTarget().arch.getName(), ".completeInitialization")) {
                    backend.completeInitialization(jvmciRuntime, options);
                }
            }
        }

        BenchmarkCounters.initialize(jvmciRuntime, options);

        assert checkArrayIndexScaleInvariants(hostJvmciBackend.getMetaAccess());

        runtimeStartTime = System.nanoTime();
        bootstrapJVMCI = config.getFlag("BootstrapJVMCI", Boolean.class);

        this.compilerProfiler = GraalServices.loadSingle(CompilerProfiler.class, false);
    }

    /**
     * Constants denoting the GC algorithms available in HotSpot. The names of the constants match
     * the constants in the {@code CollectedHeap::Name} C++ enum.
     */
    public enum HotSpotGC {
        // Supported GCs
        Serial(true, JDK >= 11, "UseSerialGC", true),
        Parallel(true, JDK >= 11, "UseParallelGC", true, "UseParallelOldGC", JDK < 15, "UseParNewGC", JDK < 10),
        CMS(true, JDK >= 11 && JDK <= 14, "UseConcMarkSweepGC", JDK < 14),
        G1(true, JDK >= 11, "UseG1GC", true),

        // Unsupported GCs
        Epsilon(false, JDK >= 11, "UseEpsilonGC", JDK >= 11),
        Z(false, JDK >= 11, "UseZGC", JDK >= 11),
        Shenandoah(false, JDK >= 12, "UseShenandoahGC", JDK >= 12);

        HotSpotGC(boolean supported, boolean expectNamePresent,
                        String flag1, boolean expectFlagPresent1,
                        String flag2, boolean expectFlagPresent2,
                        String flag3, boolean expectFlagPresent3) {
            this.supported = supported;
            this.expectNamePresent = expectNamePresent;
            this.expectFlagsPresent = new boolean[]{expectFlagPresent1, expectFlagPresent2, expectFlagPresent3};
            this.flags = new String[]{flag1, flag2, flag3};
        }

        HotSpotGC(boolean supported, boolean expectNamePresent, String flag, boolean expectFlagPresent) {
            this.supported = supported;
            this.expectNamePresent = expectNamePresent;
            this.expectFlagsPresent = new boolean[]{expectFlagPresent};
            this.flags = new String[]{flag};
        }

        /**
         * Specifies if this GC supported by Graal.
         */
        final boolean supported;

        /**
         * Specifies if {@link #name()} is expected to be present in the {@code CollectedHeap::Name}
         * C++ enum.
         */
        final boolean expectNamePresent;

        /**
         * The VM flags that will select this GC.
         */
        private final String[] flags;

        /**
         * Specifies which {@link #flags} are expected to be present in the VM.
         */
        final boolean[] expectFlagsPresent;

        public boolean isSelected(GraalHotSpotVMConfig config) {
            boolean selected = false;
            for (int i = 0; i < flags.length; i++) {
                final boolean notPresent = false;
                if (config.getFlag(flags[i], Boolean.class, notPresent, expectFlagsPresent[i])) {
                    selected = true;
                    if (!Assertions.assertionsEnabled()) {
                        // When asserting, check that isSelected works for all flag names
                        break;
                    }
                }
            }
            return selected;
        }

        /**
         * Gets the GC matching {@code name}.
         *
         * @param name the ordinal of a {@code CollectedHeap::Name} value
         */
        static HotSpotGC forName(int name, GraalHotSpotVMConfig config) {
            for (HotSpotGC gc : HotSpotGC.values()) {
                if (config.getConstant("CollectedHeap::" + gc.name(), Integer.class, -1, gc.expectNamePresent) == name) {
                    return gc;
                }
            }
            return null;
        }
    }

    private HotSpotGC getSelectedGC() throws GraalError {
        HotSpotGC selected = null;
        for (HotSpotGC gc : HotSpotGC.values()) {
            if (gc.isSelected(config)) {
                if (!gc.supported) {
                    throw new GraalError(gc.name() + " garbage collector is not supported by Graal");
                }
                selected = gc;
                if (!Assertions.assertionsEnabled()) {
                    // When asserting, check that isSelected works for all HotSpotGC values
                    break;
                }
            }
        }
        if (selected == null) {
            // As of JDK 9, exactly one GC flag is guaranteed to be selected.
            // On JDK 8, the default GC is Serial when no GC flag is true.
            selected = HotSpotGC.Serial;
        }
        return selected;
    }

    private HotSpotBackend registerBackend(HotSpotBackend backend) {
        Class<? extends Architecture> arch = backend.getTarget().arch.getClass();
        HotSpotBackend oldValue = backends.put(arch, backend);
        assert oldValue == null : "cannot overwrite existing backend for architecture " + arch.getSimpleName();
        return backend;
    }

    @Override
    public HotSpotProviders getHostProviders() {
        return getHostBackend().getProviders();
    }

    @Override
    public GraalHotSpotVMConfig getVMConfig() {
        return config;
    }

    @Override
    public DebugContext openDebugContext(OptionValues compilationOptions, CompilationIdentifier compilationId, Object compilable, Iterable<DebugHandlersFactory> factories, PrintStream logStream) {
        if (management != null && management.poll(false) != null) {
            if (compilable instanceof HotSpotResolvedJavaMethod) {
                HotSpotResolvedObjectType type = ((HotSpotResolvedJavaMethod) compilable).getDeclaringClass();
                if (type instanceof HotSpotResolvedJavaType) {
                    Class<?> clazz = runtime().getMirror(type);
                    if (clazz != null) {
                        try {
                            ClassLoader cl = clazz.getClassLoader();
                            if (cl != null) {
                                loaders.add(cl);
                            }
                        } catch (SecurityException e) {
                            // This loader can obviously not be used for resolving class names
                        }
                    }
                }
            }
        }

        Description description = new Description(compilable, compilationId.toString(CompilationIdentifier.Verbosity.ID));
        Builder builder = new Builder(compilationOptions, factories).//
                        globalMetrics(metricValues).//
                        description(description).//
                        logStream(logStream);
        if (compilerProfiler != null) {
            int compileId = ((HotSpotCompilationIdentifier) compilationId).getRequest().getId();
            builder.compilationListener(new CompilationListenerProfiler(compilerProfiler, compileId));
        }
        return builder.build();

    }

    @Override
    public OptionValues getOptions() {
        return optionsRef.get();
    }

    @Override
    public Group createSnippetCounterGroup(String groupName) {
        if (snippetCounterGroups != null) {
            Group group = new Group(groupName);
            snippetCounterGroups.add(group);
            return group;
        }
        return null;
    }

    @Override
    public String getName() {
        return runtimeName;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if (clazz == RuntimeProvider.class) {
            return (T) this;
        } else if (clazz == OptionValues.class) {
            return (T) optionsRef.get();
        } else if (clazz == StackIntrospection.class) {
            return (T) this;
        } else if (clazz == SnippetReflectionProvider.class) {
            return (T) getHostProviders().getSnippetReflection();
        } else if (clazz == GraalHotSpotVMConfig.class) {
            return (T) getVMConfig();
        } else if (clazz == StampProvider.class) {
            return (T) getHostProviders().getStampProvider();
        } else if (ForeignCallsProvider.class.isAssignableFrom(clazz)) {
            return (T) getHostProviders().getForeignCalls();
        }
        return null;
    }

    @Override
    public HotSpotGC getGarbageCollector() {
        return garbageCollector;
    }

    @Override
    public HotSpotBackend getHostBackend() {
        return hostBackend;
    }

    @Override
    public <T extends Architecture> Backend getBackend(Class<T> arch) {
        assert arch != Architecture.class;
        return backends.get(arch);
    }

    @Override
    public String getCompilerConfigurationName() {
        return compilerConfigurationName;
    }

    @Override
    public Instrumentation getInstrumentation() {
        return instrumentation;
    }

    private long runtimeStartTime;

    /**
     * Called from compiler threads to check whether to bail out of a compilation.
     */
    private volatile boolean shutdown;

    /**
     * Shutdown hooks that should be run on the same thread doing the shutdown.
     */
    private List<Runnable> shutdownHooks = new ArrayList<>();

    /**
     * Take action related to entering a new execution phase.
     *
     * @param phase the execution phase being entered
     */
    void phaseTransition(String phase) {
        if (Options.UseCompilationStatistics.getValue(optionsRef.get())) {
            CompilationStatistics.clear(phase);
        }
    }

    /**
     * Adds a {@link Runnable} that will be run when this runtime is {@link #shutdown()}. The
     * runnable will be run on the same thread doing the shutdown. All the advice for regular
     * {@linkplain Runtime#addShutdownHook(Thread) shutdown hooks} also applies here but even more
     * so since the hook runs on the shutdown thread.
     */
    public synchronized void addShutdownHook(Runnable hook) {
        if (!shutdown) {
            shutdownHooks.add(hook);
        }
    }

    synchronized void shutdown() {
        shutdown = true;

        for (Runnable r : shutdownHooks) {
            try {
                r.run();
            } catch (Throwable e) {
                e.printStackTrace(TTY.out);
            }
        }

        metricValues.print(optionsRef.get());

        phaseTransition("final");

        if (snippetCounterGroups != null) {
            for (Group group : snippetCounterGroups) {
                TTY.out().out().println(group);
            }
        }
        BenchmarkCounters.shutdown(runtime(), optionsRef.get(), runtimeStartTime);

        outputDirectory.close();

        shutdownLibGraal();
    }

    /**
     * Substituted by
     * {@code com.oracle.svm.graal.hotspot.libgraal.Target_org_graalvm_compiler_hotspot_HotSpotGraalRuntime}
     * to call {@code jdk.internal.vm.compiler.nativeimage.VMRuntime.shutdown()}.
     */
    private static void shutdownLibGraal() {
    }

    void clearMetrics() {
        metricValues.clear();
    }

    private final boolean bootstrapJVMCI;
    private boolean bootstrapFinished;

    public void notifyBootstrapFinished() {
        bootstrapFinished = true;
    }

    @Override
    public boolean isBootstrapping() {
        return bootstrapJVMCI && !bootstrapFinished;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public DiagnosticsOutputDirectory getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    public Map<ExceptionAction, Integer> getCompilationProblemsPerAction() {
        return compilationProblemsPerAction;
    }

    // ------- Management interface ---------

    private HotSpotGraalManagementRegistration management;

    /**
     * @returns the management object for this runtime or {@code null}
     */
    public HotSpotGraalManagementRegistration getManagement() {
        return management;
    }

    /**
     * Set of weak references to {@link ClassLoader}s available for resolving class names present in
     * management {@linkplain #invokeManagementAction(String, Object[]) action} arguments.
     */
    private final WeakClassLoaderSet loaders = new WeakClassLoaderSet(ClassLoader.getSystemClassLoader());

    /**
     * Sets or updates this object's {@linkplain #getOptions() options} from {@code names} and
     * {@code values}.
     *
     * @param values the values to set. The empty string represents {@code null} which resets an
     *            option to its default value. For string type options, a non-empty value must be
     *            enclosed in double quotes.
     * @return an array of Strings where the element at index i is {@code names[i]} if setting the
     *         denoted option succeeded, {@code null} if the option is unknown otherwise an error
     *         message describing the failure to set the option
     */
    public String[] setOptionValues(String[] names, String[] values) {
        EconomicMap<String, OptionDescriptor> optionDescriptors = getOptionDescriptors();
        EconomicMap<OptionKey<?>, Object> newValues = EconomicMap.create(names.length);
        EconomicSet<OptionKey<?>> resetValues = EconomicSet.create(names.length);
        String[] result = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            OptionDescriptor option = optionDescriptors.get(name);
            if (option != null) {
                String svalue = values[i];
                Class<?> optionValueType = option.getOptionValueType();
                OptionKey<?> optionKey = option.getOptionKey();
                if (svalue == null || svalue.isEmpty() && !(optionKey instanceof EnumOptionKey)) {
                    resetValues.add(optionKey);
                    result[i] = name;
                } else {
                    String valueToParse;
                    if (optionValueType == String.class) {
                        if (svalue.length() < 2 || svalue.charAt(0) != '"' || svalue.charAt(svalue.length() - 1) != '"') {
                            result[i] = "Invalid value for String option '" + name + "': must be the empty string or be enclosed in double quotes: " + svalue;
                            continue;
                        } else {
                            valueToParse = svalue.substring(1, svalue.length() - 1);
                        }
                    } else {
                        valueToParse = svalue;
                    }
                    try {
                        OptionsParser.parseOption(name, valueToParse, newValues, OptionsParser.getOptionsLoader());
                        result[i] = name;
                    } catch (IllegalArgumentException e) {
                        result[i] = e.getMessage();
                        continue;
                    }
                }
            } else {
                result[i] = null;
            }
        }

        OptionValues currentOptions;
        OptionValues newOptions;
        do {
            currentOptions = optionsRef.get();
            UnmodifiableMapCursor<OptionKey<?>, Object> cursor = currentOptions.getMap().getEntries();
            while (cursor.advance()) {
                OptionKey<?> key = cursor.getKey();
                if (!resetValues.contains(key) && !newValues.containsKey(key)) {
                    newValues.put(key, OptionValues.decodeNull(cursor.getValue()));
                }
            }
            newOptions = new OptionValues(newValues);
        } while (!optionsRef.compareAndSet(currentOptions, newOptions));

        return result;
    }

    /**
     * Gets the values for the options corresponding to {@code names} encoded as strings. The empty
     * string represents {@code null}. For string type options, non-{@code null} values will be
     * enclosed in double quotes.
     *
     * @param names a list of option names
     * @return the values for each named option. If an element in {@code names} does not denote an
     *         existing option, the corresponding element in the returned array will be {@code null}
     */
    public String[] getOptionValues(String... names) {
        String[] values = new String[names.length];
        EconomicMap<String, OptionDescriptor> optionDescriptors = getOptionDescriptors();
        for (int i = 0; i < names.length; i++) {
            OptionDescriptor option = optionDescriptors.get(names[i]);
            if (option != null) {
                OptionKey<?> optionKey = option.getOptionKey();
                Object value = optionKey.getValue(getOptions());
                String svalue;
                if (option.getOptionValueType() == String.class && value != null) {
                    svalue = "\"" + value + "\"";
                } else if (value == null) {
                    svalue = "";
                } else {
                    svalue = String.valueOf(value);
                }
                values[i] = svalue;
            } else {
                // null denotes the option does not exist
                values[i] = null;
            }
        }
        return values;
    }

    private static EconomicMap<String, OptionDescriptor> getOptionDescriptors() {
        EconomicMap<String, OptionDescriptor> result = EconomicMap.create();
        for (OptionDescriptors set : OptionsParser.getOptionsLoader()) {
            for (OptionDescriptor option : set) {
                result.put(option.getName(), option);
            }
        }
        return result;
    }

    private void dumpMethod(String className, String methodName, String filter, String host, int port) throws Exception {
        EconomicSet<ClassNotFoundException> failures = EconomicSet.create();
        EconomicSet<Class<?>> found = loaders.resolve(className, failures);
        if (found.isEmpty()) {
            ClassNotFoundException cause = failures.isEmpty() ? new ClassNotFoundException(className) : failures.iterator().next();
            throw new Exception("Cannot find class " + className + " to schedule recompilation", cause);
        }
        for (Class<?> clazz : found) {
            ResolvedJavaType type = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(clazz);
            for (ResolvedJavaMethod method : type.getDeclaredMethods()) {
                if (methodName.equals(method.getName()) && method instanceof HotSpotResolvedJavaMethod) {
                    HotSpotResolvedJavaMethod hotSpotMethod = (HotSpotResolvedJavaMethod) method;
                    dumpMethod(hotSpotMethod, filter, host, port);
                }
            }
        }
    }

    private void dumpMethod(HotSpotResolvedJavaMethod hotSpotMethod, String filter, String host, int port) throws Exception {
        EconomicMap<OptionKey<?>, Object> extra = EconomicMap.create();
        extra.put(DebugOptions.Dump, filter);
        extra.put(DebugOptions.PrintGraphHost, host);
        extra.put(DebugOptions.PrintGraphPort, port);
        OptionValues compileOptions = new OptionValues(getOptions(), extra);
        HotSpotGraalCompiler compiler = (HotSpotGraalCompiler) runtime().getCompiler();
        compiler.compileMethod(new HotSpotCompilationRequest(hotSpotMethod, -1, 0L), false, compileOptions);
    }

    public Object invokeManagementAction(String actionName, Object[] params) throws Exception {
        if ("dumpMethod".equals(actionName)) {
            if (params.length != 0 && params[0] instanceof HotSpotResolvedJavaMethod) {
                HotSpotResolvedJavaMethod method = param(params, 0, "method", HotSpotResolvedJavaMethod.class, null);
                String filter = param(params, 1, "filter", String.class, ":3");
                String host = param(params, 2, "host", String.class, "localhost");
                Number port = param(params, 3, "port", Number.class, 4445);
                dumpMethod(method, filter, host, port.intValue());
            } else {
                String className = param(params, 0, "className", String.class, null);
                String methodName = param(params, 1, "methodName", String.class, null);
                String filter = param(params, 2, "filter", String.class, ":3");
                String host = param(params, 3, "host", String.class, "localhost");
                Number port = param(params, 4, "port", Number.class, 4445);
                dumpMethod(className, methodName, filter, host, port.intValue());
            }
        }
        return null;
    }

    private static <T> T param(Object[] arr, int index, String name, Class<T> type, T defaultValue) {
        Object value = arr.length > index ? arr[index] : null;
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            if (defaultValue == null) {
                throw new IllegalArgumentException(name + " must be specified");
            }
            value = defaultValue;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        throw new IllegalArgumentException("Expecting " + type.getName() + " for " + name + " but was " + value);
    }
}

/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.common.InitTimer.timer;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayIndexScale;
import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.common.GraalOptions.HotSpotPrintInlining;
import static org.graalvm.compiler.debug.DebugContext.DEFAULT_LOG_STREAM;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.GlobalMetrics;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.CompilationStatistics.Options;
import org.graalvm.compiler.hotspot.CompilerConfigurationFactory.BackendMap;
import org.graalvm.compiler.hotspot.debug.BenchmarkCounters;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetCounter.Group;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.util.EconomicMap;
import org.graalvm.util.Equivalence;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCIBackend;

//JaCoCo Exclude

/**
 * Singleton class holding the instance of the {@link GraalRuntime}.
 */
public final class HotSpotGraalRuntime implements HotSpotGraalRuntimeProvider {

    private static boolean checkArrayIndexScaleInvariants() {
        assert getArrayIndexScale(JavaKind.Byte) == 1;
        assert getArrayIndexScale(JavaKind.Boolean) == 1;
        assert getArrayIndexScale(JavaKind.Char) == 2;
        assert getArrayIndexScale(JavaKind.Short) == 2;
        assert getArrayIndexScale(JavaKind.Int) == 4;
        assert getArrayIndexScale(JavaKind.Long) == 8;
        assert getArrayIndexScale(JavaKind.Float) == 4;
        assert getArrayIndexScale(JavaKind.Double) == 8;
        return true;
    }

    private final HotSpotBackend hostBackend;
    private final GlobalMetrics metricValues = new GlobalMetrics();
    private final List<SnippetCounter.Group> snippetCounterGroups;

    private final EconomicMap<Class<? extends Architecture>, HotSpotBackend> backends = EconomicMap.create(Equivalence.IDENTITY);

    private final GraalHotSpotVMConfig config;

    private final OptionValues options;
    private final DiagnosticsOutputDirectory outputDirectory;
    private final Map<ExceptionAction, Integer> compilationProblemsPerAction;
    private final HotSpotGraalMBean mBean;

    /**
     * @param compilerConfigurationFactory factory for the compiler configuration
     *            {@link CompilerConfigurationFactory#selectFactory(String, OptionValues)}
     */
    @SuppressWarnings("try")
    HotSpotGraalRuntime(HotSpotJVMCIRuntime jvmciRuntime, CompilerConfigurationFactory compilerConfigurationFactory, OptionValues initialOptions) {
        HotSpotVMConfigStore store = jvmciRuntime.getConfigStore();
        config = GeneratePIC.getValue(initialOptions) ? new AOTGraalHotSpotVMConfig(store) : new GraalHotSpotVMConfig(store);

        // Only set HotSpotPrintInlining if it still has its default value (false).
        if (GraalOptions.HotSpotPrintInlining.getValue(initialOptions) == false && config.printInlining) {
            options = new OptionValues(initialOptions, HotSpotPrintInlining, true);
        } else {
            options = initialOptions;
        }

        outputDirectory = new DiagnosticsOutputDirectory(options);
        compilationProblemsPerAction = new EnumMap<>(ExceptionAction.class);
        snippetCounterGroups = GraalOptions.SnippetCounters.getValue(options) ? new ArrayList<>() : null;
        CompilerConfiguration compilerConfiguration = compilerConfigurationFactory.createCompilerConfiguration();

        HotSpotGraalCompiler compiler = new HotSpotGraalCompiler(jvmciRuntime, this, initialOptions);
        this.mBean = createHotSpotGraalMBean(compiler);

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

        assert checkArrayIndexScaleInvariants();

        runtimeStartTime = System.nanoTime();
        bootstrapJVMCI = config.getFlag("BootstrapJVMCI", Boolean.class);
    }

    private static HotSpotGraalMBean createHotSpotGraalMBean(HotSpotGraalCompiler compiler) {
        try {
            return HotSpotGraalMBean.create(compiler);
        } catch (LinkageError ex) {
            return null;
        }
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
    public DebugContext openDebugContext(OptionValues compilationOptions, CompilationIdentifier compilationId, Object compilable, Iterable<DebugHandlersFactory> factories) {
        Description description = new Description(compilable, compilationId.toString(CompilationIdentifier.Verbosity.ID));
        return DebugContext.create(compilationOptions, description, metricValues, DEFAULT_LOG_STREAM, factories);
    }

    @Override
    public OptionValues getOptions() {
        return mBean == null ? options : mBean.optionsFor(options, null);
    }

    @Override
    public OptionValues getOptions(ResolvedJavaMethod forMethod) {
        return mBean == null ? options : mBean.optionsFor(options, forMethod);
    }

    @Override
    public Group createSnippetCounterGroup(String name) {
        if (snippetCounterGroups != null) {
            Group group = new Group(name);
            snippetCounterGroups.add(group);
            return group;
        }
        return null;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if (clazz == RuntimeProvider.class) {
            return (T) this;
        } else if (clazz == OptionValues.class) {
            return (T) options;
        } else if (clazz == StackIntrospection.class) {
            return (T) this;
        } else if (clazz == SnippetReflectionProvider.class) {
            return (T) getHostProviders().getSnippetReflection();
        } else if (clazz == StampProvider.class) {
            return (T) getHostProviders().getStampProvider();
        }
        return null;
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

    private long runtimeStartTime;
    private boolean shutdown;

    /**
     * Take action related to entering a new execution phase.
     *
     * @param phase the execution phase being entered
     */
    void phaseTransition(String phase) {
        if (Options.UseCompilationStatistics.getValue(options)) {
            CompilationStatistics.clear(phase);
        }
    }

    void shutdown() {
        shutdown = true;
        metricValues.print(options);

        phaseTransition("final");

        if (snippetCounterGroups != null) {
            for (Group group : snippetCounterGroups) {
                TTY.out().out().println(group);
            }
        }
        BenchmarkCounters.shutdown(runtime(), options, runtimeStartTime);

        outputDirectory.close();
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

    Object getMBean() {
        return mBean;
    }
}

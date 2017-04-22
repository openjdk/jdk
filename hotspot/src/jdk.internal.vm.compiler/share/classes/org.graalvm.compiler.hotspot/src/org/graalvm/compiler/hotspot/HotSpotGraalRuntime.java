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

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.debug.GraalDebugConfig.areScopedGlobalMetricsEnabled;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.DebugValueSummary;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Dump;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Log;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.MethodFilter;
import static org.graalvm.compiler.debug.GraalDebugConfig.Options.Verify;
import static jdk.vm.ci.common.InitTimer.timer;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntime.runtime;
import static jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider.getArrayIndexScale;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.graalvm.compiler.api.collections.CollectionsProvider;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.target.Backend;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.DebugEnvironment;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.debug.internal.DebugValuesPrinter;
import org.graalvm.compiler.debug.internal.method.MethodMetricsPrinter;
import org.graalvm.compiler.graph.DefaultNodeCollectionsProvider;
import org.graalvm.compiler.graph.NodeCollectionsProvider;
import org.graalvm.compiler.hotspot.CompilerConfigurationFactory.BackendMap;
import org.graalvm.compiler.hotspot.debug.BenchmarkCounters;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.phases.tiers.CompilerConfiguration;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.runtime.RuntimeProvider;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.stack.StackIntrospection;
import jdk.vm.ci.common.InitTimer;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.meta.JavaKind;
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
    private DebugValuesPrinter debugValuesPrinter;

    private final Map<Class<? extends Architecture>, HotSpotBackend> backends = new HashMap<>();

    private final GraalHotSpotVMConfig config;

    /**
     * @param compilerConfigurationFactory factory for the compiler configuration
     *            {@link CompilerConfigurationFactory#selectFactory(String)}
     */
    @SuppressWarnings("try")
    HotSpotGraalRuntime(HotSpotJVMCIRuntime jvmciRuntime, CompilerConfigurationFactory compilerConfigurationFactory) {

        HotSpotVMConfigStore store = jvmciRuntime.getConfigStore();
        config = GeneratePIC.getValue() ? new AOTGraalHotSpotVMConfig(store) : new GraalHotSpotVMConfig(store);
        CompileTheWorldOptions.overrideWithNativeOptions(config);

        // Only set HotSpotPrintInlining if it still has its default value (false).
        if (GraalOptions.HotSpotPrintInlining.getValue() == false) {
            GraalOptions.HotSpotPrintInlining.setValue(config.printInlining);
        }

        CompilerConfiguration compilerConfiguration = compilerConfigurationFactory.createCompilerConfiguration();
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

        if (Log.getValue() == null && !areScopedGlobalMetricsEnabled() && Dump.getValue() == null && Verify.getValue() == null) {
            if (MethodFilter.getValue() != null && !Debug.isEnabled()) {
                TTY.println("WARNING: Ignoring MethodFilter option since Log, Meter, Time, TrackMemUse, Dump and Verify options are all null");
            }
        }

        if (Debug.isEnabled()) {
            DebugEnvironment.initialize(TTY.out, hostBackend.getProviders().getSnippetReflection());

            String summary = DebugValueSummary.getValue();
            if (summary != null) {
                switch (summary) {
                    case "Name":
                    case "Partial":
                    case "Complete":
                    case "Thread":
                        break;
                    default:
                        throw new GraalError("Unsupported value for DebugSummaryValue: %s", summary);
                }
            }
        }

        if (Debug.areUnconditionalCountersEnabled() || Debug.areUnconditionalTimersEnabled() || Debug.areUnconditionalMethodMetricsEnabled() ||
                        (Debug.isEnabled() && areScopedGlobalMetricsEnabled()) || (Debug.isEnabled() && Debug.isMethodFilteringEnabled())) {
            // This must be created here to avoid loading the DebugValuesPrinter class
            // during shutdown() which in turn can cause a deadlock
            int mmPrinterType = 0;
            mmPrinterType |= MethodMetricsPrinter.Options.MethodMeterPrintAscii.getValue() ? 1 : 0;
            mmPrinterType |= MethodMetricsPrinter.Options.MethodMeterFile.getValue() != null ? 2 : 0;
            switch (mmPrinterType) {
                case 0:
                    debugValuesPrinter = new DebugValuesPrinter();
                    break;
                case 1:
                    debugValuesPrinter = new DebugValuesPrinter(new MethodMetricsPrinter.MethodMetricsASCIIPrinter(TTY.out));
                    break;
                case 2:
                    debugValuesPrinter = new DebugValuesPrinter(new MethodMetricsPrinter.MethodMetricsCSVFilePrinter());
                    break;
                case 3:
                    debugValuesPrinter = new DebugValuesPrinter(
                                    new MethodMetricsPrinter.MethodMetricsCompositePrinter(new MethodMetricsPrinter.MethodMetricsCSVFilePrinter(),
                                                    new MethodMetricsPrinter.MethodMetricsASCIIPrinter(TTY.out)));
                    break;
                default:
                    break;
            }
        }

        // Complete initialization of backends
        try (InitTimer st = timer(hostBackend.getTarget().arch.getName(), ".completeInitialization")) {
            hostBackend.completeInitialization(jvmciRuntime);
        }
        for (HotSpotBackend backend : backends.values()) {
            if (backend != hostBackend) {
                try (InitTimer st = timer(backend.getTarget().arch.getName(), ".completeInitialization")) {
                    backend.completeInitialization(jvmciRuntime);
                }
            }
        }

        BenchmarkCounters.initialize(jvmciRuntime);

        assert checkArrayIndexScaleInvariants();

        runtimeStartTime = System.nanoTime();
        bootstrapJVMCI = config.getFlag("BootstrapJVMCI", Boolean.class);
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
    public String getName() {
        return getClass().getSimpleName();
    }

    private final NodeCollectionsProvider nodeCollectionsProvider = new DefaultNodeCollectionsProvider();

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getCapability(Class<T> clazz) {
        if (clazz == RuntimeProvider.class) {
            return (T) this;
        } else if (clazz == CollectionsProvider.class || clazz == NodeCollectionsProvider.class) {
            return (T) nodeCollectionsProvider;
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

    public Map<Class<? extends Architecture>, HotSpotBackend> getBackends() {
        return Collections.unmodifiableMap(backends);
    }

    private long runtimeStartTime;

    /**
     * Take action related to entering a new execution phase.
     *
     * @param phase the execution phase being entered
     */
    static void phaseTransition(String phase) {
        CompilationStatistics.clear(phase);
    }

    void shutdown() {
        if (debugValuesPrinter != null) {
            debugValuesPrinter.printDebugValues();
        }
        phaseTransition("final");

        SnippetCounter.printGroups(TTY.out().out());
        BenchmarkCounters.shutdown(runtime(), runtimeStartTime);
    }

    void clearMeters() {
        if (debugValuesPrinter != null) {
            debugValuesPrinter.clearDebugValues();
        }
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
}

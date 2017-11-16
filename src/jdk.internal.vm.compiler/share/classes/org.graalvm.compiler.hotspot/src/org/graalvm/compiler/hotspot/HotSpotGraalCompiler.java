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
package org.graalvm.compiler.hotspot;

import static org.graalvm.compiler.core.common.GraalOptions.OptAssumptions;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.ROOT_COMPILATION;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collections;
import java.util.Formattable;
import java.util.Formatter;
import java.util.List;

import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Activation;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.hotspot.CompilationCounters.Options;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.phases.OnStackReplacementPhase;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.OptimisticOptimizations.Optimization;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;

import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotCompilationRequest;
import jdk.vm.ci.hotspot.HotSpotCompilationRequestResult;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntimeProvider;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.runtime.JVMCICompiler;

public class HotSpotGraalCompiler implements GraalJVMCICompiler {

    private final HotSpotJVMCIRuntimeProvider jvmciRuntime;
    private final HotSpotGraalRuntimeProvider graalRuntime;
    private final CompilationCounters compilationCounters;
    private final BootstrapWatchDog bootstrapWatchDog;
    private List<DebugHandlersFactory> factories;

    HotSpotGraalCompiler(HotSpotJVMCIRuntimeProvider jvmciRuntime, HotSpotGraalRuntimeProvider graalRuntime, OptionValues options) {
        this.jvmciRuntime = jvmciRuntime;
        this.graalRuntime = graalRuntime;
        // It is sufficient to have one compilation counter object per Graal compiler object.
        this.compilationCounters = Options.CompilationCountLimit.getValue(options) > 0 ? new CompilationCounters(options) : null;
        this.bootstrapWatchDog = graalRuntime.isBootstrapping() && !DebugOptions.BootstrapInitializeOnly.getValue(options) ? BootstrapWatchDog.maybeCreate(graalRuntime) : null;
    }

    public List<DebugHandlersFactory> getDebugHandlersFactories() {
        if (factories == null) {
            factories = Collections.singletonList(new GraalDebugHandlersFactory(graalRuntime.getHostProviders().getSnippetReflection()));
        }
        return factories;
    }

    @Override
    public HotSpotGraalRuntimeProvider getGraalRuntime() {
        return graalRuntime;
    }

    @Override
    public CompilationRequestResult compileMethod(CompilationRequest request) {
        return compileMethod(request, true);
    }

    @SuppressWarnings("try")
    CompilationRequestResult compileMethod(CompilationRequest request, boolean installAsDefault) {
        if (graalRuntime.isShutdown()) {
            return HotSpotCompilationRequestResult.failure(String.format("Shutdown entered"), false);
        }

        ResolvedJavaMethod method = request.getMethod();
        OptionValues options = graalRuntime.getOptions(method);

        if (graalRuntime.isBootstrapping()) {
            if (DebugOptions.BootstrapInitializeOnly.getValue(options)) {
                return HotSpotCompilationRequestResult.failure(String.format("Skip compilation because %s is enabled", DebugOptions.BootstrapInitializeOnly.getName()), true);
            }
            if (bootstrapWatchDog != null) {
                if (bootstrapWatchDog.hitCriticalCompilationRateOrTimeout()) {
                    // Drain the compilation queue to expedite completion of the bootstrap
                    return HotSpotCompilationRequestResult.failure("hit critical bootstrap compilation rate or timeout", true);
                }
            }
        }
        HotSpotCompilationRequest hsRequest = (HotSpotCompilationRequest) request;
        try (CompilationWatchDog w1 = CompilationWatchDog.watch(method, hsRequest.getId(), options);
                        BootstrapWatchDog.Watch w2 = bootstrapWatchDog == null ? null : bootstrapWatchDog.watch(request);
                        CompilationAlarm alarm = CompilationAlarm.trackCompilationPeriod(options);) {
            if (compilationCounters != null) {
                compilationCounters.countCompilation(method);
            }
            CompilationTask task = new CompilationTask(jvmciRuntime, this, hsRequest, true, installAsDefault, options);
            CompilationRequestResult r = null;
            try (DebugContext debug = graalRuntime.openDebugContext(options, task.getCompilationIdentifier(), method, getDebugHandlersFactories());
                            Activation a = debug.activate()) {
                r = task.runCompilation(debug);
            }
            assert r != null;
            return r;
        }
    }

    public StructuredGraph createGraph(ResolvedJavaMethod method, int entryBCI, boolean useProfilingInfo, CompilationIdentifier compilationId, OptionValues options, DebugContext debug) {
        HotSpotBackend backend = graalRuntime.getHostBackend();
        HotSpotProviders providers = backend.getProviders();
        final boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;
        StructuredGraph graph = method.isNative() || isOSR ? null : getIntrinsicGraph(method, providers, compilationId, options, debug);

        if (graph == null) {
            SpeculationLog speculationLog = method.getSpeculationLog();
            if (speculationLog != null) {
                speculationLog.collectFailedSpeculations();
            }
            graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.ifTrue(OptAssumptions.getValue(options))).method(method).entryBCI(entryBCI).speculationLog(
                            speculationLog).useProfilingInfo(useProfilingInfo).compilationId(compilationId).build();
        }
        return graph;
    }

    public CompilationResult compileHelper(CompilationResultBuilderFactory crbf, CompilationResult result, StructuredGraph graph, ResolvedJavaMethod method, int entryBCI, boolean useProfilingInfo,
                    OptionValues options) {

        HotSpotBackend backend = graalRuntime.getHostBackend();
        HotSpotProviders providers = backend.getProviders();
        final boolean isOSR = entryBCI != JVMCICompiler.INVOCATION_ENTRY_BCI;

        Suites suites = getSuites(providers, options);
        LIRSuites lirSuites = getLIRSuites(providers, options);
        ProfilingInfo profilingInfo = useProfilingInfo ? method.getProfilingInfo(!isOSR, isOSR) : DefaultProfilingInfo.get(TriState.FALSE);
        OptimisticOptimizations optimisticOpts = getOptimisticOpts(profilingInfo, options);

        /*
         * Cut off never executed code profiles if there is code, e.g. after the osr loop, that is
         * never executed.
         */
        if (isOSR && !OnStackReplacementPhase.Options.DeoptAfterOSR.getValue(options)) {
            optimisticOpts.remove(Optimization.RemoveNeverExecutedCode);
        }

        result.setEntryBCI(entryBCI);
        boolean shouldDebugNonSafepoints = providers.getCodeCache().shouldDebugNonSafepoints();
        PhaseSuite<HighTierContext> graphBuilderSuite = configGraphBuilderSuite(providers.getSuites().getDefaultGraphBuilderSuite(), shouldDebugNonSafepoints, isOSR);
        GraalCompiler.compileGraph(graph, method, providers, backend, graphBuilderSuite, optimisticOpts, profilingInfo, suites, lirSuites, result, crbf);

        if (!isOSR && useProfilingInfo) {
            ProfilingInfo profile = profilingInfo;
            profile.setCompilerIRSize(StructuredGraph.class, graph.getNodeCount());
        }

        return result;
    }

    public CompilationResult compile(ResolvedJavaMethod method, int entryBCI, boolean useProfilingInfo, CompilationIdentifier compilationId, OptionValues options, DebugContext debug) {
        StructuredGraph graph = createGraph(method, entryBCI, useProfilingInfo, compilationId, options, debug);
        CompilationResult result = new CompilationResult(compilationId);
        return compileHelper(CompilationResultBuilderFactory.Default, result, graph, method, entryBCI, useProfilingInfo, options);
    }

    /**
     * Gets a graph produced from the intrinsic for a given method that can be compiled and
     * installed for the method.
     *
     * @param method
     * @param compilationId
     * @param options
     * @param debug
     * @return an intrinsic graph that can be compiled and installed for {@code method} or null
     */
    @SuppressWarnings("try")
    public StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, HotSpotProviders providers, CompilationIdentifier compilationId, OptionValues options, DebugContext debug) {
        Replacements replacements = providers.getReplacements();
        Bytecode subst = replacements.getSubstitutionBytecode(method);
        if (subst != null) {
            ResolvedJavaMethod substMethod = subst.getMethod();
            assert !substMethod.equals(method);
            StructuredGraph graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.YES).method(substMethod).compilationId(compilationId).build();
            try (DebugContext.Scope scope = debug.scope("GetIntrinsicGraph", graph)) {
                Plugins plugins = new Plugins(providers.getGraphBuilderPlugins());
                GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
                IntrinsicContext initialReplacementContext = new IntrinsicContext(method, substMethod, subst.getOrigin(), ROOT_COMPILATION);
                new GraphBuilderPhase.Instance(providers.getMetaAccess(), providers.getStampProvider(), providers.getConstantReflection(), providers.getConstantFieldProvider(), config,
                                OptimisticOptimizations.NONE, initialReplacementContext).apply(graph);
                assert !graph.isFrozen();
                return graph;
            } catch (Throwable e) {
                debug.handle(e);
            }
        }
        return null;
    }

    protected OptimisticOptimizations getOptimisticOpts(ProfilingInfo profilingInfo, OptionValues options) {
        return new OptimisticOptimizations(profilingInfo, options);
    }

    protected Suites getSuites(HotSpotProviders providers, OptionValues options) {
        return providers.getSuites().getDefaultSuites(options);
    }

    protected LIRSuites getLIRSuites(HotSpotProviders providers, OptionValues options) {
        return providers.getSuites().getDefaultLIRSuites(options);
    }

    /**
     * Reconfigures a given graph builder suite (GBS) if one of the given GBS parameter values is
     * not the default.
     *
     * @param suite the graph builder suite
     * @param shouldDebugNonSafepoints specifies if extra debug info should be generated (default is
     *            false)
     * @param isOSR specifies if extra OSR-specific post-processing is required (default is false)
     * @return a new suite derived from {@code suite} if any of the GBS parameters did not have a
     *         default value otherwise {@code suite}
     */
    protected PhaseSuite<HighTierContext> configGraphBuilderSuite(PhaseSuite<HighTierContext> suite, boolean shouldDebugNonSafepoints, boolean isOSR) {
        if (shouldDebugNonSafepoints || isOSR) {
            PhaseSuite<HighTierContext> newGbs = suite.copy();

            if (shouldDebugNonSafepoints) {
                GraphBuilderPhase graphBuilderPhase = (GraphBuilderPhase) newGbs.findPhase(GraphBuilderPhase.class).previous();
                GraphBuilderConfiguration graphBuilderConfig = graphBuilderPhase.getGraphBuilderConfig();
                graphBuilderConfig = graphBuilderConfig.withNodeSourcePosition(true);
                GraphBuilderPhase newGraphBuilderPhase = new GraphBuilderPhase(graphBuilderConfig);
                newGbs.findPhase(GraphBuilderPhase.class).set(newGraphBuilderPhase);
            }
            if (isOSR) {
                // We must not clear non liveness for OSR compilations.
                GraphBuilderPhase graphBuilderPhase = (GraphBuilderPhase) newGbs.findPhase(GraphBuilderPhase.class).previous();
                GraphBuilderConfiguration graphBuilderConfig = graphBuilderPhase.getGraphBuilderConfig();
                GraphBuilderPhase newGraphBuilderPhase = new GraphBuilderPhase(graphBuilderConfig);
                newGbs.findPhase(GraphBuilderPhase.class).set(newGraphBuilderPhase);
                newGbs.appendPhase(new OnStackReplacementPhase());
            }
            return newGbs;
        }
        return suite;
    }

    public Object mbean() {
        if (graalRuntime instanceof HotSpotGraalRuntime) {
            return ((HotSpotGraalRuntime) graalRuntime).getMBean();
        }
        return null;
    }

    /**
     * Converts {@code method} to a String with {@link JavaMethod#format(String)} and the format
     * string {@code "%H.%n(%p)"}.
     */
    static String str(JavaMethod method) {
        return method.format("%H.%n(%p)");
    }

    /**
     * Wraps {@code obj} in a {@link Formatter} that standardizes formatting for certain objects.
     */
    static Formattable fmt(Object obj) {
        return new Formattable() {
            @Override
            public void formatTo(Formatter buf, int flags, int width, int precision) {
                if (obj instanceof Throwable) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ((Throwable) obj).printStackTrace(new PrintStream(baos));
                    buf.format("%s", baos.toString());
                } else if (obj instanceof StackTraceElement[]) {
                    for (StackTraceElement e : (StackTraceElement[]) obj) {
                        buf.format("\t%s%n", e);
                    }
                } else if (obj instanceof JavaMethod) {
                    buf.format("%s", str((JavaMethod) obj));
                } else {
                    buf.format("%s", obj);
                }
            }
        };
    }
}

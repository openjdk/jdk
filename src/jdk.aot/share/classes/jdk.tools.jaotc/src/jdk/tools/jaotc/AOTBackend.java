/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc;

import java.util.ListIterator;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.CompilationIdentifier.Verbosity;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotCompiledCodeBuilder;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.meta.HotSpotInvokeDynamicPlugin;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.graalvm.compiler.phases.tiers.Suites;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

final class AOTBackend {
    private final Main main;
    private final OptionValues graalOptions;
    private final HotSpotBackend backend;
    private final HotSpotProviders providers;
    private final HotSpotCodeCacheProvider codeCache;
    private final PhaseSuite<HighTierContext> graphBuilderSuite;
    private final HighTierContext highTierContext;

    AOTBackend(Main main, OptionValues graalOptions, HotSpotBackend backend, HotSpotInvokeDynamicPlugin inokeDynamicPlugin) {
        this.main = main;
        this.graalOptions = graalOptions;
        this.backend = backend;
        providers = backend.getProviders();
        codeCache = providers.getCodeCache();
        graphBuilderSuite = initGraphBuilderSuite(backend, main.options.compileWithAssertions, inokeDynamicPlugin);
        highTierContext = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.ALL);
    }

    PhaseSuite<HighTierContext> getGraphBuilderSuite() {
        return graphBuilderSuite;
    }

    HotSpotBackend getBackend() {
        return backend;
    }

    HotSpotProviders getProviders() {
        return providers;
    }

    private Suites getSuites() {
        // create suites every time, as we modify options for the compiler
        return backend.getSuites().getDefaultSuites(graalOptions);
    }

    private LIRSuites getLirSuites() {
        // create suites every time, as we modify options for the compiler
        return backend.getSuites().getDefaultLIRSuites(graalOptions);
    }

    @SuppressWarnings("try")
    CompilationResult compileMethod(ResolvedJavaMethod resolvedMethod, DebugContext debug) {
        StructuredGraph graph = buildStructuredGraph(resolvedMethod, debug);
        if (graph != null) {
            return compileGraph(resolvedMethod, graph, debug);
        }
        return null;
    }

    /**
     * Build a structured graph for the member.
     *
     * @param javaMethod method for whose code the graph is to be created
     * @param debug
     * @return structured graph
     */
    @SuppressWarnings("try")
    private StructuredGraph buildStructuredGraph(ResolvedJavaMethod javaMethod, DebugContext debug) {
        try (DebugContext.Scope s = debug.scope("AOTParseMethod")) {
            StructuredGraph graph = new StructuredGraph.Builder(graalOptions, debug).method(javaMethod).useProfilingInfo(false).build();
            graphBuilderSuite.apply(graph, highTierContext);
            return graph;
        } catch (Throwable e) {
            main.handleError(javaMethod, e, " (building graph)");
        }
        return null;
    }

    @SuppressWarnings("try")
    private CompilationResult compileGraph(ResolvedJavaMethod resolvedMethod, StructuredGraph graph, DebugContext debug) {
        try (DebugContext.Scope s = debug.scope("AOTCompileMethod")) {
            ProfilingInfo profilingInfo = DefaultProfilingInfo.get(TriState.FALSE);

            final boolean isImmutablePIC = true;
            CompilationIdentifier id = new CompilationIdentifier() {
                @Override
                public String toString(Verbosity verbosity) {
                    return resolvedMethod.getName();
                }
            };
            CompilationResult compilationResult = new CompilationResult(id, isImmutablePIC);

            return GraalCompiler.compileGraph(graph, resolvedMethod, providers, backend, graphBuilderSuite, OptimisticOptimizations.ALL, profilingInfo, getSuites(), getLirSuites(),
                            compilationResult, CompilationResultBuilderFactory.Default);

        } catch (Throwable e) {
            main.handleError(resolvedMethod, e, " (compiling graph)");
        }
        return null;
    }

    private static PhaseSuite<HighTierContext> initGraphBuilderSuite(HotSpotBackend backend, boolean compileWithAssertions, HotSpotInvokeDynamicPlugin inokeDynamicPlugin) {
        PhaseSuite<HighTierContext> graphBuilderSuite = backend.getSuites().getDefaultGraphBuilderSuite().copy();
        ListIterator<BasePhase<? super HighTierContext>> iterator = graphBuilderSuite.findPhase(GraphBuilderPhase.class);
        GraphBuilderConfiguration baseConfig = ((GraphBuilderPhase) iterator.previous()).getGraphBuilderConfig();

        // Use all default plugins.
        Plugins plugins = baseConfig.getPlugins();
        plugins.setInvokeDynamicPlugin(inokeDynamicPlugin);
        GraphBuilderConfiguration aotConfig = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withOmitAssertions(!compileWithAssertions);

        iterator.next();
        iterator.remove();
        iterator.add(new GraphBuilderPhase(aotConfig));

        return graphBuilderSuite;
    }

    void printCompiledMethod(HotSpotResolvedJavaMethod resolvedMethod, CompilationResult compResult) {
        // This is really not installing the method.
        InstalledCode installedCode = codeCache.addCode(resolvedMethod, HotSpotCompiledCodeBuilder.createCompiledCode(codeCache, resolvedMethod, null, compResult), null, null);
        String disassembly = codeCache.disassemble(installedCode);
        if (disassembly != null) {
            main.printer.printlnDebug(disassembly);
        }
    }
}

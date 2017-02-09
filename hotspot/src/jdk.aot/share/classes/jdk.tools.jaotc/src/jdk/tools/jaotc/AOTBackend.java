/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.core.common.GraalOptions.GeneratePIC;
import static org.graalvm.compiler.core.common.GraalOptions.ImmutableCode;

import java.util.ListIterator;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotBackend;
import org.graalvm.compiler.hotspot.HotSpotCompiledCodeBuilder;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
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

public class AOTBackend {

    private final Main main;

    private final HotSpotBackend backend;

    private final HotSpotProviders providers;
    private final HotSpotCodeCacheProvider codeCache;
    private final PhaseSuite<HighTierContext> graphBuilderSuite;
    private final HighTierContext highTierContext;
    private final GraalFilters filters;

    public AOTBackend(Main main, HotSpotBackend backend, GraalFilters filters) {
        this.main = main;
        this.backend = backend;
        this.filters = filters;
        providers = backend.getProviders();
        codeCache = providers.getCodeCache();
        graphBuilderSuite = initGraphBuilderSuite(backend, main.options.compileWithAssertions);
        highTierContext = new HighTierContext(providers, graphBuilderSuite, OptimisticOptimizations.ALL);
    }

    public PhaseSuite<HighTierContext> getGraphBuilderSuite() {
        return graphBuilderSuite;
    }

    private Suites getSuites() {
        // create suites every time, as we modify options for the compiler
        return backend.getSuites().getDefaultSuites();
    }

    private LIRSuites getLirSuites() {
        // create suites every time, as we modify options for the compiler
        return backend.getSuites().getDefaultLIRSuites();
    }

    @SuppressWarnings("try")
    public CompilationResult compileMethod(ResolvedJavaMethod resolvedMethod) {
        try (OverrideScope s = OptionValue.override(ImmutableCode, true, GeneratePIC, true)) {
            StructuredGraph graph = buildStructuredGraph(resolvedMethod);
            if (graph != null) {
                return compileGraph(resolvedMethod, graph);
            }
            return null;
        }
    }

    /**
     * Build a structured graph for the member.
     *
     * @param javaMethod method for whose code the graph is to be created
     * @return structured graph
     */
    @SuppressWarnings("try")
    private StructuredGraph buildStructuredGraph(ResolvedJavaMethod javaMethod) {
        try (Scope s = Debug.scope("AOTParseMethod")) {
            StructuredGraph graph = new StructuredGraph(javaMethod, StructuredGraph.AllowAssumptions.NO, false, CompilationIdentifier.INVALID_COMPILATION_ID);
            graphBuilderSuite.apply(graph, highTierContext);
            return graph;
        } catch (Throwable e) {
            handleError(javaMethod, e, " (building graph)");
        }
        return null;
    }

    @SuppressWarnings("try")
    private CompilationResult compileGraph(ResolvedJavaMethod resolvedMethod, StructuredGraph graph) {
        try (Scope s = Debug.scope("AOTCompileMethod")) {
            ProfilingInfo profilingInfo = DefaultProfilingInfo.get(TriState.FALSE);

            final boolean isImmutablePIC = true;
            CompilationResult compilationResult = new CompilationResult(resolvedMethod.getName(), isImmutablePIC);

            return GraalCompiler.compileGraph(graph, resolvedMethod, providers, backend, graphBuilderSuite, OptimisticOptimizations.ALL, profilingInfo, getSuites(), getLirSuites(),
                            compilationResult, CompilationResultBuilderFactory.Default);

        } catch (Throwable e) {
            handleError(resolvedMethod, e, " (compiling graph)");
        }
        return null;
    }

    /**
     * Returns whether the VM is a debug build.
     *
     * @return true is debug VM, false otherwise
     */
    public boolean isDebugVM() {
        return backend.getRuntime().getVMConfig().cAssertions;
    }

    private static PhaseSuite<HighTierContext> initGraphBuilderSuite(HotSpotBackend backend, boolean compileWithAssertions) {
        PhaseSuite<HighTierContext> graphBuilderSuite = backend.getSuites().getDefaultGraphBuilderSuite().copy();
        ListIterator<BasePhase<? super HighTierContext>> iterator = graphBuilderSuite.findPhase(GraphBuilderPhase.class);
        GraphBuilderConfiguration baseConfig = ((GraphBuilderPhase) iterator.previous()).getGraphBuilderConfig();

        // Use all default plugins.
        Plugins plugins = baseConfig.getPlugins();
        GraphBuilderConfiguration aotConfig = GraphBuilderConfiguration.getDefault(plugins).withEagerResolving(true).withOmitAssertions(!compileWithAssertions);

        iterator.next();
        iterator.remove();
        iterator.add(new GraphBuilderPhase(aotConfig));

        return graphBuilderSuite;
    }

    private void handleError(ResolvedJavaMethod resolvedMethod, Throwable e, String message) {
        String methodName = MiscUtils.uniqueMethodName(resolvedMethod);

        if (main.options.debug) {
            main.printError("Failed compilation: " + methodName + ": " + e);
        }

        // Ignore some exceptions when meta-compiling Graal.
        if (filters.shouldIgnoreException(e)) {
            return;
        }

        Main.writeLog("Failed compilation of method " + methodName + message);

        if (!main.options.debug) {
            main.printError("Failed compilation: " + methodName + ": " + e);
        }

        if (main.options.verbose) {
            e.printStackTrace(main.log);
        }

        if (main.options.exitOnError) {
            System.exit(1);
        }
    }

    public void printCompiledMethod(HotSpotResolvedJavaMethod resolvedMethod, CompilationResult compResult) {
        // This is really not installing the method.
        InstalledCode installedCode = codeCache.addCode(resolvedMethod, HotSpotCompiledCodeBuilder.createCompiledCode(null, null, compResult), null, null);
        String disassembly = codeCache.disassemble(installedCode);
        if (disassembly != null) {
            main.printlnDebug(disassembly);
        }
    }
}

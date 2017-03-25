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
package org.graalvm.compiler.replacements;

import static org.graalvm.compiler.core.common.CompilationIdentifier.INVALID_COMPILATION_ID;
import static org.graalvm.compiler.core.common.GraalOptions.DeoptALot;
import static org.graalvm.compiler.core.common.GraalOptions.UseSnippetGraphCache;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsing;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineIntrinsicsDuringParsing;
import static org.graalvm.compiler.nodes.StructuredGraph.NO_PROFILING_INFO;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createIntrinsicInlineInfo;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.replacements.SnippetTemplateCache;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.common.CollectionsFactory;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugTimer;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ReplacementsImpl implements Replacements, InlineInvokePlugin {

    public final Providers providers;
    public final SnippetReflectionProvider snippetReflection;
    public final TargetDescription target;
    private GraphBuilderConfiguration.Plugins graphBuilderPlugins;

    /**
     * The preprocessed replacement graphs.
     */
    protected final ConcurrentMap<ResolvedJavaMethod, StructuredGraph> graphs;

    protected final BytecodeProvider bytecodeProvider;

    public void setGraphBuilderPlugins(GraphBuilderConfiguration.Plugins plugins) {
        assert this.graphBuilderPlugins == null;
        this.graphBuilderPlugins = plugins;
    }

    public GraphBuilderConfiguration.Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }

    protected boolean hasGeneratedInvocationPluginAnnotation(ResolvedJavaMethod method) {
        return method.getAnnotation(Node.NodeIntrinsic.class) != null || method.getAnnotation(Fold.class) != null;
    }

    protected boolean hasGenericInvocationPluginAnnotation(ResolvedJavaMethod method) {
        return method.getAnnotation(Word.Operation.class) != null;
    }

    private static final int MAX_GRAPH_INLINING_DEPTH = 100; // more than enough

    /**
     * Determines whether a given method should be inlined based on whether it has a substitution or
     * whether the inlining context is already within a substitution.
     *
     * @return an object specifying how {@code method} is to be inlined or null if it should not be
     *         inlined based on substitution related criteria
     */
    @Override
    public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        ResolvedJavaMethod subst = getSubstitutionMethod(method);
        if (subst != null) {
            if (b.parsingIntrinsic() || InlineDuringParsing.getValue() || InlineIntrinsicsDuringParsing.getValue()) {
                // Forced inlining of intrinsics
                return createIntrinsicInlineInfo(subst, bytecodeProvider);
            }
            return null;
        }
        if (b.parsingIntrinsic()) {
            if (hasGeneratedInvocationPluginAnnotation(method)) {
                throw new GraalError("%s should have been handled by a %s", method.format("%H.%n(%p)"), GeneratedInvocationPlugin.class.getSimpleName());
            }
            if (hasGenericInvocationPluginAnnotation(method)) {
                throw new GraalError("%s should have been handled by %s", method.format("%H.%n(%p)"), WordOperationPlugin.class.getSimpleName());
            }

            assert b.getDepth() < MAX_GRAPH_INLINING_DEPTH : "inlining limit exceeded";

            if (method.getName().startsWith("$jacoco")) {
                throw new GraalError("Found call to JaCoCo instrumentation method " + method.format("%H.%n(%p)") + ". Placing \"//JaCoCo Exclude\" anywhere in " +
                                b.getMethod().getDeclaringClass().getSourceFileName() + " should fix this.");
            }

            // Force inlining when parsing replacements
            return createIntrinsicInlineInfo(method, bytecodeProvider);
        } else {
            assert method.getAnnotation(NodeIntrinsic.class) == null : String.format("@%s method %s must only be called from within a replacement%n%s", NodeIntrinsic.class.getSimpleName(),
                            method.format("%h.%n"), b);
        }
        return null;
    }

    @Override
    public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (b.parsingIntrinsic()) {
            IntrinsicContext intrinsic = b.getIntrinsic();
            if (!intrinsic.isCallToOriginal(method)) {
                throw new GraalError("All non-recursive calls in the intrinsic %s must be inlined or intrinsified: found call to %s",
                                intrinsic.getIntrinsicMethod().format("%H.%n(%p)"), method.format("%h.%n(%p)"));
            }
        }
    }

    // This map is key'ed by a class name instead of a Class object so that
    // it is stable across VM executions (in support of replay compilation).
    private final Map<String, SnippetTemplateCache> snippetTemplateCache;

    public ReplacementsImpl(Providers providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider, TargetDescription target) {
        this.providers = providers.copyWith(this);
        this.snippetReflection = snippetReflection;
        this.target = target;
        this.graphs = new ConcurrentHashMap<>();
        this.snippetTemplateCache = CollectionsFactory.newMap();
        this.bytecodeProvider = bytecodeProvider;
    }

    private static final DebugTimer SnippetPreparationTime = Debug.timer("SnippetPreparationTime");

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, Object[] args) {
        return getSnippet(method, null, args);
    }

    @Override
    @SuppressWarnings("try")
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args) {
        assert method.getAnnotation(Snippet.class) != null : "Snippet must be annotated with @" + Snippet.class.getSimpleName();
        assert method.hasBytecodes() : "Snippet must not be abstract or native";

        StructuredGraph graph = UseSnippetGraphCache.getValue() ? graphs.get(method) : null;
        if (graph == null) {
            try (DebugCloseable a = SnippetPreparationTime.start()) {
                StructuredGraph newGraph = makeGraph(method, args, recursiveEntry);
                Debug.counter("SnippetNodeCount[%#s]", method).add(newGraph.getNodeCount());
                if (!UseSnippetGraphCache.getValue() || args != null) {
                    return newGraph;
                }
                graphs.putIfAbsent(method, newGraph);
                graph = graphs.get(method);
            }
        }
        return graph;
    }

    @Override
    public void registerSnippet(ResolvedJavaMethod method) {
        // No initialization needed as snippet graphs are created on demand in getSnippet
    }

    @Override
    public boolean hasSubstitution(ResolvedJavaMethod method, int invokeBci) {
        InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(method);
        return plugin != null && (!plugin.inlineOnly() || invokeBci >= 0);
    }

    @Override
    public BytecodeProvider getReplacementBytecodeProvider() {
        return bytecodeProvider;
    }

    @Override
    public ResolvedJavaMethod getSubstitutionMethod(ResolvedJavaMethod method) {
        InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(method);
        if (plugin instanceof MethodSubstitutionPlugin) {
            MethodSubstitutionPlugin msPlugin = (MethodSubstitutionPlugin) plugin;
            return msPlugin.getSubstitute(providers.getMetaAccess());
        }
        return null;
    }

    @Override
    public StructuredGraph getSubstitution(ResolvedJavaMethod method, int invokeBci) {
        StructuredGraph result;
        InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(method);
        if (plugin != null && (!plugin.inlineOnly() || invokeBci >= 0)) {
            MetaAccessProvider metaAccess = providers.getMetaAccess();
            if (plugin instanceof MethodSubstitutionPlugin) {
                MethodSubstitutionPlugin msPlugin = (MethodSubstitutionPlugin) plugin;
                ResolvedJavaMethod substitute = msPlugin.getSubstitute(metaAccess);
                StructuredGraph graph = graphs.get(substitute);
                if (graph == null) {
                    graph = makeGraph(substitute, null, method);
                    graph.freeze();
                    graphs.putIfAbsent(substitute, graph);
                    graph = graphs.get(substitute);
                }
                assert graph.isFrozen();
                result = graph;
            } else {
                ResolvedJavaMethodBytecode code = new ResolvedJavaMethodBytecode(method);
                ConstantReflectionProvider constantReflection = providers.getConstantReflection();
                ConstantFieldProvider constantFieldProvider = providers.getConstantFieldProvider();
                StampProvider stampProvider = providers.getStampProvider();
                result = new IntrinsicGraphBuilder(metaAccess, constantReflection, constantFieldProvider, stampProvider, code, invokeBci).buildGraph(plugin);
            }
        } else {
            result = null;
        }
        return result;
    }

    /**
     * Creates a preprocessed graph for a snippet or method substitution.
     *
     * @param method the snippet or method substitution for which a graph will be created
     * @param args
     * @param original the original method if {@code method} is a {@linkplain MethodSubstitution
     *            substitution} otherwise null
     */
    @SuppressWarnings("try")
    public StructuredGraph makeGraph(ResolvedJavaMethod method, Object[] args, ResolvedJavaMethod original) {
        try (OverrideScope s = OptionValue.override(DeoptALot, false)) {
            return createGraphMaker(method, original).makeGraph(args);
        }
    }

    /**
     * Can be overridden to return an object that specializes various parts of graph preprocessing.
     */
    protected GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
        return new GraphMaker(this, substitute, original);
    }

    /**
     * Creates and preprocesses a graph for a replacement.
     */
    public static class GraphMaker {

        /** The replacements object that the graphs are created for. */
        protected final ReplacementsImpl replacements;

        /**
         * The method for which a graph is being created.
         */
        protected final ResolvedJavaMethod method;

        /**
         * The original method which {@link #method} is substituting. Calls to {@link #method} or
         * {@link #substitutedMethod} will be replaced with a forced inline of
         * {@link #substitutedMethod}.
         */
        protected final ResolvedJavaMethod substitutedMethod;

        protected GraphMaker(ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
            this.replacements = replacements;
            this.method = substitute;
            this.substitutedMethod = substitutedMethod;
        }

        @SuppressWarnings("try")
        public StructuredGraph makeGraph(Object[] args) {
            try (Scope s = Debug.scope("BuildSnippetGraph", method)) {
                assert method.hasBytecodes() : method;
                StructuredGraph graph = buildInitialGraph(method, args);

                finalizeGraph(graph);

                Debug.dump(Debug.INFO_LOG_LEVEL, graph, "%s: Final", method.getName());

                return graph;
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }

        /**
         * Does final processing of a snippet graph.
         */
        protected void finalizeGraph(StructuredGraph graph) {
            if (!GraalOptions.SnippetCounters.getValue() || graph.getNodes().filter(SnippetCounterNode.class).isEmpty()) {
                int sideEffectCount = 0;
                assert (sideEffectCount = graph.getNodes().filter(e -> hasSideEffect(e)).count()) >= 0;
                new ConvertDeoptimizeToGuardPhase().apply(graph, null);
                assert sideEffectCount == graph.getNodes().filter(e -> hasSideEffect(e)).count() : "deleted side effecting node";

                new DeadCodeEliminationPhase(Required).apply(graph);
            } else {
                // ConvertDeoptimizeToGuardPhase will eliminate snippet counters on paths
                // that terminate in a deopt so we disable it if the graph contains
                // snippet counters. The trade off is that we miss out on guard
                // coalescing opportunities.
            }
        }

        /**
         * Filter nodes which have side effects and shouldn't be deleted from snippets when
         * converting deoptimizations to guards. Currently this only allows exception constructors
         * to be eliminated to cover the case when Java assertions are in the inlined code.
         *
         * @param node
         * @return true for nodes that have side effects and are unsafe to delete
         */
        private boolean hasSideEffect(Node node) {
            if (node instanceof StateSplit) {
                if (((StateSplit) node).hasSideEffect()) {
                    if (node instanceof Invoke) {
                        CallTargetNode callTarget = ((Invoke) node).callTarget();
                        if (callTarget instanceof MethodCallTargetNode) {
                            ResolvedJavaMethod targetMethod = ((MethodCallTargetNode) callTarget).targetMethod();
                            if (targetMethod.isConstructor()) {
                                ResolvedJavaType throwableType = replacements.providers.getMetaAccess().lookupJavaType(Throwable.class);
                                return !throwableType.isAssignableFrom(targetMethod.getDeclaringClass());
                            }
                        }
                    }
                    // Not an exception constructor call
                    return true;
                }
            }
            // Not a StateSplit
            return false;
        }

        /**
         * Builds the initial graph for a snippet.
         */
        @SuppressWarnings("try")
        protected StructuredGraph buildInitialGraph(final ResolvedJavaMethod methodToParse, Object[] args) {
            // Replacements cannot have optimistic assumptions since they have
            // to be valid for the entire run of the VM.

            final StructuredGraph graph = new StructuredGraph(methodToParse, AllowAssumptions.NO, NO_PROFILING_INFO, INVALID_COMPILATION_ID);

            // They are not user code so they do not participate in unsafe access tracking
            graph.disableUnsafeAccessTracking();

            try (Scope s = Debug.scope("buildInitialGraph", graph)) {
                MetaAccessProvider metaAccess = replacements.providers.getMetaAccess();

                Plugins plugins = new Plugins(replacements.graphBuilderPlugins);
                GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
                if (args != null) {
                    plugins.prependParameterPlugin(new ConstantBindingParameterPlugin(args, metaAccess, replacements.snippetReflection));
                }

                IntrinsicContext initialIntrinsicContext = null;
                if (method.getAnnotation(Snippet.class) == null) {
                    // Post-parse inlined intrinsic
                    initialIntrinsicContext = new IntrinsicContext(substitutedMethod, method, replacements.bytecodeProvider, INLINE_AFTER_PARSING);
                } else {
                    // Snippet
                    ResolvedJavaMethod original = substitutedMethod != null ? substitutedMethod : method;
                    initialIntrinsicContext = new IntrinsicContext(original, method, replacements.bytecodeProvider, INLINE_AFTER_PARSING);
                }

                createGraphBuilder(metaAccess, replacements.providers.getStampProvider(), replacements.providers.getConstantReflection(), replacements.providers.getConstantFieldProvider(), config,
                                OptimisticOptimizations.NONE, initialIntrinsicContext).apply(graph);

                new CanonicalizerPhase().apply(graph, new PhaseContext(replacements.providers));
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
            return graph;
        }

        protected Instance createGraphBuilder(MetaAccessProvider metaAccess, StampProvider stampProvider, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
                        GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            return new GraphBuilderPhase.Instance(metaAccess, stampProvider, constantReflection, constantFieldProvider, graphBuilderConfig, optimisticOpts,
                            initialIntrinsicContext);
        }
    }

    @Override
    public void registerSnippetTemplateCache(SnippetTemplateCache templates) {
        assert snippetTemplateCache.get(templates.getClass().getName()) == null;
        snippetTemplateCache.put(templates.getClass().getName(), templates);
    }

    @Override
    public <T extends SnippetTemplateCache> T getSnippetTemplateCache(Class<T> templatesClass) {
        SnippetTemplateCache ret = snippetTemplateCache.get(templatesClass.getName());
        return templatesClass.cast(ret);
    }
}

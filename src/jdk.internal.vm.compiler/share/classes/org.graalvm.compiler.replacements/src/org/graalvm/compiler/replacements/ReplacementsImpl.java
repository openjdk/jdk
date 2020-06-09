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


package org.graalvm.compiler.replacements;

import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.core.common.GraalOptions.UseSnippetGraphCache;
import static org.graalvm.compiler.debug.DebugOptions.DebugStubsAndSnippets;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineDuringParsing;
import static org.graalvm.compiler.java.BytecodeParserOptions.InlineIntrinsicsDuringParsing;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createIntrinsicInlineInfo;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createMethodSubstitutionInlineInfo;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.ROOT_COMPILATION;
import static org.graalvm.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.replacements.SnippetTemplateCache;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TimerKey;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.loop.phases.ConvertDeoptimizeToGuardPhase;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.SnippetParameterInfo;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.DeadCodeEliminationPhase;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.Word;
import org.graalvm.compiler.word.WordOperationPlugin;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ReplacementsImpl implements Replacements, InlineInvokePlugin {

    @Override
    public Providers getProviders() {
        return providers;
    }

    public void setProviders(Providers providers) {
        this.providers = providers.copyWith(this);
    }

    protected Providers providers;
    public final SnippetReflectionProvider snippetReflection;
    public final TargetDescription target;
    private GraphBuilderConfiguration.Plugins graphBuilderPlugins;
    private final DebugHandlersFactory debugHandlersFactory;

    /**
     * The preprocessed replacement graphs.
     */
    protected final ConcurrentMap<ResolvedJavaMethod, StructuredGraph> graphs;

    /**
     * The default {@link BytecodeProvider} to use for accessing the bytecode of a replacement if
     * the replacement doesn't provide another {@link BytecodeProvider}.
     */
    protected final BytecodeProvider defaultBytecodeProvider;

    public void setGraphBuilderPlugins(GraphBuilderConfiguration.Plugins plugins) {
        assert this.graphBuilderPlugins == null;
        this.graphBuilderPlugins = plugins;
    }

    @Override
    public GraphBuilderConfiguration.Plugins getGraphBuilderPlugins() {
        return graphBuilderPlugins;
    }

    @Override
    public Class<? extends GraphBuilderPlugin> getIntrinsifyingPlugin(ResolvedJavaMethod method) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (method.getAnnotation(Node.NodeIntrinsic.class) != null || method.getAnnotation(Fold.class) != null) {
                return GeneratedInvocationPlugin.class;
            }
            if (method.getAnnotation(Word.Operation.class) != null) {
                return WordOperationPlugin.class;
            }
        }
        return null;
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
        MethodSubstitutionPlugin msPlugin = getMethodSubstitution(method);
        if (msPlugin != null) {
            if (b.parsingIntrinsic() || InlineDuringParsing.getValue(b.getOptions()) || InlineIntrinsicsDuringParsing.getValue(b.getOptions())) {
                // Forced inlining of intrinsics
                return createMethodSubstitutionInlineInfo(method, msPlugin);
            }
            return null;
        }
        if (b.parsingIntrinsic()) {
            assert b.getDepth() < MAX_GRAPH_INLINING_DEPTH : "inlining limit exceeded";

            // Force inlining when parsing replacements
            return createIntrinsicInlineInfo(method, defaultBytecodeProvider);
        } else {
            assert IS_BUILDING_NATIVE_IMAGE || method.getAnnotation(NodeIntrinsic.class) == null : String.format("@%s method %s must only be called from within a replacement%n%s",
                            NodeIntrinsic.class.getSimpleName(),
                            method.format("%h.%n"), b);
        }
        return null;
    }

    @Override
    public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (b.parsingIntrinsic()) {
            IntrinsicContext intrinsic = b.getIntrinsic();
            if (!intrinsic.isCallToOriginal(method)) {
                Class<? extends GraphBuilderPlugin> pluginClass = getIntrinsifyingPlugin(method);
                if (pluginClass != null) {
                    String methodDesc = method.format("%H.%n(%p)");
                    throw new GraalError("Call to %s should have been intrinsified by a %s. " +
                                    "This is typically caused by Eclipse failing to run an annotation " +
                                    "processor. This can usually be fixed by forcing Eclipse to rebuild " +
                                    "the source file in which %s is declared",
                                    methodDesc, pluginClass.getSimpleName(), methodDesc);
                }
                throw new GraalError("All non-recursive calls in the intrinsic %s must be inlined or intrinsified: found call to %s",
                                intrinsic.getIntrinsicMethod().format("%H.%n(%p)"), method.format("%h.%n(%p)"));
            }
        }
    }

    // This map is key'ed by a class name instead of a Class object so that
    // it is stable across VM executions (in support of replay compilation).
    private final EconomicMap<String, SnippetTemplateCache> snippetTemplateCache;

    public ReplacementsImpl(DebugHandlersFactory debugHandlersFactory, Providers providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider,
                    TargetDescription target) {
        this.providers = providers.copyWith(this);
        this.snippetReflection = snippetReflection;
        this.target = target;
        this.graphs = new ConcurrentHashMap<>();
        this.snippetTemplateCache = EconomicMap.create(Equivalence.DEFAULT);
        this.defaultBytecodeProvider = bytecodeProvider;
        this.debugHandlersFactory = debugHandlersFactory;

    }

    private static final TimerKey SnippetPreparationTime = DebugContext.timer("SnippetPreparationTime");

    private static final AtomicInteger nextDebugContextId = new AtomicInteger();

    public DebugContext openDebugContext(String idPrefix, ResolvedJavaMethod method, OptionValues options) {
        if (DebugStubsAndSnippets.getValue(options)) {
            DebugContext outer = DebugContext.forCurrentThread();
            Description description = new Description(method, idPrefix + nextDebugContextId.incrementAndGet());
            return new Builder(options, debugHandlersFactory).globalMetrics(outer.getGlobalMetrics()).description(description).build();
        }
        return DebugContext.disabled(options);
    }

    @Override
    @SuppressWarnings("try")
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition,
                    OptionValues options) {
        assert method.getAnnotation(Snippet.class) != null : "Snippet must be annotated with @" + Snippet.class.getSimpleName();
        assert method.hasBytecodes() : "Snippet must not be abstract or native";

        StructuredGraph graph = UseSnippetGraphCache.getValue(options) ? graphs.get(method) : null;
        if (graph == null || (trackNodeSourcePosition && !graph.trackNodeSourcePosition())) {
            try (DebugContext debug = openDebugContext("Snippet_", method, options);
                            DebugCloseable a = SnippetPreparationTime.start(debug)) {
                StructuredGraph newGraph = makeGraph(debug, defaultBytecodeProvider, method, args, recursiveEntry, trackNodeSourcePosition, replaceePosition, INLINE_AFTER_PARSING);
                DebugContext.counter("SnippetNodeCount[%#s]", method).add(newGraph.getDebug(), newGraph.getNodeCount());
                if (!UseSnippetGraphCache.getValue(options) || args != null) {
                    return newGraph;
                }
                newGraph.freeze();
                if (graph != null) {
                    graphs.replace(method, graph, newGraph);
                } else {
                    graphs.putIfAbsent(method, newGraph);
                }
                graph = graphs.get(method);
            }
        }
        assert !trackNodeSourcePosition || graph.trackNodeSourcePosition();
        return graph;
    }

    @Override
    public SnippetParameterInfo getSnippetParameterInfo(ResolvedJavaMethod method) {
        return new SnippetParameterInfo(method);
    }

    @Override
    public boolean isSnippet(ResolvedJavaMethod method) {
        return method.getAnnotation(Snippet.class) != null;
    }

    @Override
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        // No initialization needed as snippet graphs are created on demand in getSnippet
    }

    @Override
    public StructuredGraph getMethodSubstitution(MethodSubstitutionPlugin plugin, ResolvedJavaMethod original, IntrinsicContext.CompilationContext context,
                    AllowAssumptions allowAssumptions, Cancellable cancellable, OptionValues options) {
        // Method substitutions are parsed by the BytecodeParser.
        return null;
    }

    @Override
    public void registerMethodSubstitution(MethodSubstitutionPlugin plugin) {
        // No initialization needed as method substitutions are parsed by the BytecodeParser.
    }

    @Override
    public void registerConditionalPlugin(InvocationPlugin plugin) {
    }

    @Override
    public boolean hasSubstitution(ResolvedJavaMethod method, int invokeBci) {
        InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(method);
        return plugin != null && (!plugin.inlineOnly() || invokeBci >= 0);
    }

    @Override
    public BytecodeProvider getDefaultReplacementBytecodeProvider() {
        return defaultBytecodeProvider;
    }

    protected MethodSubstitutionPlugin getMethodSubstitution(ResolvedJavaMethod method) {
        InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(method);
        if (plugin instanceof MethodSubstitutionPlugin) {
            MethodSubstitutionPlugin msPlugin = (MethodSubstitutionPlugin) plugin;
            return msPlugin;
        }
        return null;
    }

    @Override
    public StructuredGraph getSubstitution(ResolvedJavaMethod method, int invokeBci, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition,
                    AllowAssumptions allowAssumptions, OptionValues options) {
        StructuredGraph result;
        InvocationPlugin plugin = graphBuilderPlugins.getInvocationPlugins().lookupInvocation(method);
        if (plugin != null && (!plugin.inlineOnly() || invokeBci >= 0)) {
            MetaAccessProvider metaAccess = providers.getMetaAccess();
            if (plugin instanceof MethodSubstitutionPlugin) {
                MethodSubstitutionPlugin msPlugin = (MethodSubstitutionPlugin) plugin;
                ResolvedJavaMethod substitute = msPlugin.getSubstitute(metaAccess);
                StructuredGraph graph = UseSnippetGraphCache.getValue(options) ? graphs.get(substitute) : null;
                if (graph == null || graph.trackNodeSourcePosition() != trackNodeSourcePosition) {
                    try (DebugContext debug = openDebugContext("Substitution_", method, options)) {
                        graph = makeGraph(debug, msPlugin.getBytecodeProvider(), substitute, null, method, trackNodeSourcePosition, replaceePosition, INLINE_AFTER_PARSING);
                        if (!UseSnippetGraphCache.getValue(options)) {
                            return graph;
                        }
                        graph.freeze();
                        graphs.putIfAbsent(substitute, graph);
                        graph = graphs.get(substitute);
                    }
                }
                assert graph.isFrozen();
                result = graph;
            } else {
                Bytecode code = new ResolvedJavaMethodBytecode(method);
                try (DebugContext debug = openDebugContext("Substitution_", method, options)) {
                    result = new IntrinsicGraphBuilder(options, debug, providers, code, invokeBci, allowAssumptions).buildGraph(plugin);
                }
            }
        } else {
            result = null;
        }
        return result;
    }

    @SuppressWarnings("try")
    @Override
    public StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, CompilationIdentifier compilationId, DebugContext debug, AllowAssumptions allowAssumptions, Cancellable cancellable) {
        MethodSubstitutionPlugin msPlugin = getMethodSubstitution(method);
        if (msPlugin != null) {
            ResolvedJavaMethod substMethod = msPlugin.getSubstitute(providers.getMetaAccess());
            assert !substMethod.equals(method);
            BytecodeProvider bytecodeProvider = msPlugin.getBytecodeProvider();
            // @formatter:off
            StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug, allowAssumptions).
                    method(substMethod).
                    compilationId(compilationId).
                    recordInlinedMethods(bytecodeProvider.shouldRecordMethodDependencies()).
                    setIsSubstitution(true).
                    build();
            // @formatter:on
            try (DebugContext.Scope scope = debug.scope("GetIntrinsicGraph", graph)) {
                Plugins plugins = new Plugins(getGraphBuilderPlugins());
                GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
                IntrinsicContext initialReplacementContext = new IntrinsicContext(method, substMethod, bytecodeProvider, ROOT_COMPILATION);
                new GraphBuilderPhase.Instance(providers, config, OptimisticOptimizations.NONE, initialReplacementContext).apply(graph);
                assert !graph.isFrozen();
                return graph;
            } catch (Throwable e) {
                debug.handle(e);
            }
        }
        return null;
    }

    /**
     * Creates a preprocessed graph for a snippet or method substitution.
     *
     * @param bytecodeProvider how to access the bytecode of {@code method}
     * @param method the snippet or method substitution for which a graph will be created
     * @param args
     * @param original the original method if {@code method} is a {@linkplain MethodSubstitution
     *            substitution} otherwise null
     * @param trackNodeSourcePosition record source information
     * @param context
     *            {@link org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext
     *            compilation context} for the graph
     */
    public StructuredGraph makeGraph(DebugContext debug, BytecodeProvider bytecodeProvider, ResolvedJavaMethod method, Object[] args, ResolvedJavaMethod original, boolean trackNodeSourcePosition,
                    NodeSourcePosition replaceePosition, IntrinsicContext.CompilationContext context) {
        return createGraphMaker(method, original).makeGraph(debug, bytecodeProvider, args, trackNodeSourcePosition, replaceePosition, context);
    }

    /**
     * Creates a preprocessed graph for a snippet or method substitution with a context of .
     * {@link org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext#INLINE_AFTER_PARSING}
     * .
     *
     *
     * @param bytecodeProvider how to access the bytecode of {@code method}
     * @param method the snippet or method substitution for which a graph will be created
     * @param args
     * @param original the original method if {@code method} is a {@linkplain MethodSubstitution
     *            substitution} otherwise null
     * @param trackNodeSourcePosition record source information
     */
    public final StructuredGraph makeGraph(DebugContext debug, BytecodeProvider bytecodeProvider, ResolvedJavaMethod method, Object[] args, ResolvedJavaMethod original,
                    boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition) {
        return makeGraph(debug, bytecodeProvider, method, args, original, trackNodeSourcePosition, replaceePosition, INLINE_AFTER_PARSING);
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

        public GraphMaker(ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
            this.replacements = replacements;
            this.method = substitute;
            this.substitutedMethod = substitutedMethod;
        }

        @SuppressWarnings("try")
        public StructuredGraph makeGraph(DebugContext debug, BytecodeProvider bytecodeProvider, Object[] args, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition,
                        IntrinsicContext.CompilationContext context) {
            try (DebugContext.Scope s = debug.scope("BuildSnippetGraph", method)) {
                assert method.hasBytecodes() : method;
                StructuredGraph graph = buildInitialGraph(debug, bytecodeProvider, method, args, trackNodeSourcePosition, replaceePosition, context);

                finalizeGraph(graph);

                debug.dump(DebugContext.INFO_LEVEL, graph, "%s: Final", method.getName());

                return graph;
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }

        /**
         * Does final processing of a snippet graph.
         */
        protected void finalizeGraph(StructuredGraph graph) {
            if (!GraalOptions.SnippetCounters.getValue(graph.getOptions()) || graph.getNodes().filter(SnippetCounterNode.class).isEmpty()) {
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

        static class EncodedIntrinsicContext extends IntrinsicContext {
            EncodedIntrinsicContext(ResolvedJavaMethod method, ResolvedJavaMethod intrinsic, BytecodeProvider bytecodeProvider, CompilationContext compilationContext,
                            boolean allowPartialIntrinsicArgumentMismatch) {
                super(method, intrinsic, bytecodeProvider, compilationContext, allowPartialIntrinsicArgumentMismatch);
            }

            @Override
            public boolean isDeferredInvoke(StateSplit stateSplit) {
                if (IS_IN_NATIVE_IMAGE) {
                    throw GraalError.shouldNotReachHere("unused in libgraal");
                }
                if (stateSplit instanceof Invoke) {
                    Invoke invoke = (Invoke) stateSplit;
                    ResolvedJavaMethod method = invoke.callTarget().targetMethod();
                    if (method.getAnnotation(Fold.class) != null) {
                        return true;
                    }
                    Node.NodeIntrinsic annotation = method.getAnnotation(Node.NodeIntrinsic.class);
                    if (annotation != null && !annotation.hasSideEffect()) {
                        return true;
                    }
                }
                return false;
            }
        }

        /**
         * Builds the initial graph for a replacement.
         */
        @SuppressWarnings("try")
        protected StructuredGraph buildInitialGraph(DebugContext debug, BytecodeProvider bytecodeProvider, final ResolvedJavaMethod methodToParse, Object[] args, boolean trackNodeSourcePosition,
                        NodeSourcePosition replaceePosition, IntrinsicContext.CompilationContext context) {
            // @formatter:off
            // Replacements cannot have optimistic assumptions since they have
            // to be valid for the entire run of the VM.
            final StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug, AllowAssumptions.NO).
                            method(methodToParse).
                            trackNodeSourcePosition(trackNodeSourcePosition).
                            callerContext(replaceePosition).
                            setIsSubstitution(true).
                            build();
            // @formatter:on

            // Replacements are not user code so they do not participate in unsafe access
            // tracking
            graph.disableUnsafeAccessTracking();

            try (DebugContext.Scope s = debug.scope("buildInitialGraph", graph)) {
                MetaAccessProvider metaAccess = replacements.providers.getMetaAccess();

                Plugins plugins = new Plugins(replacements.graphBuilderPlugins);
                GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);
                if (args != null) {
                    plugins.prependParameterPlugin(new ConstantBindingParameterPlugin(args, metaAccess, replacements.snippetReflection));
                }

                IntrinsicContext initialIntrinsicContext = null;
                Snippet snippetAnnotation = null;
                MethodSubstitution methodAnnotation = null;
                if (!IS_IN_NATIVE_IMAGE) {
                    snippetAnnotation = method.getAnnotation(Snippet.class);
                    methodAnnotation = method.getAnnotation(MethodSubstitution.class);
                }
                if (methodAnnotation == null && snippetAnnotation == null) {
                    // Post-parse inlined intrinsic
                    initialIntrinsicContext = new EncodedIntrinsicContext(substitutedMethod, method, bytecodeProvider, context, false);
                } else {
                    // Snippet
                    ResolvedJavaMethod original = substitutedMethod != null ? substitutedMethod : method;
                    initialIntrinsicContext = new EncodedIntrinsicContext(original, method, bytecodeProvider, context,
                                    snippetAnnotation != null ? snippetAnnotation.allowPartialIntrinsicArgumentMismatch() : true);
                }

                createGraphBuilder(replacements.providers, config, OptimisticOptimizations.NONE, initialIntrinsicContext).apply(graph);

                CanonicalizerPhase.create().apply(graph, replacements.providers);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
            return graph;
        }

        protected Instance createGraphBuilder(Providers providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            return new GraphBuilderPhase.Instance(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
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

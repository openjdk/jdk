/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.runtime.JVMCI.getRuntime;
import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createIntrinsicInlineInfo;
import static org.graalvm.compiler.replacements.ReplacementsImpl.Options.UseEncodedSnippets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.api.runtime.GraalJVMCICompiler;
import org.graalvm.compiler.api.runtime.GraalRuntime;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.type.AbstractObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.SymbolicJVMCIReference;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.FullInfopointNode;
import org.graalvm.compiler.nodes.GraphEncoder;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.ParameterPlugin;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.DelegatingReplacements;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.ConstantBindingParameterPlugin;
import org.graalvm.compiler.replacements.PEGraphDecoder;
import org.graalvm.compiler.replacements.ReplacementsImpl;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetIntegerHistogram;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaField;
import jdk.vm.ci.meta.UnresolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * This class performs graph encoding using {@link GraphEncoder} but also converts JVMCI type and
 * method references into a symbolic form that can be resolved at graph decode time using
 * {@link SymbolicJVMCIReference}.
 */
public class SymbolicSnippetEncoder extends DelegatingReplacements {

    /**
     * This is a customized HotSpotReplacementsImpl intended only for parsing snippets and method
     * substitutions for graph encoding.
     */
    private final HotSpotSnippetReplacementsImpl replacements;

    /**
     * The set of all snippet methods that have been encoded.
     */
    private final Set<ResolvedJavaMethod> snippetMethods = Collections.synchronizedSet(new HashSet<>());

    /**
     * A mapping from the method substitution method to the original method name. The string key and
     * values are produced using {@link #methodKey(ResolvedJavaMethod)}.
     */
    private final Map<String, String> originalMethods = new ConcurrentHashMap<>();

    /**
     * The current count of graphs encoded. Used to detect when new graphs have been enqueued for
     * encoding.
     */
    int encodedGraphs = 0;

    /**
     * All the graphs parsed so far.
     */
    private Map<String, StructuredGraph> preparedSnippetGraphs = new HashMap<>();

    /**
     * The invocation plugins which were delayed during graph preparation.
     */
    private Set<ResolvedJavaMethod> delayedInvocationPluginMethods = new HashSet<>();

    void addDelayedInvocationPluginMethod(ResolvedJavaMethod method) {
        delayedInvocationPluginMethods.add(method);
    }

    Set<ResolvedJavaMethod> getSnippetMethods() {
        return snippetMethods;
    }

    protected class SnippetInlineInvokePlugin implements InlineInvokePlugin {

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            if (method.getAnnotation(Fold.class) != null) {
                delayedInvocationPluginMethods.add(method);
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }

            if (getIntrinsifyingPlugin(method) != null) {
                delayedInvocationPluginMethods.add(method);
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }

            // Force inlining when parsing replacements
            return createIntrinsicInlineInfo(method, null, getDefaultReplacementBytecodeProvider());
        }

        @Override
        public void notifyAfterInline(ResolvedJavaMethod methodToInline) {
            assert methodToInline.getAnnotation(Fold.class) == null : methodToInline;
        }
    }

    public static class SnippetInvocationPlugins extends InvocationPlugins {

        SnippetInvocationPlugins(InvocationPlugins invocationPlugins) {
            super(invocationPlugins);
        }

        @Override
        public InvocationPlugin lookupInvocation(ResolvedJavaMethod method) {
            if (method.getAnnotation(Fold.class) != null) {
                return null;
            }
            return super.lookupInvocation(method);
        }
    }

    /**
     * This plugin disables the snippet counter machinery.
     */
    private class SnippetCounterPlugin implements NodePlugin {
        String snippetCounterName = 'L' + SnippetCounter.class.getName().replace('.', '/') + ';';
        String snippetIntegerHistogramName = 'L' + SnippetIntegerHistogram.class.getName().replace('.', '/') + ';';

        @Override
        public boolean handleLoadField(GraphBuilderContext b, ValueNode object, ResolvedJavaField field) {
            if (field.getName().equals("group") && field.getDeclaringClass().getName().equals(snippetCounterName)) {
                b.addPush(JavaKind.Object, ConstantNode.forConstant(JavaConstant.NULL_POINTER, b.getMetaAccess()));
                return true;
            }
            if (field.getType().getName().equals(snippetCounterName)) {
                b.addPush(JavaKind.Object, ConstantNode.forConstant(replacements.snippetReflection.forObject(SnippetCounter.DISABLED_COUNTER), b.getMetaAccess()));
                return true;
            }

            if (field.getType().getName().equals(snippetIntegerHistogramName)) {
                b.addPush(JavaKind.Object, ConstantNode.forConstant(replacements.snippetReflection.forObject(SnippetIntegerHistogram.DISABLED_COUNTER), b.getMetaAccess()));
                return true;
            }
            return false;
        }
    }

    /**
     * Generate a String name for a method including all type information. Used as a symbolic key
     * for lookup.
     */
    private static String methodKey(ResolvedJavaMethod method) {
        return method.format("%f %H.%n(%P)");
    }

    SymbolicSnippetEncoder(HotSpotReplacementsImpl replacements) {
        super(replacements);

        GraphBuilderConfiguration.Plugins plugins = replacements.getGraphBuilderPlugins();
        SnippetInvocationPlugins invocationPlugins = new SnippetInvocationPlugins(plugins.getInvocationPlugins());
        GraphBuilderConfiguration.Plugins copy = new GraphBuilderConfiguration.Plugins(plugins, invocationPlugins);
        copy.clearInlineInvokePlugins();
        copy.appendInlineInvokePlugin(new SnippetInlineInvokePlugin());
        copy.appendNodePlugin(new SnippetCounterPlugin());
        HotSpotProviders providers = (HotSpotProviders) replacements.getProviders().copyWith(new HotSpotSubstrateConstantReflectionProvider(replacements.getProviders().getConstantReflection()));
        this.replacements = new HotSpotSnippetReplacementsImpl(replacements, providers.copyWith(copy));
        this.replacements.setGraphBuilderPlugins(copy);
    }

    @Override
    public GraphBuilderConfiguration.Plugins getGraphBuilderPlugins() {
        return replacements.getGraphBuilderPlugins();
    }

    /**
     * Compiles the snippet and stores the graph.
     */
    public void registerMethodSubstitution(ResolvedJavaMethod method, ResolvedJavaMethod original) {
        assert method.getAnnotation(MethodSubstitution.class) != null : "MethodSubstitution must be annotated with @" + MethodSubstitution.class.getSimpleName();
        buildGraph(method, original, null, false, false);
        snippetMethods.add(method);
    }

    static class EncodedSnippets {
        private byte[] snippetEncoding;
        private Object[] snippetObjects;
        private NodeClass<?>[] snippetNodeClasses;
        private Map<String, Integer> snippetStartOffsets;
        private Map<String, String> originalMethods;

        EncodedSnippets(byte[] snippetEncoding, Object[] snippetObjects, NodeClass<?>[] snippetNodeClasses, Map<String, Integer> snippetStartOffsets, Map<String, String> originalMethods) {
            this.snippetEncoding = snippetEncoding;
            this.snippetObjects = snippetObjects;
            this.snippetNodeClasses = snippetNodeClasses;
            this.snippetStartOffsets = snippetStartOffsets;
            this.originalMethods = originalMethods;
        }

        public StructuredGraph getMethodSubstitutionGraph(MethodSubstitutionPlugin plugin, ResolvedJavaMethod original, ReplacementsImpl replacements) {
            Integer startOffset = snippetStartOffsets.get(plugin.toString());
            if (startOffset == null) {
                throw GraalError.shouldNotReachHere("plugin graph not found: " + plugin);
            }

            return decodeGraph(original, null, startOffset, replacements);
        }

        @SuppressWarnings("try")
        private StructuredGraph decodeGraph(ResolvedJavaMethod method, Object[] args, int startOffset, ReplacementsImpl replacements) {
            OptionValues options = replacements.getOptions();
            SnippetReflectionProvider snippetReflection = replacements.snippetReflection;
            ParameterPlugin parameterPlugin = null;
            Providers providers = replacements.getProviders();
            if (args != null) {
                parameterPlugin = new ConstantBindingParameterPlugin(args, providers.getMetaAccess(), snippetReflection);
            }

            EncodedGraph encodedGraph = new SymbolicEncodedGraph(snippetEncoding, startOffset, snippetObjects, snippetNodeClasses, method.getDeclaringClass(),
                            originalMethods.get(methodKey(method)));
            try (DebugContext debug = replacements.openDebugContext("SVMSnippet_", method)) {
                StructuredGraph result = new StructuredGraph.Builder(options, debug).method(method).setIsSubstitution(true).build();
                PEGraphDecoder graphDecoder = new PEGraphDecoder(
                                providers.getCodeCache().getTarget().arch,
                                result,
                                providers,
                                null, // loopExplosionPlugin
                                replacements.getGraphBuilderPlugins().getInvocationPlugins(),
                                new InlineInvokePlugin[0],
                                parameterPlugin,
                                null, // nodePlugins
                                null, // callInlinedMethod
                                null // sourceLanguagePositionProvider
                ) {
                    @Override
                    protected EncodedGraph lookupEncodedGraph(ResolvedJavaMethod lookupMethod,
                                    ResolvedJavaMethod originalMethod,
                                    BytecodeProvider intrinsicBytecodeProvider,
                                    boolean isSubstitution,
                                    boolean trackNodeSourcePosition) {
                        if (lookupMethod.equals(method)) {
                            return encodedGraph;
                        } else {
                            throw GraalError.shouldNotReachHere(method.format("%H.%n(%p)"));
                        }
                    }
                };

                graphDecoder.decode(method, result.isSubstitution(), encodedGraph.trackNodeSourcePosition());

                assert result.verify();
                return result;
            }
        }

        StructuredGraph getEncodedSnippet(ResolvedJavaMethod method, ReplacementsImpl replacements, Object[] args) {
            Integer startOffset = null;
            if (snippetStartOffsets != null) {
                startOffset = snippetStartOffsets.get(methodKey(method));
            }
            if (startOffset == null) {
                if (IS_IN_NATIVE_IMAGE) {
                    throw GraalError.shouldNotReachHere("snippet not found: " + method.format("%H.%n(%p)"));
                } else {
                    return null;
                }
            }

            SymbolicEncodedGraph encodedGraph = new SymbolicEncodedGraph(snippetEncoding, startOffset, snippetObjects, snippetNodeClasses, method.getDeclaringClass(),
                            originalMethods.get(methodKey(method)));
            return decodeSnippetGraph(encodedGraph, method, replacements, args, HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch);
        }

    }

    private StructuredGraph buildGraph(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean requireInlining, boolean trackNodeSourcePosition) {
        assert method.hasBytecodes() : "Snippet must not be abstract or native";
        Object[] args = null;
        if (receiver != null) {
            args = new Object[method.getSignature().getParameterCount(true)];
            args[0] = receiver;
        }
        try (DebugContext debug = openDebugContext("Snippet_", method)) {
            StructuredGraph graph = replacements.makeGraph(debug, replacements.getDefaultReplacementBytecodeProvider(), method, args, original, trackNodeSourcePosition, null);

            // Check if all methods which should be inlined are really inlined.
            for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
                ResolvedJavaMethod callee = callTarget.targetMethod();
                if (requireInlining && !delayedInvocationPluginMethods.contains(callee) && !Objects.equals(callee, original)) {
                    throw GraalError.shouldNotReachHere("method " + callee.format("%H.%n") + " not inlined in snippet " + method.getName() + " (maybe not final?)");
                }
            }
            assert verifySnippetEncodeDecode(method, original, trackNodeSourcePosition, graph);
            debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After buildGraph");
            return graph;
        }
    }

    @SuppressWarnings("try")
    static StructuredGraph decodeSnippetGraph(SymbolicEncodedGraph encodedGraph, ResolvedJavaMethod method, ReplacementsImpl replacements, Object[] args, Architecture architecture) {
        Providers providers = replacements.getProviders();
        ParameterPlugin parameterPlugin = null;
        if (args != null) {
            parameterPlugin = new ConstantBindingParameterPlugin(args, providers.getMetaAccess(), replacements.snippetReflection);
        }

        try (DebugContext debug = replacements.openDebugContext("SVMSnippet_", method)) {
            // @formatter:off
            StructuredGraph result = new StructuredGraph.Builder(replacements.getOptions(), debug)
                            .method(method)
                            .trackNodeSourcePosition(encodedGraph.trackNodeSourcePosition())
                            .setIsSubstitution(true)
                            .build();
            // @formatter:on
            try (DebugContext.Scope scope = debug.scope("DecodeSnippetGraph", result)) {
                PEGraphDecoder graphDecoder = new PEGraphDecoder(
                                architecture,
                                result,
                                providers,
                                null,
                                replacements.getGraphBuilderPlugins().getInvocationPlugins(),
                                new InlineInvokePlugin[0],
                                parameterPlugin,
                                null,
                                null,
                                null) {
                    @Override
                    protected EncodedGraph lookupEncodedGraph(
                                    ResolvedJavaMethod lookupMethod,
                                    ResolvedJavaMethod originalMethod,
                                    BytecodeProvider intrinsicBytecodeProvider,
                                    boolean isSubstitution,
                                    boolean track) {
                        if (lookupMethod.equals(method)) {
                            assert !track || encodedGraph.trackNodeSourcePosition();
                            return encodedGraph;
                        } else {
                            throw GraalError.shouldNotReachHere(method.format("%H.%n(%p)"));
                        }
                    }
                };

                graphDecoder.decode(method, result.isSubstitution(), encodedGraph.trackNodeSourcePosition());
                debug.dump(DebugContext.VERBOSE_LEVEL, result, "After decoding");

                assert result.verify();
                return result;
            } catch (Throwable t) {
                throw debug.handle(t);
            }
        }
    }

    @SuppressWarnings("try")
    private boolean verifySnippetEncodeDecode(ResolvedJavaMethod method, ResolvedJavaMethod original, boolean trackNodeSourcePosition, StructuredGraph structuredGraph) {
        // Verify the encoding and decoding process
        EncodedGraph encodedGraph = GraphEncoder.encodeSingleGraph(structuredGraph, HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch);

        Architecture arch = HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch;

        try (DebugContext debug = replacements.openDebugContext("VerifySnippetEncodeDecode_", method)) {
            HotSpotProviders originalProvider = (HotSpotProviders) replacements.getProviders();

            SnippetReflectionProvider snippetReflection = originalProvider.getSnippetReflection();
            SymbolicSnippetEncoder.HotSpotSubstrateConstantReflectionProvider constantReflection = new SymbolicSnippetEncoder.HotSpotSubstrateConstantReflectionProvider(
                            originalProvider.getConstantReflection());
            HotSpotProviders newProviders = new HotSpotProviders(originalProvider.getMetaAccess(), originalProvider.getCodeCache(), constantReflection,
                            originalProvider.getConstantFieldProvider(), originalProvider.getForeignCalls(), originalProvider.getLowerer(), null, originalProvider.getSuites(),
                            originalProvider.getRegisters(), snippetReflection, originalProvider.getWordTypes(), originalProvider.getGraphBuilderPlugins());
            HotSpotSnippetReplacementsImpl filteringReplacements = new HotSpotSnippetReplacementsImpl(getOptions(), newProviders, snippetReflection,
                            originalProvider.getReplacements().getDefaultReplacementBytecodeProvider(),
                            originalProvider.getCodeCache().getTarget());
            filteringReplacements.setGraphBuilderPlugins(originalProvider.getReplacements().getGraphBuilderPlugins());
            try (DebugContext.Scope scaope = debug.scope("VerifySnippetEncodeDecode", structuredGraph)) {
                for (int i = 0; i < encodedGraph.getNumObjects(); i++) {
                    filterSnippetObject(encodedGraph.getObject(i));
                }
                StructuredGraph snippet = filteringReplacements.makeGraph(debug, filteringReplacements.getDefaultReplacementBytecodeProvider(), method, null, original,
                                trackNodeSourcePosition, null);
                SymbolicEncodedGraph symbolicGraph = new SymbolicEncodedGraph(encodedGraph, method.getDeclaringClass(), original != null ? methodKey(original) : null);
                StructuredGraph decodedSnippet = decodeSnippetGraph(symbolicGraph, method, replacements, null, arch);
                String snippetString = getCanonicalGraphString(snippet, true, false);
                String decodedSnippetString = getCanonicalGraphString(decodedSnippet, true, false);
                if (snippetString.equals(decodedSnippetString)) {
                    debug.log("Snippet decode for %s produces exactly same graph", method);
                    debug.dump(DebugContext.INFO_LEVEL, decodedSnippet, "Decoded snippet graph for %s", method);
                } else {
                    debug.log("Snippet decode for %s produces different graph", method);
                    debug.log("%s", compareGraphStrings(snippet, snippetString, decodedSnippet, decodedSnippetString));
                    debug.dump(DebugContext.INFO_LEVEL, snippet, "Snippet graph for %s", method);
                    debug.dump(DebugContext.INFO_LEVEL, structuredGraph, "Encoded snippet graph for %s", method);
                    debug.dump(DebugContext.INFO_LEVEL, decodedSnippet, "Decoded snippet graph for %s", method);
                }
            } catch (Throwable t) {
                throw debug.handle(t);
            }
        }
        return true;
    }

    /**
     * If there are new graphs waiting to be encoded, reencode all the graphs and return the result.
     */
    @SuppressWarnings("try")
    synchronized EncodedSnippets maybeEncodeSnippets() {
        Map<String, StructuredGraph> graphs = this.preparedSnippetGraphs;
        if (encodedGraphs != graphs.size()) {
            DebugContext debug = openDebugContext("SnippetEncoder", null);
            try (DebugContext.Scope scope = debug.scope("SnippetSupportEncode")) {
                encodedGraphs = graphs.size();
                for (StructuredGraph graph : graphs.values()) {
                    for (Node node : graph.getNodes()) {
                        node.setNodeSourcePosition(null);
                    }
                }
                return encodeSnippets(debug);
            }
        }
        return null;
    }

    @Override
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition) {
        if (IS_BUILDING_NATIVE_IMAGE || UseEncodedSnippets.getValue(getOptions())) {
            assert method.getAnnotation(Snippet.class) != null : "Snippet must be annotated with @" + Snippet.class.getSimpleName();
            String key = methodKey(method);
            if (!preparedSnippetGraphs.containsKey(key)) {
                if (original != null) {
                    originalMethods.put(key, methodKey(original));
                }
                StructuredGraph snippet = buildGraph(method, original, receiver, true, trackNodeSourcePosition);
                snippetMethods.add(method);
                preparedSnippetGraphs.put(key, snippet);
            }
        }
    }

    EncodedSnippets encodeSnippets(DebugContext debug) {
        GraphEncoder encoder = new GraphEncoder(HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch, debug);
        for (StructuredGraph graph : preparedSnippetGraphs.values()) {
            encoder.prepare(graph);
        }
        encoder.finishPrepare();

        byte[] snippetEncoding;
        Object[] snippetObjects;
        NodeClass<?>[] snippetNodeClasses;
        Map<String, Integer> snippetStartOffsets;

        snippetStartOffsets = new HashMap<>();
        for (Map.Entry<String, StructuredGraph> entry : preparedSnippetGraphs.entrySet()) {
            snippetStartOffsets.put(entry.getKey(), encoder.encode(entry.getValue()));
        }
        snippetEncoding = encoder.getEncoding();
        snippetObjects = encoder.getObjects();
        snippetNodeClasses = encoder.getNodeClasses();
        for (int i = 0; i < snippetObjects.length; i++) {
            Object o = filterSnippetObject(snippetObjects[i]);
            debug.log("snippetObjects[%d] = %s -> %s", i, o != null ? o.getClass().getSimpleName() : null, o);
            snippetObjects[i] = o;
        }
        debug.log("Encoded %d snippet preparedSnippetGraphs using %d bytes with %d objects", snippetStartOffsets.size(), snippetEncoding.length, snippetObjects.length);
        return new EncodedSnippets(snippetEncoding, snippetObjects, snippetNodeClasses, snippetStartOffsets, originalMethods);
    }

    /**
     * Encode any outstanding graphs and return true if any work was done.
     */
    @SuppressWarnings("try")
    public boolean encode() {
        EncodedSnippets encodedSnippets = maybeEncodeSnippets();
        if (encodedSnippets != null) {
            HotSpotReplacementsImpl.setEncodedSnippets(encodedSnippets);
            return true;
        }
        return false;
    }

    private DebugContext openDebugContext(String idPrefix, ResolvedJavaMethod method) {
        return replacements.openDebugContext(idPrefix, method);
    }

    static class SymbolicEncodedGraph extends EncodedGraph {

        private final ResolvedJavaType accessingClass;
        private final String originalMethod;

        SymbolicEncodedGraph(byte[] encoding, int startOffset, Object[] objects, NodeClass<?>[] types, ResolvedJavaType accessingClass, String originalMethod) {
            super(encoding, startOffset, objects, types, null, null, null, false, false);
            this.accessingClass = accessingClass;
            this.originalMethod = originalMethod;
        }

        SymbolicEncodedGraph(EncodedGraph encodedGraph, ResolvedJavaType declaringClass, String originalMethod) {
            this(encodedGraph.getEncoding(), encodedGraph.getStartOffset(), encodedGraph.getObjects(), encodedGraph.getNodeClasses(), declaringClass, originalMethod);
        }

        @Override
        public Object getObject(int i) {
            Object o = objects[i];
            if (o instanceof SymbolicJVMCIReference) {
                objects[i] = o = ((SymbolicJVMCIReference<?>) o).resolve(accessingClass);
            } else if (o instanceof UnresolvedJavaType) {
                objects[i] = o = ((UnresolvedJavaType) o).resolve(accessingClass);
            } else if (o instanceof UnresolvedJavaMethod) {
                throw new InternalError(o.toString());
            } else if (o instanceof UnresolvedJavaField) {
                objects[i] = o = ((UnresolvedJavaField) o).resolve(accessingClass);
            } else if (o instanceof GraalCapability) {
                objects[i] = o = ((GraalCapability) o).resolve(((GraalJVMCICompiler) getRuntime().getCompiler()).getGraalRuntime());
            }
            return o;
        }

        @Override
        public boolean isCallToOriginal(ResolvedJavaMethod callTarget) {
            if (originalMethod != null && originalMethod.equals(methodKey(callTarget))) {
                return true;
            }
            return super.isCallToOriginal(callTarget);
        }
    }

    /**
     * Symbolic reference to an object which can be retrieved from
     * {@link GraalRuntime#getCapability(Class)}.
     */
    static class GraalCapability {
        final Class<?> capabilityClass;

        GraalCapability(Class<?> capabilityClass) {
            this.capabilityClass = capabilityClass;
        }

        public Object resolve(GraalRuntime runtime) {
            Object capability = runtime.getCapability(this.capabilityClass);
            if (capability != null) {
                assert capability.getClass() == capabilityClass;
                return capability;
            }
            throw new InternalError(this.capabilityClass.getName());
        }
    }

    static class SymbolicResolvedJavaMethod implements SymbolicJVMCIReference<ResolvedJavaMethod> {
        final UnresolvedJavaType type;
        final String methodName;
        final String signature;

        SymbolicResolvedJavaMethod(HotSpotResolvedJavaMethod method) {
            this.type = UnresolvedJavaType.create(method.getDeclaringClass().getName());
            this.methodName = method.getName();
            this.signature = method.getSignature().toMethodDescriptor();
        }

        @Override
        public String toString() {
            return "SymbolicResolvedJavaMethod{" +
                            "declaringType='" + type.getName() + '\'' +
                            ", methodName='" + methodName + '\'' +
                            ", signature='" + signature + '\'' +
                            '}';
        }

        @Override
        public ResolvedJavaMethod resolve(ResolvedJavaType accessingClass) {
            ResolvedJavaType resolvedType = type.resolve(accessingClass);
            for (ResolvedJavaMethod method : methodName.equals("<init>") ? resolvedType.getDeclaredConstructors() : resolvedType.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getSignature().toMethodDescriptor().equals(signature)) {
                    return method;
                }
            }
            throw new InternalError("Could not resolve " + this + " in context of " + accessingClass.toJavaName());
        }
    }

    static class SymbolicResolvedJavaField implements SymbolicJVMCIReference<ResolvedJavaField> {
        final UnresolvedJavaType declaringType;
        final String name;
        final UnresolvedJavaType signature;
        private final boolean isStatic;

        SymbolicResolvedJavaField(HotSpotResolvedJavaField field) {
            this.declaringType = UnresolvedJavaType.create(field.getDeclaringClass().getName());
            this.name = field.getName();
            this.signature = UnresolvedJavaType.create(field.getType().getName());
            this.isStatic = field.isStatic();
        }

        @Override
        public ResolvedJavaField resolve(ResolvedJavaType accessingClass) {
            ResolvedJavaType resolvedType = declaringType.resolve(accessingClass);
            ResolvedJavaType resolvedFieldType = signature.resolve(accessingClass);
            ResolvedJavaField[] fields = isStatic ? resolvedType.getStaticFields() : resolvedType.getInstanceFields(true);
            for (ResolvedJavaField field : fields) {
                if (field.getName().equals(name)) {
                    if (field.getType().equals(resolvedFieldType)) {
                        return field;
                    }
                }
            }
            throw new InternalError("Could not resolve " + this + " in context of " + accessingClass.toJavaName());
        }

        @Override
        public String toString() {
            return "SymbolicResolvedJavaField{" +
                            signature.getName() + ' ' +
                            declaringType.getName() + '.' +
                            name +
                            '}';
        }
    }

    static class SymbolicStampPair implements SymbolicJVMCIReference<StampPair> {
        Object trustedStamp;
        Object uncheckdStamp;

        SymbolicStampPair(StampPair stamp) {
            this.trustedStamp = maybeMakeSymbolic(stamp.getTrustedStamp());
            this.uncheckdStamp = maybeMakeSymbolic(stamp.getUncheckedStamp());
        }

        @Override
        public StampPair resolve(ResolvedJavaType accessingClass) {
            return StampPair.create(resolveStamp(accessingClass, trustedStamp), resolveStamp(accessingClass, uncheckdStamp));
        }
    }

    private static Object maybeMakeSymbolic(Stamp trustedStamp) {
        if (trustedStamp != null) {
            SymbolicJVMCIReference<?> symbolicJVMCIReference = trustedStamp.makeSymbolic();
            if (symbolicJVMCIReference != null) {
                return symbolicJVMCIReference;
            }
        }
        return trustedStamp;
    }

    private static Stamp resolveStamp(ResolvedJavaType accessingClass, Object stamp) {
        if (stamp == null) {
            return null;
        }
        if (stamp instanceof Stamp) {
            return (Stamp) stamp;
        }
        return (Stamp) ((SymbolicJVMCIReference<?>) stamp).resolve(accessingClass);
    }

    public static class HotSpotSubstrateConstantReflectionProvider implements ConstantReflectionProvider {

        private final ConstantReflectionProvider constantReflection;

        HotSpotSubstrateConstantReflectionProvider(ConstantReflectionProvider constantReflection) {
            this.constantReflection = constantReflection;
        }

        HashSet<JavaConstant> safeConstants = new HashSet<>();

        @Override
        public Boolean constantEquals(Constant x, Constant y) {
            return constantReflection.constantEquals(x, y);
        }

        @Override
        public Integer readArrayLength(JavaConstant array) {
            return constantReflection.readArrayLength(array);
        }

        @Override
        public JavaConstant readArrayElement(JavaConstant array, int index) {
            return constantReflection.readArrayElement(array, index);
        }

        @Override
        public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
            JavaConstant javaConstant = constantReflection.readFieldValue(field, receiver);
            if (!safeConstants.contains(receiver) && !field.getDeclaringClass().getName().contains("graalvm") && !field.getDeclaringClass().getName().contains("jdk/vm/ci/") &&
                            !field.getName().equals("TYPE")) {
                // Only permit constant reflection on compiler classes. This is necessary primarily
                // because of the boxing snippets which are compiled as snippets but are really just
                // regular JDK java sources that are being compiled like a snippet. These shouldn't
                // permit constant folding during graph preparation as that embeds constants from
                // the runtime into a compiler graph.
                return null;
            }
            if (javaConstant.getJavaKind() == JavaKind.Object) {
                safeConstants.add(javaConstant);
            }
            return javaConstant;
        }

        @Override
        public JavaConstant boxPrimitive(JavaConstant source) {
            return constantReflection.boxPrimitive(source);
        }

        @Override
        public JavaConstant unboxPrimitive(JavaConstant source) {
            return constantReflection.unboxPrimitive(source);
        }

        @Override
        public JavaConstant forString(String value) {
            return constantReflection.forString(value);
        }

        @Override
        public ResolvedJavaType asJavaType(Constant constant) {
            return constantReflection.asJavaType(constant);
        }

        @Override
        public MethodHandleAccessProvider getMethodHandleAccess() {
            return constantReflection.getMethodHandleAccess();
        }

        @Override
        public MemoryAccessProvider getMemoryAccessProvider() {
            return constantReflection.getMemoryAccessProvider();
        }

        @Override
        public JavaConstant asJavaClass(ResolvedJavaType type) {
            return constantReflection.asJavaClass(type);
        }

        @Override
        public Constant asObjectHub(ResolvedJavaType type) {
            return constantReflection.asObjectHub(type);
        }
    }

    /**
     * Objects embedded in encoded graphs might need to converted into a symbolic form so convert
     * the object or pass it through.
     */
    static Object filterSnippetObject(Object o) {
        if (o instanceof HotSpotResolvedJavaMethod) {
            return new SymbolicResolvedJavaMethod((HotSpotResolvedJavaMethod) o);
        } else if (o instanceof HotSpotResolvedJavaField) {
            return new SymbolicResolvedJavaField((HotSpotResolvedJavaField) o);
        } else if (o instanceof HotSpotResolvedJavaType) {
            return UnresolvedJavaType.create(((HotSpotResolvedJavaType) o).getName());
        } else if (o instanceof NodeSourcePosition) {
            // Filter these out for now. These can't easily be handled because these positions
            // description snippet methods which might not be available in the runtime.
            return null;
        } else if (o instanceof HotSpotForeignCallsProvider || o instanceof GraalHotSpotVMConfig) {
            return new GraalCapability(o.getClass());
        } else if (o instanceof Stamp) {
            SymbolicJVMCIReference<?> ref = ((Stamp) o).makeSymbolic();
            if (ref != null) {
                return ref;
            }
            return o;
        } else if (o instanceof StampPair) {
            if (((StampPair) o).getTrustedStamp() instanceof AbstractObjectStamp) {
                return new SymbolicStampPair((StampPair) o);
            }
        }
        return o;
    }

    static String compareGraphStrings(StructuredGraph expectedGraph, String expectedString, StructuredGraph actualGraph, String actualString) {
        if (!expectedString.equals(actualString)) {
            String[] expectedLines = expectedString.split("\n");
            String[] actualLines = actualString.split("\n");
            int diffIndex = -1;
            int limit = Math.min(actualLines.length, expectedLines.length);
            String marker = " <<<";
            for (int i = 0; i < limit; i++) {
                if (!expectedLines[i].equals(actualLines[i])) {
                    diffIndex = i;
                    break;
                }
            }
            if (diffIndex == -1) {
                // Prefix is the same so add some space after the prefix
                diffIndex = limit;
                if (actualLines.length == limit) {
                    actualLines = Arrays.copyOf(actualLines, limit + 1);
                    actualLines[diffIndex] = "";
                } else {
                    assert expectedLines.length == limit;
                    expectedLines = Arrays.copyOf(expectedLines, limit + 1);
                    expectedLines[diffIndex] = "";
                }
            }
            // Place a marker next to the first line that differs
            expectedLines[diffIndex] = expectedLines[diffIndex] + marker;
            actualLines[diffIndex] = actualLines[diffIndex] + marker;
            String ediff = String.join("\n", expectedLines);
            String adiff = String.join("\n", actualLines);
            return "mismatch in preparedSnippetGraphs:\n========= expected (" + expectedGraph + ") =========\n" + ediff + "\n\n========= actual (" + actualGraph + ") =========\n" + adiff;
        } else {
            return "mismatch in preparedSnippetGraphs";
        }
    }

    static String getCanonicalGraphString(StructuredGraph graph, boolean excludeVirtual, boolean checkConstants) {
        SchedulePhase schedule = new SchedulePhase(SchedulePhase.SchedulingStrategy.EARLIEST);
        schedule.apply(graph);
        StructuredGraph.ScheduleResult scheduleResult = graph.getLastSchedule();

        NodeMap<Integer> canonicalId = graph.createNodeMap();
        int nextId = 0;

        List<String> constantsLines = new ArrayList<>();

        StringBuilder result = new StringBuilder();
        for (Block block : scheduleResult.getCFG().getBlocks()) {
            result.append("Block ").append(block).append(' ');
            if (block == scheduleResult.getCFG().getStartBlock()) {
                result.append("* ");
            }
            result.append("-> ");
            for (Block succ : block.getSuccessors()) {
                result.append(succ).append(' ');
            }
            result.append('\n');
            for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                if (node instanceof ValueNode && node.isAlive()) {
                    if (!excludeVirtual || !(node instanceof VirtualObjectNode || node instanceof ProxyNode || node instanceof FullInfopointNode || node instanceof ParameterNode)) {
                        if (node instanceof ConstantNode) {
                            if (checkConstants) {
                                String name = node.toString(Verbosity.Name);
                                if (excludeVirtual) {
                                    constantsLines.add(name);
                                } else {
                                    constantsLines.add(name + "    (" + filteredUsageCount(node) + ")");
                                }
                            }
                        } else {
                            int id;
                            if (canonicalId.get(node) != null) {
                                id = canonicalId.get(node);
                            } else {
                                id = nextId++;
                                canonicalId.set(node, id);
                            }
                            String name = node.getClass().getSimpleName();
                            result.append("  ").append(id).append('|').append(name);
                            if (node instanceof AccessFieldNode) {
                                result.append('#');
                                result.append(((AccessFieldNode) node).field());
                            }
                            if (!excludeVirtual) {
                                result.append("    (");
                                result.append(filteredUsageCount(node));
                                result.append(')');
                            }
                            result.append('\n');
                        }
                    }
                }
            }
        }

        StringBuilder constantsLinesResult = new StringBuilder();
        if (checkConstants) {
            constantsLinesResult.append(constantsLines.size()).append(" constants:\n");
        }
        Collections.sort(constantsLines);
        for (String s : constantsLines) {
            constantsLinesResult.append(s);
            constantsLinesResult.append('\n');
        }

        return constantsLinesResult.toString() + result.toString();
    }

    private static int filteredUsageCount(Node node) {
        return node.usages().filter(n -> !(n instanceof FrameState)).count();
    }

    /**
     * This horror show of classes exists solely get {@link HotSpotSnippetBytecodeParser} to be used
     * as the parser for these snippets.
     */
    static class HotSpotSnippetReplacementsImpl extends HotSpotReplacementsImpl {
        HotSpotSnippetReplacementsImpl(HotSpotReplacementsImpl replacements, Providers providers) {
            super(replacements, providers);
        }

        HotSpotSnippetReplacementsImpl(OptionValues options, Providers providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider, TargetDescription target) {
            super(options, providers, snippetReflection, bytecodeProvider, target);
        }

        @Override
        protected GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
            return new SnippetGraphMaker(this, substitute, original);
        }
    }

    static class SnippetGraphMaker extends ReplacementsImpl.GraphMaker {
        SnippetGraphMaker(ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
            super(replacements, substitute, substitutedMethod);
        }

        @Override
        protected GraphBuilderPhase.Instance createGraphBuilder(Providers providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                        IntrinsicContext initialIntrinsicContext) {
            return new HotSpotSnippetGraphBuilderPhase(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        }
    }

    static class HotSpotSnippetGraphBuilderPhase extends GraphBuilderPhase.Instance {
        HotSpotSnippetGraphBuilderPhase(Providers theProviders, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            super(theProviders, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        }

        @Override
        protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            return new HotSpotSnippetBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
        }
    }

    static class HotSpotSnippetBytecodeParser extends BytecodeParser {
        HotSpotSnippetBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
        }

        @Override
        public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
            return plugin.getSource().equals(Fold.class) || plugin.getSource().equals(Node.NodeIntrinsic.class);
        }

        @Override
        protected boolean tryInvocationPlugin(CallTargetNode.InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType, JavaType returnType) {
            if (intrinsicContext != null && intrinsicContext.isCallToOriginal(targetMethod)) {
                return false;
            }
            return super.tryInvocationPlugin(invokeKind, args, targetMethod, resultType, returnType);
        }
    }
}

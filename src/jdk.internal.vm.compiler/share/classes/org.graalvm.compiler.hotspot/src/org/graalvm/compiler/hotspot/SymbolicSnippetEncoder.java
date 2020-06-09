/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.services.Services.IS_BUILDING_NATIVE_IMAGE;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.compiler.core.common.GraalOptions.UseEncodedGraphs;
import static org.graalvm.compiler.hotspot.EncodedSnippets.methodKey;
import static org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin.InlineInfo.createIntrinsicInlineInfo;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.ROOT_COMPILATION_ENCODING;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.EconomicSet;
import jdk.internal.vm.compiler.collections.MapCursor;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
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
import org.graalvm.compiler.hotspot.EncodedSnippets.GraalCapability;
import org.graalvm.compiler.hotspot.EncodedSnippets.SymbolicEncodedGraph;
import org.graalvm.compiler.hotspot.EncodedSnippets.SymbolicResolvedJavaField;
import org.graalvm.compiler.hotspot.EncodedSnippets.SymbolicResolvedJavaMethod;
import org.graalvm.compiler.hotspot.EncodedSnippets.SymbolicResolvedJavaMethodBytecode;
import org.graalvm.compiler.hotspot.EncodedSnippets.SymbolicStampPair;
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
import org.graalvm.compiler.nodes.spi.SnippetParameterInfo;
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

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaField;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.hotspot.HotSpotSignature;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.UnresolvedJavaType;

/**
 * This class performs graph encoding using {@link GraphEncoder} but also converts JVMCI type and
 * method references into a symbolic form that can be resolved at graph decode time using
 * {@link SymbolicJVMCIReference}.
 */
public class SymbolicSnippetEncoder {

    /**
     * This is a customized HotSpotReplacementsImpl intended only for parsing snippets and method
     * substitutions for graph encoding.
     */
    private final HotSpotSnippetReplacementsImpl snippetReplacements;

    /**
     * A mapping from the method substitution method to the original method name. The string key and
     * values are produced using {@link EncodedSnippets#methodKey(ResolvedJavaMethod)}.
     */
    private final EconomicMap<String, String> originalMethods = EconomicMap.create();

    private final HotSpotReplacementsImpl originalReplacements;

    /**
     * The current count of graphs encoded. Used to detect when new graphs have been enqueued for
     * encoding.
     */
    private int encodedGraphs = 0;

    /**
     * All the graphs parsed so far.
     */
    private EconomicMap<String, StructuredGraph> preparedSnippetGraphs = EconomicMap.create();

    private EconomicMap<String, SnippetParameterInfo> snippetParameterInfos = EconomicMap.create();

    private EconomicSet<MethodSubstitutionPlugin> knownPlugins = EconomicSet.create();

    private EconomicSet<InvocationPlugin> conditionalPlugins = EconomicSet.create();

    private int preparedPlugins = 0;

    /**
     * The invocation plugins which were delayed during graph preparation.
     */
    private Set<ResolvedJavaMethod> delayedInvocationPluginMethods = new HashSet<>();

    void addDelayedInvocationPluginMethod(ResolvedJavaMethod method) {
        delayedInvocationPluginMethods.add(method);
    }

    public void clearSnippetParameterNames() {
        MapCursor<String, SnippetParameterInfo> cursor = snippetParameterInfos.getEntries();
        while (cursor.advance()) {
            cursor.getValue().clearNames();
        }
    }

    protected class SnippetInlineInvokePlugin implements InlineInvokePlugin {

        @Override
        public InlineInfo shouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            if (method.getAnnotation(Fold.class) != null) {
                delayedInvocationPluginMethods.add(method);
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }

            if (snippetReplacements.getIntrinsifyingPlugin(method) != null) {
                delayedInvocationPluginMethods.add(method);
                return InlineInfo.DO_NOT_INLINE_NO_EXCEPTION;
            }

            // Force inlining when parsing replacements
            return createIntrinsicInlineInfo(method, snippetReplacements.getDefaultReplacementBytecodeProvider());
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
                b.addPush(JavaKind.Object, ConstantNode.forConstant(snippetReplacements.snippetReflection.forObject(SnippetCounter.DISABLED_COUNTER), b.getMetaAccess()));
                return true;
            }

            if (field.getType().getName().equals(snippetIntegerHistogramName)) {
                b.addPush(JavaKind.Object, ConstantNode.forConstant(snippetReplacements.snippetReflection.forObject(SnippetIntegerHistogram.DISABLED_COUNTER), b.getMetaAccess()));
                return true;
            }
            return false;
        }
    }

    SymbolicSnippetEncoder(HotSpotReplacementsImpl replacements) {
        this.originalReplacements = replacements;
        GraphBuilderConfiguration.Plugins plugins = replacements.getGraphBuilderPlugins();
        SnippetInvocationPlugins invocationPlugins = new SnippetInvocationPlugins(plugins.getInvocationPlugins());
        GraphBuilderConfiguration.Plugins copy = new GraphBuilderConfiguration.Plugins(plugins, invocationPlugins);
        copy.clearInlineInvokePlugins();
        copy.appendInlineInvokePlugin(new SnippetInlineInvokePlugin());
        copy.appendNodePlugin(new SnippetCounterPlugin());
        HotSpotProviders providers = (HotSpotProviders) replacements.getProviders().copyWith(new HotSpotSubstrateConstantReflectionProvider(replacements.getProviders().getConstantReflection()));
        this.snippetReplacements = new HotSpotSnippetReplacementsImpl(replacements, providers.copyWith(copy));
        this.snippetReplacements.setGraphBuilderPlugins(copy);
    }

    synchronized void registerMethodSubstitution(MethodSubstitutionPlugin plugin) {
        knownPlugins.add(plugin);
    }

    void registerConditionalPlugin(InvocationPlugin plugin) {
        conditionalPlugins.add(plugin);
    }

    synchronized void checkRegistered(MethodSubstitutionPlugin plugin) {
        if (!knownPlugins.contains(plugin)) {
            throw new GraalError("missing plugin should have been registered during construction");
        }
    }

    /**
     * Compiles the snippet and stores the graph.
     */
    private synchronized void registerMethodSubstitution(MethodSubstitutionPlugin plugin, ResolvedJavaMethod original, IntrinsicContext.CompilationContext context, OptionValues options) {
        ResolvedJavaMethod method = plugin.getSubstitute(snippetReplacements.getProviders().getMetaAccess());
        assert method.getAnnotation(MethodSubstitution.class) != null : "MethodSubstitution must be annotated with @" + MethodSubstitution.class.getSimpleName();
        String originalMethodString = plugin.originalMethodAsString();
        StructuredGraph subst = buildGraph(method, original, originalMethodString, null, true, false, context, options);
        originalMethods.put(methodKey(method), originalMethodString);
        preparedSnippetGraphs.put(plugin.toString() + context, subst);
    }

    private StructuredGraph buildGraph(ResolvedJavaMethod method, ResolvedJavaMethod original, String originalMethodString, Object receiver, boolean requireInlining, boolean trackNodeSourcePosition,
                    IntrinsicContext.CompilationContext context, OptionValues options) {
        assert method.hasBytecodes() : "Snippet must not be abstract or native";
        Object[] args = null;
        if (receiver != null) {
            args = new Object[method.getSignature().getParameterCount(true)];
            args[0] = receiver;
        }
        // To get dumping out from this context during image building, it's necessary to pass the
        // dumping options directly to the VM, otherwise they aren't available during initialization
        // of the backend. Use this:
        //
        // -J-Dgraal.Dump=SymbolicSnippetEncoder_:2 -J-Dgraal.PrintGraph=File
        // -J-Dgraal.DebugStubsAndSnippets=true
        IntrinsicContext.CompilationContext contextToUse = context;
        if (context == IntrinsicContext.CompilationContext.ROOT_COMPILATION) {
            contextToUse = IntrinsicContext.CompilationContext.ROOT_COMPILATION_ENCODING;
        }
        try (DebugContext debug = openDebugContext("SymbolicSnippetEncoder_", method, options)) {
            StructuredGraph graph = snippetReplacements.makeGraph(debug, snippetReplacements.getDefaultReplacementBytecodeProvider(), method, args, original, trackNodeSourcePosition, null,
                            contextToUse);

            // Check if all methods which should be inlined are really inlined.
            for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
                ResolvedJavaMethod callee = callTarget.targetMethod();
                if (requireInlining && !delayedInvocationPluginMethods.contains(callee) && !Objects.equals(callee, original)) {
                    throw GraalError.shouldNotReachHere("method " + callee.format("%H.%n") + " not inlined in snippet " + method.getName() + " (maybe not final?)");
                }
            }
            assert verifySnippetEncodeDecode(debug, method, original, originalMethodString, trackNodeSourcePosition, graph);
            debug.dump(DebugContext.VERBOSE_LEVEL, graph, "After buildGraph");
            return graph;
        }
    }

    @SuppressWarnings("try")
    private static StructuredGraph decodeSnippetGraph(SymbolicEncodedGraph encodedGraph, ResolvedJavaMethod method, ReplacementsImpl replacements, Object[] args,
                    StructuredGraph.AllowAssumptions allowAssumptions, OptionValues options) {
        Providers providers = replacements.getProviders();
        ParameterPlugin parameterPlugin = null;
        if (args != null) {
            parameterPlugin = new ConstantBindingParameterPlugin(args, providers.getMetaAccess(), replacements.snippetReflection);
        }

        try (DebugContext debug = replacements.openDebugContext("SVMSnippet_", method, options)) {
            // @formatter:off
            boolean isSubstitution = true;
            StructuredGraph result = new StructuredGraph.Builder(options, debug, allowAssumptions)
                    .method(method)
                    .trackNodeSourcePosition(encodedGraph.trackNodeSourcePosition())
                    .setIsSubstitution(isSubstitution)
                    .build();
            // @formatter:on
            try (DebugContext.Scope scope = debug.scope("DecodeSnippetGraph", result)) {
                PEGraphDecoder graphDecoder = new EncodedSnippets.SubstitutionGraphDecoder(providers, result, replacements, parameterPlugin, method, INLINE_AFTER_PARSING, encodedGraph);

                graphDecoder.decode(method, isSubstitution, encodedGraph.trackNodeSourcePosition());
                debug.dump(DebugContext.VERBOSE_LEVEL, result, "After decoding");

                assert result.verify();
                return result;
            } catch (Throwable t) {
                throw debug.handle(t);
            }
        }
    }

    @SuppressWarnings("try")
    private boolean verifySnippetEncodeDecode(DebugContext debug, ResolvedJavaMethod method, ResolvedJavaMethod original, String originalMethodString, boolean trackNodeSourcePosition,
                    StructuredGraph graph) {
        // Verify the encoding and decoding process
        EncodedGraph encodedGraph = GraphEncoder.encodeSingleGraph(graph, HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch);

        HotSpotProviders originalProvider = (HotSpotProviders) snippetReplacements.getProviders();

        SnippetReflectionProvider snippetReflection = originalProvider.getSnippetReflection();
        SymbolicSnippetEncoder.HotSpotSubstrateConstantReflectionProvider constantReflection = new SymbolicSnippetEncoder.HotSpotSubstrateConstantReflectionProvider(
                        originalProvider.getConstantReflection());
        HotSpotProviders newProviders = new HotSpotProviders(originalProvider.getMetaAccess(), originalProvider.getCodeCache(), constantReflection,
                        originalProvider.getConstantFieldProvider(), originalProvider.getForeignCalls(), originalProvider.getLowerer(), null, originalProvider.getSuites(),
                        originalProvider.getRegisters(), snippetReflection, originalProvider.getWordTypes(), originalProvider.getGraphBuilderPlugins(),
                        originalProvider.getPlatformConfigurationProvider(), originalProvider.getMetaAccessExtensionProvider());
        HotSpotSnippetReplacementsImpl filteringReplacements = new HotSpotSnippetReplacementsImpl(newProviders, snippetReflection,
                        originalProvider.getReplacements().getDefaultReplacementBytecodeProvider(), originalProvider.getCodeCache().getTarget());
        filteringReplacements.setGraphBuilderPlugins(originalProvider.getReplacements().getGraphBuilderPlugins());
        try (DebugContext.Scope scaope = debug.scope("VerifySnippetEncodeDecode", graph)) {
            for (int i = 0; i < encodedGraph.getNumObjects(); i++) {
                filterSnippetObject(encodedGraph.getObject(i));
            }
            StructuredGraph snippet = filteringReplacements.makeGraph(debug, filteringReplacements.getDefaultReplacementBytecodeProvider(), method, null, original,
                            trackNodeSourcePosition, null);
            SymbolicEncodedGraph symbolicGraph = new SymbolicEncodedGraph(encodedGraph, method.getDeclaringClass(), originalMethodString);
            StructuredGraph decodedSnippet = decodeSnippetGraph(symbolicGraph, original != null ? original : method, originalReplacements, null,
                            StructuredGraph.AllowAssumptions.ifNonNull(graph.getAssumptions()), graph.getOptions());
            String snippetString = getCanonicalGraphString(snippet, true, false);
            String decodedSnippetString = getCanonicalGraphString(decodedSnippet, true, false);
            if (snippetString.equals(decodedSnippetString)) {
                debug.log("Snippet decode for %s produces exactly same graph", method);
                debug.dump(DebugContext.VERBOSE_LEVEL, decodedSnippet, "Decoded snippet graph for %s", method);
            } else {
                debug.log("Snippet decode for %s produces different graph", method);
                debug.log("%s", compareGraphStrings(snippet, snippetString, decodedSnippet, decodedSnippetString));
                debug.dump(DebugContext.VERBOSE_LEVEL, snippet, "Snippet graph for %s", method);
                debug.dump(DebugContext.VERBOSE_LEVEL, graph, "Encoded snippet graph for %s", method);
                debug.dump(DebugContext.VERBOSE_LEVEL, decodedSnippet, "Decoded snippet graph for %s", method);
            }
        } catch (Throwable t) {
            throw debug.handle(t);
        }
        return true;
    }

    /**
     * If there are new graphs waiting to be encoded, reencode all the graphs and return the result.
     */
    @SuppressWarnings("try")
    private synchronized EncodedSnippets maybeEncodeSnippets(OptionValues options) {
        EconomicSet<MethodSubstitutionPlugin> plugins = this.knownPlugins;
        if (preparedPlugins != plugins.size()) {
            for (MethodSubstitutionPlugin plugin : plugins) {
                ResolvedJavaMethod original = plugin.getOriginalMethod(originalReplacements.getProviders().getMetaAccess());
                registerMethodSubstitution(plugin, original, INLINE_AFTER_PARSING, options);
                if (!original.isNative()) {
                    registerMethodSubstitution(plugin, original, ROOT_COMPILATION_ENCODING, options);
                }
            }
            preparedPlugins = plugins.size();
        }
        EconomicMap<String, StructuredGraph> graphs = this.preparedSnippetGraphs;
        if (encodedGraphs != graphs.size()) {
            DebugContext debug = openDebugContext("SnippetEncoder", null, options);
            try (DebugContext.Scope scope = debug.scope("SnippetSupportEncode")) {
                encodedGraphs = graphs.size();
                for (StructuredGraph graph : graphs.getValues()) {
                    for (Node node : graph.getNodes()) {
                        node.setNodeSourcePosition(null);
                    }
                }
                return encodeSnippets(debug);
            }
        }
        return null;

    }

    synchronized void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        if (IS_BUILDING_NATIVE_IMAGE || UseEncodedGraphs.getValue(options)) {
            assert method.getAnnotation(Snippet.class) != null : "Snippet must be annotated with @" + Snippet.class.getSimpleName();
            String key = methodKey(method);
            if (!preparedSnippetGraphs.containsKey(key)) {
                if (original != null) {
                    originalMethods.put(key, methodKey(original));
                }
                StructuredGraph snippet = buildGraph(method, original, null, receiver, true, trackNodeSourcePosition, INLINE_AFTER_PARSING, options);
                preparedSnippetGraphs.put(key, snippet);
                snippetParameterInfos.put(key, new SnippetParameterInfo(method));
            }
        }

    }

    private synchronized EncodedSnippets encodeSnippets(DebugContext debug) {
        GraphEncoder encoder = new GraphEncoder(HotSpotJVMCIRuntime.runtime().getHostJVMCIBackend().getTarget().arch, debug);
        for (StructuredGraph graph : preparedSnippetGraphs.getValues()) {
            encoder.prepare(graph);
        }
        encoder.finishPrepare();

        byte[] snippetEncoding;
        Object[] snippetObjects;
        NodeClass<?>[] snippetNodeClasses;
        EconomicMap<String, Integer> snippetStartOffsets;

        snippetStartOffsets = EconomicMap.create();
        MapCursor<String, StructuredGraph> cursor = preparedSnippetGraphs.getEntries();
        while (cursor.advance()) {
            snippetStartOffsets.put(cursor.getKey(), encoder.encode(cursor.getValue()));
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
        return new EncodedSnippets(snippetEncoding, snippetObjects, snippetNodeClasses, snippetStartOffsets, originalMethods, snippetParameterInfos);
    }

    /**
     * Encode any outstanding graphs and return true if any work was done.
     */
    @SuppressWarnings("try")
    public boolean encode(OptionValues options) {
        if (!IS_IN_NATIVE_IMAGE) {
            EncodedSnippets encodedSnippets = maybeEncodeSnippets(options);
            if (encodedSnippets != null) {
                HotSpotReplacementsImpl.setEncodedSnippets(encodedSnippets);
                return true;
            }
        }
        return false;
    }

    private DebugContext openDebugContext(String idPrefix, ResolvedJavaMethod method, OptionValues options) {
        return snippetReplacements.openDebugContext(idPrefix, method, options);
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
            if (!safeConstants.contains(receiver) &&
                            !field.getDeclaringClass().getName().contains("graalvm") &&
                            !field.getDeclaringClass().getName().contains("jdk/vm/ci/") &&
                            !field.getDeclaringClass().getName().contains("jdk/internal/vm/compiler") &&

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
    private static Object filterSnippetObject(Object o) {
        if (o instanceof HotSpotResolvedJavaMethod) {
            return new SymbolicResolvedJavaMethod((HotSpotResolvedJavaMethod) o);
        } else if (o instanceof HotSpotResolvedJavaField) {
            return new SymbolicResolvedJavaField((HotSpotResolvedJavaField) o);
        } else if (o instanceof HotSpotResolvedJavaType) {
            return UnresolvedJavaType.create(((ResolvedJavaType) o).getName());
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
        } else if (o instanceof ResolvedJavaMethodBytecode) {
            return new SymbolicResolvedJavaMethodBytecode((ResolvedJavaMethodBytecode) o);
        } else if (o instanceof HotSpotSignature) {
            throw new GraalError(o.toString());
        }
        return o;
    }

    private static String compareGraphStrings(StructuredGraph expectedGraph, String expectedString, StructuredGraph actualGraph, String actualString) {
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

    private static String getCanonicalGraphString(StructuredGraph graph, boolean excludeVirtual, boolean checkConstants) {
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
    class HotSpotSnippetReplacementsImpl extends HotSpotReplacementsImpl {
        HotSpotSnippetReplacementsImpl(HotSpotReplacementsImpl replacements, Providers providers) {
            super(replacements, providers);
        }

        HotSpotSnippetReplacementsImpl(Providers providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider, TargetDescription target) {
            super(providers, snippetReflection, bytecodeProvider, target);
        }

        @Override
        protected GraphMaker createGraphMaker(ResolvedJavaMethod substitute, ResolvedJavaMethod original) {
            return new SnippetGraphMaker(this, substitute, original);
        }

        @Override
        public boolean isEncodingSnippets() {
            return true;
        }

    }

    class SnippetGraphMaker extends ReplacementsImpl.GraphMaker {
        SnippetGraphMaker(ReplacementsImpl replacements, ResolvedJavaMethod substitute, ResolvedJavaMethod substitutedMethod) {
            super(replacements, substitute, substitutedMethod);
        }

        @Override
        protected GraphBuilderPhase.Instance createGraphBuilder(Providers providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                        IntrinsicContext initialIntrinsicContext) {
            return new HotSpotSnippetGraphBuilderPhase(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        }
    }

    class HotSpotSnippetGraphBuilderPhase extends GraphBuilderPhase.Instance {
        HotSpotSnippetGraphBuilderPhase(Providers theProviders, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext) {
            super(theProviders, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        }

        @Override
        protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
            return new HotSpotSnippetBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
        }
    }

    class HotSpotSnippetBytecodeParser extends BytecodeParser {
        HotSpotSnippetBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
        }

        @Override
        public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
            // Fold is always deferred but NodeIntrinsics may have to wait if all their arguments
            // aren't constant yet.
            return plugin.isGeneratedFromFoldOrNodeIntrinsic();
        }

        @Override
        protected boolean canInlinePartialIntrinsicExit() {
            return false;
        }

        @Override
        protected boolean tryInvocationPlugin(CallTargetNode.InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType) {
            if (intrinsicContext != null && intrinsicContext.isCallToOriginal(targetMethod)) {
                return false;
            }
            if (targetMethod.getAnnotation(Fold.class) != null) {
                // Always defer Fold until decode time but NodeIntrinsics may fold if they are able.
                return false;
            }
            InvocationPlugin plugin = graphBuilderConfig.getPlugins().getInvocationPlugins().lookupInvocation(targetMethod);
            if (plugin != null && conditionalPlugins.contains(plugin)) {
                // Because supporting arbitrary plugins in the context of encoded graphs is complex
                // we disallow it. This limitation can be worked around through the use of method
                // substitutions.
                throw new GraalError("conditional plugins are unsupported in snippets and method substitutions: " + targetMethod + " " + plugin);
            }
            return super.tryInvocationPlugin(invokeKind, args, targetMethod, resultType);
        }
    }
}

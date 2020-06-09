/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.ROOT_COMPILATION;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.hotspot.meta.HotSpotWordOperationPlugin;
import org.graalvm.compiler.hotspot.word.HotSpotOperation;
import org.graalvm.compiler.nodes.Cancellable;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
import org.graalvm.compiler.nodes.spi.SnippetParameterInfo;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.compiler.replacements.ReplacementsImpl;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.NativeImageReinitialize;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Filters certain method substitutions based on whether there is underlying hardware support for
 * them.
 */
public class HotSpotReplacementsImpl extends ReplacementsImpl {
    public HotSpotReplacementsImpl(Providers providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider, TargetDescription target) {
        super(new GraalDebugHandlersFactory(snippetReflection), providers, snippetReflection, bytecodeProvider, target);
    }

    HotSpotReplacementsImpl(HotSpotReplacementsImpl replacements, Providers providers) {
        super(new GraalDebugHandlersFactory(replacements.snippetReflection), providers, replacements.snippetReflection,
                        replacements.getDefaultReplacementBytecodeProvider(), replacements.target);
    }

    public void maybeInitializeEncoder(OptionValues options) {
        if (IS_IN_NATIVE_IMAGE) {
            return;
        }
        if (IS_BUILDING_NATIVE_IMAGE || UseEncodedGraphs.getValue(options)) {
            synchronized (HotSpotReplacementsImpl.class) {
                if (snippetEncoder == null) {
                    snippetEncoder = new SymbolicSnippetEncoder(this);
                }
            }
        }
    }

    /**
     * Returns true if this Replacements is being used for preparation of snippets and substitutions
     * for libgraal.
     */
    public boolean isEncodingSnippets() {
        return false;
    }

    @Override
    public Class<? extends GraphBuilderPlugin> getIntrinsifyingPlugin(ResolvedJavaMethod method) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (method.getAnnotation(HotSpotOperation.class) != null) {
                return HotSpotWordOperationPlugin.class;
            }
        }
        return super.getIntrinsifyingPlugin(method);
    }

    @Override
    public void registerMethodSubstitution(MethodSubstitutionPlugin plugin) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (snippetEncoder != null) {
                snippetEncoder.registerMethodSubstitution(plugin);
            }
        }
    }

    @Override
    public void registerConditionalPlugin(InvocationPlugin plugin) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (snippetEncoder != null) {
                snippetEncoder.registerConditionalPlugin(plugin);
            }
        }
    }

    public void checkRegistered(MethodSubstitutionPlugin plugin) {
        snippetEncoder.checkRegistered(plugin);
    }

    @Override
    public StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, CompilationIdentifier compilationId, DebugContext debug, AllowAssumptions allowAssumptions, Cancellable cancellable) {
        boolean useEncodedGraphs = UseEncodedGraphs.getValue(debug.getOptions());
        if (IS_IN_NATIVE_IMAGE || useEncodedGraphs) {
            HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) providers.getReplacements();
            InvocationPlugin plugin = replacements.getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(method);
            if (plugin instanceof MethodSubstitutionPlugin) {
                MethodSubstitutionPlugin msp = (MethodSubstitutionPlugin) plugin;
                if (!IS_IN_NATIVE_IMAGE && useEncodedGraphs) {
                    replacements.maybeInitializeEncoder(debug.getOptions());
                    replacements.registerMethodSubstitution(msp);
                }
                StructuredGraph methodSubstitution = replacements.getMethodSubstitution(msp, method, ROOT_COMPILATION, allowAssumptions, cancellable, debug.getOptions());
                methodSubstitution.resetDebug(debug);
                return methodSubstitution;
            }
            return null;
        }
        return super.getIntrinsicGraph(method, compilationId, debug, allowAssumptions, cancellable);
    }

    @Override
    public StructuredGraph getSubstitution(ResolvedJavaMethod targetMethod, int invokeBci, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition,
                    AllowAssumptions allowAssumptions, OptionValues options) {
        boolean useEncodedGraphs = UseEncodedGraphs.getValue(options);
        if (IS_IN_NATIVE_IMAGE || useEncodedGraphs) {
            InvocationPlugin plugin = getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(targetMethod);
            if (plugin instanceof MethodSubstitutionPlugin && (!plugin.inlineOnly() || invokeBci >= 0)) {
                MethodSubstitutionPlugin msPlugin = (MethodSubstitutionPlugin) plugin;
                if (!IS_IN_NATIVE_IMAGE && useEncodedGraphs) {
                    maybeInitializeEncoder(options);
                    registerMethodSubstitution(msPlugin);
                }
                // This assumes the normal path creates the graph using
                // GraphBuilderConfiguration.getSnippetDefault with omits exception edges
                StructuredGraph subst = getMethodSubstitution(msPlugin, targetMethod, INLINE_AFTER_PARSING, allowAssumptions, null, options);
                return subst;
            }
        }

        return super.getSubstitution(targetMethod, invokeBci, trackNodeSourcePosition, replaceePosition, allowAssumptions, options);
    }

    @Override
    public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (b.parsingIntrinsic() && snippetEncoder != null) {
                if (getIntrinsifyingPlugin(method) != null) {
                    snippetEncoder.addDelayedInvocationPluginMethod(method);
                    return;
                }
            }
        }
        super.notifyNotInlined(b, method, invoke);
    }

    // When assertions are enabled, these fields are used to ensure all snippets are
    // registered during Graal initialization which in turn ensures that native image
    // building will not miss any snippets.
    @NativeImageReinitialize private EconomicSet<ResolvedJavaMethod> registeredSnippets = EconomicSet.create();
    private boolean snippetRegistrationClosed;

    @Override
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition, OptionValues options) {
        if (!IS_IN_NATIVE_IMAGE) {
            assert !snippetRegistrationClosed : "Cannot register snippet after registration is closed: " + method.format("%H.%n(%p)");
            assert registeredSnippets.add(method) : "Cannot register snippet twice: " + method.format("%H.%n(%p)");
            if (IS_BUILDING_NATIVE_IMAGE || UseEncodedGraphs.getValue(options)) {
                synchronized (HotSpotReplacementsImpl.class) {
                    snippetEncoder.registerSnippet(method, original, receiver, trackNodeSourcePosition, options);
                }
            }
        }
    }

    @Override
    public SnippetParameterInfo getSnippetParameterInfo(ResolvedJavaMethod method) {
        if (IS_IN_NATIVE_IMAGE) {
            OptionValues options = null;
            if (getEncodedSnippets(options) == null) {
                throw GraalError.shouldNotReachHere("encoded snippets not found");
            }
            return getEncodedSnippets(options).getSnippetParameterInfo(method);
        }
        return super.getSnippetParameterInfo(method);
    }

    @Override
    public boolean isSnippet(ResolvedJavaMethod method) {
        if (IS_IN_NATIVE_IMAGE) {
            if (encodedSnippets == null) {
                throw GraalError.shouldNotReachHere("encoded snippets not found");
            }
            return encodedSnippets.isSnippet(method);
        }
        return super.isSnippet(method);
    }

    @Override
    public void closeSnippetRegistration() {
        snippetRegistrationClosed = true;
    }

    public static EncodedSnippets getEncodedSnippets(OptionValues options) {
        if (!IS_IN_NATIVE_IMAGE && snippetEncoder != null) {
            snippetEncoder.encode(options);
        }
        return encodedSnippets;
    }

    public void clearSnippetParameterNames() {
        assert snippetEncoder != null;
        snippetEncoder.clearSnippetParameterNames();
    }

    static void setEncodedSnippets(EncodedSnippets encodedSnippets) {
        HotSpotReplacementsImpl.encodedSnippets = encodedSnippets;
    }

    public boolean encode(OptionValues options) {
        SymbolicSnippetEncoder encoder = snippetEncoder;
        if (encoder != null) {
            return encoder.encode(options);
        }
        return false;
    }

    private static volatile EncodedSnippets encodedSnippets;

    @NativeImageReinitialize private static SymbolicSnippetEncoder snippetEncoder;

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition,
                    OptionValues options) {
        StructuredGraph graph = getEncodedSnippet(method, args, StructuredGraph.AllowAssumptions.NO, options);
        if (graph != null) {
            return graph;
        }

        assert !IS_IN_NATIVE_IMAGE : "should be using encoded snippets";
        return super.getSnippet(method, recursiveEntry, args, trackNodeSourcePosition, replaceePosition, options);
    }

    @SuppressWarnings("try")
    private StructuredGraph getEncodedSnippet(ResolvedJavaMethod method, Object[] args, StructuredGraph.AllowAssumptions allowAssumptions, OptionValues options) {
        if (IS_IN_NATIVE_IMAGE || UseEncodedGraphs.getValue(options)) {
            synchronized (HotSpotReplacementsImpl.class) {
                if (getEncodedSnippets(options) == null) {
                    throw GraalError.shouldNotReachHere("encoded snippets not found");
                }
                // Snippets graphs can contain foreign object reference and
                // outlive a single compilation.
                try (CompilationContext scope = HotSpotGraalServices.enterGlobalCompilationContext()) {
                    StructuredGraph graph = getEncodedSnippets(options).getEncodedSnippet(method, this, args, allowAssumptions, options);
                    if (graph == null) {
                        throw GraalError.shouldNotReachHere("snippet not found: " + method.format("%H.%n(%p)"));
                    }
                    return graph;
                }
            }
        } else {
            assert registeredSnippets == null || registeredSnippets.contains(method) : "Asking for snippet method that was never registered: " + method.format("%H.%n(%p)");
        }
        return null;
    }

    @Override
    public StructuredGraph getMethodSubstitution(MethodSubstitutionPlugin plugin, ResolvedJavaMethod original, IntrinsicContext.CompilationContext context,
                    StructuredGraph.AllowAssumptions allowAssumptions, Cancellable cancellable, OptionValues options) {
        if (IS_IN_NATIVE_IMAGE || UseEncodedGraphs.getValue(options)) {
            if (getEncodedSnippets(options) == null) {
                throw GraalError.shouldNotReachHere("encoded snippets not found");
            }
            return getEncodedSnippets(options).getMethodSubstitutionGraph(plugin, original, this, context, allowAssumptions, cancellable, options);
        }
        return null;
    }

}

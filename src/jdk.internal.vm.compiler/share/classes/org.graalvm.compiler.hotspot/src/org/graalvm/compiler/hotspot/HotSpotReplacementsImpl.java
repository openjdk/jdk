/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
import static org.graalvm.compiler.replacements.ReplacementsImpl.Options.UseEncodedSnippets;

import java.util.Set;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.hotspot.meta.HotSpotWordOperationPlugin;
import org.graalvm.compiler.hotspot.word.HotSpotOperation;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.MethodSubstitutionPlugin;
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
    public HotSpotReplacementsImpl(OptionValues options, Providers providers, SnippetReflectionProvider snippetReflection, BytecodeProvider bytecodeProvider, TargetDescription target) {
        super(options, new GraalDebugHandlersFactory(snippetReflection), providers, snippetReflection, bytecodeProvider, target);
    }

    protected HotSpotReplacementsImpl(HotSpotReplacementsImpl replacements, Providers providers) {
        super(replacements.options, new GraalDebugHandlersFactory(replacements.snippetReflection), providers, replacements.snippetReflection,
                        replacements.getDefaultReplacementBytecodeProvider(), replacements.target);
    }

    @Override
    public Class<? extends GraphBuilderPlugin> getIntrinsifyingPlugin(ResolvedJavaMethod method) {
        return method.getAnnotation(HotSpotOperation.class) != null ? HotSpotWordOperationPlugin.class : super.getIntrinsifyingPlugin(method);
    }

    public void registerMethodSubstitution(ResolvedJavaMethod method, ResolvedJavaMethod original) {
        if (!IS_IN_NATIVE_IMAGE) {
            if (IS_BUILDING_NATIVE_IMAGE || UseEncodedSnippets.getValue(options)) {
                synchronized (HotSpotReplacementsImpl.class) {
                    if (snippetEncoder == null) {
                        snippetEncoder = new SymbolicSnippetEncoder(this);
                    }
                    snippetEncoder.registerMethodSubstitution(method, original);
                }
            }
        }
    }

    @Override
    public StructuredGraph getIntrinsicGraph(ResolvedJavaMethod method, CompilationIdentifier compilationId, DebugContext debug) {
        if (IS_IN_NATIVE_IMAGE) {
            HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) providers.getReplacements();
            InvocationPlugin plugin = replacements.getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(method);
            if (plugin instanceof MethodSubstitutionPlugin) {
                MethodSubstitutionPlugin msp = (MethodSubstitutionPlugin) plugin;
                return replacements.getMethodSubstitution(msp, method);
            }
            return null;
        }
        return super.getIntrinsicGraph(method, compilationId, debug);
    }

    @Override
    public void notifyNotInlined(GraphBuilderContext b, ResolvedJavaMethod method, Invoke invoke) {
        if (b.parsingIntrinsic() && snippetEncoder != null) {
            if (getIntrinsifyingPlugin(method) != null) {
                snippetEncoder.addDelayedInvocationPluginMethod(method);
                return;
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
    public void registerSnippet(ResolvedJavaMethod method, ResolvedJavaMethod original, Object receiver, boolean trackNodeSourcePosition) {
        if (!IS_IN_NATIVE_IMAGE) {
            assert !snippetRegistrationClosed : "Cannot register snippet after registration is closed: " + method.format("%H.%n(%p)");
            assert registeredSnippets.add(method) : "Cannot register snippet twice: " + method.format("%H.%n(%p)");
            if (IS_BUILDING_NATIVE_IMAGE || UseEncodedSnippets.getValue(options)) {
                synchronized (HotSpotReplacementsImpl.class) {
                    if (snippetEncoder == null) {
                        snippetEncoder = new SymbolicSnippetEncoder(this);
                    }
                    snippetEncoder.registerSnippet(method, original, receiver, trackNodeSourcePosition);
                }
            }
        }
    }

    @Override
    public void closeSnippetRegistration() {
        snippetRegistrationClosed = true;
    }

    static SymbolicSnippetEncoder.EncodedSnippets getEncodedSnippets() {
        return encodedSnippets;
    }

    public Set<ResolvedJavaMethod> getSnippetMethods() {
        if (snippetEncoder != null) {
            return snippetEncoder.getSnippetMethods();
        }
        return null;
    }

    static void setEncodedSnippets(SymbolicSnippetEncoder.EncodedSnippets encodedSnippets) {
        HotSpotReplacementsImpl.encodedSnippets = encodedSnippets;
    }

    public boolean encode() {
        SymbolicSnippetEncoder encoder = HotSpotReplacementsImpl.snippetEncoder;
        if (encoder != null) {
            return encoder.encode();
        }
        return false;
    }

    private static volatile SymbolicSnippetEncoder.EncodedSnippets encodedSnippets;

    @NativeImageReinitialize static SymbolicSnippetEncoder snippetEncoder;

    @Override
    public StructuredGraph getSnippet(ResolvedJavaMethod method, ResolvedJavaMethod recursiveEntry, Object[] args, boolean trackNodeSourcePosition, NodeSourcePosition replaceePosition) {
        StructuredGraph graph = getEncodedSnippet(method, args);
        if (graph != null) {
            return graph;
        }

        assert !IS_IN_NATIVE_IMAGE : "should be using encoded snippets";
        return super.getSnippet(method, recursiveEntry, args, trackNodeSourcePosition, replaceePosition);
    }

    public StructuredGraph getEncodedSnippet(ResolvedJavaMethod method, Object[] args) {
        if (IS_IN_NATIVE_IMAGE || UseEncodedSnippets.getValue(options)) {
            synchronized (HotSpotReplacementsImpl.class) {
                if (!IS_IN_NATIVE_IMAGE && UseEncodedSnippets.getValue(options)) {
                    snippetEncoder.encode();
                }

                if (getEncodedSnippets() == null) {
                    throw GraalError.shouldNotReachHere("encoded snippets not found");
                }
                StructuredGraph graph = getEncodedSnippets().getEncodedSnippet(method, this, args);
                if (graph == null) {
                    throw GraalError.shouldNotReachHere("snippet not found: " + method.format("%H.%n(%p)"));
                }
                return graph;
            }
        } else if (registeredSnippets != null) {
            assert registeredSnippets.contains(method) : "Asking for snippet method that was never registered: " + method.format("%H.%n(%p)");
        }
        return null;
    }

    public StructuredGraph getMethodSubstitution(MethodSubstitutionPlugin plugin, ResolvedJavaMethod original) {
        if (IS_IN_NATIVE_IMAGE || UseEncodedSnippets.getValue(options)) {
            if (getEncodedSnippets() == null) {
                throw GraalError.shouldNotReachHere("encoded snippets not found");
            }
            return getEncodedSnippets().getMethodSubstitutionGraph(plugin, original, this);
        }
        return null;
    }

}

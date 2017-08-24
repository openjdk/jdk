/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.stubs;

import static org.graalvm.compiler.nodes.StructuredGraph.NO_PROFILING_INFO;
import static org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext.CompilationContext.INLINE_AFTER_PARSING;

import java.lang.reflect.Method;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.phases.tiers.PhaseContext;
import org.graalvm.compiler.replacements.ConstantBindingParameterPlugin;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.Snippets;

import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Base class for a stub defined by a snippet.
 */
public abstract class SnippetStub extends Stub implements Snippets {

    protected final ResolvedJavaMethod method;

    /**
     * Creates a new snippet stub.
     *
     * @param snippetMethodName name of the single {@link Snippet} annotated method in the class of
     *            this object
     * @param linkage linkage details for a call to the stub
     */
    public SnippetStub(String snippetMethodName, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        this(null, snippetMethodName, providers, linkage);
    }

    /**
     * Creates a new snippet stub.
     *
     * @param snippetDeclaringClass this class in which the {@link Snippet} annotated method is
     *            declared. If {@code null}, this the class of this object is used.
     * @param snippetMethodName name of the single {@link Snippet} annotated method in
     *            {@code snippetDeclaringClass}
     * @param linkage linkage details for a call to the stub
     */
    public SnippetStub(Class<? extends Snippets> snippetDeclaringClass, String snippetMethodName, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(providers, linkage);
        Method javaMethod = SnippetTemplate.AbstractTemplates.findMethod(snippetDeclaringClass == null ? getClass() : snippetDeclaringClass, snippetMethodName, null);
        this.method = providers.getMetaAccess().lookupJavaMethod(javaMethod);
    }

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    public static final ThreadLocal<StructuredGraph> SnippetGraphUnderConstruction = assertionsEnabled() ? new ThreadLocal<>() : null;

    @Override
    @SuppressWarnings("try")
    protected StructuredGraph getGraph(CompilationIdentifier compilationId) {
        Plugins defaultPlugins = providers.getGraphBuilderPlugins();
        MetaAccessProvider metaAccess = providers.getMetaAccess();
        SnippetReflectionProvider snippetReflection = providers.getSnippetReflection();

        Plugins plugins = new Plugins(defaultPlugins);
        plugins.prependParameterPlugin(new ConstantBindingParameterPlugin(makeConstArgs(), metaAccess, snippetReflection));
        GraphBuilderConfiguration config = GraphBuilderConfiguration.getSnippetDefault(plugins);

        // Stubs cannot have optimistic assumptions since they have
        // to be valid for the entire run of the VM.
        final StructuredGraph graph = new StructuredGraph(method, AllowAssumptions.NO, NO_PROFILING_INFO, compilationId);
        try (Scope outer = Debug.scope("SnippetStub", graph)) {
            graph.disableUnsafeAccessTracking();

            if (SnippetGraphUnderConstruction != null) {
                assert SnippetGraphUnderConstruction.get() == null : SnippetGraphUnderConstruction.get().toString() + " " + graph;
                SnippetGraphUnderConstruction.set(graph);
            }

            try {
                IntrinsicContext initialIntrinsicContext = new IntrinsicContext(method, method, providers.getReplacements().getReplacementBytecodeProvider(), INLINE_AFTER_PARSING);
                GraphBuilderPhase.Instance instance = new GraphBuilderPhase.Instance(metaAccess, providers.getStampProvider(),
                                providers.getConstantReflection(), providers.getConstantFieldProvider(),
                                config, OptimisticOptimizations.NONE,
                                initialIntrinsicContext);
                instance.apply(graph);

            } finally {
                if (SnippetGraphUnderConstruction != null) {
                    SnippetGraphUnderConstruction.set(null);
                }
            }

            graph.setGuardsStage(GuardsStage.FLOATING_GUARDS);
            CanonicalizerPhase canonicalizer = new CanonicalizerPhase();
            PhaseContext context = new PhaseContext(providers);
            canonicalizer.apply(graph, context);
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, context);
        } catch (Throwable e) {
            throw Debug.handle(e);
        }

        return graph;
    }

    protected boolean checkConstArg(int index, String expectedName) {
        assert method.getParameterAnnotation(ConstantParameter.class, index) != null : String.format("parameter %d of %s is expected to be constant", index, method.format("%H.%n(%p)"));
        LocalVariableTable lvt = method.getLocalVariableTable();
        if (lvt != null) {
            Local local = lvt.getLocal(index, 0);
            assert local != null;
            String actualName = local.getName();
            assert actualName.equals(expectedName) : String.format("parameter %d of %s is expected to be named %s, not %s", index, method.format("%H.%n(%p)"), expectedName, actualName);
        }
        return true;
    }

    protected Object[] makeConstArgs() {
        int count = method.getSignature().getParameterCount(false);
        Object[] args = new Object[count];
        for (int i = 0; i < args.length; i++) {
            if (method.getParameterAnnotation(ConstantParameter.class, i) != null) {
                args[i] = getConstantParameterValue(i, null);
            }
        }
        return args;
    }

    protected Object getConstantParameterValue(int index, String name) {
        throw new GraalError("%s must override getConstantParameterValue() to provide a value for parameter %d%s", getClass().getName(), index, name == null ? "" : " (" + name + ")");
    }

    @Override
    protected Object debugScopeContext() {
        return getInstalledCodeOwner();
    }

    @Override
    public ResolvedJavaMethod getInstalledCodeOwner() {
        return method;
    }

    @Override
    public String toString() {
        return "Stub<" + getInstalledCodeOwner().format("%h.%n") + ">";
    }
}

/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.nodes.spi.SnippetParameterInfo;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.LoweringPhase;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.Snippets;

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
    public SnippetStub(String snippetMethodName, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        this(null, snippetMethodName, options, providers, linkage);
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
    public SnippetStub(Class<? extends Snippets> snippetDeclaringClass, String snippetMethodName, OptionValues options, HotSpotProviders providers, HotSpotForeignCallLinkage linkage) {
        super(options, providers, linkage);
        this.method = SnippetTemplate.AbstractTemplates.findMethod(providers.getMetaAccess(), snippetDeclaringClass == null ? getClass() : snippetDeclaringClass, snippetMethodName);
        registerSnippet();
    }

    protected void registerSnippet() {
        providers.getReplacements().registerSnippet(method, null, null, false, options);
    }

    @Override
    @SuppressWarnings("try")
    protected StructuredGraph getGraph(DebugContext debug, CompilationIdentifier compilationId) {
        // Stubs cannot have optimistic assumptions since they have
        // to be valid for the entire run of the VM.
        SnippetParameterInfo info = providers.getReplacements().getSnippetParameterInfo(method);
        final StructuredGraph graph = buildInitialGraph(debug, compilationId, makeConstArgs(info));
        try (DebugContext.Scope outer = debug.scope("SnippetStub", graph)) {
            for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                int index = param.index();
                if (info.isNonNullParameter(index)) {
                    param.setStamp(param.stamp(NodeView.DEFAULT).join(StampFactory.objectNonNull()));
                }
            }

            graph.setGuardsStage(GuardsStage.FLOATING_GUARDS);
            CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
            canonicalizer.apply(graph, providers);
            new LoweringPhase(canonicalizer, LoweringTool.StandardLoweringStage.HIGH_TIER).apply(graph, providers);
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        return graph;
    }

    protected StructuredGraph buildInitialGraph(DebugContext debug, CompilationIdentifier compilationId, Object[] args) {
        return providers.getReplacements().getSnippet(method, null, args, false, null, options).copyWithIdentifier(compilationId, debug);
    }

    protected Object[] makeConstArgs(SnippetParameterInfo info) {
        Object[] args = new Object[info.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            if (info.isConstantParameter(i)) {
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

    public ResolvedJavaMethod getMethod() {
        return method;
    }
}

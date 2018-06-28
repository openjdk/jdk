/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.phases.common.inlining.info;

import jdk.internal.vm.compiler.collections.EconomicSet;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;
import org.graalvm.compiler.phases.common.inlining.info.elem.Inlineable;
import org.graalvm.compiler.phases.common.inlining.info.elem.InlineableGraph;
import org.graalvm.compiler.phases.tiers.HighTierContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class AbstractInlineInfo implements InlineInfo {

    protected final Invoke invoke;

    public AbstractInlineInfo(Invoke invoke) {
        this.invoke = invoke;
    }

    @Override
    public StructuredGraph graph() {
        return invoke.asNode().graph();
    }

    @Override
    public Invoke invoke() {
        return invoke;
    }

    @SuppressWarnings("try")
    protected static EconomicSet<Node> inline(Invoke invoke, ResolvedJavaMethod concrete, Inlineable inlineable, boolean receiverNullCheck, String reason) {
        assert inlineable instanceof InlineableGraph;
        StructuredGraph calleeGraph = ((InlineableGraph) inlineable).getGraph();
        return InliningUtil.inlineForCanonicalization(invoke, calleeGraph, receiverNullCheck, concrete, reason, "InliningPhase");
    }

    @Override
    @SuppressWarnings("try")
    public final void populateInlinableElements(HighTierContext context, StructuredGraph caller, CanonicalizerPhase canonicalizer, OptionValues options) {
        for (int i = 0; i < numberOfMethods(); i++) {
            Inlineable elem = Inlineable.getInlineableElement(methodAt(i), invoke, context, canonicalizer, caller.trackNodeSourcePosition());
            setInlinableElement(i, elem);
        }
    }

    @Override
    public final int determineNodeCount() {
        int nodes = 0;
        for (int i = 0; i < numberOfMethods(); i++) {
            Inlineable elem = inlineableElementAt(i);
            if (elem != null) {
                nodes += elem.getNodeCount();
            }
        }
        return nodes;
    }
}

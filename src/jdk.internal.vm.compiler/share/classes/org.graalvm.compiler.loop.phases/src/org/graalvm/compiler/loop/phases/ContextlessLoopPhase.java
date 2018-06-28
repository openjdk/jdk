/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.loop.phases;

import org.graalvm.compiler.loop.LoopPolicies;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.tiers.PhaseContext;

public abstract class ContextlessLoopPhase<P extends LoopPolicies> extends LoopPhase<P> {

    public ContextlessLoopPhase(P policies) {
        super(policies);
    }

    public final void apply(final StructuredGraph graph) {
        apply(graph, true);
    }

    public final void apply(final StructuredGraph graph, final boolean dumpGraph) {
        apply(graph, null, dumpGraph);
    }

    protected abstract void run(StructuredGraph graph);

    @Override
    protected final void run(StructuredGraph graph, PhaseContext context) {
        run(graph);
    }
}

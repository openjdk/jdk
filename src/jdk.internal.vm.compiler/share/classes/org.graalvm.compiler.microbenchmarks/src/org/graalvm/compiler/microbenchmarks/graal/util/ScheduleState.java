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


package org.graalvm.compiler.microbenchmarks.graal.util;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import org.graalvm.compiler.phases.tiers.HighTierContext;

public class ScheduleState extends GraphState {

    public SchedulePhase schedule;

    private final SchedulingStrategy selectedStrategy;

    public ScheduleState(SchedulingStrategy selectedStrategy) {
        this.selectedStrategy = selectedStrategy;
    }

    public ScheduleState() {
        this(SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER);
    }

    @Override
    public void beforeInvocation() {
        schedule = new SchedulePhase(selectedStrategy);
        super.beforeInvocation();
    }

    @Override
    protected StructuredGraph preprocessOriginal(StructuredGraph structuredGraph) {
        StructuredGraph g = super.preprocessOriginal(structuredGraph);
        GraalState graal = new GraalState();
        PhaseSuite<HighTierContext> highTier = graal.backend.getSuites().getDefaultSuites(graal.options).getHighTier();
        highTier.apply(g, new HighTierContext(graal.providers, graal.backend.getSuites().getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL));
        return g;
    }
}

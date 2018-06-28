/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.loop.LoopEx;
import org.graalvm.compiler.loop.LoopsData;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.MidTierContext;

public class LoopSafepointEliminationPhase extends BasePhase<MidTierContext> {

    @Override
    protected void run(StructuredGraph graph, MidTierContext context) {
        LoopsData loops = new LoopsData(graph);
        if (context.getOptimisticOptimizations().useLoopLimitChecks(graph.getOptions()) && graph.getGuardsStage().allowsFloatingGuards()) {
            loops.detectedCountedLoops();
            for (LoopEx loop : loops.countedLoops()) {
                if (loop.loop().getChildren().isEmpty() && loop.counted().getStamp().getBits() <= 32) {
                    boolean hasSafepoint = false;
                    for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                        hasSafepoint |= loopEnd.canSafepoint();
                    }
                    if (hasSafepoint) {
                        loop.counted().createOverFlowGuard();
                        loop.loopBegin().disableSafepoint();
                    }
                }
            }
        }
        for (LoopEx loop : loops.loops()) {
            for (LoopEndNode loopEnd : loop.loopBegin().loopEnds()) {
                Block b = loops.getCFG().blockFor(loopEnd);
                blocks: while (b != loop.loop().getHeader()) {
                    assert b != null;
                    for (FixedNode node : b.getNodes()) {
                        if (node instanceof Invoke || (node instanceof ForeignCallNode && ((ForeignCallNode) node).isGuaranteedSafepoint())) {
                            loopEnd.disableSafepoint();
                            break blocks;
                        }
                    }
                    b = b.getDominator();
                }
            }
        }
        loops.deleteUnusedNodes();
    }
}

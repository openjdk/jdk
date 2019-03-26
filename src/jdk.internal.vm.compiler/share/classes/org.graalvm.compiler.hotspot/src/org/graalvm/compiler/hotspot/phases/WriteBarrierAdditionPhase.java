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


package org.graalvm.compiler.hotspot.phases;

import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.gc.g1.G1BarrierSet;
import org.graalvm.compiler.hotspot.gc.shared.BarrierSet;
import org.graalvm.compiler.hotspot.gc.shared.CardTableBarrierSet;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ArrayRangeWrite;
import org.graalvm.compiler.nodes.java.AbstractCompareAndSwapNode;
import org.graalvm.compiler.nodes.java.LoweredAtomicReadAndWriteNode;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.phases.Phase;

public class WriteBarrierAdditionPhase extends Phase {

    private BarrierSet barrierSet;

    public WriteBarrierAdditionPhase(GraalHotSpotVMConfig config) {
        this.barrierSet = createBarrierSet(config);
    }

    @SuppressWarnings("try")
    @Override
    protected void run(StructuredGraph graph) {
        for (Node n : graph.getNodes()) {
            try (DebugCloseable scope = n.graph().withNodeSourcePosition(n)) {
                if (n instanceof ReadNode) {
                    barrierSet.addReadNodeBarriers((ReadNode) n, graph);
                } else if (n instanceof WriteNode) {
                    barrierSet.addWriteNodeBarriers((WriteNode) n, graph);
                } else if (n instanceof LoweredAtomicReadAndWriteNode) {
                    LoweredAtomicReadAndWriteNode loweredAtomicReadAndWriteNode = (LoweredAtomicReadAndWriteNode) n;
                    barrierSet.addAtomicReadWriteNodeBarriers(loweredAtomicReadAndWriteNode, graph);
                } else if (n instanceof AbstractCompareAndSwapNode) {
                    barrierSet.addCASBarriers((AbstractCompareAndSwapNode) n, graph);
                } else if (n instanceof ArrayRangeWrite) {
                    ArrayRangeWrite node = (ArrayRangeWrite) n;
                    if (node.writesObjectArray()) {
                        barrierSet.addArrayRangeBarriers(node, graph);
                    }
                }
            }
        }
    }

    @Override
    public boolean checkContract() {
        return false;
    }

    private BarrierSet createBarrierSet(GraalHotSpotVMConfig config) {
        if (config.useG1GC) {
            return createG1BarrierSet();
        } else {
            return createCardTableBarrierSet();
        }
    }

    protected BarrierSet createCardTableBarrierSet() {
        return new CardTableBarrierSet();
    }

    protected BarrierSet createG1BarrierSet() {
        return new G1BarrierSet();
    }
}

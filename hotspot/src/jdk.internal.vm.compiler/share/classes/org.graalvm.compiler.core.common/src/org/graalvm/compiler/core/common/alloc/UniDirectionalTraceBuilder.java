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
package org.graalvm.compiler.core.common.alloc;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.PriorityQueue;

import org.graalvm.compiler.core.common.alloc.TraceBuilderResult.TrivialTracePredicate;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.Indent;

/**
 * Computes traces by starting at a trace head and keep adding predecessors as long as possible.
 */
public final class UniDirectionalTraceBuilder {

    public static TraceBuilderResult computeTraces(DebugContext debug, AbstractBlockBase<?> startBlock, AbstractBlockBase<?>[] blocks, TrivialTracePredicate pred) {
        return new UniDirectionalTraceBuilder(blocks).build(debug, startBlock, blocks, pred);
    }

    private final PriorityQueue<AbstractBlockBase<?>> worklist;
    private final BitSet processed;
    /**
     * Contains the number of unprocessed predecessors for every {@link AbstractBlockBase#getId()
     * block}.
     */
    private final int[] blocked;
    private final Trace[] blockToTrace;

    private UniDirectionalTraceBuilder(AbstractBlockBase<?>[] blocks) {
        processed = new BitSet(blocks.length);
        worklist = new PriorityQueue<>(UniDirectionalTraceBuilder::compare);
        assert (worklist != null);

        blocked = new int[blocks.length];
        blockToTrace = new Trace[blocks.length];
        for (AbstractBlockBase<?> block : blocks) {
            blocked[block.getId()] = block.getPredecessorCount();
        }
    }

    private static int compare(AbstractBlockBase<?> a, AbstractBlockBase<?> b) {
        return Double.compare(b.probability(), a.probability());
    }

    private boolean processed(AbstractBlockBase<?> b) {
        return processed.get(b.getId());
    }

    @SuppressWarnings("try")
    private TraceBuilderResult build(DebugContext debug, AbstractBlockBase<?> startBlock, AbstractBlockBase<?>[] blocks, TrivialTracePredicate pred) {
        try (Indent indent = debug.logAndIndent("UniDirectionalTraceBuilder: start trace building: %s", startBlock)) {
            ArrayList<Trace> traces = buildTraces(debug, startBlock);
            return TraceBuilderResult.create(debug, blocks, traces, blockToTrace, pred);
        }
    }

    protected ArrayList<Trace> buildTraces(DebugContext debug, AbstractBlockBase<?> startBlock) {
        ArrayList<Trace> traces = new ArrayList<>();
        // add start block
        worklist.add(startBlock);
        // process worklist
        while (!worklist.isEmpty()) {
            AbstractBlockBase<?> block = worklist.poll();
            assert block != null;
            if (!processed(block)) {
                Trace trace = new Trace(startTrace(debug, block));
                for (AbstractBlockBase<?> traceBlock : trace.getBlocks()) {
                    blockToTrace[traceBlock.getId()] = trace;
                }
                trace.setId(traces.size());
                traces.add(trace);
            }
        }
        return traces;
    }

    /**
     * Build a new trace starting at {@code block}.
     */
    @SuppressWarnings("try")
    private List<AbstractBlockBase<?>> startTrace(DebugContext debug, AbstractBlockBase<?> block) {
        assert checkPredecessorsProcessed(block);
        ArrayList<AbstractBlockBase<?>> trace = new ArrayList<>();
        int blockNumber = 0;
        try (Indent i = debug.logAndIndent("StartTrace: %s", block)) {
            for (AbstractBlockBase<?> currentBlock = block; currentBlock != null; currentBlock = selectNext(currentBlock)) {
                debug.log("add %s (prob: %f)", currentBlock, currentBlock.probability());
                processed.set(currentBlock.getId());
                trace.add(currentBlock);
                unblock(currentBlock);
                currentBlock.setLinearScanNumber(blockNumber++);
            }
        }
        return trace;
    }

    private boolean checkPredecessorsProcessed(AbstractBlockBase<?> block) {
        for (AbstractBlockBase<?> pred : block.getPredecessors()) {
            if (!processed(pred)) {
                assert false : "Predecessor unscheduled: " + pred;
                return false;
            }

        }
        return true;
    }

    /**
     * Decrease the {@link #blocked} count for all predecessors and add them to the worklist once
     * the count reaches 0.
     */
    private void unblock(AbstractBlockBase<?> currentBlock) {
        for (AbstractBlockBase<?> successor : currentBlock.getSuccessors()) {
            if (!processed(successor)) {
                int blockCount = --blocked[successor.getId()];
                assert blockCount >= 0;
                if (blockCount == 0) {
                    worklist.add(successor);
                }
            }
        }
    }

    /**
     * @return The unprocessed predecessor with the highest probability, or {@code null}.
     */
    private AbstractBlockBase<?> selectNext(AbstractBlockBase<?> currentBlock) {
        AbstractBlockBase<?> next = null;
        for (AbstractBlockBase<?> succ : currentBlock.getSuccessors()) {
            if (!processed(succ) && (next == null || succ.probability() > next.probability())) {
                next = succ;
            }
        }
        return next;
    }
}

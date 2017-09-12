/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.stackslotalloc;

import static org.graalvm.compiler.lir.LIRValueUtil.asVirtualStackSlot;
import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;
import static org.graalvm.compiler.lir.stackslotalloc.StackSlotAllocatorUtil.allocatedFramesize;
import static org.graalvm.compiler.lir.stackslotalloc.StackSlotAllocatorUtil.allocatedSlots;
import static org.graalvm.compiler.lir.stackslotalloc.StackSlotAllocatorUtil.virtualFramesize;

import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.ValueProcedure;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.framemap.FrameMapBuilderTool;
import org.graalvm.compiler.lir.framemap.SimpleVirtualStackSlot;
import org.graalvm.compiler.lir.framemap.VirtualStackSlotRange;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.phases.AllocationPhase;

import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.code.TargetDescription;

public class SimpleStackSlotAllocator extends AllocationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, AllocationContext context) {
        allocateStackSlots((FrameMapBuilderTool) lirGenRes.getFrameMapBuilder(), lirGenRes);
        lirGenRes.buildFrameMap();
    }

    public void allocateStackSlots(FrameMapBuilderTool builder, LIRGenerationResult res) {
        DebugContext debug = res.getLIR().getDebug();
        StackSlot[] mapping = new StackSlot[builder.getNumberOfStackSlots()];
        boolean allocatedFramesizeEnabled = allocatedFramesize.isEnabled(debug);
        long currentFrameSize = allocatedFramesizeEnabled ? builder.getFrameMap().currentFrameSize() : 0;
        for (VirtualStackSlot virtualSlot : builder.getStackSlots()) {
            final StackSlot slot;
            if (virtualSlot instanceof SimpleVirtualStackSlot) {
                slot = mapSimpleVirtualStackSlot(builder, (SimpleVirtualStackSlot) virtualSlot);
                virtualFramesize.add(debug, builder.getFrameMap().spillSlotSize(virtualSlot.getValueKind()));
            } else if (virtualSlot instanceof VirtualStackSlotRange) {
                VirtualStackSlotRange slotRange = (VirtualStackSlotRange) virtualSlot;
                slot = mapVirtualStackSlotRange(builder, slotRange);
                virtualFramesize.add(debug, builder.getFrameMap().spillSlotRangeSize(slotRange.getSlots()));
            } else {
                throw GraalError.shouldNotReachHere("Unknown VirtualStackSlot: " + virtualSlot);
            }
            allocatedSlots.increment(debug);
            mapping[virtualSlot.getId()] = slot;
        }
        updateLIR(res, mapping);
        if (allocatedFramesizeEnabled) {
            allocatedFramesize.add(debug, builder.getFrameMap().currentFrameSize() - currentFrameSize);
        }
    }

    @SuppressWarnings("try")
    protected void updateLIR(LIRGenerationResult res, StackSlot[] mapping) {
        DebugContext debug = res.getLIR().getDebug();
        try (DebugContext.Scope scope = debug.scope("StackSlotMappingLIR")) {
            ValueProcedure updateProc = (value, mode, flags) -> {
                if (isVirtualStackSlot(value)) {
                    StackSlot stackSlot = mapping[asVirtualStackSlot(value).getId()];
                    debug.log("map %s -> %s", value, stackSlot);
                    return stackSlot;
                }
                return value;
            };
            for (AbstractBlockBase<?> block : res.getLIR().getControlFlowGraph().getBlocks()) {
                try (Indent indent0 = debug.logAndIndent("block: %s", block)) {
                    for (LIRInstruction inst : res.getLIR().getLIRforBlock(block)) {
                        try (Indent indent1 = debug.logAndIndent("Inst: %d: %s", inst.id(), inst)) {
                            inst.forEachAlive(updateProc);
                            inst.forEachInput(updateProc);
                            inst.forEachOutput(updateProc);
                            inst.forEachTemp(updateProc);
                            inst.forEachState(updateProc);
                        }
                    }
                }
            }
        }
    }

    protected StackSlot mapSimpleVirtualStackSlot(FrameMapBuilderTool builder, SimpleVirtualStackSlot virtualStackSlot) {
        return builder.getFrameMap().allocateSpillSlot(virtualStackSlot.getValueKind());
    }

    protected StackSlot mapVirtualStackSlotRange(FrameMapBuilderTool builder, VirtualStackSlotRange virtualStackSlot) {
        return builder.getFrameMap().allocateStackSlots(virtualStackSlot.getSlots(), virtualStackSlot.getObjects());
    }
}

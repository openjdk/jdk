/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.framemap;

import static org.graalvm.compiler.lir.LIRValueUtil.isVirtualStackSlot;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * A FrameMapBuilder that records allocation.
 */
public class FrameMapBuilderImpl extends FrameMapBuilderTool {

    private final RegisterConfig registerConfig;
    private final CodeCacheProvider codeCache;
    private final FrameMap frameMap;
    private final List<VirtualStackSlot> stackSlots;
    private final List<CallingConvention> calls;
    private int numStackSlots;

    public FrameMapBuilderImpl(FrameMap frameMap, CodeCacheProvider codeCache, RegisterConfig registerConfig) {
        assert registerConfig != null : "No register config!";
        this.registerConfig = registerConfig == null ? codeCache.getRegisterConfig() : registerConfig;
        this.codeCache = codeCache;
        this.frameMap = frameMap;
        this.stackSlots = new ArrayList<>();
        this.calls = new ArrayList<>();
        this.numStackSlots = 0;
    }

    @Override
    public VirtualStackSlot allocateSpillSlot(ValueKind<?> kind) {
        SimpleVirtualStackSlot slot = new SimpleVirtualStackSlot(numStackSlots++, kind);
        stackSlots.add(slot);
        return slot;
    }

    @Override
    public VirtualStackSlot allocateStackSlots(int slots, BitSet objects, List<VirtualStackSlot> outObjectStackSlots) {
        if (slots == 0) {
            return null;
        }
        if (outObjectStackSlots != null) {
            throw GraalError.unimplemented();
        }
        VirtualStackSlotRange slot = new VirtualStackSlotRange(numStackSlots++, slots, objects, LIRKind.fromJavaKind(frameMap.getTarget().arch, JavaKind.Object));
        stackSlots.add(slot);
        return slot;
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return registerConfig;
    }

    @Override
    public CodeCacheProvider getCodeCache() {
        return codeCache;
    }

    @Override
    public FrameMap getFrameMap() {
        return frameMap;
    }

    @Override
    public int getNumberOfStackSlots() {
        return numStackSlots;
    }

    @Override
    public void callsMethod(CallingConvention cc) {
        calls.add(cc);
    }

    @Override
    @SuppressWarnings("try")
    public FrameMap buildFrameMap(LIRGenerationResult res) {
        DebugContext debug = res.getLIR().getDebug();
        if (debug.areScopesEnabled()) {
            verifyStackSlotAllocation(res);
        }
        for (CallingConvention cc : calls) {
            frameMap.callsMethod(cc);
        }
        frameMap.finish();
        return frameMap;
    }

    private static void verifyStackSlotAllocation(LIRGenerationResult res) {
        LIR lir = res.getLIR();
        InstructionValueConsumer verifySlots = (LIRInstruction op, Value value, OperandMode mode, EnumSet<OperandFlag> flags) -> {
            assert !isVirtualStackSlot(value) : String.format("Instruction %s contains a virtual stack slot %s", op, value);
        };
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            lir.getLIRforBlock(block).forEach(op -> {
                op.visitEachInput(verifySlots);
                op.visitEachAlive(verifySlots);
                op.visitEachState(verifySlots);

                op.visitEachTemp(verifySlots);
                op.visitEachOutput(verifySlots);
            });
        }
    }

    @Override
    public List<VirtualStackSlot> getStackSlots() {
        return stackSlots;
    }

}

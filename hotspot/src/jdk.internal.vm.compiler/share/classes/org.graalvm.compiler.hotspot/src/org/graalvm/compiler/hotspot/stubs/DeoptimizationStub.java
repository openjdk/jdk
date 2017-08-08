/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static org.graalvm.compiler.hotspot.HotSpotBackend.UNPACK_FRAMES;
import static org.graalvm.compiler.hotspot.HotSpotBackend.Options.PreferGraalStubs;
import static org.graalvm.compiler.hotspot.nodes.DeoptimizationFetchUnrollInfoCallNode.fetchUnrollInfo;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.pageSize;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.wordSize;
import static org.graalvm.compiler.hotspot.replacements.HotSpotReplacementsUtil.writeRegisterAsWord;
import static org.graalvm.compiler.hotspot.stubs.UncommonTrapStub.STACK_BANG_LOCATION;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Fold.InjectedParameter;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.asm.NumUtil;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotForeignCallLinkage;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.nodes.EnterUnpackFramesStackFrameNode;
import org.graalvm.compiler.hotspot.nodes.LeaveCurrentStackFrameNode;
import org.graalvm.compiler.hotspot.nodes.LeaveDeoptimizedStackFrameNode;
import org.graalvm.compiler.hotspot.nodes.LeaveUnpackFramesStackFrameNode;
import org.graalvm.compiler.hotspot.nodes.PushInterpreterFrameNode;
import org.graalvm.compiler.hotspot.nodes.SaveAllRegistersNode;
import org.graalvm.compiler.hotspot.nodes.StubForeignCallNode;
import org.graalvm.compiler.word.Word;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

/**
 * Deoptimization stub.
 *
 * This is the entry point for code which is returning to a de-optimized frame.
 *
 * The steps taken by this frame are as follows:
 *
 * <li>push a dummy "register_save" and save the return values (O0, O1, F0/F1, G1) and all
 * potentially live registers (at a pollpoint many registers can be live).
 *
 * <li>call the C routine: Deoptimization::fetch_unroll_info (this function returns information
 * about the number and size of interpreter frames which are equivalent to the frame which is being
 * deoptimized)
 *
 * <li>deallocate the unpack frame, restoring only results values. Other volatile registers will now
 * be captured in the vframeArray as needed.
 *
 * <li>deallocate the deoptimization frame
 *
 * <li>in a loop using the information returned in the previous step push new interpreter frames
 * (take care to propagate the return values through each new frame pushed)
 *
 * <li>create a dummy "unpack_frame" and save the return values (O0, O1, F0)
 *
 * <li>call the C routine: Deoptimization::unpack_frames (this function lays out values on the
 * interpreter frame which was just created)
 *
 * <li>deallocate the dummy unpack_frame
 *
 * <li>ensure that all the return values are correctly set and then do a return to the interpreter
 * entry point
 *
 * <p>
 * <b>ATTENTION: We cannot do any complicated operations e.g. logging via printf in this snippet
 * because we change the current stack layout and so the code is very sensitive to register
 * allocation.</b>
 */
public class DeoptimizationStub extends SnippetStub {

    private final TargetDescription target;

    public DeoptimizationStub(HotSpotProviders providers, TargetDescription target, HotSpotForeignCallLinkage linkage) {
        super(DeoptimizationStub.class, "deoptimizationHandler", providers, linkage);
        this.target = target;
        assert PreferGraalStubs.getValue();
    }

    @Override
    public boolean preservesRegisters() {
        return false;
    }

    @Override
    protected Object getConstantParameterValue(int index, String name) {
        switch (index) {
            case 0:
                return providers.getRegisters().getThreadRegister();
            case 1:
                return providers.getRegisters().getStackPointerRegister();
            default:
                throw GraalError.shouldNotReachHere("unknown parameter " + name + " at index " + index);
        }
    }

    /**
     * Deoptimization handler for normal deoptimization
     * {@link GraalHotSpotVMConfig#deoptimizationUnpackDeopt}.
     */
    @Snippet
    private static void deoptimizationHandler(@ConstantParameter Register threadRegister, @ConstantParameter Register stackPointerRegister) {
        final Word thread = registerAsWord(threadRegister);
        final long registerSaver = SaveAllRegistersNode.saveAllRegisters();

        final Word unrollBlock = fetchUnrollInfo(registerSaver, deoptimizationUnpackDeopt(INJECTED_VMCONFIG));

        deoptimizationCommon(stackPointerRegister, thread, registerSaver, unrollBlock);
    }

    static void deoptimizationCommon(Register stackPointerRegister, final Word thread, final long registerSaver, final Word unrollBlock) {
        // Pop all the frames we must move/replace.
        //
        // Frame picture (youngest to oldest)
        // 1: self-frame
        // 2: deoptimizing frame
        // 3: caller of deoptimizing frame (could be compiled/interpreted).

        // Pop self-frame.
        LeaveCurrentStackFrameNode.leaveCurrentStackFrame(registerSaver);

        // Load the initial info we should save (e.g. frame pointer).
        final Word initialInfo = unrollBlock.readWord(deoptimizationUnrollBlockInitialInfoOffset(INJECTED_VMCONFIG));

        // Pop deoptimized frame.
        final int sizeOfDeoptimizedFrame = unrollBlock.readInt(deoptimizationUnrollBlockSizeOfDeoptimizedFrameOffset(INJECTED_VMCONFIG));
        LeaveDeoptimizedStackFrameNode.leaveDeoptimizedStackFrame(sizeOfDeoptimizedFrame, initialInfo);

        /*
         * Stack bang to make sure there's enough room for the interpreter frames. Bang stack for
         * total size of the interpreter frames plus shadow page size. Bang one page at a time
         * because large sizes can bang beyond yellow and red zones.
         *
         * @deprecated This code should go away as soon as JDK-8032410 hits the Graal repository.
         */
        final int totalFrameSizes = unrollBlock.readInt(deoptimizationUnrollBlockTotalFrameSizesOffset(INJECTED_VMCONFIG));
        final int bangPages = NumUtil.roundUp(totalFrameSizes, pageSize()) / pageSize() + stackShadowPages(INJECTED_VMCONFIG);
        Word stackPointer = readRegister(stackPointerRegister);

        for (int i = 1; i < bangPages; i++) {
            stackPointer.writeInt((-i * pageSize()) + stackBias(INJECTED_VMCONFIG), 0, STACK_BANG_LOCATION);
        }

        // Load number of interpreter frames.
        final int numberOfFrames = unrollBlock.readInt(deoptimizationUnrollBlockNumberOfFramesOffset(INJECTED_VMCONFIG));

        // Load address of array of frame sizes.
        final Word frameSizes = unrollBlock.readWord(deoptimizationUnrollBlockFrameSizesOffset(INJECTED_VMCONFIG));

        // Load address of array of frame PCs.
        final Word framePcs = unrollBlock.readWord(deoptimizationUnrollBlockFramePcsOffset(INJECTED_VMCONFIG));

        /*
         * Get the current stack pointer (sender's original SP) before adjustment so that we can
         * save it in the skeletal interpreter frame.
         */
        Word senderSp = readRegister(stackPointerRegister);

        // Adjust old interpreter frame to make space for new frame's extra Java locals.
        final int callerAdjustment = unrollBlock.readInt(deoptimizationUnrollBlockCallerAdjustmentOffset(INJECTED_VMCONFIG));
        writeRegister(stackPointerRegister, readRegister(stackPointerRegister).subtract(callerAdjustment));

        for (int i = 0; i < numberOfFrames; i++) {
            final Word frameSize = frameSizes.readWord(i * wordSize());
            final Word framePc = framePcs.readWord(i * wordSize());

            // Push an interpreter frame onto the stack.
            PushInterpreterFrameNode.pushInterpreterFrame(frameSize, framePc, senderSp, initialInfo);

            // Get the current stack pointer (sender SP) and pass it to next frame.
            senderSp = readRegister(stackPointerRegister);
        }

        // Get final return address.
        final Word framePc = framePcs.readWord(numberOfFrames * wordSize());

        /*
         * Enter a frame to call out to unpack frames. Since we changed the stack pointer to an
         * unknown alignment we need to align it here before calling C++ code.
         */
        final Word senderFp = initialInfo;
        EnterUnpackFramesStackFrameNode.enterUnpackFramesStackFrame(framePc, senderSp, senderFp, registerSaver);

        final int mode = unrollBlock.readInt(deoptimizationUnrollBlockUnpackKindOffset(INJECTED_VMCONFIG));
        unpackFrames(UNPACK_FRAMES, thread, mode);

        LeaveUnpackFramesStackFrameNode.leaveUnpackFramesStackFrame(registerSaver);
    }

    /**
     * Reads the value of the passed register as a Word.
     */
    private static Word readRegister(Register register) {
        return registerAsWord(register, false, false);
    }

    /**
     * Writes the value of the passed register.
     *
     * @param value value the register should be set to
     */
    private static void writeRegister(Register register, Word value) {
        writeRegisterAsWord(register, value);
    }

    @Fold
    static int stackShadowPages(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.useStackBanging ? config.stackShadowPages : 0;
    }

    /**
     * Returns the stack bias for the host architecture.
     *
     * @deprecated This method should go away as soon as JDK-8032410 hits the Graal repository.
     *
     * @return stack bias
     */
    @Deprecated
    @Fold
    static int stackBias(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.stackBias;
    }

    @Fold
    static int deoptimizationUnrollBlockSizeOfDeoptimizedFrameOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptimizationUnrollBlockSizeOfDeoptimizedFrameOffset;
    }

    @Fold
    static int deoptimizationUnrollBlockCallerAdjustmentOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptimizationUnrollBlockCallerAdjustmentOffset;
    }

    @Fold
    static int deoptimizationUnrollBlockNumberOfFramesOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptimizationUnrollBlockNumberOfFramesOffset;
    }

    @Fold
    static int deoptimizationUnrollBlockTotalFrameSizesOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptimizationUnrollBlockTotalFrameSizesOffset;
    }

    @Fold
    static int deoptimizationUnrollBlockUnpackKindOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptimizationUnrollBlockUnpackKindOffset;
    }

    @Fold
    static int deoptimizationUnrollBlockFrameSizesOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptimizationUnrollBlockFrameSizesOffset;
    }

    @Fold
    static int deoptimizationUnrollBlockFramePcsOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptimizationUnrollBlockFramePcsOffset;
    }

    @Fold
    static int deoptimizationUnrollBlockInitialInfoOffset(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptimizationUnrollBlockInitialInfoOffset;
    }

    @Fold
    static int deoptimizationUnpackDeopt(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptimizationUnpackDeopt;
    }

    @Fold
    static int deoptimizationUnpackUncommonTrap(@InjectedParameter GraalHotSpotVMConfig config) {
        return config.deoptimizationUnpackUncommonTrap;
    }

    @NodeIntrinsic(value = StubForeignCallNode.class, setStampFromReturnType = true)
    public static native int unpackFrames(@ConstantNodeParameter ForeignCallDescriptor unpackFrames, Word thread, int mode);
}

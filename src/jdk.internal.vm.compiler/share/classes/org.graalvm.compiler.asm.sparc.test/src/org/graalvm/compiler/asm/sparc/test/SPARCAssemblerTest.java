/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.asm.sparc.test;

import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BPCC;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BPR;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BR;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CBCOND;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.Annul.ANNUL;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.BranchPredict.PREDICT_NOT_TAKEN;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.CC.Xcc;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.CarryClear;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.ConditionFlag.Equal;
import static org.graalvm.compiler.asm.sparc.SPARCAssembler.RCondition.Rc_z;
import static jdk.vm.ci.sparc.SPARC.g0;

import java.util.EnumSet;
import java.util.function.Consumer;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.sparc.SPARC;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.sparc.SPARCAssembler;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.ControlTransferOp;
import org.graalvm.compiler.asm.sparc.SPARCAssembler.SPARCOp;
import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.test.GraalTest;

public class SPARCAssemblerTest extends GraalTest {
    private SPARCMacroAssembler masm;

    private static EnumSet<SPARC.CPUFeature> computeFeatures() {
        EnumSet<SPARC.CPUFeature> features = EnumSet.noneOf(SPARC.CPUFeature.class);
        features.add(SPARC.CPUFeature.CBCOND);
        return features;
    }

    private static TargetDescription createTarget() {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        Architecture arch = new SPARC(computeFeatures());
        return new TargetDescription(arch, true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    @Before
    public void setup() {
        TargetDescription target = createTarget();
        masm = new SPARCMacroAssembler(target);
    }

    @Test
    public void testPatchCbcod() {
        testControlTransferOp(l -> CBCOND.emit(masm, CarryClear, false, g0, 3, l), -512, 511);
    }

    @Test
    public void testPatchBpcc() {
        int maxDisp = 1 << 18;
        testControlTransferOp(l -> BPCC.emit(masm, Xcc, Equal, ANNUL, PREDICT_NOT_TAKEN, l), -maxDisp,
                        maxDisp - 1);
    }

    @Test
    public void testPatchBpr() {
        int maxDisp = 1 << 15;
        testControlTransferOp(l -> BPR.emit(masm, Rc_z, ANNUL, PREDICT_NOT_TAKEN, g0, l), -maxDisp,
                        maxDisp - 1);
    }

    @Test
    public void testPatchBr() {
        int maxDisp = 1 << 21;
        testControlTransferOp(l -> BR.emit(masm, Equal, ANNUL, l), -maxDisp,
                        maxDisp - 1);
    }

    @Test(expected = BailoutException.class)
    public void testControlTransferInvalidDisp() {
        int cbcondInstruction = 0x12f83f60;
        CBCOND.setDisp(cbcondInstruction, 0x2ff);
    }

    public void testControlTransferOp(Consumer<Label> opCreator, int minDisp, int maxDisp) {
        doTestControlTransferOp(opCreator, minDisp, maxDisp);
        try {
            doTestControlTransferOp(opCreator, minDisp - 1, maxDisp);
            fail("minDisp out of bound must not assemble correctly");
        } catch (BailoutException e) {
            // ignored
        }
        try {
            doTestControlTransferOp(opCreator, minDisp, maxDisp + 1);
            fail("maxDisp out of bound must not assemble correctly");
        } catch (BailoutException e) {
            // ignored
        }
    }

    /**
     * Assembles the control transfer op and then verifies the expected disp value against the disp
     * field provided by the disassembler.
     */
    public void doTestControlTransferOp(Consumer<Label> opCreator, int minDisp, int maxDisp) {
        Label lBack = new Label();
        Label lForward = new Label();
        masm.bind(lBack);
        for (int i = 0; i < -minDisp; i++) {
            masm.nop();
        }
        int backPos = masm.position();
        opCreator.accept(lBack);
        masm.nop(); // Nop required to separate the two control transfer instructions
        int forwardPos = masm.position();
        opCreator.accept(lForward);
        for (int i = 0; i < maxDisp - 1; i++) {
            masm.nop();
        }
        masm.bind(lForward);

        int condBack = masm.getInt(backPos);
        SPARCOp backOp = SPARCAssembler.getSPARCOp(condBack);
        int dispBack = ((ControlTransferOp) backOp).getDisp(condBack);
        Assert.assertEquals(minDisp, dispBack);

        int condFwd = masm.getInt(forwardPos);
        SPARCOp fwdOp = SPARCAssembler.getSPARCOp(condFwd);
        int dispFwd = ((ControlTransferOp) fwdOp).getDisp(condFwd);
        Assert.assertEquals(maxDisp, dispFwd);
    }
}

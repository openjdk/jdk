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

package org.graalvm.compiler.asm.aarch64.test;

import static org.junit.Assert.assertArrayEquals;

import java.util.EnumSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.graalvm.compiler.asm.NumUtil;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.AddressGenerationPlan;
import org.graalvm.compiler.test.GraalTest;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64.CPUFeature;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.TargetDescription;

public class AArch64MacroAssemblerTest extends GraalTest {

    private AArch64MacroAssembler masm;
    private TestProtectedAssembler asm;
    private Register base;
    private Register index;
    private Register scratch;

    private static EnumSet<AArch64.CPUFeature> computeFeatures() {
        EnumSet<AArch64.CPUFeature> features = EnumSet.noneOf(AArch64.CPUFeature.class);
        features.add(CPUFeature.FP);
        return features;
    }

    private static EnumSet<AArch64.Flag> computeFlags() {
        EnumSet<AArch64.Flag> flags = EnumSet.noneOf(AArch64.Flag.class);
        return flags;
    }

    private static TargetDescription createTarget() {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        Architecture arch = new AArch64(computeFeatures(), computeFlags());
        return new TargetDescription(arch, true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    @Before
    public void setupEnvironment() {
        TargetDescription target = createTarget();
        masm = new AArch64MacroAssembler(target);
        asm = new TestProtectedAssembler(target);
        base = AArch64.r10;
        index = AArch64.r13;
        scratch = AArch64.r15;
    }

    @Test
    public void testGenerateAddressPlan() {
        AddressGenerationPlan plan = AArch64MacroAssembler.generateAddressPlan(NumUtil.getNbitNumberInt(8), false, 0);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.NO_WORK && !plan.needsScratch &&
                        (plan.addressingMode == AArch64Address.AddressingMode.IMMEDIATE_SCALED || plan.addressingMode == AArch64Address.AddressingMode.IMMEDIATE_UNSCALED));

        plan = AArch64MacroAssembler.generateAddressPlan(NumUtil.getNbitNumberInt(8), false, 1);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.NO_WORK && !plan.needsScratch &&
                        (plan.addressingMode == AArch64Address.AddressingMode.IMMEDIATE_SCALED || plan.addressingMode == AArch64Address.AddressingMode.IMMEDIATE_UNSCALED));

        plan = AArch64MacroAssembler.generateAddressPlan(-NumUtil.getNbitNumberInt(8) - 1, false, 0);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.NO_WORK && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.IMMEDIATE_UNSCALED);

        plan = AArch64MacroAssembler.generateAddressPlan(NumUtil.getNbitNumberInt(12), false, 1);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.NO_WORK && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.IMMEDIATE_SCALED);

        plan = AArch64MacroAssembler.generateAddressPlan(NumUtil.getNbitNumberInt(12) << 2, false, 4);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.NO_WORK && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.IMMEDIATE_SCALED);

        plan = AArch64MacroAssembler.generateAddressPlan(0, false, 8);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.NO_WORK && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.REGISTER_OFFSET);

        plan = AArch64MacroAssembler.generateAddressPlan(0, false, 0);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.NO_WORK && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.REGISTER_OFFSET);

        plan = AArch64MacroAssembler.generateAddressPlan(NumUtil.getNbitNumberInt(9), false, 0);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.ADD_TO_BASE && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.REGISTER_OFFSET);

        plan = AArch64MacroAssembler.generateAddressPlan(NumUtil.getNbitNumberInt(12), false, 8);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.ADD_TO_BASE && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.REGISTER_OFFSET);

        plan = AArch64MacroAssembler.generateAddressPlan(NumUtil.getNbitNumberInt(13), false, 8);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.ADD_TO_BASE && plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.REGISTER_OFFSET);

        plan = AArch64MacroAssembler.generateAddressPlan(-NumUtil.getNbitNumberInt(12), false, 8);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.ADD_TO_BASE && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.REGISTER_OFFSET);

        plan = AArch64MacroAssembler.generateAddressPlan(-(NumUtil.getNbitNumberInt(12) << 12), false, 8);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.ADD_TO_BASE && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.REGISTER_OFFSET);

        plan = AArch64MacroAssembler.generateAddressPlan(NumUtil.getNbitNumberInt(12), true, 8);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.ADD_TO_BASE && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.REGISTER_OFFSET);

        plan = AArch64MacroAssembler.generateAddressPlan(NumUtil.getNbitNumberInt(12) << 3, true, 8);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.ADD_TO_INDEX && !plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.REGISTER_OFFSET);

        plan = AArch64MacroAssembler.generateAddressPlan(NumUtil.getNbitNumberInt(13) << 3, true, 8);
        Assert.assertTrue(plan.workPlan == AddressGenerationPlan.WorkPlan.ADD_TO_INDEX && plan.needsScratch && plan.addressingMode == AArch64Address.AddressingMode.REGISTER_OFFSET);
    }

    @Test
    public void testMakeAddressNoAction() {
        AArch64Address address = masm.makeAddress(base, NumUtil.getNbitNumberInt(12) << 3, AArch64.zr, false, 8, null, false);
        Assert.assertTrue(address.isScaled() && address.getAddressingMode() == AArch64Address.AddressingMode.IMMEDIATE_SCALED && address.getBase().equals(base) &&
                        address.getOffset().equals(AArch64.zr) && address.getImmediateRaw() == NumUtil.getNbitNumberInt(12));
        // No code generated.
        compareAssembly();
    }

    @Test
    public void testMakeAddressAddIndex() {
        AArch64Address address = masm.makeAddress(base, NumUtil.getNbitNumberInt(8) << 5, index, false, 8, null, true);
        Assert.assertTrue(address.isScaled() && address.getAddressingMode() == AArch64Address.AddressingMode.REGISTER_OFFSET && address.getBase().equals(base) && address.getOffset().equals(index));
        asm.add(64, index, index, NumUtil.getNbitNumberInt(8) << 2);
        compareAssembly();
    }

    @Test
    public void testMakeAddressAddIndexNoOverwrite() {
        AArch64Address address = masm.makeAddress(base, NumUtil.getNbitNumberInt(8) << 5, index, false, 8, scratch, false);
        Assert.assertTrue(address.isScaled() && address.getAddressingMode() == AArch64Address.AddressingMode.REGISTER_OFFSET && address.getBase().equals(base) && address.getOffset().equals(scratch));
        asm.add(64, scratch, index, NumUtil.getNbitNumberInt(8) << 2);
        compareAssembly();
    }

    @Test
    public void testMakeAddressAddBaseNoOverwrite() {
        AArch64Address address = masm.makeAddress(base, NumUtil.getNbitNumberInt(12), index, false, 8, scratch, false);
        Assert.assertTrue(address.isScaled() && address.getAddressingMode() == AArch64Address.AddressingMode.REGISTER_OFFSET && address.getBase().equals(scratch) && address.getOffset().equals(index));
        asm.add(64, scratch, base, NumUtil.getNbitNumberInt(12));
        compareAssembly();
    }

    @Test
    public void testMakeAddressAddBase() {
        AArch64Address address = masm.makeAddress(base, NumUtil.getNbitNumberInt(12), index, false, 8, null, true);
        Assert.assertTrue(address.isScaled() && address.getAddressingMode() == AArch64Address.AddressingMode.REGISTER_OFFSET && address.getBase().equals(base) && address.getOffset().equals(index));
        asm.add(64, base, base, NumUtil.getNbitNumberInt(12));
        compareAssembly();
    }

    @Test
    public void testMakeAddressAddIndexNoOverwriteExtend() {
        AArch64Address address = masm.makeAddress(base, NumUtil.getNbitNumberInt(8) << 5, index, true, 8, scratch, false);
        Assert.assertTrue(address.isScaled() && address.getAddressingMode() == AArch64Address.AddressingMode.EXTENDED_REGISTER_OFFSET && address.getBase().equals(base) &&
                        address.getOffset().equals(scratch) && address.getExtendType() == AArch64Assembler.ExtendType.SXTW);
        asm.add(32, scratch, index, NumUtil.getNbitNumberInt(8) << 2);
        compareAssembly();
    }

    @Test
    public void testMakeAddressAddIndexExtend() {
        AArch64Address address = masm.makeAddress(base, NumUtil.getNbitNumberInt(8) << 5, index, true, 8, scratch, true);
        Assert.assertTrue(address.isScaled() && address.getAddressingMode() == AArch64Address.AddressingMode.EXTENDED_REGISTER_OFFSET && address.getBase().equals(base) &&
                        address.getOffset().equals(index) && address.getExtendType() == AArch64Assembler.ExtendType.SXTW);
        asm.add(32, index, index, NumUtil.getNbitNumberInt(8) << 2);
        compareAssembly();
    }

    @Test
    public void testLoadAddressUnscaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createUnscaledImmediateAddress(base, NumUtil.getNbitNumberInt(8));
        masm.loadAddress(dst, address, 8);
        asm.add(64, dst, base, NumUtil.getNbitNumberInt(8));
        compareAssembly();
    }

    @Test
    public void testLoadAddressUnscaled2() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createUnscaledImmediateAddress(base, -NumUtil.getNbitNumberInt(8));
        masm.loadAddress(dst, address, 8);
        asm.sub(64, dst, base, NumUtil.getNbitNumberInt(8));
        compareAssembly();
    }

    @Test
    public void testLoadAddressScaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createScaledImmediateAddress(base, NumUtil.getNbitNumberInt(12));
        masm.loadAddress(dst, address, 8);
        asm.add(64, dst, base, NumUtil.getNbitNumberInt(9) << 3);
        asm.add(64, dst, dst, NumUtil.getNbitNumberInt(3) << 12);
        compareAssembly();
    }

    @Test
    public void testLoadAddressScaledLowerOnly() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createScaledImmediateAddress(base, NumUtil.getNbitNumberInt(5));
        masm.loadAddress(dst, address, 8);
        asm.add(64, dst, base, NumUtil.getNbitNumberInt(5) << 3);
        compareAssembly();
    }

    @Test
    public void testLoadAddressScaledHigherOnly() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createScaledImmediateAddress(base, 1 << 11);
        masm.loadAddress(dst, address, 8);
        asm.add(64, dst, base, 1 << 11 << 3);
        compareAssembly();
    }

    @Test
    public void testLoadAddressRegisterOffsetUnscaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createRegisterOffsetAddress(base, index, false);
        masm.loadAddress(dst, address, 4);
        asm.add(64, dst, base, index, AArch64Assembler.ShiftType.LSL, 0);
        compareAssembly();
    }

    @Test
    public void testLoadAddressRegisterOffsetScaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createRegisterOffsetAddress(base, index, true);
        masm.loadAddress(dst, address, 4);
        asm.add(64, dst, base, index, AArch64Assembler.ShiftType.LSL, 2);
        compareAssembly();
    }

    @Test
    public void testLoadAddressExtendedRegisterOffsetUnscaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createExtendedRegisterOffsetAddress(base, index, false, AArch64Assembler.ExtendType.SXTW);
        masm.loadAddress(dst, address, 4);
        asm.add(64, dst, base, index, AArch64Assembler.ExtendType.SXTW, 0);
        compareAssembly();
    }

    @Test
    public void testLoadAddressExtendedRegisterOffsetScaled() {
        Register dst = AArch64.r26;
        AArch64Address address = AArch64Address.createExtendedRegisterOffsetAddress(base, index, true, AArch64Assembler.ExtendType.SXTW);
        masm.loadAddress(dst, address, 4);
        asm.add(64, dst, base, index, AArch64Assembler.ExtendType.SXTW, 2);
        compareAssembly();
    }

    /**
     * Compares assembly generated by the macro assembler to the hand-generated assembly.
     */
    private void compareAssembly() {
        byte[] expected = asm.close(true);
        byte[] actual = masm.close(true);
        assertArrayEquals(expected, actual);
    }

}

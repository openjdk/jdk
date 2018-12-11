/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Red Hat Inc. All rights reserved.
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



package jdk.tools.jaotc.aarch64;

import static jdk.vm.ci.aarch64.AArch64.r12;
import static jdk.vm.ci.aarch64.AArch64.r16;
import static jdk.vm.ci.aarch64.AArch64.r17;
import static jdk.vm.ci.aarch64.AArch64.r9;

import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;

import jdk.tools.jaotc.ELFMacroAssembler;
import jdk.tools.jaotc.StubInformation;
import jdk.vm.ci.code.TargetDescription;

public final class AArch64ELFMacroAssembler extends AArch64MacroAssembler implements ELFMacroAssembler {

    private int currentEndOfInstruction;

    public AArch64ELFMacroAssembler(TargetDescription target) {
        super(target);
    }

    @Override
    public int currentEndOfInstruction() {
        return currentEndOfInstruction;
    }

    @Override
    public byte[] getPLTJumpCode() {
        // The main dispatch instruction
        addressOf(r16);
        ldr(64, r16, AArch64Address.createBaseRegisterOnlyAddress(r16));
        jmp(r16);

        currentEndOfInstruction = position();

        align(8);

        return close(true);
    }

    @Override
    public byte[] getPLTStaticEntryCode(StubInformation stub) {
        // The main dispatch instruction
        addressOf(r16);
        ldr(64, r16, AArch64Address.createBaseRegisterOnlyAddress(r16));
        jmp(r16);
        stub.setDispatchJumpOffset(position());

        // C2I stub used to call interpreter. First load r12
        // (i.e. rmethod) with a pointer to the Method structure ...
        addressOf(r12);
        ldr(64, r12, AArch64Address.createBaseRegisterOnlyAddress(r12));
        nop();
        stub.setMovOffset(position());

        // ... then jump to the interpreter.
        addressOf(r16);
        ldr(64, r16, AArch64Address.createBaseRegisterOnlyAddress(r16));
        jmp(r16);
        stub.setC2IJumpOffset(position());

        // Call to VM runtime to resolve the call.
        stub.setResolveJumpStart(position());
        addressOf(r16);
        ldr(64, r16, AArch64Address.createBaseRegisterOnlyAddress(r16));
        jmp(r16);
        stub.setResolveJumpOffset(position());
        currentEndOfInstruction = position();

        align(8);
        stub.setSize(position());

        return close(true);
    }

    @Override
    public byte[] getPLTVirtualEntryCode(StubInformation stub) {
        // Fixup an inline cache.
        // Load r9 with a pointer to the Klass.
        addressOf(r17);
        ldr(64, r9, AArch64Address.createBaseRegisterOnlyAddress(r17));
        nop();
        stub.setMovOffset(position());

        // Jump to the method.
        addressOf(r16);
        ldr(64, r16, AArch64Address.createBaseRegisterOnlyAddress(r16));
        jmp(r16);
        stub.setDispatchJumpOffset(position());

        // Call to VM runtime to resolve the call.
        stub.setResolveJumpStart(position());
        addressOf(r16);
        ldr(64, r16, AArch64Address.createBaseRegisterOnlyAddress(r16));
        jmp(r16);
        stub.setResolveJumpOffset(position());
        currentEndOfInstruction = position();

        align(8);
        stub.setSize(position());

        return close(true);
    }
}

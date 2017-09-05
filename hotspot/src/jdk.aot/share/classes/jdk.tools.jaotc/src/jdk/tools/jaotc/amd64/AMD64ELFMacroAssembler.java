/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc.amd64;

import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rip;

import jdk.tools.jaotc.StubInformation;
import jdk.tools.jaotc.ELFMacroAssembler;
import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;

import jdk.vm.ci.code.TargetDescription;

public final class AMD64ELFMacroAssembler extends AMD64MacroAssembler implements ELFMacroAssembler {

    private int currentEndOfInstruction;

    public AMD64ELFMacroAssembler(TargetDescription target) {
        super(target);
    }

    @Override
    public int currentEndOfInstruction() {
        return currentEndOfInstruction;
    }

    @Override
    public byte[] getPLTJumpCode() {
        // The main dispatch instruction
        // jmpq *0x00000000(%rip)
        jmp(new AMD64Address(rip, 0));
        currentEndOfInstruction = position();

        // Align to 8 bytes
        align(8);

        return close(true);
    }

    @Override
    public byte[] getPLTStaticEntryCode(StubInformation stub) {
        // The main dispatch instruction
        // jmpq *0x00000000(%rip)
        jmp(new AMD64Address(rip, 0));
        stub.setDispatchJumpOffset(position());

        // C2I stub used to call interpreter.
        // mov 0x00000000(%rip),%rbx Loading Method*
        movq(rbx, new AMD64Address(rip, 0));
        stub.setMovOffset(position());

        // jmpq *0x00000000(%rip) [c2i addr]
        jmp(new AMD64Address(rip, 0));
        stub.setC2IJumpOffset(position());

        // Call to VM runtime to resolve the call.
        // jmpq *0x00000000(%rip)
        stub.setResolveJumpStart(position());
        jmp(new AMD64Address(rip, 0));
        stub.setResolveJumpOffset(position());
        currentEndOfInstruction = position();

        // Align to 8 bytes
        align(8);
        stub.setSize(position());

        return close(true);
    }

    @Override
    public byte[] getPLTVirtualEntryCode(StubInformation stub) {
        // Klass loading instruction
        // mov 0x00000000(%rip),%rax
        movq(rax, new AMD64Address(rip, 0));
        stub.setMovOffset(position());

        // The main dispatch instruction
        // jmpq *0x00000000(%rip)
        jmp(new AMD64Address(rip, 0));
        stub.setDispatchJumpOffset(position());

        // Call to VM runtime to resolve the call.
        // jmpq *0x00000000(%rip)
        stub.setResolveJumpStart(position());
        jmp(new AMD64Address(rip, 0));
        stub.setResolveJumpOffset(position());
        currentEndOfInstruction = position();

        // Align to 8 bytes
        align(8);
        stub.setSize(position());

        return close(true);
    }

}

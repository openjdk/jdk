/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.amd64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.ArrayList;
import java.util.BitSet;

import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.amd64.AMD64Call.ForeignCallOp;
import org.graalvm.compiler.lir.amd64.vector.AMD64VectorInstruction;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.Value;

/**
 * vzeroupper is essential to avoid performance penalty during SSE-AVX transition. Specifically,
 * once we have executed instructions that modify the upper bits (i.e., 128+) of the YMM registers,
 * we need to perform vzeroupper to transit the state to 128bits before executing any SSE
 * instructions. We don't need to place vzeroupper between VEX-encoded SSE instructions and legacy
 * SSE instructions, nor between AVX instructions and VEX-encoded SSE instructions.
 *
 * When running Graal on HotSpot, we emit a vzeroupper LIR operation (i.e. an instance of this
 * class) before a foreign call to the runtime function where Graal has no knowledge. The underlying
 * reason is that HotSpot is SSE-compiled so as to support older CPUs. We also emit a vzeroupper
 * instruction (see {@code AMD64HotSpotReturnOp.emitCode}) upon returning, if the current LIR graph
 * contains LIR operations that touch the upper bits of the YMM registers, including but not limited
 * to {@link AMD64VectorInstruction}, {@link AMD64ArrayCompareToOp}, {@link AMD64ArrayEqualsOp},
 * {@link AMD64ArrayIndexOfOp}, and {@link ForeignCallOp} that invokes to Graal-compiled stubs. For
 * the last case, since Graal-compiled stubs is under our control, we don't emit vzeroupper upon
 * returning of the stub, but rather do that upon returning of the current method.
 *
 * On JDK8, C2 does not emit many vzeroupper instructions, potentially because that YMM registers
 * are not heavily employed (C2 vectorization starts using YMM registers in 9, source
 * https://cr.openjdk.java.net/~vlivanov/talks/2017_Vectorization_in_HotSpot_JVM.pdf) and thus less
 * care has been taken to place these instructions. One example is that many intrinsics employ YMM
 * registers starting from https://bugs.openjdk.java.net/browse/JDK-8005419, but does not properly
 * place vzeroupper upon returning of the intrinsic stub or the caller of the stub.
 *
 * Most vzeroupper were added in JDK 10 (https://bugs.openjdk.java.net/browse/JDK-8178811), and was
 * later restricted on Haswell Xeon due to performance regression
 * (https://bugs.openjdk.java.net/browse/JDK-8190934). The actual condition for placing vzeroupper
 * is at http://hg.openjdk.java.net/jdk/jdk/file/c7d9df2e470c/src/hotspot/cpu/x86/x86_64.ad#l428. To
 * summarize, if nmethod employs YMM registers (or intrinsics which use them, search for
 * clear_upper_avx() in opto/library_call.cpp) vzeroupper will be generated on nmethod's exit and
 * before any calls in nmethod, because even compiled nmethods can still use only SSE instructions.
 *
 * This means, if a Java method performs a call to an intrinsic that employs YMM registers,
 * C2-compiled code will place a vzeroupper before the call, upon exit of the stub and upon exit of
 * this method. Graal will only place the last, because it ensures that Graal-compiled Java method
 * and stubs will be consistent on using VEX-encoding.
 *
 * In SubstrateVM, since the whole image is compiled consistently with or without VEX encoding (the
 * later is the default behavior, see {@code NativeImageGenerator.createTarget}), there is no need
 * for vzeroupper. For dynamic compilation on a SubstrateVM image, if the image is SSE-compiled, we
 * then need vzeroupper when returning from the dynamic compiled code to the pre-built image code.
 */
public class AMD64VZeroUpper extends AMD64LIRInstruction {

    public static final LIRInstructionClass<AMD64VZeroUpper> TYPE = LIRInstructionClass.create(AMD64VZeroUpper.class);

    @Temp protected final RegisterValue[] xmmRegisters;

    public AMD64VZeroUpper(Value[] exclude, RegisterConfig registerConfig) {
        super(TYPE);
        xmmRegisters = initRegisterValues(exclude, registerConfig);
    }

    private static RegisterValue[] initRegisterValues(Value[] exclude, RegisterConfig registerConfig) {
        BitSet skippedRegs = new BitSet();
        if (exclude != null) {
            for (Value value : exclude) {
                if (isRegister(value) && asRegister(value).getRegisterCategory().equals(AMD64.XMM)) {
                    skippedRegs.set(asRegister(value).number);
                }
            }
        }
        ArrayList<RegisterValue> regs = new ArrayList<>();
        for (Register r : registerConfig.getCallerSaveRegisters()) {
            if (r.getRegisterCategory().equals(AMD64.XMM) && !skippedRegs.get(r.number)) {
                regs.add(r.asValue());
            }
        }
        return regs.toArray(new RegisterValue[regs.size()]);
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        asm.vzeroupper();
    }
}

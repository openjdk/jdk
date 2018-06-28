/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.sparc;

import static jdk.vm.ci.sparc.SPARC.g5;

import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.sparc.SPARCCall.DirectCallOp;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Value;

/**
 * A direct call that complies with the conventions for such calls in HotSpot. In particular, for
 * calls using an inline cache, a MOVE instruction is emitted just prior to the aligned direct call.
 */
@Opcode("CALL_DIRECT")
final class SPARCHotspotDirectVirtualCallOp extends DirectCallOp {
    public static final LIRInstructionClass<SPARCHotspotDirectVirtualCallOp> TYPE = LIRInstructionClass.create(SPARCHotspotDirectVirtualCallOp.class);
    public static final SizeEstimate SIZE = SizeEstimate.create(8);

    private final InvokeKind invokeKind;
    private final GraalHotSpotVMConfig config;

    SPARCHotspotDirectVirtualCallOp(ResolvedJavaMethod target, Value result, Value[] parameters, Value[] temps, LIRFrameState state, InvokeKind invokeKind, GraalHotSpotVMConfig config) {
        super(TYPE, SIZE, target, result, parameters, temps, state);
        this.invokeKind = invokeKind;
        this.config = config;
        assert invokeKind.isIndirect();
    }

    @Override
    public void emitCallPrefixCode(CompilationResultBuilder crb, SPARCMacroAssembler masm) {
        // The mark for an invocation that uses an inline cache must be placed at the
        // instruction that loads the Klass from the inline cache.
        crb.recordMark(invokeKind == InvokeKind.Virtual ? config.MARKID_INVOKEVIRTUAL : config.MARKID_INVOKEINTERFACE);
        Register scratchRegister = g5;
        masm.setx(config.nonOopBits, scratchRegister, true);
    }
}

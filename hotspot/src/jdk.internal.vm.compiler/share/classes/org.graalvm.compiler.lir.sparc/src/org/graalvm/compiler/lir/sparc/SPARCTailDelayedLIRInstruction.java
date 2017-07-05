/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.sparc;

import org.graalvm.compiler.asm.sparc.SPARCMacroAssembler;
import org.graalvm.compiler.lir.LIRInstruction;

/**
 * Implementors of this interface are able to place its last instruction into the delay slot of a
 * {@link SPARCDelayedControlTransfer} instruction.
 *
 * The implementor has to do following steps to emit code into the delay slot for the
 * DelayedControlTransfer instruction:
 * <ol>
 * <li>Emit everything up to the second last instruction.</li>
 * <li>Call
 * {@link SPARCDelayedControlTransfer#emitControlTransfer(org.graalvm.compiler.lir.asm.CompilationResultBuilder, SPARCMacroAssembler)}
 * to let the DelayedControlTransfer instruction emit its own code (But must not stuff the delay
 * slot with Nop)</li>
 * <li>emit the last instruction for this {@link LIRInstruction}</li>
 * </ol>
 *
 * Note: If this instruction decides not to use the delay slot, it can skip the call of
 * {@link SPARCDelayedControlTransfer#emitControlTransfer(org.graalvm.compiler.lir.asm.CompilationResultBuilder, SPARCMacroAssembler)}
 * . The DelayedControlTransfer instruction will emit the code just with Nop in the delay slot.
 */
public interface SPARCTailDelayedLIRInstruction extends SPARCLIRInstructionMixin {
    default void setDelayedControlTransfer(SPARCDelayedControlTransfer holder) {
        getSPARCLIRInstructionStore().delayedControlTransfer = holder;
    }

    default SPARCDelayedControlTransfer getDelayedControlTransfer() {
        return getSPARCLIRInstructionStore().delayedControlTransfer;
    }
}

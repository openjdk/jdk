/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.amd64;

import org.graalvm.compiler.asm.amd64.AMD64Address;
import org.graalvm.compiler.asm.amd64.AMD64MacroAssembler;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LabelRef;
import org.graalvm.compiler.lir.SwitchStrategy;
import org.graalvm.compiler.lir.amd64.AMD64ControlFlow;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.ValueUtil;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.Value;

final class AMD64HotSpotStrategySwitchOp extends AMD64ControlFlow.StrategySwitchOp {
    public static final LIRInstructionClass<AMD64HotSpotStrategySwitchOp> TYPE = LIRInstructionClass.create(AMD64HotSpotStrategySwitchOp.class);

    AMD64HotSpotStrategySwitchOp(SwitchStrategy strategy, LabelRef[] keyTargets, LabelRef defaultTarget, Value key, Value scratch) {
        super(TYPE, strategy, keyTargets, defaultTarget, key, scratch);
    }

    @Override
    public void emitCode(final CompilationResultBuilder crb, final AMD64MacroAssembler masm) {
        strategy.run(new HotSpotSwitchClosure(ValueUtil.asRegister(key), crb, masm));
    }

    public class HotSpotSwitchClosure extends SwitchClosure {

        protected HotSpotSwitchClosure(Register keyRegister, CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            super(keyRegister, crb, masm);
        }

        @Override
        protected void emitComparison(Constant c) {
            if (c instanceof HotSpotMetaspaceConstant) {
                HotSpotMetaspaceConstant meta = (HotSpotMetaspaceConstant) c;
                if (meta.isCompressed()) {
                    crb.recordInlineDataInCode(meta);
                    masm.cmpl(keyRegister, 0xDEADDEAD);
                } else {
                    AMD64Address addr = (AMD64Address) crb.recordDataReferenceInCode(meta, 8);
                    masm.cmpq(keyRegister, addr);
                }
            } else {
                super.emitComparison(c);
            }
        }
    }
}

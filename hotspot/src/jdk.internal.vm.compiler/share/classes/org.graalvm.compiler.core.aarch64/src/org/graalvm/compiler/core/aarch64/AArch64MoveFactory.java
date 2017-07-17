/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.compiler.core.aarch64;

import static org.graalvm.compiler.lir.LIRValueUtil.asConstant;
import static org.graalvm.compiler.lir.LIRValueUtil.isConstantValue;
import static org.graalvm.compiler.lir.LIRValueUtil.isStackSlotValue;

import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.common.type.DataPointerConstant;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.lir.aarch64.AArch64Move;
import org.graalvm.compiler.lir.aarch64.AArch64Move.LoadAddressOp;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool.MoveFactory;

import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;

public class AArch64MoveFactory implements MoveFactory {

    @Override
    public LIRInstruction createMove(AllocatableValue dst, Value src) {
        boolean srcIsSlot = isStackSlotValue(src);
        boolean dstIsSlot = isStackSlotValue(dst);
        if (isConstantValue(src)) {
            return createLoad(dst, asConstant(src));
        } else if (src instanceof AArch64AddressValue) {
            return new LoadAddressOp(dst, (AArch64AddressValue) src);
        } else {
            assert src instanceof AllocatableValue;
            if (srcIsSlot && dstIsSlot) {
                throw GraalError.shouldNotReachHere(src.getClass() + " " + dst.getClass());
            } else {
                return new AArch64Move.Move(dst, (AllocatableValue) src);
            }
        }
    }

    @Override
    public LIRInstruction createStackMove(AllocatableValue result, AllocatableValue input) {
        return new AArch64Move.Move(result, input);
    }

    @Override
    public LIRInstruction createLoad(AllocatableValue dst, Constant src) {
        if (src instanceof JavaConstant) {
            JavaConstant javaConstant = (JavaConstant) src;
            if (canInlineConstant(javaConstant)) {
                return new AArch64Move.LoadInlineConstant(javaConstant, dst);
            } else {
                // return new AArch64Move.LoadConstantFromTable(javaConstant,
                // constantTableBaseProvider.getConstantTableBase(), dst);
                return new AArch64Move.LoadInlineConstant(javaConstant, dst);
            }
        } else if (src instanceof DataPointerConstant) {
            return new AArch64Move.LoadDataOp(dst, (DataPointerConstant) src);
        } else {
            // throw GraalError.shouldNotReachHere(src.getClass().toString());
            throw GraalError.unimplemented();
        }
    }

    @Override
    public LIRInstruction createStackLoad(AllocatableValue result, Constant input) {
        return createLoad(result, input);
    }

    @Override
    public boolean canInlineConstant(Constant con) {
        if (con instanceof JavaConstant) {
            JavaConstant c = (JavaConstant) con;
            switch (c.getJavaKind()) {
                case Boolean:
                case Byte:
                case Char:
                case Short:
                case Int:
                    return AArch64MacroAssembler.isMovableImmediate(c.asInt());
                case Long:
                    return AArch64MacroAssembler.isMovableImmediate(c.asLong());
                case Object:
                    return c.isNull();
                default:
                    return false;
            }
        }
        return false;
    }

    @Override
    public boolean allowConstantToStackMove(Constant value) {
        return false;
    }

}

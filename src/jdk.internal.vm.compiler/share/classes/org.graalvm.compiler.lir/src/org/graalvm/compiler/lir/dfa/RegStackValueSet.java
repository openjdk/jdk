/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir.dfa;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.asStackSlot;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;
import org.graalvm.compiler.lir.ValueConsumer;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.ReferenceMapBuilder;
import org.graalvm.compiler.lir.util.IndexedValueMap;
import org.graalvm.compiler.lir.util.ValueSet;

import jdk.vm.ci.meta.Value;

final class RegStackValueSet extends ValueSet<RegStackValueSet> {

    private final FrameMap frameMap;
    private final IndexedValueMap registers;
    private final IndexedValueMap stack;
    private Set<Value> extraStack;

    RegStackValueSet(FrameMap frameMap) {
        this.frameMap = frameMap;
        registers = new IndexedValueMap();
        stack = new IndexedValueMap();
    }

    private RegStackValueSet(FrameMap frameMap, RegStackValueSet s) {
        this.frameMap = frameMap;
        registers = new IndexedValueMap(s.registers);
        stack = new IndexedValueMap(s.stack);
        if (s.extraStack != null) {
            extraStack = new HashSet<>(s.extraStack);
        }
    }

    @Override
    public RegStackValueSet copy() {
        return new RegStackValueSet(frameMap, this);
    }

    @Override
    public void put(Value v) {
        if (!shouldProcessValue(v)) {
            return;
        }
        if (isRegister(v)) {
            int index = asRegister(v).number;
            registers.put(index, v);
        } else if (isStackSlot(v)) {
            int index = frameMap.offsetForStackSlot(asStackSlot(v));
            assert index >= 0;
            if (index % 4 == 0) {
                stack.put(index / 4, v);
            } else {
                if (extraStack == null) {
                    extraStack = new HashSet<>();
                }
                extraStack.add(v);
            }
        }
    }

    @Override
    public void putAll(RegStackValueSet v) {
        registers.putAll(v.registers);
        stack.putAll(v.stack);
        if (v.extraStack != null) {
            if (extraStack == null) {
                extraStack = new HashSet<>();
            }
            extraStack.addAll(v.extraStack);
        }
    }

    @Override
    public void remove(Value v) {
        if (!shouldProcessValue(v)) {
            return;
        }
        if (isRegister(v)) {
            int index = asRegister(v).number;
            registers.put(index, null);
        } else if (isStackSlot(v)) {
            int index = frameMap.offsetForStackSlot(asStackSlot(v));
            assert index >= 0;
            if (index % 4 == 0) {
                stack.put(index / 4, null);
            } else if (extraStack != null) {
                extraStack.remove(v);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RegStackValueSet) {
            RegStackValueSet other = (RegStackValueSet) obj;
            return registers.equals(other.registers) && stack.equals(other.stack) && Objects.equals(extraStack, other.extraStack);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    private static boolean shouldProcessValue(Value v) {
        /*
         * We always process registers because we have to track the largest register size that is
         * alive across safepoints in order to save and restore them.
         */
        return isRegister(v) || !LIRKind.isValue(v);
    }

    public void addLiveValues(ReferenceMapBuilder refMap) {
        ValueConsumer addLiveValue = new ValueConsumer() {
            @Override
            public void visitValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                refMap.addLiveValue(value);
            }
        };
        registers.visitEach(null, null, null, addLiveValue);
        stack.visitEach(null, null, null, addLiveValue);
        if (extraStack != null) {
            for (Value v : extraStack) {
                refMap.addLiveValue(v);
            }
        }
    }
}

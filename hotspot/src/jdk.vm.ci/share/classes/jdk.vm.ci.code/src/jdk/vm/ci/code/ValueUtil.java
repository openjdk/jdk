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
package jdk.vm.ci.code;

import java.util.*;

import jdk.vm.ci.meta.*;

/**
 * Utility class for working with the {@link Value} class and its subclasses.
 */
public final class ValueUtil {

    public static boolean isIllegal(Value value) {
        assert value != null;
        return Value.ILLEGAL.equals(value);
    }

    public static boolean isIllegalJavaValue(JavaValue value) {
        assert value != null;
        return Value.ILLEGAL.equals(value);
    }

    public static boolean isLegal(Value value) {
        return !isIllegal(value);
    }

    public static boolean isVirtualObject(JavaValue value) {
        assert value != null;
        return value instanceof VirtualObject;
    }

    public static VirtualObject asVirtualObject(JavaValue value) {
        assert value != null;
        return (VirtualObject) value;
    }

    public static boolean isConstantJavaValue(JavaValue value) {
        assert value != null;
        return value instanceof JavaConstant;
    }

    public static boolean isAllocatableValue(Value value) {
        assert value != null;
        return value instanceof AllocatableValue;
    }

    public static AllocatableValue asAllocatableValue(Value value) {
        assert value != null;
        return (AllocatableValue) value;
    }

    public static boolean isStackSlot(Value value) {
        assert value != null;
        return value instanceof StackSlot;
    }

    public static StackSlot asStackSlot(Value value) {
        assert value != null;
        return (StackSlot) value;
    }

    public static boolean isStackSlotValue(Value value) {
        assert value != null;
        return value instanceof StackSlotValue;
    }

    public static StackSlotValue asStackSlotValue(Value value) {
        assert value != null;
        return (StackSlotValue) value;
    }

    public static boolean isVirtualStackSlot(Value value) {
        assert value != null;
        return value instanceof VirtualStackSlot;
    }

    public static VirtualStackSlot asVirtualStackSlot(Value value) {
        assert value != null;
        return (VirtualStackSlot) value;
    }

    public static boolean isRegister(Value value) {
        assert value != null;
        return value instanceof RegisterValue;
    }

    public static Register asRegister(Value value) {
        return asRegisterValue(value).getRegister();
    }

    public static RegisterValue asRegisterValue(Value value) {
        assert value != null;
        return (RegisterValue) value;
    }

    public static Register asRegister(Value value, PlatformKind kind) {
        if (value.getPlatformKind() != kind) {
            throw new InternalError("needed: " + kind + " got: " + value.getPlatformKind());
        } else {
            return asRegister(value);
        }
    }

    public static boolean sameRegister(Value v1, Value v2) {
        return isRegister(v1) && isRegister(v2) && asRegister(v1).equals(asRegister(v2));
    }

    public static boolean sameRegister(Value v1, Value v2, Value v3) {
        return sameRegister(v1, v2) && sameRegister(v1, v3);
    }

    /**
     * Checks if all the provided values are different physical registers. The parameters can be
     * either {@link Register registers}, {@link Value values} or arrays of them. All values that
     * are not {@link RegisterValue registers} are ignored.
     */
    public static boolean differentRegisters(Object... values) {
        List<Register> registers = collectRegisters(values, new ArrayList<Register>());
        for (int i = 1; i < registers.size(); i++) {
            Register r1 = registers.get(i);
            for (int j = 0; j < i; j++) {
                Register r2 = registers.get(j);
                if (r1.equals(r2)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<Register> collectRegisters(Object[] values, List<Register> registers) {
        for (Object o : values) {
            if (o instanceof Register) {
                registers.add((Register) o);
            } else if (o instanceof Value) {
                if (isRegister((Value) o)) {
                    registers.add(asRegister((Value) o));
                }
            } else if (o instanceof Object[]) {
                collectRegisters((Object[]) o, registers);
            } else {
                throw new IllegalArgumentException("Not a Register or Value: " + o);
            }
        }
        return registers;
    }

    /**
     * Subtract sets of registers (x - y).
     *
     * @param x a set of register to subtract from.
     * @param y a set of registers to subtract.
     * @return resulting set of registers (x - y).
     */
    public static Value[] subtractRegisters(Value[] x, Value[] y) {
        ArrayList<Value> result = new ArrayList<>(x.length);
        for (Value i : x) {
            boolean append = true;
            for (Value j : y) {
                if (ValueUtil.sameRegister(i, j)) {
                    append = false;
                    break;
                }
            }
            if (append) {
                result.add(i);
            }
        }
        Value[] resultArray = new Value[result.size()];
        return result.toArray(resultArray);
    }
}

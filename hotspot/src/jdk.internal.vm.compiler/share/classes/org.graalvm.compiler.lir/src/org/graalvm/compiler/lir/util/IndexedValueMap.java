/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.util;

import java.util.EnumSet;
import java.util.Objects;

import org.graalvm.compiler.lir.InstructionValueConsumer;
import org.graalvm.compiler.lir.InstructionValueProcedure;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

import jdk.vm.ci.meta.Value;

public final class IndexedValueMap {
    private Value[] values;

    public IndexedValueMap() {
        values = Value.NO_VALUES;
    }

    public IndexedValueMap(IndexedValueMap other) {
        int limit = other.values.length;
        while (limit > 0) {
            if (other.values[limit - 1] == null) {
                limit--;
                continue;
            }
            break;
        }
        if (limit == 0) {
            values = Value.NO_VALUES;
        } else {
            values = new Value[limit];
            System.arraycopy(other.values, 0, values, 0, values.length);
        }
    }

    public Value get(int index) {
        return values[index];
    }

    public void put(int index, Value value) {
        if (values.length <= index) {
            if (value == null) {
                return;
            }
            Value[] newValues = new Value[index + 1];
            if (values.length > 0) {
                System.arraycopy(values, 0, newValues, 0, values.length);
            }
            values = newValues;
            values[index] = value;
        } else {
            values[index] = value;
        }
    }

    public void putAll(IndexedValueMap stack) {
        Value[] otherValues = stack.values;
        int limit = otherValues.length;
        if (limit > values.length) {
            while (limit > 0) {
                if (otherValues[limit - 1] == null) {
                    limit--;
                    continue;
                }
                break;
            }
            if (limit > values.length) {
                Value[] newValues = new Value[limit];
                System.arraycopy(values, 0, newValues, 0, values.length);
                values = newValues;
            }
        }
        for (int i = 0; i < limit; i++) {
            Value value = otherValues[i];
            if (value != null) {
                values[i] = value;
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof IndexedValueMap) {
            IndexedValueMap that = (IndexedValueMap) other;
            int limit = Math.min(values.length, that.values.length);
            for (int i = 0; i < limit; i++) {
                if (!Objects.equals(values[i], that.values[i])) {
                    return false;
                }
            }
            for (int i = limit; i < values.length; i++) {
                if (values[i] != null) {
                    return false;
                }
            }
            for (int i = limit; i < that.values.length; i++) {
                if (that.values[i] != null) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    public void forEach(LIRInstruction inst, OperandMode mode, EnumSet<OperandFlag> flags, InstructionValueProcedure proc) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                values[i] = proc.doValue(inst, values[i], mode, flags);
            }
        }
    }

    public void visitEach(LIRInstruction inst, OperandMode mode, EnumSet<OperandFlag> flags, InstructionValueConsumer consumer) {
        for (Value v : values) {
            if (v != null) {
                consumer.visitValue(inst, v, mode, flags);
            }
        }
    }

    @Override
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        boolean comma = false;

        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                if (comma) {
                    sb.append(", ");
                } else {
                    comma = true;
                }

                sb.append(i);
                sb.append(": ");
                sb.append(values[i]);
            }
        }
        sb.append(']');
        return sb.toString();
    }
}

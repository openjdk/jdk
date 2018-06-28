/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.lir;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.EnumSet;

import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * Base class to represent values that need to be stored in more than one register. This is mainly
 * intended to support addresses and not general arbitrary nesting of composite values. Because of
 * the possibility of sharing of CompositeValues they should be immutable.
 */
public abstract class CompositeValue extends Value {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface Component {

        OperandFlag[] value() default OperandFlag.REG;
    }

    public CompositeValue(ValueKind<?> kind) {
        super(kind);
        assert CompositeValueClass.get(getClass()) != null;
    }

    /**
     * Invoke {@code proc} on each {@link Value} element of this {@link CompositeValue}. If
     * {@code proc} replaces any value then a new CompositeValue should be returned.
     *
     * @param inst
     * @param mode
     * @param proc
     * @return the original CompositeValue or a copy with any modified values
     */
    public abstract CompositeValue forEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueProcedure proc);

    /**
     * A helper method to visit {@link Value}[] ensuring that a copy of the array is made if it's
     * needed.
     *
     * @param inst
     * @param values
     * @param mode
     * @param proc
     * @param flags
     * @return the original {@code values} array or a copy if values changed
     */
    protected Value[] visitValueArray(LIRInstruction inst, Value[] values, OperandMode mode, InstructionValueProcedure proc, EnumSet<OperandFlag> flags) {
        Value[] newValues = null;
        for (int i = 0; i < values.length; i++) {
            Value value = values[i];
            Value newValue = proc.doValue(inst, value, mode, flags);
            if (!value.identityEquals(newValue)) {
                if (newValues == null) {
                    newValues = values.clone();
                }
                newValues[i] = value;
            }
        }
        return newValues != null ? newValues : values;
    }

    protected abstract void visitEachComponent(LIRInstruction inst, OperandMode mode, InstructionValueConsumer proc);

    @Override
    public String toString() {
        return CompositeValueClass.format(this);
    }

    @Override
    public int hashCode() {
        return 53 * super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CompositeValue) {
            CompositeValue other = (CompositeValue) obj;
            return super.equals(other);
        }
        return false;
    }
}

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


package org.graalvm.compiler.lir;

import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.STACK;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;

import jdk.internal.vm.compiler.collections.EconomicMap;
import jdk.internal.vm.compiler.collections.Equivalence;
import jdk.internal.vm.compiler.collections.MapCursor;
import org.graalvm.compiler.core.common.FieldIntrospection;
import org.graalvm.compiler.core.common.Fields;
import org.graalvm.compiler.core.common.FieldsScanner;
import org.graalvm.compiler.lir.LIRInstruction.OperandFlag;
import org.graalvm.compiler.lir.LIRInstruction.OperandMode;

import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Value;

abstract class LIRIntrospection<T> extends FieldIntrospection<T> {

    private static final Class<Value> VALUE_CLASS = Value.class;
    private static final Class<ConstantValue> CONSTANT_VALUE_CLASS = ConstantValue.class;
    private static final Class<Variable> VARIABLE_CLASS = Variable.class;
    private static final Class<RegisterValue> REGISTER_VALUE_CLASS = RegisterValue.class;
    private static final Class<StackSlot> STACK_SLOT_CLASS = StackSlot.class;
    private static final Class<Value[]> VALUE_ARRAY_CLASS = Value[].class;

    LIRIntrospection(Class<T> clazz) {
        super(clazz);
    }

    protected static class Values extends Fields {
        private final int directCount;
        private final EnumSet<OperandFlag>[] flags;

        public Values(OperandModeAnnotation mode) {
            this(mode.directCount, mode.values);
        }

        @SuppressWarnings({"unchecked"})
        public Values(int directCount, ArrayList<ValueFieldInfo> fields) {
            super(fields);
            this.directCount = directCount;
            flags = (EnumSet<OperandFlag>[]) new EnumSet<?>[fields.size()];
            for (int i = 0; i < fields.size(); i++) {
                flags[i] = fields.get(i).flags;
            }
        }

        public int getDirectCount() {
            return directCount;
        }

        public EnumSet<OperandFlag> getFlags(int i) {
            return flags[i];
        }

        protected Value getValue(Object obj, int index) {
            return (Value) getObject(obj, index);
        }

        protected void setValue(Object obj, int index, Value value) {
            putObject(obj, index, value);
        }

        protected Value[] getValueArray(Object obj, int index) {
            return (Value[]) getObject(obj, index);
        }

        protected void setValueArray(Object obj, int index, Value[] valueArray) {
            putObject(obj, index, valueArray);
        }
    }

    /**
     * The component values in an {@link LIRInstruction} or {@link CompositeValue}.
     */
    protected Values values;

    protected static class ValueFieldInfo extends FieldsScanner.FieldInfo {

        final EnumSet<OperandFlag> flags;

        public ValueFieldInfo(long offset, String name, Class<?> type, Class<?> declaringClass, EnumSet<OperandFlag> flags) {
            super(offset, name, type, declaringClass);
            assert VALUE_ARRAY_CLASS.isAssignableFrom(type) || VALUE_CLASS.isAssignableFrom(type);
            this.flags = flags;
        }

        /**
         * Sorts non-array fields before array fields.
         */
        @Override
        public int compareTo(FieldsScanner.FieldInfo o) {
            if (VALUE_ARRAY_CLASS.isAssignableFrom(o.type)) {
                if (!VALUE_ARRAY_CLASS.isAssignableFrom(type)) {
                    return -1;
                }
            } else {
                if (VALUE_ARRAY_CLASS.isAssignableFrom(type)) {
                    return 1;
                }
            }
            return super.compareTo(o);
        }

        @Override
        public String toString() {
            return super.toString() + flags;
        }
    }

    protected static class OperandModeAnnotation {

        /**
         * Number of non-array fields in {@link #values}.
         */
        public int directCount;
        public final ArrayList<ValueFieldInfo> values = new ArrayList<>();
    }

    protected abstract static class LIRFieldsScanner extends FieldsScanner {

        public final EconomicMap<Class<? extends Annotation>, OperandModeAnnotation> valueAnnotations;
        public final ArrayList<FieldsScanner.FieldInfo> states = new ArrayList<>();

        public LIRFieldsScanner(FieldsScanner.CalcOffset calc) {
            super(calc);
            valueAnnotations = EconomicMap.create(Equivalence.DEFAULT);
        }

        protected OperandModeAnnotation getOperandModeAnnotation(Field field) {
            OperandModeAnnotation result = null;
            MapCursor<Class<? extends Annotation>, OperandModeAnnotation> cursor = valueAnnotations.getEntries();
            while (cursor.advance()) {
                Annotation annotation = field.getAnnotation(cursor.getKey());
                if (annotation != null) {
                    assert result == null : "Field has two operand mode annotations: " + field;
                    result = cursor.getValue();
                }
            }
            return result;
        }

        protected abstract EnumSet<OperandFlag> getFlags(Field field);

        @Override
        protected void scanField(Field field, long offset) {
            Class<?> type = field.getType();
            if (VALUE_CLASS.isAssignableFrom(type) && !CONSTANT_VALUE_CLASS.isAssignableFrom(type)) {
                assert !Modifier.isFinal(field.getModifiers()) : "Value field must not be declared final because it is modified by register allocator: " + field;
                OperandModeAnnotation annotation = getOperandModeAnnotation(field);
                assert annotation != null : "Field must have operand mode annotation: " + field;
                EnumSet<OperandFlag> flags = getFlags(field);
                assert verifyFlags(field, type, flags);
                annotation.values.add(new ValueFieldInfo(offset, field.getName(), type, field.getDeclaringClass(), flags));
                annotation.directCount++;
            } else if (VALUE_ARRAY_CLASS.isAssignableFrom(type)) {
                OperandModeAnnotation annotation = getOperandModeAnnotation(field);
                assert annotation != null : "Field must have operand mode annotation: " + field;
                EnumSet<OperandFlag> flags = getFlags(field);
                assert verifyFlags(field, type.getComponentType(), flags);
                annotation.values.add(new ValueFieldInfo(offset, field.getName(), type, field.getDeclaringClass(), flags));
            } else {
                assert getOperandModeAnnotation(field) == null : "Field must not have operand mode annotation: " + field;
                assert field.getAnnotation(LIRInstruction.State.class) == null : "Field must not have state annotation: " + field;
                super.scanField(field, offset);
            }
        }

        private static boolean verifyFlags(Field field, Class<?> type, EnumSet<OperandFlag> flags) {
            if (flags.contains(REG)) {
                assert type.isAssignableFrom(REGISTER_VALUE_CLASS) || type.isAssignableFrom(VARIABLE_CLASS) : "Cannot assign RegisterValue / Variable to field with REG flag:" + field;
            }
            if (flags.contains(STACK)) {
                assert type.isAssignableFrom(STACK_SLOT_CLASS) : "Cannot assign StackSlot to field with STACK flag:" + field;
            }
            if (flags.contains(CONST)) {
                assert type.isAssignableFrom(CONSTANT_VALUE_CLASS) : "Cannot assign Constant to field with CONST flag:" + field;
            }
            return true;
        }
    }

    protected static void forEach(LIRInstruction inst, Values values, OperandMode mode, InstructionValueProcedure proc) {
        for (int i = 0; i < values.getCount(); i++) {
            assert LIRInstruction.ALLOWED_FLAGS.get(mode).containsAll(values.getFlags(i));

            if (i < values.getDirectCount()) {
                Value value = values.getValue(inst, i);
                Value newValue;
                if (value instanceof CompositeValue) {
                    CompositeValue composite = (CompositeValue) value;
                    newValue = composite.forEachComponent(inst, mode, proc);
                } else {
                    newValue = proc.doValue(inst, value, mode, values.getFlags(i));
                }
                if (!value.identityEquals(newValue)) {
                    values.setValue(inst, i, newValue);
                }
            } else {
                Value[] valueArray = values.getValueArray(inst, i);
                for (int j = 0; j < valueArray.length; j++) {
                    Value value = valueArray[j];
                    Value newValue;
                    if (value instanceof CompositeValue) {
                        CompositeValue composite = (CompositeValue) value;
                        newValue = composite.forEachComponent(inst, mode, proc);
                    } else {
                        newValue = proc.doValue(inst, value, mode, values.getFlags(i));
                    }
                    if (!value.identityEquals(newValue)) {
                        valueArray[j] = newValue;
                    }
                }
            }
        }
    }

    protected static void visitEach(LIRInstruction inst, Values values, OperandMode mode, InstructionValueConsumer proc) {
        for (int i = 0; i < values.getCount(); i++) {
            assert LIRInstruction.ALLOWED_FLAGS.get(mode).containsAll(values.getFlags(i));

            if (i < values.getDirectCount()) {
                Value value = values.getValue(inst, i);
                if (value instanceof CompositeValue) {
                    CompositeValue composite = (CompositeValue) value;
                    composite.visitEachComponent(inst, mode, proc);
                } else {
                    proc.visitValue(inst, value, mode, values.getFlags(i));
                }
            } else {
                Value[] valueArray = values.getValueArray(inst, i);
                for (int j = 0; j < valueArray.length; j++) {
                    Value value = valueArray[j];
                    if (value instanceof CompositeValue) {
                        CompositeValue composite = (CompositeValue) value;
                        composite.visitEachComponent(inst, mode, proc);
                    } else {
                        proc.visitValue(inst, value, mode, values.getFlags(i));
                    }
                }
            }
        }
    }

    protected static void appendValues(StringBuilder sb, Object obj, String start, String end, String startMultiple, String endMultiple, String[] prefix, Fields... fieldsList) {
        int total = 0;
        for (Fields fields : fieldsList) {
            total += fields.getCount();
        }
        if (total == 0) {
            return;
        }

        sb.append(start);
        if (total > 1) {
            sb.append(startMultiple);
        }
        String sep = "";
        int i = 0;
        for (Fields fields : fieldsList) {
            for (int j = 0; j < fields.getCount(); j++) {
                sb.append(sep).append(prefix[i]);
                if (total > 1) {
                    sb.append(fields.getName(j)).append(": ");
                }
                sb.append(getFieldString(obj, j, fields));
                sep = ", ";
            }
            i++;
        }
        if (total > 1) {
            sb.append(endMultiple);
        }
        sb.append(end);
    }

    protected static String getFieldString(Object obj, int index, Fields fields) {
        Object value = fields.get(obj, index);
        Class<?> type = fields.getType(index);
        if (value == null || type.isPrimitive() || !type.isArray()) {
            return String.valueOf(value);
        }
        if (type == int[].class) {
            return Arrays.toString((int[]) value);
        } else if (type == double[].class) {
            return Arrays.toString((double[]) value);
        } else if (type == byte[].class) {
            byte[] byteValue = (byte[]) value;
            if (isPrintableAsciiString(byteValue)) {
                return toString(byteValue);
            } else {
                return Arrays.toString(byteValue);
            }
        } else if (!type.getComponentType().isPrimitive()) {
            return Arrays.toString((Object[]) value);
        }
        assert false : "unhandled field type: " + type;
        return "";
    }

    /**
     * Tests if all values in this string are printable ASCII characters or value \0 (b in
     * [0x20,0x7F]) or b == 0.
     *
     * @param array
     * @return true if there are only printable ASCII characters and \0, false otherwise
     */
    private static boolean isPrintableAsciiString(byte[] array) {
        for (byte b : array) {
            char c = (char) b;
            if (c != 0 && (c < 0x20 || c > 0x7F)) {
                return false;
            }
        }
        return true;
    }

    private static String toString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        sb.append('"');
        for (byte b : bytes) {
            if (b == 0) {
                sb.append("\\0");
            } else if (b == '"') {
                sb.append("\\\"");
            } else if (b == '\n') {
                sb.append("\\n");
            } else {
                sb.append((char) b);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}

/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.common;

import static org.graalvm.compiler.core.common.UnsafeAccess.UNSAFE;

import java.util.ArrayList;
import java.util.Collections;

import org.graalvm.compiler.debug.GraalError;

import sun.misc.Unsafe;

/**
 * Describes fields in a class, primarily for access via {@link Unsafe}.
 */
public class Fields {

    /**
     * Offsets used with {@link Unsafe} to access the fields.
     */
    protected final long[] offsets;

    /**
     * The names of the fields.
     */
    private final String[] names;

    /**
     * The types of the fields.
     */
    private final Class<?>[] types;

    private final Class<?>[] declaringClasses;

    public static Fields forClass(Class<?> clazz, Class<?> endClazz, boolean includeTransient, FieldsScanner.CalcOffset calcOffset) {
        FieldsScanner scanner = new FieldsScanner(calcOffset == null ? new FieldsScanner.DefaultCalcOffset() : calcOffset);
        scanner.scan(clazz, endClazz, includeTransient);
        return new Fields(scanner.data);
    }

    public Fields(ArrayList<? extends FieldsScanner.FieldInfo> fields) {
        Collections.sort(fields);
        this.offsets = new long[fields.size()];
        this.names = new String[offsets.length];
        this.types = new Class<?>[offsets.length];
        this.declaringClasses = new Class<?>[offsets.length];
        int index = 0;
        for (FieldsScanner.FieldInfo f : fields) {
            offsets[index] = f.offset;
            names[index] = f.name;
            types[index] = f.type;
            declaringClasses[index] = f.declaringClass;
            index++;
        }
    }

    /**
     * Gets the number of fields represented by this object.
     */
    public int getCount() {
        return offsets.length;
    }

    public static void translateInto(Fields fields, ArrayList<FieldsScanner.FieldInfo> infos) {
        for (int index = 0; index < fields.getCount(); index++) {
            infos.add(new FieldsScanner.FieldInfo(fields.offsets[index], fields.names[index], fields.types[index], fields.declaringClasses[index]));
        }
    }

    /**
     * Function enabling an object field value to be replaced with another value when being copied
     * within {@link Fields#copy(Object, Object, ObjectTransformer)}.
     */
    @FunctionalInterface
    public interface ObjectTransformer {
        Object apply(int index, Object from);
    }

    /**
     * Copies fields from {@code from} to {@code to}, both of which must be of the same type.
     *
     * @param from the object from which the fields should be copied
     * @param to the object to which the fields should be copied
     */
    public void copy(Object from, Object to) {
        copy(from, to, null);
    }

    /**
     * Copies fields from {@code from} to {@code to}, both of which must be of the same type.
     *
     * @param from the object from which the fields should be copied
     * @param to the object to which the fields should be copied
     * @param trans function to applied to object field values as they are copied. If {@code null},
     *            the value is copied unchanged.
     */
    public void copy(Object from, Object to, ObjectTransformer trans) {
        assert from.getClass() == to.getClass();
        for (int index = 0; index < offsets.length; index++) {
            long offset = offsets[index];
            Class<?> type = types[index];
            if (type.isPrimitive()) {
                if (type == Integer.TYPE) {
                    UNSAFE.putInt(to, offset, UNSAFE.getInt(from, offset));
                } else if (type == Long.TYPE) {
                    UNSAFE.putLong(to, offset, UNSAFE.getLong(from, offset));
                } else if (type == Boolean.TYPE) {
                    UNSAFE.putBoolean(to, offset, UNSAFE.getBoolean(from, offset));
                } else if (type == Float.TYPE) {
                    UNSAFE.putFloat(to, offset, UNSAFE.getFloat(from, offset));
                } else if (type == Double.TYPE) {
                    UNSAFE.putDouble(to, offset, UNSAFE.getDouble(from, offset));
                } else if (type == Short.TYPE) {
                    UNSAFE.putShort(to, offset, UNSAFE.getShort(from, offset));
                } else if (type == Character.TYPE) {
                    UNSAFE.putChar(to, offset, UNSAFE.getChar(from, offset));
                } else if (type == Byte.TYPE) {
                    UNSAFE.putByte(to, offset, UNSAFE.getByte(from, offset));
                } else {
                    assert false : "unhandled property type: " + type;
                }
            } else {
                Object obj = UNSAFE.getObject(from, offset);
                UNSAFE.putObject(to, offset, trans == null ? obj : trans.apply(index, obj));
            }
        }
    }

    /**
     * Gets the value of a field for a given object.
     *
     * @param object the object whose field is to be read
     * @param index the index of the field (between 0 and {@link #getCount()})
     * @return the value of the specified field which will be boxed if the field type is primitive
     */
    public Object get(Object object, int index) {
        long offset = offsets[index];
        Class<?> type = types[index];
        Object value = null;
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                value = UNSAFE.getInt(object, offset);
            } else if (type == Long.TYPE) {
                value = UNSAFE.getLong(object, offset);
            } else if (type == Boolean.TYPE) {
                value = UNSAFE.getBoolean(object, offset);
            } else if (type == Float.TYPE) {
                value = UNSAFE.getFloat(object, offset);
            } else if (type == Double.TYPE) {
                value = UNSAFE.getDouble(object, offset);
            } else if (type == Short.TYPE) {
                value = UNSAFE.getShort(object, offset);
            } else if (type == Character.TYPE) {
                value = UNSAFE.getChar(object, offset);
            } else if (type == Byte.TYPE) {
                value = UNSAFE.getByte(object, offset);
            } else {
                assert false : "unhandled property type: " + type;
            }
        } else {
            value = UNSAFE.getObject(object, offset);
        }
        return value;
    }

    /**
     * Gets the value of a field for a given object.
     *
     * @param object the object whose field is to be read
     * @param index the index of the field (between 0 and {@link #getCount()})
     * @return the value of the specified field which will be boxed if the field type is primitive
     */
    public long getRawPrimitive(Object object, int index) {
        long offset = offsets[index];
        Class<?> type = types[index];

        if (type == Integer.TYPE) {
            return UNSAFE.getInt(object, offset);
        } else if (type == Long.TYPE) {
            return UNSAFE.getLong(object, offset);
        } else if (type == Boolean.TYPE) {
            return UNSAFE.getBoolean(object, offset) ? 1 : 0;
        } else if (type == Float.TYPE) {
            return Float.floatToRawIntBits(UNSAFE.getFloat(object, offset));
        } else if (type == Double.TYPE) {
            return Double.doubleToRawLongBits(UNSAFE.getDouble(object, offset));
        } else if (type == Short.TYPE) {
            return UNSAFE.getShort(object, offset);
        } else if (type == Character.TYPE) {
            return UNSAFE.getChar(object, offset);
        } else if (type == Byte.TYPE) {
            return UNSAFE.getByte(object, offset);
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * Determines if a field in the domain of this object is the same as the field denoted by the
     * same index in another {@link Fields} object.
     */
    public boolean isSame(Fields other, int index) {
        return other.offsets[index] == offsets[index];
    }

    public long[] getOffsets() {
        return offsets;
    }

    /**
     * Gets the name of a field.
     *
     * @param index index of a field
     */
    public String getName(int index) {
        return names[index];
    }

    /**
     * Gets the type of a field.
     *
     * @param index index of a field
     */
    public Class<?> getType(int index) {
        return types[index];
    }

    public Class<?> getDeclaringClass(int index) {
        return declaringClasses[index];
    }

    /**
     * Checks that a given field is assignable from a given value.
     *
     * @param index the index of the field to check
     * @param value a value that will be assigned to the field
     */
    private boolean checkAssignableFrom(Object object, int index, Object value) {
        assert value == null || getType(index).isAssignableFrom(value.getClass()) : String.format("Field %s.%s of type %s is not assignable from %s", object.getClass().getSimpleName(),
                        getName(index), getType(index).getSimpleName(), value.getClass().getSimpleName());
        return true;
    }

    public void set(Object object, int index, Object value) {
        long offset = offsets[index];
        Class<?> type = types[index];
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                UNSAFE.putInt(object, offset, (Integer) value);
            } else if (type == Long.TYPE) {
                UNSAFE.putLong(object, offset, (Long) value);
            } else if (type == Boolean.TYPE) {
                UNSAFE.putBoolean(object, offset, (Boolean) value);
            } else if (type == Float.TYPE) {
                UNSAFE.putFloat(object, offset, (Float) value);
            } else if (type == Double.TYPE) {
                UNSAFE.putDouble(object, offset, (Double) value);
            } else if (type == Short.TYPE) {
                UNSAFE.putShort(object, offset, (Short) value);
            } else if (type == Character.TYPE) {
                UNSAFE.putChar(object, offset, (Character) value);
            } else if (type == Byte.TYPE) {
                UNSAFE.putByte(object, offset, (Byte) value);
            } else {
                assert false : "unhandled property type: " + type;
            }
        } else {
            assert checkAssignableFrom(object, index, value);
            UNSAFE.putObject(object, offset, value);
        }
    }

    public void setRawPrimitive(Object object, int index, long value) {
        long offset = offsets[index];
        Class<?> type = types[index];
        if (type == Integer.TYPE) {
            UNSAFE.putInt(object, offset, (int) value);
        } else if (type == Long.TYPE) {
            UNSAFE.putLong(object, offset, value);
        } else if (type == Boolean.TYPE) {
            UNSAFE.putBoolean(object, offset, value != 0);
        } else if (type == Float.TYPE) {
            UNSAFE.putFloat(object, offset, Float.intBitsToFloat((int) value));
        } else if (type == Double.TYPE) {
            UNSAFE.putDouble(object, offset, Double.longBitsToDouble(value));
        } else if (type == Short.TYPE) {
            UNSAFE.putShort(object, offset, (short) value);
        } else if (type == Character.TYPE) {
            UNSAFE.putChar(object, offset, (char) value);
        } else if (type == Byte.TYPE) {
            UNSAFE.putByte(object, offset, (byte) value);
        } else {
            throw GraalError.shouldNotReachHere();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('[');
        appendFields(sb);
        return sb.append(']').toString();
    }

    public void appendFields(StringBuilder sb) {
        for (int i = 0; i < offsets.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(getDeclaringClass(i).getSimpleName()).append('.').append(getName(i)).append('@').append(offsets[i]);
        }
    }

    public boolean getBoolean(Object n, int i) {
        assert types[i] == boolean.class;
        return UNSAFE.getBoolean(n, offsets[i]);
    }

    public byte getByte(Object n, int i) {
        assert types[i] == byte.class;
        return UNSAFE.getByte(n, offsets[i]);
    }

    public short getShort(Object n, int i) {
        assert types[i] == short.class;
        return UNSAFE.getShort(n, offsets[i]);
    }

    public char getChar(Object n, int i) {
        assert types[i] == char.class;
        return UNSAFE.getChar(n, offsets[i]);
    }

    public int getInt(Object n, int i) {
        assert types[i] == int.class;
        return UNSAFE.getInt(n, offsets[i]);
    }

    public long getLong(Object n, int i) {
        assert types[i] == long.class;
        return UNSAFE.getLong(n, offsets[i]);
    }

    public float getFloat(Object n, int i) {
        assert types[i] == float.class;
        return UNSAFE.getFloat(n, offsets[i]);
    }

    public double getDouble(Object n, int i) {
        assert types[i] == double.class;
        return UNSAFE.getDouble(n, offsets[i]);
    }

    public Object getObject(Object object, int i) {
        assert !types[i].isPrimitive();
        return UNSAFE.getObject(object, offsets[i]);
    }

    public void putObject(Object object, int i, Object value) {
        assert checkAssignableFrom(object, i, value);
        UNSAFE.putObject(object, offsets[i], value);
    }
}

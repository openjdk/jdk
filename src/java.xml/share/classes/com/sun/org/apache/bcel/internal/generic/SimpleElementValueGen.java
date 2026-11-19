/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.bcel.internal.generic;

import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.classfile.ConstantDouble;
import com.sun.org.apache.bcel.internal.classfile.ConstantFloat;
import com.sun.org.apache.bcel.internal.classfile.ConstantInteger;
import com.sun.org.apache.bcel.internal.classfile.ConstantLong;
import com.sun.org.apache.bcel.internal.classfile.ConstantUtf8;
import com.sun.org.apache.bcel.internal.classfile.ElementValue;
import com.sun.org.apache.bcel.internal.classfile.SimpleElementValue;

/**
 * Generates simple element values in annotations.
 *
 * @since 6.0
 */
public class SimpleElementValueGen extends ElementValueGen {
    // For primitive types and string type, this points to the value entry in
    // the cpGen
    // For 'class' this points to the class entry in the cpGen
    private final int idx;

    /**
     * Constructs a SimpleElementValueGen for a boolean value.
     *
     * @param type The element value type.
     * @param cpGen The constant pool generator.
     * @param value The boolean value.
     */
    public SimpleElementValueGen(final int type, final ConstantPoolGen cpGen, final boolean value) {
        super(type, cpGen);
        if (value) {
            idx = getConstantPool().addInteger(1);
        } else {
            idx = getConstantPool().addInteger(0);
        }
    }

    /**
     * Constructs a SimpleElementValueGen for a byte value.
     *
     * @param type The element value type.
     * @param cpGen The constant pool generator.
     * @param value The byte value.
     */
    public SimpleElementValueGen(final int type, final ConstantPoolGen cpGen, final byte value) {
        super(type, cpGen);
        idx = getConstantPool().addInteger(value);
    }

    /**
     * Constructs a SimpleElementValueGen for a char value.
     *
     * @param type The element value type.
     * @param cpGen The constant pool generator.
     * @param value The char value.
     */
    public SimpleElementValueGen(final int type, final ConstantPoolGen cpGen, final char value) {
        super(type, cpGen);
        idx = getConstantPool().addInteger(value);
    }

    /**
     * Constructs a SimpleElementValueGen for a double value.
     *
     * @param type The element value type.
     * @param cpGen The constant pool generator.
     * @param value The double value.
     */
    public SimpleElementValueGen(final int type, final ConstantPoolGen cpGen, final double value) {
        super(type, cpGen);
        idx = getConstantPool().addDouble(value);
    }

    /**
     * Constructs a SimpleElementValueGen for a float value.
     *
     * @param type The element value type.
     * @param cpGen The constant pool generator.
     * @param value The float value.
     */
    public SimpleElementValueGen(final int type, final ConstantPoolGen cpGen, final float value) {
        super(type, cpGen);
        idx = getConstantPool().addFloat(value);
    }

    /**
     * Constructs a SimpleElementValueGen for an int value.
     *
     * @param type The element value type.
     * @param cpGen The constant pool generator.
     * @param value The int value.
     */
    public SimpleElementValueGen(final int type, final ConstantPoolGen cpGen, final int value) {
        super(type, cpGen);
        idx = getConstantPool().addInteger(value);
    }

    /**
     * Constructs a SimpleElementValueGen for a long value.
     *
     * @param type The element value type.
     * @param cpGen The constant pool generator.
     * @param value The long value.
     */
    public SimpleElementValueGen(final int type, final ConstantPoolGen cpGen, final long value) {
        super(type, cpGen);
        idx = getConstantPool().addLong(value);
    }

    /**
     * Constructs a SimpleElementValueGen for a short value.
     *
     * @param type The element value type.
     * @param cpGen The constant pool generator.
     * @param value The short value.
     */
    public SimpleElementValueGen(final int type, final ConstantPoolGen cpGen, final short value) {
        super(type, cpGen);
        idx = getConstantPool().addInteger(value);
    }

    /**
     * Constructs a SimpleElementValueGen for a String value.
     *
     * @param type The element value type.
     * @param cpGen The constant pool generator.
     * @param value The string value.
     */
    public SimpleElementValueGen(final int type, final ConstantPoolGen cpGen, final String value) {
        super(type, cpGen);
        idx = getConstantPool().addUtf8(value);
    }

    // ctors for each supported type... type could be inferred but for now lets
    // force it to be passed

    /**
     * Protected ctor used for deserialization, doesn't *put* an entry in the constant pool, assumes the one at the supplied
     * index is correct.
     *
     * @param type The element value type.
     * @param idx The constant pool index.
     * @param cpGen The constant pool generator.
     */
    protected SimpleElementValueGen(final int type, final int idx, final ConstantPoolGen cpGen) {
        super(type, cpGen);
        this.idx = idx;
    }

    /**
     * The boolean controls whether we copy info from the 'old' constant pool to the 'new'. You need to use this ctor if the
     * annotation is being copied from one file to another.
     *
     * @param value The simple element value to copy.
     * @param cpool The constant pool generator.
     * @param copyPoolEntries whether to copy pool entries.
     */
    public SimpleElementValueGen(final SimpleElementValue value, final ConstantPoolGen cpool, final boolean copyPoolEntries) {
        super(value.getElementValueType(), cpool);
        if (!copyPoolEntries) {
            // J5ASSERT: Could assert value.stringifyValue() is the same as
            // cpool.getConstant(SimpleElementValuevalue.getIndex())
            idx = value.getIndex();
        } else {
            switch (value.getElementValueType()) {
            case STRING:
                idx = cpool.addUtf8(value.getValueString());
                break;
            case PRIMITIVE_INT:
                idx = cpool.addInteger(value.getValueInt());
                break;
            case PRIMITIVE_BYTE:
                idx = cpool.addInteger(value.getValueByte());
                break;
            case PRIMITIVE_CHAR:
                idx = cpool.addInteger(value.getValueChar());
                break;
            case PRIMITIVE_LONG:
                idx = cpool.addLong(value.getValueLong());
                break;
            case PRIMITIVE_FLOAT:
                idx = cpool.addFloat(value.getValueFloat());
                break;
            case PRIMITIVE_DOUBLE:
                idx = cpool.addDouble(value.getValueDouble());
                break;
            case PRIMITIVE_BOOLEAN:
                if (value.getValueBoolean()) {
                    idx = cpool.addInteger(1);
                } else {
                    idx = cpool.addInteger(0);
                }
                break;
            case PRIMITIVE_SHORT:
                idx = cpool.addInteger(value.getValueShort());
                break;
            default:
                throw new IllegalArgumentException("SimpleElementValueGen class does not know how to copy this type " + super.getElementValueType());
            }
        }
    }

    @Override
    public void dump(final DataOutputStream dos) throws IOException {
        dos.writeByte(super.getElementValueType()); // u1 kind of value
        switch (super.getElementValueType()) {
        case PRIMITIVE_INT:
        case PRIMITIVE_BYTE:
        case PRIMITIVE_CHAR:
        case PRIMITIVE_FLOAT:
        case PRIMITIVE_LONG:
        case PRIMITIVE_BOOLEAN:
        case PRIMITIVE_SHORT:
        case PRIMITIVE_DOUBLE:
        case STRING:
            dos.writeShort(idx);
            break;
        default:
            throw new IllegalStateException("SimpleElementValueGen doesn't know how to write out type " + super.getElementValueType());
        }
    }

    /**
     * Gets the immutable variant.
     *
     * @return The immutable variant.
     */
    @Override
    public ElementValue getElementValue() {
        return new SimpleElementValue(super.getElementValueType(), idx, getConstantPool().getConstantPool());
    }

    /**
     * Gets the constant pool index.
     *
     * @return The index.
     */
    public int getIndex() {
        return idx;
    }

    /**
     * Gets the int value.
     *
     * @return The int value.
     */
    public int getValueInt() {
        if (super.getElementValueType() != PRIMITIVE_INT) {
            throw new IllegalStateException("Don't call getValueString() on a non STRING ElementValue");
        }
        final ConstantInteger c = (ConstantInteger) getConstantPool().getConstant(idx);
        return c.getBytes();
    }

    /**
     * Gets the string value.
     *
     * @return The string value.
     */
    public String getValueString() {
        if (super.getElementValueType() != STRING) {
            throw new IllegalStateException("Don't call getValueString() on a non STRING ElementValue");
        }
        final ConstantUtf8 c = (ConstantUtf8) getConstantPool().getConstant(idx);
        return c.getBytes();
    }

    // Whatever kind of value it is, return it as a string
    @Override
    public String stringifyValue() {
        switch (super.getElementValueType()) {
        case PRIMITIVE_INT:
            final ConstantInteger c = (ConstantInteger) getConstantPool().getConstant(idx);
            return Integer.toString(c.getBytes());
        case PRIMITIVE_LONG:
            final ConstantLong j = (ConstantLong) getConstantPool().getConstant(idx);
            return Long.toString(j.getBytes());
        case PRIMITIVE_DOUBLE:
            final ConstantDouble d = (ConstantDouble) getConstantPool().getConstant(idx);
            return Double.toString(d.getBytes());
        case PRIMITIVE_FLOAT:
            final ConstantFloat f = (ConstantFloat) getConstantPool().getConstant(idx);
            return Float.toString(f.getBytes());
        case PRIMITIVE_SHORT:
            final ConstantInteger s = (ConstantInteger) getConstantPool().getConstant(idx);
            return Integer.toString(s.getBytes());
        case PRIMITIVE_BYTE:
            final ConstantInteger b = (ConstantInteger) getConstantPool().getConstant(idx);
            return Integer.toString(b.getBytes());
        case PRIMITIVE_CHAR:
            final ConstantInteger ch = (ConstantInteger) getConstantPool().getConstant(idx);
            return Integer.toString(ch.getBytes());
        case PRIMITIVE_BOOLEAN:
            final ConstantInteger bo = (ConstantInteger) getConstantPool().getConstant(idx);
            if (bo.getBytes() == 0) {
                return "false";
            }
            return "true";
        case STRING:
            final ConstantUtf8 cu8 = (ConstantUtf8) getConstantPool().getConstant(idx);
            return cu8.getBytes();
        default:
            throw new IllegalStateException("SimpleElementValueGen class does not know how to stringify type " + super.getElementValueType());
        }
    }
}

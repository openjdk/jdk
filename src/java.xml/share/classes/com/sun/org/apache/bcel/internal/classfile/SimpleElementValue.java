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

package com.sun.org.apache.bcel.internal.classfile;

import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.Const;

/**
 * Represents a simple element value in an annotation.
 *
 * @since 6.0
 */
public class SimpleElementValue extends ElementValue {
    private int index;

    /**
     * Constructs a SimpleElementValue.
     *
     * @param type The element value type.
     * @param index The index into the constant pool.
     * @param cpool The constant pool.
     */
    public SimpleElementValue(final int type, final int index, final ConstantPool cpool) {
        super(type, cpool);
        this.index = index;
    }

    @Override
    public void dump(final DataOutputStream dos) throws IOException {
        final int type = super.getType();
        dos.writeByte(type); // u1 kind of value
        switch (type) {
        case PRIMITIVE_INT:
        case PRIMITIVE_BYTE:
        case PRIMITIVE_CHAR:
        case PRIMITIVE_FLOAT:
        case PRIMITIVE_LONG:
        case PRIMITIVE_BOOLEAN:
        case PRIMITIVE_SHORT:
        case PRIMITIVE_DOUBLE:
        case STRING:
            dos.writeShort(getIndex());
            break;
        default:
            throw new ClassFormatException("SimpleElementValue doesn't know how to write out type " + type);
        }
    }

    /**
     * Gets the value entry index in the constant pool.
     *
     * @return Value entry index in the constant pool.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Gets the boolean value.
     *
     * @return The boolean value.
     */
    public boolean getValueBoolean() {
        if (super.getType() != PRIMITIVE_BOOLEAN) {
            throw new IllegalStateException("Don't call getValueBoolean() on a non BOOLEAN ElementValue");
        }
        final ConstantInteger bo = (ConstantInteger) super.getConstantPool().getConstant(getIndex());
        return bo.getBytes() != 0;
    }

    /**
     * Gets the byte value.
     *
     * @return The byte value.
     */
    public byte getValueByte() {
        if (super.getType() != PRIMITIVE_BYTE) {
            throw new IllegalStateException("Don't call getValueByte() on a non BYTE ElementValue");
        }
        return (byte) super.getConstantPool().getConstantInteger(getIndex()).getBytes();
    }

    /**
     * Gets the char value.
     *
     * @return The char value.
     */
    public char getValueChar() {
        if (super.getType() != PRIMITIVE_CHAR) {
            throw new IllegalStateException("Don't call getValueChar() on a non CHAR ElementValue");
        }
        return (char) super.getConstantPool().getConstantInteger(getIndex()).getBytes();
    }

    /**
     * Gets the double value.
     *
     * @return The double value.
     */
    public double getValueDouble() {
        if (super.getType() != PRIMITIVE_DOUBLE) {
            throw new IllegalStateException("Don't call getValueDouble() on a non DOUBLE ElementValue");
        }
        final ConstantDouble d = (ConstantDouble) super.getConstantPool().getConstant(getIndex());
        return d.getBytes();
    }

    /**
     * Gets the float value.
     *
     * @return The float value.
     */
    public float getValueFloat() {
        if (super.getType() != PRIMITIVE_FLOAT) {
            throw new IllegalStateException("Don't call getValueFloat() on a non FLOAT ElementValue");
        }
        final ConstantFloat f = (ConstantFloat) super.getConstantPool().getConstant(getIndex());
        return f.getBytes();
    }

    /**
     * Gets the int value.
     *
     * @return The int value.
     */
    public int getValueInt() {
        if (super.getType() != PRIMITIVE_INT) {
            throw new IllegalStateException("Don't call getValueInt() on a non INT ElementValue");
        }
        return super.getConstantPool().getConstantInteger(getIndex()).getBytes();
    }

    /**
     * Gets the long value.
     *
     * @return The long value.
     */
    public long getValueLong() {
        if (super.getType() != PRIMITIVE_LONG) {
            throw new IllegalStateException("Don't call getValueLong() on a non LONG ElementValue");
        }
        final ConstantLong j = (ConstantLong) super.getConstantPool().getConstant(getIndex());
        return j.getBytes();
    }

    /**
     * Gets the short value.
     *
     * @return The short value.
     */
    public short getValueShort() {
        if (super.getType() != PRIMITIVE_SHORT) {
            throw new IllegalStateException("Don't call getValueShort() on a non SHORT ElementValue");
        }
        final ConstantInteger s = (ConstantInteger) super.getConstantPool().getConstant(getIndex());
        return (short) s.getBytes();
    }

    /**
     * Gets the string value.
     *
     * @return The string value.
     */
    public String getValueString() {
        if (super.getType() != STRING) {
            throw new IllegalStateException("Don't call getValueString() on a non STRING ElementValue");
        }
        return super.getConstantPool().getConstantUtf8(getIndex()).getBytes();
    }

    /**
     * Sets the index into the constant pool.
     *
     * @param index The index.
     */
    public void setIndex(final int index) {
        this.index = index;
    }

    // Whatever kind of value it is, return it as a string
    @Override
    public String stringifyValue() {
        final ConstantPool cpool = super.getConstantPool();
        final int type = super.getType();
        switch (type) {
        case PRIMITIVE_INT:
            return Integer.toString(cpool.getConstantInteger(getIndex()).getBytes());
        case PRIMITIVE_LONG:
            final ConstantLong j = cpool.getConstant(getIndex(), Const.CONSTANT_Long, ConstantLong.class);
            return Long.toString(j.getBytes());
        case PRIMITIVE_DOUBLE:
            final ConstantDouble d = cpool.getConstant(getIndex(), Const.CONSTANT_Double, ConstantDouble.class);
            return Double.toString(d.getBytes());
        case PRIMITIVE_FLOAT:
            final ConstantFloat f = cpool.getConstant(getIndex(), Const.CONSTANT_Float, ConstantFloat.class);
            return Float.toString(f.getBytes());
        case PRIMITIVE_SHORT:
            final ConstantInteger s = cpool.getConstantInteger(getIndex());
            return Integer.toString(s.getBytes());
        case PRIMITIVE_BYTE:
            final ConstantInteger b = cpool.getConstantInteger(getIndex());
            return Integer.toString(b.getBytes());
        case PRIMITIVE_CHAR:
            final ConstantInteger ch = cpool.getConstantInteger(getIndex());
            return String.valueOf((char) ch.getBytes());
        case PRIMITIVE_BOOLEAN:
            final ConstantInteger bo = cpool.getConstantInteger(getIndex());
            if (bo.getBytes() == 0) {
                return "false";
            }
            return "true";
        case STRING:
            return cpool.getConstantUtf8(getIndex()).getBytes();
        default:
            throw new IllegalStateException("SimpleElementValue class does not know how to stringify type " + type);
        }
    }

    @Override
    public String toString() {
        return stringifyValue();
    }
}

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

import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.util.Args;

/**
 * This class is derived from <em>Attribute</em> and represents a constant value, i.e., a default value for initializing
 * a class field. This class is instantiated by the <em>Attribute.readAttribute()</em> method.
 *
 * <pre>
 * ConstantValue_attribute {
 *   u2 attribute_name_index;
 *   u4 attribute_length;
 *   u2 constantvalue_index;
 * }
 * </pre>
 * @see Attribute
 */
public final class ConstantValue extends Attribute {

    private int constantValueIndex;

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use clone() for a
     * physical copy.
     *
     * @param c Source to copy.
     */
    public ConstantValue(final ConstantValue c) {
        this(c.getNameIndex(), c.getLength(), c.getConstantValueIndex(), c.getConstantPool());
    }

    /**
     * Construct object from input stream.
     *
     * @param nameIndex Name index in constant pool
     * @param length Content length in bytes
     * @param input Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    ConstantValue(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, input.readUnsignedShort(), constantPool);
    }

    /**
     * @param nameIndex Name index in constant pool
     * @param length Content length in bytes
     * @param constantValueIndex Index in constant pool
     * @param constantPool Array of constants
     */
    public ConstantValue(final int nameIndex, final int length, final int constantValueIndex, final ConstantPool constantPool) {
        super(Const.ATTR_CONSTANT_VALUE, nameIndex, Args.require(length, 2, "ConstantValue attribute length"), constantPool);
        this.constantValueIndex = constantValueIndex;
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitConstantValue(this);
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final ConstantValue c = (ConstantValue) clone();
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dump constant value attribute to file stream on binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeShort(constantValueIndex);
    }

    /**
     * @return Index in constant pool of constant value.
     */
    public int getConstantValueIndex() {
        return constantValueIndex;
    }

    /**
     * @param constantValueIndex the index info the constant pool of this constant value
     */
    public void setConstantValueIndex(final int constantValueIndex) {
        this.constantValueIndex = constantValueIndex;
    }

    /**
     * @return String representation of constant value.
     */
    @Override
    public String toString() {
        Constant c = super.getConstantPool().getConstant(constantValueIndex);
        String buf;
        int i;
        // Print constant to string depending on its type
        switch (c.getTag()) {
        case Const.CONSTANT_Long:
            buf = String.valueOf(((ConstantLong) c).getBytes());
            break;
        case Const.CONSTANT_Float:
            buf = String.valueOf(((ConstantFloat) c).getBytes());
            break;
        case Const.CONSTANT_Double:
            buf = String.valueOf(((ConstantDouble) c).getBytes());
            break;
        case Const.CONSTANT_Integer:
            buf = String.valueOf(((ConstantInteger) c).getBytes());
            break;
        case Const.CONSTANT_String:
            i = ((ConstantString) c).getStringIndex();
            c = super.getConstantPool().getConstantUtf8(i);
            buf = "\"" + Utility.convertString(((ConstantUtf8) c).getBytes()) + "\"";
            break;
        default:
            throw new IllegalStateException("Type of ConstValue invalid: " + c);
        }
        return buf;
    }
}

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
 * This class is derived from <em>Attribute</em> and declares this class as 'synthetic', i.e., it needs special
 * handling. The JVM specification states "A class member that does not appear in the source code must be marked using a
 * Synthetic attribute." It may appear in the ClassFile attribute table, a field_info table or a method_info table. This
 * class is intended to be instantiated from the <em>Attribute.readAttribute()</em> method.
 *
 * @see Attribute
 */
public final class Synthetic extends Attribute {

    private byte[] bytes;

    /**
     * @param nameIndex Index in constant pool to CONSTANT_Utf8, which should represent the string "Synthetic".
     * @param length Content length in bytes - should be zero.
     * @param bytes Attribute contents
     * @param constantPool The constant pool this attribute is associated with.
     */
    public Synthetic(final int nameIndex, final int length, final byte[] bytes, final ConstantPool constantPool) {
        super(Const.ATTR_SYNTHETIC, nameIndex, Args.require0(length, "Synthetic attribute length"), constantPool);
        this.bytes = bytes;
    }

    /**
     * Constructs object from input stream.
     *
     * @param nameIndex Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param input Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    Synthetic(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, (byte[]) null, constantPool);
        if (length > 0) {
            bytes = new byte[length];
            input.readFully(bytes);
            println("Synthetic attribute with length > 0");
        }
    }

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use copy() for a
     * physical copy.
     *
     * @param c Source to copy.
     */
    public Synthetic(final Synthetic c) {
        this(c.getNameIndex(), c.getLength(), c.getBytes(), c.getConstantPool());
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitSynthetic(this);
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final Synthetic c = (Synthetic) clone();
        if (bytes != null) {
            c.bytes = bytes.clone();
        }
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dump source file attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        if (super.getLength() > 0) {
            file.write(bytes, 0, super.getLength());
        }
    }

    /**
     * @return data bytes.
     */
    public byte[] getBytes() {
        return bytes;
    }

    /**
     * @param bytes
     */
    public void setBytes(final byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * @return String representation.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("Synthetic");
        if (super.getLength() > 0) {
            buf.append(" ").append(Utility.toHexString(bytes));
        }
        return buf.toString();
    }
}

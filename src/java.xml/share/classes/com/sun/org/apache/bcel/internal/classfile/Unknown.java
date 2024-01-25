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
import java.util.Arrays;

import com.sun.org.apache.bcel.internal.Const;

/**
 * This class represents a reference to an unknown (i.e., application-specific) attribute of a class. It is instantiated
 * from the {@link Attribute#readAttribute(java.io.DataInput, ConstantPool)} method. Applications that need to read in
 * application-specific attributes should create an {@link UnknownAttributeReader} implementation and attach it via
 * {@link Attribute#addAttributeReader(String, UnknownAttributeReader)}.
 *
 * @see Attribute
 * @see UnknownAttributeReader
 */
public final class Unknown extends Attribute {

    private byte[] bytes;

    private final String name;

    /**
     * Constructs a new instance for a non-standard attribute.
     *
     * @param nameIndex Index in constant pool
     * @param length Content length in bytes
     * @param bytes Attribute contents
     * @param constantPool Array of constants
     */
    public Unknown(final int nameIndex, final int length, final byte[] bytes, final ConstantPool constantPool) {
        super(Const.ATTR_UNKNOWN, nameIndex, length, constantPool);
        this.bytes = bytes;
        this.name = constantPool.getConstantUtf8(nameIndex).getBytes();
    }

    /**
     * Constructs a new instance from an input stream.
     *
     * @param nameIndex Index in constant pool
     * @param length Content length in bytes
     * @param input Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    Unknown(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, (byte[]) null, constantPool);
        if (length > 0) {
            bytes = new byte[length];
            input.readFully(bytes);
        }
    }

    /**
     * Constructs a new instance from another instance. Note that both objects use the same references (shallow copy). Use clone() for a physical copy.
     *
     * @param unknown Source.
     */
    public Unknown(final Unknown unknown) {
        this(unknown.getNameIndex(), unknown.getLength(), unknown.getBytes(), unknown.getConstantPool());
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitUnknown(this);
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final Unknown c = (Unknown) clone();
        if (bytes != null) {
            c.bytes = bytes.clone();
        }
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dumps unknown bytes to file stream.
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
     * @return name of attribute.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * @param bytes the bytes to set
     */
    public void setBytes(final byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * @return String representation.
     */
    @Override
    public String toString() {
        if (super.getLength() == 0 || bytes == null) {
            return "(Unknown attribute " + name + ")";
        }
        String hex;
        final int limit = 10;
        if (super.getLength() > limit) {
            final byte[] tmp = Arrays.copyOf(bytes, limit);
            hex = Utility.toHexString(tmp) + "... (truncated)";
        } else {
            hex = Utility.toHexString(bytes);
        }
        return "(Unknown attribute " + name + ": " + hex + ")";
    }
}

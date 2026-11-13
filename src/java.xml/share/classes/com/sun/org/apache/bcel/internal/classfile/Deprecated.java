/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.IOException;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.util.Args;

/**
 * This class is derived from <em>Attribute</em> and denotes that this is a deprecated method. It is instantiated from the <em>Attribute.readAttribute()</em>
 * method.
 *
 * <pre>
 * Deprecated_attribute {
 *     u2 attribute_name_index;
 *     u4 attribute_length;
 * }
 * </pre>
 *
 * @see Attribute
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.15">JVM Specification: The Deprecated Attribute</a>
 * @LastModified: Nov 2026
 */
public final class Deprecated extends Attribute {

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use clone() for a physical copy.
     *
     * @param c Source to copy.
     */
    public Deprecated(final Deprecated c) {
        this(c.getNameIndex(), c.getLength(), c.getBytes(), c.getConstantPool());
    }

    /**
     * Constructs a Deprecated attribute.
     *
     * @param nameIndex    Index in constant pool to CONSTANT_Utf8.
     * @param length       JVM Specification: "The value of the attribute_length item must be zero.".
     * @param bytes        Attribute contents.
     * @param constantPool Array of constants.
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.15">JVM Specification: The Deprecated Attribute</a>
     */
    public Deprecated(final int nameIndex, final int length, final byte[] bytes, final ConstantPool constantPool) {
        super(Const.ATTR_DEPRECATED, nameIndex, Args.require0(length, "Deprecated attribute length"), constantPool);
    }

    /**
     * Constructs object from input stream.
     *
     * @param nameIndex    Index in constant pool to CONSTANT_Utf8.
     * @param length       JVM Specification: "The value of the attribute_length item must be zero.".
     * @param input        Input stream.
     * @param constantPool Array of constants.
     * @throws IOException Thrown if an I/O error occurs.
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.7.15">JVM Specification: The Deprecated Attribute</a>
     */
    Deprecated(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, (byte[]) null, constantPool);
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class. That is, the hierarchy of methods,
     * fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object.
     */
    @Override
    public void accept(final Visitor v) {
        v.visitDeprecated(this);
    }

    /**
     * @return deep copy of this attribute.
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final Deprecated c = (Deprecated) clone();
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Gets the data bytes.
     *
     * @return data bytes.
     */
    public byte[] getBytes() {
        return Const.EMPTY_BYTE_ARRAY;
    }

    /**
     * Sets the data bytes.
     *
     * @param bytes The raw bytes that represents this byte array.
     */
    public void setBytes(final byte[] bytes) {
        if (bytes != null) {
            Args.require0(bytes.length, "Deprecated attribute length");
        }
    }

    /**
     * @return attribute name.
     */
    @Override
    public String toString() {
        return Const.getAttributeName(Const.ATTR_DEPRECATED) + ": true";
    }
}

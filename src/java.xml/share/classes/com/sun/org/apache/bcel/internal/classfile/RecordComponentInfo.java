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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.sun.org.apache.bcel.internal.classfile;

import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;

import com.sun.org.apache.bcel.internal.Const;

/**
 * Record component info from a record. Instances from this class maps
 * every component from a given record.
 *
 * @see <a href="https://docs.oracle.com/javase/specs/jvms/se14/preview/specs/records-jvms.html#jvms-4.7.30">
 *      The Java Virtual Machine Specification, Java SE 14 Edition, Records (preview)</a>
 * @since 6.9.0
 */
public class RecordComponentInfo implements Node {

    private final int index;
    private final int descriptorIndex;
    private final Attribute[] attributes;
    private final ConstantPool constantPool;

    /**
     * Constructs a new instance from an input stream.
     *
     * @param input        Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    public RecordComponentInfo(final DataInput input, final ConstantPool constantPool) throws IOException {
        this.index = input.readUnsignedShort();
        this.descriptorIndex = input.readUnsignedShort();
        final int attributesCount = input.readUnsignedShort();
        this.attributes = new Attribute[attributesCount];
        for (int j = 0; j < attributesCount; j++) {
            attributes[j] = Attribute.readAttribute(input, constantPool);
        }
        this.constantPool = constantPool;
    }

    @Override
    public void accept(final Visitor v) {
        v.visitRecordComponent(this);
    }

    /**
     * Dumps contents into a file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    public void dump(final DataOutputStream file) throws IOException {
        file.writeShort(index);
        file.writeShort(descriptorIndex);
        file.writeShort(attributes.length);
        for (final Attribute attribute : attributes) {
            attribute.dump(file);
        }
    }

    /**
     * Gets all attributes.
     *
     * @return all attributes.
     */
    public Attribute[] getAttributes() {
        return attributes;
    }

    /**
     * Gets the constant pool.
     *
     * @return Constant pool.
     */
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    /**
     * Gets the description index.
     *
     * @return index in constant pool of this record component descriptor.
     */
    public int getDescriptorIndex() {
        return descriptorIndex;
    }

    /**
     * Gets the name index.
     *
     * @return index in constant pool of this record component name.
     */
    public int getIndex() {
        return index;
    }

    /**
     * Converts this instance to a String suitable for debugging.
     *
     * @return a String suitable for debugging.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("RecordComponentInfo(");
        buf.append(constantPool.getConstantString(index, Const.CONSTANT_Utf8));
        buf.append(",");
        buf.append(constantPool.getConstantString(descriptorIndex, Const.CONSTANT_Utf8));
        buf.append(",");
        buf.append(attributes.length);
        buf.append("):\n");
        for (final Attribute attribute : attributes) {
            buf.append("  ").append(attribute.toString()).append("\n");
        }
        return buf.substring(0, buf.length() - 1); // remove the last newline
    }

}

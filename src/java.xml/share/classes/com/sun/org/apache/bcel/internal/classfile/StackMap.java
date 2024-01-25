/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.org.apache.bcel.internal.util.Args;

/**
 * This class represents a stack map attribute used for preverification of Java classes for the
 * <a href="http://java.sun.com/j2me/"> Java 2 Micro Edition</a> (J2ME). This attribute is used by the
 * <a href="http://java.sun.com/products/cldc/">KVM</a> and contained within the Code attribute of a method. See CLDC
 * specification 5.3.1.2
 *
 * <pre>
 * StackMapTable_attribute {
 *   u2              attribute_name_index;
 *   u4              attribute_length;
 *   u2              number_of_entries;
 *   stack_map_frame entries[number_of_entries];
 * }
 * </pre>
 *
 * @see Code
 * @see StackMapEntry
 * @see StackMapType
 * @LastModified: Oct 2020
 */
public final class StackMap extends Attribute {

    private StackMapEntry[] table; // Table of stack map entries

    /**
     * Construct object from input stream.
     *
     * @param nameIndex Index of name
     * @param length Content length in bytes
     * @param dataInput Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    StackMap(final int nameIndex, final int length, final DataInput dataInput, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, (StackMapEntry[]) null, constantPool);
        final int mapLength = dataInput.readUnsignedShort();
        table = new StackMapEntry[mapLength];
        for (int i = 0; i < mapLength; i++) {
            table[i] = new StackMapEntry(dataInput, constantPool);
        }
    }

    /*
     * @param nameIndex Index of name
     *
     * @param length Content length in bytes
     *
     * @param map Table of stack map entries
     *
     * @param constantPool Array of constants
     */
    public StackMap(final int nameIndex, final int length, final StackMapEntry[] table, final ConstantPool constantPool) {
        super(Const.ATTR_STACK_MAP, nameIndex, length, constantPool);
        this.table = table != null ? table : StackMapEntry.EMPTY_ARRAY;
        Args.requireU2(this.table.length, "table.length");
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitStackMap(this);
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final StackMap c = (StackMap) clone();
        c.table = new StackMapEntry[table.length];
        Arrays.setAll(c.table, i -> table[i].copy());
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dump stack map table attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeShort(table.length);
        for (final StackMapEntry entry : table) {
            entry.dump(file);
        }
    }

    public int getMapLength() {
        return table.length;
    }

    /**
     * @return Array of stack map entries
     */
    public StackMapEntry[] getStackMap() {
        return table;
    }

    /**
     * @param table Array of stack map entries
     */
    public void setStackMap(final StackMapEntry[] table) {
        this.table = table != null ? table : StackMapEntry.EMPTY_ARRAY;
        int len = 2; // Length of 'number_of_entries' field prior to the array of stack maps
        for (final StackMapEntry element : this.table) {
            len += element.getMapEntrySize();
        }
        setLength(len);
    }

    /**
     * @return String representation.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder("StackMap(");
        int runningOffset = -1; // no +1 on first entry
        for (int i = 0; i < table.length; i++) {
            runningOffset = table[i].getByteCodeOffset() + runningOffset + 1;
            buf.append(String.format("%n@%03d %s", runningOffset, table[i]));
            if (i < table.length - 1) {
                buf.append(", ");
            }
        }
        buf.append(')');
        return buf.toString();
    }
}

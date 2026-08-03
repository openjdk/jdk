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
import com.sun.org.apache.bcel.internal.util.Args;

/**
 * Extends {@link Attribute} and records the classes and
 * interfaces that are authorized to claim membership in the nest hosted by the
 * current class or interface. There may be at most one Record attribute in a
 * ClassFile structure.
 *
 * @see Attribute
 * @since 6.9.0
 */
public final class Record extends Attribute {

    private static final RecordComponentInfo[] EMPTY_RCI_ARRAY = {};

    private static RecordComponentInfo[] readComponents(final DataInput input, final ConstantPool constantPool)
            throws IOException {
        final int classCount = input.readUnsignedShort();
        final RecordComponentInfo[] components = new RecordComponentInfo[classCount];
        for (int i = 0; i < classCount; i++) {
            components[i] = new RecordComponentInfo(input, constantPool);
        }
        return components;
    }

    private RecordComponentInfo[] components;

    /**
     * Constructs object from input stream.
     *
     * @param nameIndex    Index in constant pool
     * @param length       Content length in bytes
     * @param input        Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    Record(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool)
            throws IOException {
        this(nameIndex, length, readComponents(input, constantPool), constantPool);
    }

    /**
     * Constructs a new instance using components.
     *
     * @param nameIndex    Index in constant pool
     * @param length       Content length in bytes
     * @param classes      Array of Record Component Info elements
     * @param constantPool Array of constants
     */
    public Record(final int nameIndex, final int length, final RecordComponentInfo[] classes,
            final ConstantPool constantPool) {
        super(Const.ATTR_RECORD, nameIndex, length, constantPool);
        this.components = classes != null ? classes : EMPTY_RCI_ARRAY;
        Args.requireU2(this.components.length, "attributes.length");
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly
     * defined by the contents of a Java class. For example, the hierarchy of methods,
     * fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitRecord(this);
    }

    /**
     * Copies this instance and its components.
     *
     * @return a deep copy of this instance and its components.
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final Record c = (Record) clone();
        if (components.length > 0) {
            c.components = components.clone();
        }
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dumps this instance into a file stream in binary format.
     *
     * @param file output stream.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeShort(components.length);
        for (final RecordComponentInfo component : components) {
            component.dump(file);
        }
    }

    /**
     * Gets all the record components.
     *
     * @return array of Record Component Info elements.
     */
    public RecordComponentInfo[] getComponents() {
        return components;
    }

    /**
     * Converts this instance to a String suitable for debugging.
     *
     * @return String a String suitable for debugging.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("Record(");
        buf.append(components.length);
        buf.append("):\n");
        for (final RecordComponentInfo component : components) {
            buf.append("  ").append(component.toString()).append("\n");
        }
        return buf.substring(0, buf.length() - 1); // remove the last newline
    }

}

/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * This class represents the table of exceptions that are thrown by a method. This attribute may be used once per
 * method. The name of this class is <em>ExceptionTable</em> for historical reasons; The Java Virtual Machine
 * Specification, Second Edition defines this attribute using the name <em>Exceptions</em> (which is inconsistent with
 * the other classes).
 *
 * <pre>
 * Exceptions_attribute {
 *   u2 attribute_name_index;
 *   u4 attribute_length;
 *   u2 number_of_exceptions;
 *   u2 exception_index_table[number_of_exceptions];
 * }
 * </pre>
 * @see Code
 * @LastModified: Feb 2023
 */
public final class ExceptionTable extends Attribute {

    private int[] exceptionIndexTable; // constant pool

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use copy() for a
     * physical copy.
     *
     * @param c Source to copy.
     */
    public ExceptionTable(final ExceptionTable c) {
        this(c.getNameIndex(), c.getLength(), c.getExceptionIndexTable(), c.getConstantPool());
    }

    /**
     * Construct object from input stream.
     *
     * @param nameIndex Index in constant pool
     * @param length Content length in bytes
     * @param input Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    ExceptionTable(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, (int[]) null, constantPool);
        final int exceptionCount = input.readUnsignedShort();
        exceptionIndexTable = new int[exceptionCount];
        for (int i = 0; i < exceptionCount; i++) {
            exceptionIndexTable[i] = input.readUnsignedShort();
        }
    }

    /**
     * @param nameIndex Index in constant pool
     * @param length Content length in bytes
     * @param exceptionIndexTable Table of indices in constant pool
     * @param constantPool Array of constants
     */
    public ExceptionTable(final int nameIndex, final int length, final int[] exceptionIndexTable, final ConstantPool constantPool) {
        super(Const.ATTR_EXCEPTIONS, nameIndex, length, constantPool);
        this.exceptionIndexTable = exceptionIndexTable != null ? exceptionIndexTable : Const.EMPTY_INT_ARRAY;
        Args.requireU2(this.exceptionIndexTable.length, "exceptionIndexTable.length");
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitExceptionTable(this);
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final ExceptionTable c = (ExceptionTable) clone();
        if (exceptionIndexTable != null) {
            c.exceptionIndexTable = exceptionIndexTable.clone();
        }
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dump exceptions attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeShort(exceptionIndexTable.length);
        for (final int index : exceptionIndexTable) {
            file.writeShort(index);
        }
    }

    /**
     * @return Array of indices into constant pool of thrown exceptions.
     */
    public int[] getExceptionIndexTable() {
        return exceptionIndexTable;
    }

    /**
     * @return class names of thrown exceptions
     */
    public String[] getExceptionNames() {
        final String[] names = new String[exceptionIndexTable.length];
        Arrays.setAll(names, i -> Utility.pathToPackage(super.getConstantPool().getConstantString(exceptionIndexTable[i], Const.CONSTANT_Class)));
        return names;
    }

    /**
     * @return Length of exception table.
     */
    public int getNumberOfExceptions() {
        return exceptionIndexTable == null ? 0 : exceptionIndexTable.length;
    }

    /**
     * @param exceptionIndexTable the list of exception indexes Also redefines number_of_exceptions according to table
     *        length.
     */
    public void setExceptionIndexTable(final int[] exceptionIndexTable) {
        this.exceptionIndexTable = exceptionIndexTable != null ? exceptionIndexTable : Const.EMPTY_INT_ARRAY;
    }

    /**
     * @return String representation, i.e., a list of thrown exceptions.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        String str;
        buf.append("Exceptions: ");
        for (int i = 0; i < exceptionIndexTable.length; i++) {
            str = super.getConstantPool().getConstantString(exceptionIndexTable[i], Const.CONSTANT_Class);
            buf.append(Utility.compactClassName(str, false));
            if (i < exceptionIndexTable.length - 1) {
                buf.append(", ");
            }
        }
        return buf.toString();
    }
}

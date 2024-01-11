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
 * This class represents a chunk of Java byte code contained in a method. It is instantiated by the
 * <em>Attribute.readAttribute()</em> method. A <em>Code</em> attribute contains informations about operand stack, local
 * variables, byte code and the exceptions handled within this method.
 *
 * This attribute has attributes itself, namely <em>LineNumberTable</em> which is used for debugging purposes and
 * <em>LocalVariableTable</em> which contains information about the local variables.
 *
 * <pre>
 * Code_attribute {
 *   u2 attribute_name_index;
 *   u4 attribute_length;
 *   u2 max_stack;
 *   u2 max_locals;
 *   u4 code_length;
 *   u1 code[code_length];
 *   u2 exception_table_length;
 *   {
 *     u2 start_pc;
 *     u2 end_pc;
 *     u2 handler_pc;
 *     u2 catch_type;
 *   } exception_table[exception_table_length];
 *   u2 attributes_count;
 *   attribute_info attributes[attributes_count];
 * }
 * </pre>
 * @see Attribute
 * @see CodeException
 * @see LineNumberTable
 * @see LocalVariableTable
 * @LastModified: Feb 2023
 */
public final class Code extends Attribute {

    private int maxStack; // Maximum size of stack used by this method // TODO this could be made final (setter is not used)
    private int maxLocals; // Number of local variables // TODO this could be made final (setter is not used)
    private byte[] code; // Actual byte code
    private CodeException[] exceptionTable; // Table of handled exceptions
    private Attribute[] attributes; // or LocalVariable

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use copy() for a
     * physical copy.
     *
     * @param code The source Code.
     */
    public Code(final Code code) {
        this(code.getNameIndex(), code.getLength(), code.getMaxStack(), code.getMaxLocals(), code.getCode(), code.getExceptionTable(), code.getAttributes(),
                code.getConstantPool());
    }

    /**
     * @param nameIndex Index pointing to the name <em>Code</em>
     * @param length Content length in bytes
     * @param file Input stream
     * @param constantPool Array of constants
     */
    Code(final int nameIndex, final int length, final DataInput file, final ConstantPool constantPool) throws IOException {
        // Initialize with some default values which will be overwritten later
        this(nameIndex, length, file.readUnsignedShort(), file.readUnsignedShort(), (byte[]) null, (CodeException[]) null, (Attribute[]) null, constantPool);
        final int codeLength = Args.requireU4(file.readInt(), 1, "Code length attribute");
        code = new byte[codeLength]; // Read byte code
        file.readFully(code);
        /*
         * Read exception table that contains all regions where an exception handler is active, i.e., a try { ... } catch()
         * block.
         */
        final int exceptionTableLength = file.readUnsignedShort();
        exceptionTable = new CodeException[exceptionTableLength];
        for (int i = 0; i < exceptionTableLength; i++) {
            exceptionTable[i] = new CodeException(file);
        }
        /*
         * Read all attributes, currently 'LineNumberTable' and 'LocalVariableTable'
         */
        final int attributesCount = file.readUnsignedShort();
        attributes = new Attribute[attributesCount];
        for (int i = 0; i < attributesCount; i++) {
            attributes[i] = Attribute.readAttribute(file, constantPool);
        }
        /*
         * Adjust length, because of setAttributes in this(), s.b. length is incorrect, because it didn't take the internal
         * attributes into account yet! Very subtle bug, fixed in 3.1.1.
         */
        super.setLength(length);
    }

    /**
     * @param nameIndex Index pointing to the name <em>Code</em>
     * @param length Content length in bytes
     * @param maxStack Maximum size of stack
     * @param maxLocals Number of local variables
     * @param code Actual byte code
     * @param exceptionTable of handled exceptions
     * @param attributes Attributes of code: LineNumber or LocalVariable
     * @param constantPool Array of constants
     */
    public Code(final int nameIndex, final int length, final int maxStack, final int maxLocals, final byte[] code, final CodeException[] exceptionTable,
        final Attribute[] attributes, final ConstantPool constantPool) {
        super(Const.ATTR_CODE, nameIndex, length, constantPool);
        this.maxStack = Args.requireU2(maxStack, "maxStack");
        this.maxLocals = Args.requireU2(maxLocals, "maxLocals");
        this.code = code != null ? code : Const.EMPTY_BYTE_ARRAY;
        this.exceptionTable = exceptionTable != null ? exceptionTable : CodeException.EMPTY_CODE_EXCEPTION_ARRAY;
        Args.requireU2(this.exceptionTable.length, "exceptionTable.length");
        this.attributes = attributes != null ? attributes : EMPTY_ARRAY;
        super.setLength(calculateLength()); // Adjust length
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitCode(this);
    }

    /**
     * @return the full size of this code attribute, minus its first 6 bytes, including the size of all its contained
     *         attributes
     */
    private int calculateLength() {
        int len = 0;
        if (attributes != null) {
            for (final Attribute attribute : attributes) {
                len += attribute.getLength() + 6 /* attribute header size */;
            }
        }
        return len + getInternalLength();
    }

    /**
     * @return deep copy of this attribute
     *
     * @param constantPool the constant pool to duplicate
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final Code c = (Code) clone();
        if (code != null) {
            c.code = code.clone();
        }
        c.setConstantPool(constantPool);
        c.exceptionTable = new CodeException[exceptionTable.length];
        Arrays.setAll(c.exceptionTable, i -> exceptionTable[i].copy());
        c.attributes = new Attribute[attributes.length];
        Arrays.setAll(c.attributes, i -> attributes[i].copy(constantPool));
        return c;
    }

    /**
     * Dump code attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeShort(maxStack);
        file.writeShort(maxLocals);
        file.writeInt(code.length);
        file.write(code, 0, code.length);
        file.writeShort(exceptionTable.length);
        for (final CodeException exception : exceptionTable) {
            exception.dump(file);
        }
        file.writeShort(attributes.length);
        for (final Attribute attribute : attributes) {
            attribute.dump(file);
        }
    }

    /**
     * @return Collection of code attributes.
     * @see Attribute
     */
    public Attribute[] getAttributes() {
        return attributes;
    }

    /**
     * @return Actual byte code of the method.
     */
    public byte[] getCode() {
        return code;
    }

    /**
     * @return Table of handled exceptions.
     * @see CodeException
     */
    public CodeException[] getExceptionTable() {
        return exceptionTable;
    }

    /**
     * @return the internal length of this code attribute (minus the first 6 bytes) and excluding all its attributes
     */
    private int getInternalLength() {
        return 2 /* maxStack */ + 2 /* maxLocals */ + 4 /* code length */
            + code.length /* byte-code */
            + 2 /* exception-table length */
            + 8 * (exceptionTable == null ? 0 : exceptionTable.length) /* exception table */
            + 2 /* attributes count */;
    }

    /**
     * @return LineNumberTable of Code, if it has one
     */
    public LineNumberTable getLineNumberTable() {
        for (final Attribute attribute : attributes) {
            if (attribute instanceof LineNumberTable) {
                return (LineNumberTable) attribute;
            }
        }
        return null;
    }

    /**
     * @return LocalVariableTable of Code, if it has one
     */
    public LocalVariableTable getLocalVariableTable() {
        for (final Attribute attribute : attributes) {
            if (attribute instanceof LocalVariableTable) {
                return (LocalVariableTable) attribute;
            }
        }
        return null;
    }

    /**
     * @return Number of local variables.
     */
    public int getMaxLocals() {
        return maxLocals;
    }

    /**
     * @return Maximum size of stack used by this method.
     */
    public int getMaxStack() {
        return maxStack;
    }

    /**
     * @param attributes the attributes to set for this Code
     */
    public void setAttributes(final Attribute[] attributes) {
        this.attributes = attributes != null ? attributes : EMPTY_ARRAY;
        super.setLength(calculateLength()); // Adjust length
    }

    /**
     * @param code byte code
     */
    public void setCode(final byte[] code) {
        this.code = code != null ? code : Const.EMPTY_BYTE_ARRAY;
        super.setLength(calculateLength()); // Adjust length
    }

    /**
     * @param exceptionTable exception table
     */
    public void setExceptionTable(final CodeException[] exceptionTable) {
        this.exceptionTable = exceptionTable != null ? exceptionTable : CodeException.EMPTY_CODE_EXCEPTION_ARRAY;
        super.setLength(calculateLength()); // Adjust length
    }

    /**
     * @param maxLocals maximum number of local variables
     */
    public void setMaxLocals(final int maxLocals) {
        this.maxLocals = maxLocals;
    }

    /**
     * @param maxStack maximum stack size
     */
    public void setMaxStack(final int maxStack) {
        this.maxStack = maxStack;
    }

    /**
     * @return String representation of code chunk.
     */
    @Override
    public String toString() {
        return toString(true);
    }

    /**
     * Converts this object to a String.
     *
     * @param verbose Provides verbose output when true.
     * @return String representation of code chunk.
     */
    public String toString(final boolean verbose) {
        final StringBuilder buf = new StringBuilder(100); // CHECKSTYLE IGNORE MagicNumber
        buf.append("Code(maxStack = ").append(maxStack).append(", maxLocals = ").append(maxLocals).append(", code_length = ").append(code.length).append(")\n")
            .append(Utility.codeToString(code, super.getConstantPool(), 0, -1, verbose));
        if (exceptionTable.length > 0) {
            buf.append("\nException handler(s) = \n").append("From\tTo\tHandler\tType\n");
            for (final CodeException exception : exceptionTable) {
                buf.append(exception.toString(super.getConstantPool(), verbose)).append("\n");
            }
        }
        if (attributes.length > 0) {
            buf.append("\nAttribute(s) = ");
            for (final Attribute attribute : attributes) {
                buf.append("\n").append(attribute.getName()).append(":");
                buf.append("\n").append(attribute);
            }
        }
        return buf.toString();
    }
}

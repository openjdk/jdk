/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.xml.internal.Utils;

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
 *
 * @see Attribute
 * @see CodeException
 * @see LineNumberTable
 * @see LocalVariableTable
 * @LastModified: Nov 2026
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
     * Constructs a Code attribute object from a DataInput.
     *
     * @param nameIndex    Index pointing to the name <em>Code</em>.
     * @param length       Content length in bytes.
     * @param dataInput    Data input.
     * @param constantPool Array of constants.
     * @throws ClassFormatException Thrown if the code array read from {@code file} is greater than {@link Const#MAX_CODE_SIZE}.
     */
    Code(final int nameIndex, final int length, final DataInput dataInput, final ConstantPool constantPool) throws IOException {
        // Initialize with some default values which will be overwritten later
        this(nameIndex, length, dataInput.readUnsignedShort(), dataInput.readUnsignedShort(), (byte[]) null, (CodeException[]) null, (Attribute[]) null,
                constantPool);
        final int codeLength = Args.requireU4(dataInput.readInt(), 0, Const.MAX_CODE_SIZE, "Code length attribute");
        code = new byte[codeLength]; // Read byte code
        dataInput.readFully(code);
        /*
         * Read exception table that contains all regions where an exception handler is active, that is, a try { ... } catch () block.
         */
        final int exceptionTableLength = dataInput.readUnsignedShort();
        exceptionTable = new CodeException[exceptionTableLength];
        for (int i = 0; i < exceptionTableLength; i++) {
            exceptionTable[i] = new CodeException(dataInput);
        }
        /*
         * Read all attributes, currently 'LineNumberTable' and 'LocalVariableTable'
         */
        final int attributesCount = dataInput.readUnsignedShort();
        attributes = new Attribute[attributesCount];
        for (int i = 0; i < attributesCount; i++) {
            attributes[i] = readAttribute(dataInput, constantPool);
        }
        /*
         * Adjust length, because of setAttributes in this(), s.b. length is incorrect, because it didn't take the internal attributes into account yet! Very
         * subtle bug, fixed in 3.1.1.
         */
        super.setLength(length);
    }

    /**
     * Constructs a Code attribute.
     *
     * @param nameIndex Index pointing to the name <em>Code</em>.
     * @param length Content length in bytes.
     * @param maxStack Maximum size of stack.
     * @param maxLocals Number of local variables.
     * @param code Actual byte code.
     * @param exceptionTable of handled exceptions.
     * @param attributes Attributes of code: LineNumber or LocalVariable.
     * @param constantPool Array of constants.
     * @throws ClassFormatException Thrown if the code array is greater than {@link Const#MAX_CODE_SIZE}.
     */
    public Code(final int nameIndex, final int length, final int maxStack, final int maxLocals, final byte[] code, final CodeException[] exceptionTable,
        final Attribute[] attributes, final ConstantPool constantPool) {
        super(Const.ATTR_CODE, nameIndex, length, constantPool);
        this.maxStack = Args.requireU2(maxStack, "maxStack");
        this.maxLocals = Args.requireU2(maxLocals, "maxLocals");
        this.code = Utils.createEmptyArrayIfNull(code);
        Args.requireU4(this.code.length, 0, Const.MAX_CODE_SIZE, "Code length attribute");
        this.exceptionTable = Utils.createEmptyArrayIfNull(exceptionTable, CodeException[].class);
        Args.requireU2(this.exceptionTable.length, "exceptionTable.length");
        this.attributes = attributes != null ? attributes : EMPTY_ARRAY;
        super.setLength(calculateLength()); // Adjust length
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * That is, the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object.
     */
    @Override
    public void accept(final Visitor v) {
        v.visitCode(this);
    }

    /**
     * @return The full size of this code attribute, minus its first 6 bytes, including the size of all its contained attributes.
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
     * Creates a deep copy of this attribute.
     *
     * @param constantPool The constant pool to duplicate.
     * @return deep copy of this attribute.
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
     * Dumps code attribute to file stream in binary format.
     *
     * @param file Output file stream.
     * @throws IOException Thrown if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeShort(maxStack);
        file.writeShort(maxLocals);
        file.writeInt(code.length);
        file.write(code, 0, code.length);
        file.writeShort(Args.requireU2(exceptionTable.length, "exceptionTable.length"));
        for (final CodeException exception : exceptionTable) {
            exception.dump(file);
        }
        file.writeShort(Args.requireU2(attributes.length, "attributes.length"));
        for (final Attribute attribute : attributes) {
            attribute.dump(file);
        }
    }

    /**
     * Gets the collection of code attributes.
     *
     * @return Collection of code attributes.
     * @see Attribute
     */
    public Attribute[] getAttributes() {
        return attributes;
    }

    /**
     * Gets the actual byte code of the method.
     *
     * @return Actual byte code of the method.
     */
    public byte[] getCode() {
        return code;
    }

    /**
     * Gets the table of handled exceptions.
     *
     * @return Table of handled exceptions.
     * @see CodeException
     */
    public CodeException[] getExceptionTable() {
        return exceptionTable;
    }

    /**
     * Gets the internal length of this code attribute (minus the first 6 bytes) and excluding all its attributes.
     *
     * @return The internal length of this code attribute (minus the first 6 bytes) and excluding all its attributes.
     */
    private int getInternalLength() {
        return 2 /* maxStack */ + 2 /* maxLocals */ + 4 /* code length */
            + code.length /* byte-code */
            + 2 /* exception-table length */
            + 8 * (exceptionTable == null ? 0 : exceptionTable.length) /* exception table */
            + 2 /* attributes count */;
    }

    /**
     * Gets the LineNumberTable of Code, if it has one.
     *
     * @return LineNumberTable of Code, if it has one.
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
     * Gets the LocalVariableTable of Code, if it has one.
     *
     * @return LocalVariableTable of Code, if it has one.
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
     * Gets the local variable type table attribute {@link LocalVariableTypeTable}.
     *
     * @return LocalVariableTypeTable of Code, if it has one, null otherwise.
     * @since 6.10.0
     */
    public LocalVariableTypeTable getLocalVariableTypeTable() {
        for (final Attribute attribute : attributes) {
            if (attribute instanceof LocalVariableTypeTable) {
                return (LocalVariableTypeTable) attribute;
            }
        }
        return null;
    }

    /**
     * Gets the number of local variables.
     *
     * @return Number of local variables.
     */
    public int getMaxLocals() {
        return maxLocals;
    }

    /**
     * Gets the maximum size of stack used by this method.
     *
     * @return Maximum size of stack used by this method.
     */
    public int getMaxStack() {
        return maxStack;
    }

    /**
     * Finds the attribute of {@link StackMap} instance.
     *
     * @return StackMap of Code, if it has one, else null.
     * @since 6.8.0
     */
    public StackMap getStackMap() {
        for (final Attribute attribute : attributes) {
            if (attribute instanceof StackMap) {
                return (StackMap) attribute;
            }
        }
        return null;
    }

    /**
     * Sets the attributes for this Code.
     *
     * @param attributes The attributes to set for this Code.
     */
    public void setAttributes(final Attribute[] attributes) {
        this.attributes = attributes != null ? attributes : EMPTY_ARRAY;
        super.setLength(calculateLength()); // Adjust length
    }

    /**
     * Sets the byte code.
     *
     * @param code byte code.
     * @throws ClassFormatException Thrown if the code array is greater than {@link Const#MAX_CODE_SIZE}.
     */
    public void setCode(final byte[] code) {
        this.code = Utils.createEmptyArrayIfNull(code);
        Args.requireU4(this.code.length, 0, Const.MAX_CODE_SIZE, "Code length attribute");
        super.setLength(calculateLength()); // Adjust length
    }

    /**
     * Sets the exception table.
     *
     * @param exceptionTable exception table.
     */
    public void setExceptionTable(final CodeException[] exceptionTable) {
        this.exceptionTable = exceptionTable != null ? exceptionTable : CodeException.EMPTY_ARRAY;
        Args.requireU2(this.exceptionTable.length, "exceptionTable.length");
        super.setLength(calculateLength()); // Adjust length
    }

    /**
     * Sets the maximum number of local variables.
     *
     * @param maxLocals maximum number of local variables.
     */
    public void setMaxLocals(final int maxLocals) {
        this.maxLocals = maxLocals;
    }

    /**
     * Sets the maximum stack size.
     *
     * @param maxStack maximum stack size.
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

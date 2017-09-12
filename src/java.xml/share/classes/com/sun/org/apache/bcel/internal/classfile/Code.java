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

/**
 * This class represents a chunk of Java byte code contained in a
 * method. It is instantiated by the
 * <em>Attribute.readAttribute()</em> method. A <em>Code</em>
 * attribute contains informations about operand stack, local
 * variables, byte code and the exceptions handled within this
 * method.
 *
 * This attribute has attributes itself, namely <em>LineNumberTable</em> which
 * is used for debugging purposes and <em>LocalVariableTable</em> which
 * contains information about the local variables.
 *
 * @version $Id: Code.java 1749603 2016-06-21 20:50:19Z ggregory $
 * @see     Attribute
 * @see     CodeException
 * @see     LineNumberTable
 * @see LocalVariableTable
 */
public final class Code extends Attribute {

    private int max_stack; // Maximum size of stack used by this method  // TODO this could be made final (setter is not used)
    private int max_locals; // Number of local variables  // TODO this could be made final (setter is not used)
    private byte[] code; // Actual byte code
    private CodeException[] exception_table; // Table of handled exceptions
    private Attribute[] attributes; // or LocalVariable


    /**
     * Initialize from another object. Note that both objects use the same
     * references (shallow copy). Use copy() for a physical copy.
     */
    public Code(final Code c) {
        this(c.getNameIndex(), c.getLength(), c.getMaxStack(), c.getMaxLocals(), c.getCode(), c
                .getExceptionTable(), c.getAttributes(), c.getConstantPool());
    }


    /**
     * @param name_index Index pointing to the name <em>Code</em>
     * @param length Content length in bytes
     * @param file Input stream
     * @param constant_pool Array of constants
     */
    Code(final int name_index, final int length, final DataInput file, final ConstantPool constant_pool)
            throws IOException {
        // Initialize with some default values which will be overwritten later
        this(name_index, length, file.readUnsignedShort(), file.readUnsignedShort(), (byte[]) null,
                (CodeException[]) null, (Attribute[]) null, constant_pool);
        final int code_length = file.readInt();
        code = new byte[code_length]; // Read byte code
        file.readFully(code);
        /* Read exception table that contains all regions where an exception
         * handler is active, i.e., a try { ... } catch() block.
         */
        final int exception_table_length = file.readUnsignedShort();
        exception_table = new CodeException[exception_table_length];
        for (int i = 0; i < exception_table_length; i++) {
            exception_table[i] = new CodeException(file);
        }
        /* Read all attributes, currently `LineNumberTable' and
         * `LocalVariableTable'
         */
        final int attributes_count = file.readUnsignedShort();
        attributes = new Attribute[attributes_count];
        for (int i = 0; i < attributes_count; i++) {
            attributes[i] = Attribute.readAttribute(file, constant_pool);
        }
        /* Adjust length, because of setAttributes in this(), s.b.  length
         * is incorrect, because it didn't take the internal attributes
         * into account yet! Very subtle bug, fixed in 3.1.1.
         */
        super.setLength(length);
    }


    /**
     * @param name_index Index pointing to the name <em>Code</em>
     * @param length Content length in bytes
     * @param max_stack Maximum size of stack
     * @param max_locals Number of local variables
     * @param code Actual byte code
     * @param exception_table Table of handled exceptions
     * @param attributes Attributes of code: LineNumber or LocalVariable
     * @param constant_pool Array of constants
     */
    public Code(final int name_index, final int length, final int max_stack, final int max_locals, final byte[] code,
            final CodeException[] exception_table, final Attribute[] attributes, final ConstantPool constant_pool) {
        super(Const.ATTR_CODE, name_index, length, constant_pool);
        this.max_stack = max_stack;
        this.max_locals = max_locals;
        this.code = code != null ? code : new byte[0];
        this.exception_table = exception_table != null ? exception_table : new CodeException[0];
        this.attributes = attributes != null ? attributes : new Attribute[0];
        super.setLength(calculateLength()); // Adjust length
    }


    /**
     * Called by objects that are traversing the nodes of the tree implicitely
     * defined by the contents of a Java class. I.e., the hierarchy of methods,
     * fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept( final Visitor v ) {
        v.visitCode(this);
    }


    /**
     * Dump code attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    @Override
    public final void dump( final DataOutputStream file ) throws IOException {
        super.dump(file);
        file.writeShort(max_stack);
        file.writeShort(max_locals);
        file.writeInt(code.length);
        file.write(code, 0, code.length);
        file.writeShort(exception_table.length);
        for (final CodeException exception : exception_table) {
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
    public final Attribute[] getAttributes() {
        return attributes;
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
     * @return Actual byte code of the method.
     */
    public final byte[] getCode() {
        return code;
    }


    /**
     * @return Table of handled exceptions.
     * @see CodeException
     */
    public final CodeException[] getExceptionTable() {
        return exception_table;
    }


    /**
     * @return Number of local variables.
     */
    public final int getMaxLocals() {
        return max_locals;
    }


    /**
     * @return Maximum size of stack used by this method.
     */
    public final int getMaxStack() {
        return max_stack;
    }


    /**
     * @return the internal length of this code attribute (minus the first 6 bytes)
     * and excluding all its attributes
     */
    private int getInternalLength() {
        return 2 /*max_stack*/+ 2 /*max_locals*/+ 4 /*code length*/
                + code.length /*byte-code*/
                + 2 /*exception-table length*/
                + 8 * (exception_table == null ? 0 : exception_table.length) /* exception table */
                + 2 /* attributes count */;
    }


    /**
     * @return the full size of this code attribute, minus its first 6 bytes,
     * including the size of all its contained attributes
     */
    private int calculateLength() {
        int len = 0;
        if (attributes != null) {
            for (final Attribute attribute : attributes) {
                len += attribute.getLength() + 6 /*attribute header size*/;
            }
        }
        return len + getInternalLength();
    }


    /**
     * @param attributes the attributes to set for this Code
     */
    public final void setAttributes( final Attribute[] attributes ) {
        this.attributes = attributes != null ? attributes : new Attribute[0];
        super.setLength(calculateLength()); // Adjust length
    }


    /**
     * @param code byte code
     */
    public final void setCode( final byte[] code ) {
        this.code = code != null ? code : new byte[0];
        super.setLength(calculateLength()); // Adjust length
    }


    /**
     * @param exception_table exception table
     */
    public final void setExceptionTable( final CodeException[] exception_table ) {
        this.exception_table = exception_table != null ? exception_table : new CodeException[0];
        super.setLength(calculateLength()); // Adjust length
    }


    /**
     * @param max_locals maximum number of local variables
     */
    public final void setMaxLocals( final int max_locals ) {
        this.max_locals = max_locals;
    }


    /**
     * @param max_stack maximum stack size
     */
    public final void setMaxStack( final int max_stack ) {
        this.max_stack = max_stack;
    }


    /**
     * @return String representation of code chunk.
     */
    public final String toString( final boolean verbose ) {
        final StringBuilder buf = new StringBuilder(100); // CHECKSTYLE IGNORE MagicNumber
        buf.append("Code(max_stack = ").append(max_stack).append(", max_locals = ").append(
                max_locals).append(", code_length = ").append(code.length).append(")\n").append(
                Utility.codeToString(code, super.getConstantPool(), 0, -1, verbose));
        if (exception_table.length > 0) {
            buf.append("\nException handler(s) = \n").append("From\tTo\tHandler\tType\n");
            for (final CodeException exception : exception_table) {
                buf.append(exception.toString(super.getConstantPool(), verbose)).append("\n");
            }
        }
        if (attributes.length > 0) {
            buf.append("\nAttribute(s) = ");
            for (final Attribute attribute : attributes) {
                buf.append("\n").append(attribute);
            }
        }
        return buf.toString();
    }


    /**
     * @return String representation of code chunk.
     */
    @Override
    public final String toString() {
        return toString(true);
    }


    /**
     * @return deep copy of this attribute
     *
     * @param _constant_pool the constant pool to duplicate
     */
    @Override
    public Attribute copy( final ConstantPool _constant_pool ) {
        final Code c = (Code) clone();
        if (code != null) {
            c.code = new byte[code.length];
            System.arraycopy(code, 0, c.code, 0, code.length);
        }
        c.setConstantPool(_constant_pool);
        c.exception_table = new CodeException[exception_table.length];
        for (int i = 0; i < exception_table.length; i++) {
            c.exception_table[i] = exception_table[i].copy();
        }
        c.attributes = new Attribute[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            c.attributes[i] = attributes[i].copy(_constant_pool);
        }
        return c;
    }
}

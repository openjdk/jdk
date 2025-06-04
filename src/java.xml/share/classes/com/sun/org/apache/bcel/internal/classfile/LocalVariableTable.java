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
import java.util.Iterator;
import java.util.stream.Stream;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.util.Args;

/**
 * This class represents colection of local variables in a method. This attribute is contained in the <em>Code</em>
 * attribute.
 *
 * @see Code
 * @see LocalVariable
 */
public class LocalVariableTable extends Attribute implements Iterable<LocalVariable> {

    private LocalVariable[] localVariableTable; // variables

    /**
     * Construct object from input stream.
     *
     * @param nameIndex Index in constant pool
     * @param length Content length in bytes
     * @param input Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    LocalVariableTable(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, (LocalVariable[]) null, constantPool);
        final int localVariableTableLength = input.readUnsignedShort();
        localVariableTable = new LocalVariable[localVariableTableLength];
        for (int i = 0; i < localVariableTableLength; i++) {
            localVariableTable[i] = new LocalVariable(input, constantPool);
        }
    }

    /**
     * @param nameIndex Index in constant pool to 'LocalVariableTable'
     * @param length Content length in bytes
     * @param localVariableTable Table of local variables
     * @param constantPool Array of constants
     */
    public LocalVariableTable(final int nameIndex, final int length, final LocalVariable[] localVariableTable, final ConstantPool constantPool) {
        super(Const.ATTR_LOCAL_VARIABLE_TABLE, nameIndex, length, constantPool);
        this.localVariableTable = localVariableTable != null ? localVariableTable : LocalVariable.EMPTY_ARRAY;
        Args.requireU2(this.localVariableTable.length, "localVariableTable.length");
    }

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use copy() for a
     * physical copy.
     *
     * @param c Source to copy.
     */
    public LocalVariableTable(final LocalVariableTable c) {
        this(c.getNameIndex(), c.getLength(), c.getLocalVariableTable(), c.getConstantPool());
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitLocalVariableTable(this);
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final LocalVariableTable c = (LocalVariableTable) clone();
        c.localVariableTable = new LocalVariable[localVariableTable.length];
        Arrays.setAll(c.localVariableTable, i -> localVariableTable[i].copy());
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dump local variable table attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public final void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeShort(localVariableTable.length);
        for (final LocalVariable variable : localVariableTable) {
            variable.dump(file);
        }
    }

    /**
     *
     * @param index the variable slot
     *
     * @return the first LocalVariable that matches the slot or null if not found
     *
     * @deprecated since 5.2 because multiple variables can share the same slot, use getLocalVariable(int index, int pc)
     *             instead.
     */
    @java.lang.Deprecated
    public final LocalVariable getLocalVariable(final int index) {
        for (final LocalVariable variable : localVariableTable) {
            if (variable.getIndex() == index) {
                return variable;
            }
        }
        return null;
    }

    /**
     *
     * @param index the variable slot
     * @param pc the current pc that this variable is alive
     *
     * @return the LocalVariable that matches or null if not found
     */
    public final LocalVariable getLocalVariable(final int index, final int pc) {
        for (final LocalVariable variable : localVariableTable) {
            if (variable.getIndex() == index) {
                final int startPc = variable.getStartPC();
                final int endPc = startPc + variable.getLength();
                if (pc >= startPc && pc <= endPc) {
                    return variable;
                }
            }
        }
        return null;
    }

    /**
     * @return Array of local variables of method.
     */
    public final LocalVariable[] getLocalVariableTable() {
        return localVariableTable;
    }

    public final int getTableLength() {
        return localVariableTable == null ? 0 : localVariableTable.length;
    }

    @Override
    public Iterator<LocalVariable> iterator() {
        return Stream.of(localVariableTable).iterator();
    }

    public final void setLocalVariableTable(final LocalVariable[] localVariableTable) {
        this.localVariableTable = localVariableTable;
    }

    /**
     * @return String representation.
     */
    @Override
    public final String toString() {
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < localVariableTable.length; i++) {
            buf.append(localVariableTable[i]);
            if (i < localVariableTable.length - 1) {
                buf.append('\n');
            }
        }
        return buf.toString();
    }
}

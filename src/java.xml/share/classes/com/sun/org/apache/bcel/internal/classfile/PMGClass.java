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
 * This class is derived from <em>Attribute</em> and represents a reference to a PMG attribute.
 *
 * @see Attribute
 */
public final class PMGClass extends Attribute {

    private int pmgClassIndex;
    private int pmgIndex;

    /**
     * Construct object from input stream.
     *
     * @param nameIndex Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param input Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    PMGClass(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, input.readUnsignedShort(), input.readUnsignedShort(), constantPool);
    }

    /**
     * @param nameIndex Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param pmgIndex index in constant pool for source file name
     * @param pmgClassIndex Index in constant pool to CONSTANT_Utf8
     * @param constantPool Array of constants
     */
    public PMGClass(final int nameIndex, final int length, final int pmgIndex, final int pmgClassIndex, final ConstantPool constantPool) {
        super(Const.ATTR_PMG, nameIndex, length, constantPool);
        this.pmgIndex = pmgIndex;
        this.pmgClassIndex = pmgClassIndex;
    }

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use copy() for a
     * physical copy.
     *
     * @param pgmClass Source to copy.
     */
    public PMGClass(final PMGClass pgmClass) {
        this(pgmClass.getNameIndex(), pgmClass.getLength(), pgmClass.getPMGIndex(), pgmClass.getPMGClassIndex(), pgmClass.getConstantPool());
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        println("Visiting non-standard PMGClass object");
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        return (Attribute) clone();
    }

    /**
     * Dump source file attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeShort(pmgIndex);
        file.writeShort(pmgClassIndex);
    }

    /**
     * @return Index in constant pool of source file name.
     */
    public int getPMGClassIndex() {
        return pmgClassIndex;
    }

    /**
     * @return PMG class name.
     */
    public String getPMGClassName() {
        return super.getConstantPool().getConstantUtf8(pmgClassIndex).getBytes();
    }

    /**
     * @return Index in constant pool of source file name.
     */
    public int getPMGIndex() {
        return pmgIndex;
    }

    /**
     * @return PMG name.
     */
    public String getPMGName() {
        return super.getConstantPool().getConstantUtf8(pmgIndex).getBytes();
    }

    /**
     * @param pmgClassIndex
     */
    public void setPMGClassIndex(final int pmgClassIndex) {
        this.pmgClassIndex = pmgClassIndex;
    }

    /**
     * @param pmgIndex
     */
    public void setPMGIndex(final int pmgIndex) {
        this.pmgIndex = pmgIndex;
    }

    /**
     * @return String representation
     */
    @Override
    public String toString() {
        return "PMGClass(" + getPMGName() + ", " + getPMGClassName() + ")";
    }
}

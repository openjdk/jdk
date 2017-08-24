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
 * This class is derived from <em>Attribute</em> and represents a reference
 * to a PMG attribute.
 *
 * @version $Id: PMGClass.java 1749603 2016-06-21 20:50:19Z ggregory $
 * @see     Attribute
 */
public final class PMGClass extends Attribute {

    private int pmg_class_index;
    private int pmg_index;


    /**
     * Initialize from another object. Note that both objects use the same
     * references (shallow copy). Use copy() for a physical copy.
     */
    public PMGClass(final PMGClass c) {
        this(c.getNameIndex(), c.getLength(), c.getPMGIndex(), c.getPMGClassIndex(), c
                .getConstantPool());
    }


    /**
     * Construct object from input stream.
     * @param name_index Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param input Input stream
     * @param constant_pool Array of constants
     * @throws IOException
     */
    PMGClass(final int name_index, final int length, final DataInput input, final ConstantPool constant_pool)
            throws IOException {
        this(name_index, length, input.readUnsignedShort(), input.readUnsignedShort(), constant_pool);
    }


    /**
     * @param name_index Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param pmg_index index in constant pool for source file name
     * @param pmg_class_index Index in constant pool to CONSTANT_Utf8
     * @param constant_pool Array of constants
     */
    public PMGClass(final int name_index, final int length, final int pmg_index, final int pmg_class_index,
            final ConstantPool constant_pool) {
        super(Const.ATTR_PMG, name_index, length, constant_pool);
        this.pmg_index = pmg_index;
        this.pmg_class_index = pmg_class_index;
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
        System.err.println("Visiting non-standard PMGClass object");
    }


    /**
     * Dump source file attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    @Override
    public final void dump( final DataOutputStream file ) throws IOException {
        super.dump(file);
        file.writeShort(pmg_index);
        file.writeShort(pmg_class_index);
    }


    /**
     * @return Index in constant pool of source file name.
     */
    public final int getPMGClassIndex() {
        return pmg_class_index;
    }


    /**
     * @param pmg_class_index
     */
    public final void setPMGClassIndex( final int pmg_class_index ) {
        this.pmg_class_index = pmg_class_index;
    }


    /**
     * @return Index in constant pool of source file name.
     */
    public final int getPMGIndex() {
        return pmg_index;
    }


    /**
     * @param pmg_index
     */
    public final void setPMGIndex( final int pmg_index ) {
        this.pmg_index = pmg_index;
    }


    /**
     * @return PMG name.
     */
    public final String getPMGName() {
        final ConstantUtf8 c = (ConstantUtf8) super.getConstantPool().getConstant(pmg_index,
                Const.CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return PMG class name.
     */
    public final String getPMGClassName() {
        final ConstantUtf8 c = (ConstantUtf8) super.getConstantPool().getConstant(pmg_class_index,
                Const.CONSTANT_Utf8);
        return c.getBytes();
    }


    /**
     * @return String representation
     */
    @Override
    public final String toString() {
        return "PMGClass(" + getPMGName() + ", " + getPMGClassName() + ")";
    }


    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy( final ConstantPool _constant_pool ) {
        return (Attribute) clone();
    }
}

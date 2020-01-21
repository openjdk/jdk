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
 * This class represents a inner class attribute, i.e., the class
 * indices of the inner and outer classes, the name and the attributes
 * of the inner class.
 *
 * @see InnerClasses
 */
public final class InnerClass implements Cloneable, Node {

    private int inner_class_index;
    private int outer_class_index;
    private int inner_name_index;
    private int inner_access_flags;


    /**
     * Initialize from another object.
     */
    public InnerClass(final InnerClass c) {
        this(c.getInnerClassIndex(), c.getOuterClassIndex(), c.getInnerNameIndex(), c
                .getInnerAccessFlags());
    }


    /**
     * Construct object from file stream.
     * @param file Input stream
     * @throws IOException
     */
    InnerClass(final DataInput file) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort(), file.readUnsignedShort(), file
                .readUnsignedShort());
    }


    /**
     * @param inner_class_index Class index in constant pool of inner class
     * @param outer_class_index Class index in constant pool of outer class
     * @param inner_name_index  Name index in constant pool of inner class
     * @param inner_access_flags Access flags of inner class
     */
    public InnerClass(final int inner_class_index, final int outer_class_index, final int inner_name_index,
            final int inner_access_flags) {
        this.inner_class_index = inner_class_index;
        this.outer_class_index = outer_class_index;
        this.inner_name_index = inner_name_index;
        this.inner_access_flags = inner_access_flags;
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
        v.visitInnerClass(this);
    }


    /**
     * Dump inner class attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    public void dump( final DataOutputStream file ) throws IOException {
        file.writeShort(inner_class_index);
        file.writeShort(outer_class_index);
        file.writeShort(inner_name_index);
        file.writeShort(inner_access_flags);
    }


    /**
     * @return access flags of inner class.
     */
    public int getInnerAccessFlags() {
        return inner_access_flags;
    }


    /**
     * @return class index of inner class.
     */
    public int getInnerClassIndex() {
        return inner_class_index;
    }


    /**
     * @return name index of inner class.
     */
    public int getInnerNameIndex() {
        return inner_name_index;
    }


    /**
     * @return class index of outer class.
     */
    public int getOuterClassIndex() {
        return outer_class_index;
    }


    /**
     * @param inner_access_flags access flags for this inner class
     */
    public void setInnerAccessFlags( final int inner_access_flags ) {
        this.inner_access_flags = inner_access_flags;
    }


    /**
     * @param inner_class_index index into the constant pool for this class
     */
    public void setInnerClassIndex( final int inner_class_index ) {
        this.inner_class_index = inner_class_index;
    }


    /**
     * @param inner_name_index index into the constant pool for this class's name
     */
    public void setInnerNameIndex( final int inner_name_index ) { // TODO unused
        this.inner_name_index = inner_name_index;
    }


    /**
     * @param outer_class_index index into the constant pool for the owning class
     */
    public void setOuterClassIndex( final int outer_class_index ) { // TODO unused
        this.outer_class_index = outer_class_index;
    }


    /**
     * @return String representation.
     */
    @Override
    public String toString() {
        return "InnerClass(" + inner_class_index + ", " + outer_class_index + ", "
                + inner_name_index + ", " + inner_access_flags + ")";
    }


    /**
     * @return Resolved string representation
     */
    public String toString( final ConstantPool constant_pool ) {
        String outer_class_name;
        String inner_name;
        String inner_class_name = constant_pool.getConstantString(inner_class_index,
                Const.CONSTANT_Class);
        inner_class_name = Utility.compactClassName(inner_class_name, false);
        if (outer_class_index != 0) {
            outer_class_name = constant_pool.getConstantString(outer_class_index,
                    Const.CONSTANT_Class);
            outer_class_name = " of class " + Utility.compactClassName(outer_class_name, false);
        } else {
            outer_class_name = "";
        }
        if (inner_name_index != 0) {
            inner_name = ((ConstantUtf8) constant_pool.getConstant(inner_name_index,
                    Const.CONSTANT_Utf8)).getBytes();
        } else {
            inner_name = "(anonymous)";
        }
        String access = Utility.accessToString(inner_access_flags, true);
        access = access.isEmpty() ? "" : (access + " ");
        return "  " + access + inner_name + "=class " + inner_class_name + outer_class_name;
    }


    /**
     * @return deep copy of this object
     */
    public InnerClass copy() {
        try {
            return (InnerClass) clone();
        } catch (final CloneNotSupportedException e) {
            // TODO should this throw?
        }
        return null;
    }
}

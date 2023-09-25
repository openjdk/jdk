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
 * This class represents a inner class attribute, i.e., the class indices of the inner and outer classes, the name and
 * the attributes of the inner class.
 *
 * @see InnerClasses
 */
public final class InnerClass implements Cloneable, Node {

    private int innerClassIndex;
    private int outerClassIndex;
    private int innerNameIndex;
    private int innerAccessFlags;

    /**
     * Construct object from file stream.
     *
     * @param file Input stream
     * @throws IOException if an I/O error occurs.
     */
    InnerClass(final DataInput file) throws IOException {
        this(file.readUnsignedShort(), file.readUnsignedShort(), file.readUnsignedShort(), file.readUnsignedShort());
    }

    /**
     * Initialize from another object.
     *
     * @param c Source to copy.
     */
    public InnerClass(final InnerClass c) {
        this(c.getInnerClassIndex(), c.getOuterClassIndex(), c.getInnerNameIndex(), c.getInnerAccessFlags());
    }

    /**
     * @param innerClassIndex Class index in constant pool of inner class
     * @param outerClassIndex Class index in constant pool of outer class
     * @param innerNameIndex Name index in constant pool of inner class
     * @param innerAccessFlags Access flags of inner class
     */
    public InnerClass(final int innerClassIndex, final int outerClassIndex, final int innerNameIndex, final int innerAccessFlags) {
        this.innerClassIndex = innerClassIndex;
        this.outerClassIndex = outerClassIndex;
        this.innerNameIndex = innerNameIndex;
        this.innerAccessFlags = innerAccessFlags;
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitInnerClass(this);
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

    /**
     * Dump inner class attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    public void dump(final DataOutputStream file) throws IOException {
        file.writeShort(innerClassIndex);
        file.writeShort(outerClassIndex);
        file.writeShort(innerNameIndex);
        file.writeShort(innerAccessFlags);
    }

    /**
     * @return access flags of inner class.
     */
    public int getInnerAccessFlags() {
        return innerAccessFlags;
    }

    /**
     * @return class index of inner class.
     */
    public int getInnerClassIndex() {
        return innerClassIndex;
    }

    /**
     * @return name index of inner class.
     */
    public int getInnerNameIndex() {
        return innerNameIndex;
    }

    /**
     * @return class index of outer class.
     */
    public int getOuterClassIndex() {
        return outerClassIndex;
    }

    /**
     * @param innerAccessFlags access flags for this inner class
     */
    public void setInnerAccessFlags(final int innerAccessFlags) {
        this.innerAccessFlags = innerAccessFlags;
    }

    /**
     * @param innerClassIndex index into the constant pool for this class
     */
    public void setInnerClassIndex(final int innerClassIndex) {
        this.innerClassIndex = innerClassIndex;
    }

    /**
     * @param innerNameIndex index into the constant pool for this class's name
     */
    public void setInnerNameIndex(final int innerNameIndex) { // TODO unused
        this.innerNameIndex = innerNameIndex;
    }

    /**
     * @param outerClassIndex index into the constant pool for the owning class
     */
    public void setOuterClassIndex(final int outerClassIndex) { // TODO unused
        this.outerClassIndex = outerClassIndex;
    }

    /**
     * @return String representation.
     */
    @Override
    public String toString() {
        return "InnerClass(" + innerClassIndex + ", " + outerClassIndex + ", " + innerNameIndex + ", " + innerAccessFlags + ")";
    }

    /**
     * @return Resolved string representation
     */
    public String toString(final ConstantPool constantPool) {
        String outerClassName;
        String innerName;
        String innerClassName = constantPool.getConstantString(innerClassIndex, Const.CONSTANT_Class);
        innerClassName = Utility.compactClassName(innerClassName, false);
        if (outerClassIndex != 0) {
            outerClassName = constantPool.getConstantString(outerClassIndex, Const.CONSTANT_Class);
            outerClassName = " of class " + Utility.compactClassName(outerClassName, false);
        } else {
            outerClassName = "";
        }
        if (innerNameIndex != 0) {
            innerName = constantPool.getConstantUtf8(innerNameIndex).getBytes();
        } else {
            innerName = "(anonymous)";
        }
        String access = Utility.accessToString(innerAccessFlags, true);
        access = access.isEmpty() ? "" : access + " ";
        return "  " + access + innerName + "=class " + innerClassName + outerClassName;
    }
}

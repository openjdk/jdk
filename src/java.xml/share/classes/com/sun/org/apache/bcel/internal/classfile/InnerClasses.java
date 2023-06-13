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
 * This class is derived from <em>Attribute</em> and denotes that this class is an Inner class of another. to the source
 * file of this class. It is instantiated from the <em>Attribute.readAttribute()</em> method.
 *
 * @see Attribute
 */
public final class InnerClasses extends Attribute implements Iterable<InnerClass> {

    /**
     * Empty array.
     */
    private static final InnerClass[] EMPTY_INNER_CLASSE_ARRAY = {};

    private InnerClass[] innerClasses;

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use clone() for a
     * physical copy.
     *
     * @param c Source to copy.
     */
    public InnerClasses(final InnerClasses c) {
        this(c.getNameIndex(), c.getLength(), c.getInnerClasses(), c.getConstantPool());
    }

    /**
     * Construct object from input stream.
     *
     * @param nameIndex Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param input Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    InnerClasses(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, (InnerClass[]) null, constantPool);
        final int classCount = input.readUnsignedShort();
        innerClasses = new InnerClass[classCount];
        for (int i = 0; i < classCount; i++) {
            innerClasses[i] = new InnerClass(input);
        }
    }

    /**
     * @param nameIndex Index in constant pool to CONSTANT_Utf8
     * @param length Content length in bytes
     * @param innerClasses array of inner classes attributes
     * @param constantPool Array of constants
     */
    public InnerClasses(final int nameIndex, final int length, final InnerClass[] innerClasses, final ConstantPool constantPool) {
        super(Const.ATTR_INNER_CLASSES, nameIndex, length, constantPool);
        this.innerClasses = innerClasses != null ? innerClasses : EMPTY_INNER_CLASSE_ARRAY;
        Args.requireU2(this.innerClasses.length, "innerClasses.length");
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitInnerClasses(this);
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        // TODO this could be recoded to use a lower level constructor after creating a copy of the inner classes
        final InnerClasses c = (InnerClasses) clone();
        c.innerClasses = new InnerClass[innerClasses.length];
        Arrays.setAll(c.innerClasses, i -> innerClasses[i].copy());
        c.setConstantPool(constantPool);
        return c;
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
        file.writeShort(innerClasses.length);
        for (final InnerClass innerClass : innerClasses) {
            innerClass.dump(file);
        }
    }

    /**
     * @return array of inner class "records"
     */
    public InnerClass[] getInnerClasses() {
        return innerClasses;
    }

    @Override
    public Iterator<InnerClass> iterator() {
        return Stream.of(innerClasses).iterator();
    }

    /**
     * @param innerClasses the array of inner classes
     */
    public void setInnerClasses(final InnerClass[] innerClasses) {
        this.innerClasses = innerClasses != null ? innerClasses : EMPTY_INNER_CLASSE_ARRAY;
    }

    /**
     * @return String representation.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("InnerClasses(");
        buf.append(innerClasses.length);
        buf.append("):\n");
        for (final InnerClass innerClass : innerClasses) {
            buf.append(innerClass.toString(super.getConstantPool())).append("\n");
        }
        return buf.substring(0, buf.length() - 1); // remove the last newline
    }
}

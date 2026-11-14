/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
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
 * This class is derived from <em>Attribute</em> and records the classes and interfaces that are permitted to extend or
 * implement the current class or interface. There may be at most one PermittedSubclasses attribute in a ClassFile
 * structure.
 *
 * @see Attribute
 * @since 6.13.0
 */
public final class PermittedSubclasses extends Attribute {

    private int[] classes;

    /**
     * Constructs object from input stream.
     *
     * @param nameIndex Index in constant pool.
     * @param length Content length in bytes.
     * @param dataInput Input stream.
     * @param constantPool Array of constants.
     * @throws IOException Thrown if an I/O error occurs.
     */
    PermittedSubclasses(final int nameIndex, final int length, final DataInput dataInput, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, (int[]) null, constantPool);
        classes = ClassParser.readU2U2Table(dataInput);
    }

    /**
     * Constructs object from table of class indices in constant pool.
     *
     * @param nameIndex Index in constant pool.
     * @param length Content length in bytes.
     * @param classes Table of indices in constant pool.
     * @param constantPool Array of constants.
     */
    public PermittedSubclasses(final int nameIndex, final int length, final int[] classes, final ConstantPool constantPool) {
        super(Const.ATTR_PERMITTED_SUBCLASSES, nameIndex, length, constantPool);
        this.classes = Utils.createEmptyArrayIfNull(classes);
        Args.requireU2(this.classes.length, "classes.length");
    }

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use copy() for a
     * physical copy.
     *
     * @param c Source to copy.
     */
    public PermittedSubclasses(final PermittedSubclasses c) {
        this(c.getNameIndex(), c.getLength(), c.getClasses(), c.getConstantPool());
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object.
     */
    @Override
    public void accept(final Visitor v) {
        v.visitPermittedSubclasses(this);
    }

    /**
     * Creates a deep clone of this object given constant pool.
     *
     * @return deep copy of this attribute.
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final PermittedSubclasses c = (PermittedSubclasses) clone();
        if (classes.length > 0) {
            c.classes = classes.clone();
        }
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dumps PermittedSubclasses attribute to file stream in binary format.
     *
     * @param file Output file stream.
     * @throws IOException Thrown if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeShort(Args.requireU2(classes.length, "classes.length"));
        for (final int index : classes) {
            file.writeShort(index);
        }
    }

    /**
     * Gets the class indices in constant pool.
     *
     * @return array of indices into constant pool of class names.
     */
    public int[] getClasses() {
        return classes;
    }

    /**
     * Gets permitted class names.
     *
     * @return string array of class names.
     */
    public String[] getClassNames() {
        final String[] names = new String[classes.length];
        Arrays.setAll(names, i -> Utility.pathToPackage(super.getConstantPool().getConstantString(classes[i], Const.CONSTANT_Class)));
        return names;
    }

    /**
     * Gets the number of classes.
     *
     * @return Length of classes table.
     */
    public int getNumberClasses() {
        return classes.length;
    }

    /**
     * Sets class indices.
     *
     * @param classes The list of class indexes Also redefines number_of_classes according to table length.
     */
    public void setClasses(final int[] classes) {
        this.classes = Utils.createEmptyArrayIfNull(classes);
    }

    /**
     * String representation of PermittedSubclasses (for debugging purposes).
     *
     * @return String representation, that is, a list of permitted subclasses.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("PermittedSubclasses(");
        buf.append(classes.length);
        buf.append("):\n");
        for (final int index : classes) {
            final String className = super.getConstantPool().getConstantString(index, Const.CONSTANT_Class);
            buf.append("  ").append(Utility.compactClassName(className, false)).append("\n");
        }
        return buf.substring(0, buf.length() - 1); // remove the last newline
    }
}

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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.sun.org.apache.bcel.internal.classfile;

import java.io.DataInput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

import com.sun.org.apache.bcel.internal.Const;
import com.sun.org.apache.bcel.internal.util.Args;

/**
 * This class is derived from <em>Attribute</em> and represents the list of packages that are exported or opened by the
 * Module attribute. There may be at most one ModulePackages attribute in a ClassFile structure.
 *
 * @see Attribute
 * @LastModified: Feb 2023
 */
public final class ModulePackages extends Attribute {

    private int[] packageIndexTable;

    /**
     * Construct object from input stream.
     *
     * @param nameIndex Index in constant pool
     * @param length Content length in bytes
     * @param input Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    ModulePackages(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        this(nameIndex, length, (int[]) null, constantPool);
        final int packageCount = input.readUnsignedShort();
        packageIndexTable = new int[packageCount];
        for (int i = 0; i < packageCount; i++) {
            packageIndexTable[i] = input.readUnsignedShort();
        }
    }

    /**
     * @param nameIndex Index in constant pool
     * @param length Content length in bytes
     * @param packageIndexTable Table of indices in constant pool
     * @param constantPool Array of constants
     */
    public ModulePackages(final int nameIndex, final int length, final int[] packageIndexTable, final ConstantPool constantPool) {
        super(Const.ATTR_MODULE_PACKAGES, nameIndex, length, constantPool);
        this.packageIndexTable = packageIndexTable != null ? packageIndexTable : Const.EMPTY_INT_ARRAY;
        Args.requireU2(this.packageIndexTable.length, "packageIndexTable.length");
    }

    /**
     * Initialize from another object. Note that both objects use the same references (shallow copy). Use copy() for a
     * physical copy.
     *
     * @param c Source to copy.
     */
    public ModulePackages(final ModulePackages c) {
        this(c.getNameIndex(), c.getLength(), c.getPackageIndexTable(), c.getConstantPool());
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitModulePackages(this);
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final ModulePackages c = (ModulePackages) clone();
        if (packageIndexTable != null) {
            c.packageIndexTable = packageIndexTable.clone();
        }
        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dump ModulePackages attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);
        file.writeShort(packageIndexTable.length);
        for (final int index : packageIndexTable) {
            file.writeShort(index);
        }
    }

    /**
     * @return Length of package table.
     */
    public int getNumberOfPackages() {
        return packageIndexTable == null ? 0 : packageIndexTable.length;
    }

    /**
     * @return array of indices into constant pool of package names.
     */
    public int[] getPackageIndexTable() {
        return packageIndexTable;
    }

    /**
     * @return string array of package names
     */
    public String[] getPackageNames() {
        final String[] names = new String[packageIndexTable.length];
        Arrays.setAll(names, i -> Utility.pathToPackage(super.getConstantPool().getConstantString(packageIndexTable[i], Const.CONSTANT_Package)));
        return names;
    }

    /**
     * @param packageIndexTable the list of package indexes Also redefines number_of_packages according to table length.
     */
    public void setPackageIndexTable(final int[] packageIndexTable) {
        this.packageIndexTable = packageIndexTable != null ? packageIndexTable : Const.EMPTY_INT_ARRAY;
    }

    /**
     * @return String representation, i.e., a list of packages.
     */
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append("ModulePackages(");
        buf.append(packageIndexTable.length);
        buf.append("):\n");
        for (final int index : packageIndexTable) {
            final String packageName = super.getConstantPool().getConstantString(index, Const.CONSTANT_Package);
            buf.append("  ").append(Utility.compactClassName(packageName, false)).append("\n");
        }
        return buf.substring(0, buf.length() - 1); // remove the last newline
    }
}

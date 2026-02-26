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

import com.sun.org.apache.bcel.internal.Const;

/**
 * This class represents an entry in the exports table of the Module attribute. Each entry describes a package which may
 * open the parent module.
 *
 * @see Module
 * @since 6.4.0
 */
public final class ModuleExports implements Cloneable, Node {

    private static String getToModuleNameAtIndex(final ConstantPool constantPool, final int index) {
        return constantPool.getConstantString(index, Const.CONSTANT_Module);
    }
    private final int exportsIndex; // points to CONSTANT_Package_info
    private final int exportsFlags;
    private final int exportsToCount;

    private final int[] exportsToIndex; // points to CONSTANT_Module_info

    /**
     * Constructs object from file stream.
     *
     * @param file Input stream
     * @throws IOException if an I/O Exception occurs in readUnsignedShort
     */
    ModuleExports(final DataInput file) throws IOException {
        exportsIndex = file.readUnsignedShort();
        exportsFlags = file.readUnsignedShort();
        exportsToCount = file.readUnsignedShort();
        exportsToIndex = new int[exportsToCount];
        for (int i = 0; i < exportsToCount; i++) {
            exportsToIndex[i] = file.readUnsignedShort();
        }
    }

    /**
     * Called by objects that are traversing the nodes of the tree implicitly defined by the contents of a Java class.
     * I.e., the hierarchy of methods, fields, attributes, etc. spawns a tree of objects.
     *
     * @param v Visitor object
     */
    @Override
    public void accept(final Visitor v) {
        v.visitModuleExports(this);
    }

    /**
     * @return deep copy of this object
     */
    public ModuleExports copy() {
        try {
            return (ModuleExports) clone();
        } catch (final CloneNotSupportedException e) {
            // TODO should this throw?
        }
        return null;
    }

    /**
     * Dump table entry to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O Exception occurs in writeShort
     */
    public void dump(final DataOutputStream file) throws IOException {
        file.writeShort(exportsIndex);
        file.writeShort(exportsFlags);
        file.writeShort(exportsToCount);
        for (final int entry : exportsToIndex) {
            file.writeShort(entry);
        }
    }

    /**
     * Gets the flags for this ModuleExports.
     * @return the exportsFlags
     * @since 6.10.0
     */
    public int getExportsFlags() {
        return exportsFlags;
    }

    /**
     * Gets the exported package name.
     * @param constantPool the constant pool from the ClassFile
     * @return the exported package name
     * @since 6.10.0
     */
    public String getPackageName(final ConstantPool constantPool) {
        return constantPool.constantToString(exportsIndex, Const.CONSTANT_Package);
    }

    /**
     * Gets an array of module names for this ModuleExports.
     * @param constantPool Array of constants usually obtained from the ClassFile object
     * @return array of module names following 'exports to'
     * @since 6.10.0
     */
    public String[] getToModuleNames(final ConstantPool constantPool) {
        final String[] toModuleNames = new String[exportsToCount];
        for (int i = 0; i < exportsToCount; i++) {
            toModuleNames[i] = getToModuleNameAtIndex(constantPool, exportsToIndex[i]);
        }
        return toModuleNames;
    }

    /**
     * @return String representation
     */
    @Override
    public String toString() {
        return "exports(" + exportsIndex + ", " + exportsFlags + ", " + exportsToCount + ", ...)";
    }

    /**
     * @return Resolved string representation
     */
    public String toString(final ConstantPool constantPool) {
        final StringBuilder buf = new StringBuilder();
        final String packageName = getPackageName(constantPool);
        buf.append(packageName);
        buf.append(", ").append(String.format("%04x", exportsFlags));
        buf.append(", to(").append(exportsToCount).append("):\n");
        for (final int index : exportsToIndex) {
            final String moduleName = getToModuleNameAtIndex(constantPool, index);
            buf.append("      ").append(moduleName).append("\n");
        }
        return buf.substring(0, buf.length() - 1); // remove the last newline
    }
}

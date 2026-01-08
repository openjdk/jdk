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
 * This class represents an entry in the opens table of the Module attribute. Each entry describes a package which the
 * parent module opens.
 *
 * @see Module
 * @since 6.4.0
 */
public final class ModuleOpens implements Cloneable, Node {

    private static String getToModuleNameAtIndex(final ConstantPool constantPool, final int index) {
        return constantPool.getConstantString(index, Const.CONSTANT_Module);
    }
    private final int opensIndex; // points to CONSTANT_Package_info
    private final int opensFlags;
    private final int opensToCount;

    private final int[] opensToIndex; // points to CONSTANT_Module_info

    /**
     * Constructs object from file stream.
     *
     * @param file Input stream
     * @throws IOException if an I/O Exception occurs in readUnsignedShort
     */
    ModuleOpens(final DataInput file) throws IOException {
        opensIndex = file.readUnsignedShort();
        opensFlags = file.readUnsignedShort();
        opensToCount = file.readUnsignedShort();
        opensToIndex = new int[opensToCount];
        for (int i = 0; i < opensToCount; i++) {
            opensToIndex[i] = file.readUnsignedShort();
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
        v.visitModuleOpens(this);
    }

    /**
     * @return deep copy of this object
     */
    public ModuleOpens copy() {
        try {
            return (ModuleOpens) clone();
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
        file.writeShort(opensIndex);
        file.writeShort(opensFlags);
        file.writeShort(opensToCount);
        for (final int entry : opensToIndex) {
            file.writeShort(entry);
        }
    }

    /**
     * Gets the flags for this ModuleOpens.
     * @return the opensFlags
     * @since 6.10.0
     */
    public int getOpensFlags() {
        return opensFlags;
    }

    /**
     * Gets the opened package name.
     * @param constantPool the constant pool from the ClassFile
     * @return the opened package name
     * @since 6.10.0
     */
    public String getPackageName(final ConstantPool constantPool) {
        return constantPool.constantToString(opensIndex, Const.CONSTANT_Package);
    }

    /**
     * Gets an array of module names for this ModuleOpens.
     * @param constantPool Array of constants usually obtained from the ClassFile object
     * @return array of module names following 'opens to'
     * @since 6.10.0
     */
    public String[] getToModuleNames(final ConstantPool constantPool) {
        final String[] toModuleNames = new String[opensToCount];
        for (int i = 0; i < opensToCount; i++) {
            toModuleNames[i] = getToModuleNameAtIndex(constantPool, opensToIndex[i]);
        }
        return toModuleNames;
    }

    /**
     * @return String representation
     */
    @Override
    public String toString() {
        return "opens(" + opensIndex + ", " + opensFlags + ", " + opensToCount + ", ...)";
    }

    /**
     * @return Resolved string representation
     */
    public String toString(final ConstantPool constantPool) {
        final StringBuilder buf = new StringBuilder();
        final String packageName = getPackageName(constantPool);
        buf.append(packageName);
        buf.append(", ").append(String.format("%04x", opensFlags));
        buf.append(", to(").append(opensToCount).append("):\n");
        for (final int index : opensToIndex) {
            final String moduleName = getToModuleNameAtIndex(constantPool, index);
            buf.append("      ").append(moduleName).append("\n");
        }
        return buf.substring(0, buf.length() - 1); // remove the last newline
    }
}

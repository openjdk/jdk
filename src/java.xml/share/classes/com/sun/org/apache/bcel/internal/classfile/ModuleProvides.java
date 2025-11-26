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
 * This class represents an entry in the provides table of the Module attribute. Each entry describes a service
 * implementation that the parent module provides.
 *
 * @see Module
 * @since 6.4.0
 */
public final class ModuleProvides implements Cloneable, Node {

    private static String getImplementationClassNameAtIndex(final ConstantPool constantPool, final int index, final boolean compactClassName) {
        final String className = constantPool.getConstantString(index, Const.CONSTANT_Class);
        if (compactClassName) {
            return Utility.compactClassName(className, false);
        }
        return className;
    }
    private final int providesIndex; // points to CONSTANT_Class_info
    private final int providesWithCount;

    private final int[] providesWithIndex; // points to CONSTANT_Class_info

    /**
     * Constructs object from file stream.
     *
     * @param file Input stream
     * @throws IOException if an I/O Exception occurs in readUnsignedShort
     */
    ModuleProvides(final DataInput file) throws IOException {
        providesIndex = file.readUnsignedShort();
        providesWithCount = file.readUnsignedShort();
        providesWithIndex = new int[providesWithCount];
        for (int i = 0; i < providesWithCount; i++) {
            providesWithIndex[i] = file.readUnsignedShort();
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
        v.visitModuleProvides(this);
    }

    /**
     * @return deep copy of this object
     */
    public ModuleProvides copy() {
        try {
            return (ModuleProvides) clone();
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
        file.writeShort(providesIndex);
        file.writeShort(providesWithCount);
        for (final int entry : providesWithIndex) {
            file.writeShort(entry);
        }
    }

    /**
     * Gets the array of implementation class names for this ModuleProvides.
     * @param constantPool Array of constants usually obtained from the ClassFile object
     * @param compactClassName false for original constant pool value, true to replace '/' with '.'
     * @return array of implementation class names
     * @since 6.10.0
     */
    public String[] getImplementationClassNames(final ConstantPool constantPool, final boolean compactClassName) {
        final String[] implementationClassNames = new String[providesWithCount];
        for (int i = 0; i < providesWithCount; i++) {
            implementationClassNames[i] = getImplementationClassNameAtIndex(constantPool, providesWithIndex[i], compactClassName);
        }
        return implementationClassNames;
    }

    /**
     * Gets the interface name for this ModuleProvides.
     * @param constantPool Array of constants usually obtained from the ClassFile object
     * @return interface name
     * @since 6.10.0
     */
    public String getInterfaceName(final ConstantPool constantPool) {
        return constantPool.constantToString(providesIndex, Const.CONSTANT_Class);
    }

    /**
     * @return String representation
     */
    @Override
    public String toString() {
        return "provides(" + providesIndex + ", " + providesWithCount + ", ...)";
    }

    /**
     * @return Resolved string representation
     */
    public String toString(final ConstantPool constantPool) {
        final StringBuilder buf = new StringBuilder();
        final String interfaceName = getInterfaceName(constantPool);
        buf.append(interfaceName);
        buf.append(", with(").append(providesWithCount).append("):\n");
        for (final int index : providesWithIndex) {
            final String className = getImplementationClassNameAtIndex(constantPool, index, true);
            buf.append("      ").append(className).append("\n");
        }
        return buf.substring(0, buf.length() - 1); // remove the last newline
    }
}

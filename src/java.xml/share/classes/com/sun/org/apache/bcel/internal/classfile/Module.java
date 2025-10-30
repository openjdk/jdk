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
import java.util.Arrays;

import com.sun.org.apache.bcel.internal.Const;

/**
 * This class is derived from <em>Attribute</em> and represents the list of modules required, exported, opened or
 * provided by a module. There may be at most one Module attribute in a ClassFile structure.
 *
 * @see Attribute
 * @since 6.4.0
 */
public final class Module extends Attribute {

    /**
     * The module file name extension.
     *
     * @since 6.7.0
     */
    public static final String EXTENSION = ".jmod";

    private static String getClassNameAtIndex(final ConstantPool cp, final int index, final boolean compactClassName) {
        final String className = cp.getConstantString(index, Const.CONSTANT_Class);
        if (compactClassName) {
            return Utility.compactClassName(className, false);
        }
        return className;
    }
    private final int moduleNameIndex;
    private final int moduleFlags;

    private final int moduleVersionIndex;
    private ModuleRequires[] requiresTable;
    private ModuleExports[] exportsTable;
    private ModuleOpens[] opensTable;
    private final int usesCount;
    private final int[] usesIndex;

    private ModuleProvides[] providesTable;

    /**
     * Constructs object from input stream.
     *
     * @param nameIndex Index in constant pool
     * @param length Content length in bytes
     * @param input Input stream
     * @param constantPool Array of constants
     * @throws IOException if an I/O error occurs.
     */
    Module(final int nameIndex, final int length, final DataInput input, final ConstantPool constantPool) throws IOException {
        super(Const.ATTR_MODULE, nameIndex, length, constantPool);

        moduleNameIndex = input.readUnsignedShort();
        moduleFlags = input.readUnsignedShort();
        moduleVersionIndex = input.readUnsignedShort();

        final int requiresCount = input.readUnsignedShort();
        requiresTable = new ModuleRequires[requiresCount];
        for (int i = 0; i < requiresCount; i++) {
            requiresTable[i] = new ModuleRequires(input);
        }

        final int exportsCount = input.readUnsignedShort();
        exportsTable = new ModuleExports[exportsCount];
        for (int i = 0; i < exportsCount; i++) {
            exportsTable[i] = new ModuleExports(input);
        }

        final int opensCount = input.readUnsignedShort();
        opensTable = new ModuleOpens[opensCount];
        for (int i = 0; i < opensCount; i++) {
            opensTable[i] = new ModuleOpens(input);
        }

        usesCount = input.readUnsignedShort();
        usesIndex = new int[usesCount];
        for (int i = 0; i < usesCount; i++) {
            usesIndex[i] = input.readUnsignedShort();
        }

        final int providesCount = input.readUnsignedShort();
        providesTable = new ModuleProvides[providesCount];
        for (int i = 0; i < providesCount; i++) {
            providesTable[i] = new ModuleProvides(input);
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
        v.visitModule(this);
    }

    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy(final ConstantPool constantPool) {
        final Module c = (Module) clone();

        c.requiresTable = new ModuleRequires[requiresTable.length];
        Arrays.setAll(c.requiresTable, i -> requiresTable[i].copy());

        c.exportsTable = new ModuleExports[exportsTable.length];
        Arrays.setAll(c.exportsTable, i -> exportsTable[i].copy());

        c.opensTable = new ModuleOpens[opensTable.length];
        Arrays.setAll(c.opensTable, i -> opensTable[i].copy());

        c.providesTable = new ModuleProvides[providesTable.length];
        Arrays.setAll(c.providesTable, i -> providesTable[i].copy());

        c.setConstantPool(constantPool);
        return c;
    }

    /**
     * Dump Module attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void dump(final DataOutputStream file) throws IOException {
        super.dump(file);

        file.writeShort(moduleNameIndex);
        file.writeShort(moduleFlags);
        file.writeShort(moduleVersionIndex);

        file.writeShort(requiresTable.length);
        for (final ModuleRequires entry : requiresTable) {
            entry.dump(file);
        }

        file.writeShort(exportsTable.length);
        for (final ModuleExports entry : exportsTable) {
            entry.dump(file);
        }

        file.writeShort(opensTable.length);
        for (final ModuleOpens entry : opensTable) {
            entry.dump(file);
        }

        file.writeShort(usesIndex.length);
        for (final int entry : usesIndex) {
            file.writeShort(entry);
        }

        file.writeShort(providesTable.length);
        for (final ModuleProvides entry : providesTable) {
            entry.dump(file);
        }
    }

    /**
     * @return table of exported interfaces
     * @see ModuleExports
     */
    public ModuleExports[] getExportsTable() {
        return exportsTable;
    }

    /**
     * Gets flags for this module.
     * @return module flags
     * @since 6.10.0
     */
    public int getModuleFlags() {
        return moduleFlags;
    }

    /**
     * Gets module name.
     * @param cp Array of constants
     * @return module name
     * @since 6.10.0
     */
    public String getModuleName(final ConstantPool cp) {
        return cp.getConstantString(moduleNameIndex, Const.CONSTANT_Module);
    }

    /**
     * @return table of provided interfaces
     * @see ModuleOpens
     */
    public ModuleOpens[] getOpensTable() {
        return opensTable;
    }

    /**
     * @return table of provided interfaces
     * @see ModuleProvides
     */
    public ModuleProvides[] getProvidesTable() {
        return providesTable;
    }

    /**
     * @return table of required modules
     * @see ModuleRequires
     */
    public ModuleRequires[] getRequiresTable() {
        return requiresTable;
    }

    /**
     * Gets the array of class names for this module's uses.
     * @param constantPool Array of constants usually obtained from the ClassFile object
     * @param compactClassName false for original constant pool value, true to replace '/' with '.'
     * @return array of used class names
     * @since 6.10.0
     */
    public String[] getUsedClassNames(final ConstantPool constantPool, final boolean compactClassName) {
        final String[] usedClassNames = new String[usesCount];
        for (int i = 0; i < usesCount; i++) {
            usedClassNames[i] = getClassNameAtIndex(constantPool, usesIndex[i], compactClassName);
        }
        return usedClassNames;
    }

    /**
     * Gets version for this module.
     * @param cp Array of constants
     * @return version from constant pool, "0" if version index is 0
     * @since 6.10.0
     */
    public String getVersion(final ConstantPool cp) {
        return moduleVersionIndex == 0 ? "0" : cp.getConstantString(moduleVersionIndex, Const.CONSTANT_Utf8);
    }

    /**
     * @return String representation, i.e., a list of packages.
     */
    @Override
    public String toString() {
        final ConstantPool cp = super.getConstantPool();
        final StringBuilder buf = new StringBuilder();
        buf.append("Module:\n");
        buf.append("  name:    ").append(Utility.pathToPackage(getModuleName(cp))).append("\n");
        buf.append("  flags:   ").append(String.format("%04x", moduleFlags)).append("\n");
        final String version = getVersion(cp);
        buf.append("  version: ").append(version).append("\n");

        buf.append("  requires(").append(requiresTable.length).append("):\n");
        for (final ModuleRequires module : requiresTable) {
            buf.append("    ").append(module.toString(cp)).append("\n");
        }

        buf.append("  exports(").append(exportsTable.length).append("):\n");
        for (final ModuleExports module : exportsTable) {
            buf.append("    ").append(module.toString(cp)).append("\n");
        }

        buf.append("  opens(").append(opensTable.length).append("):\n");
        for (final ModuleOpens module : opensTable) {
            buf.append("    ").append(module.toString(cp)).append("\n");
        }

        buf.append("  uses(").append(usesIndex.length).append("):\n");
        for (final int index : usesIndex) {
            final String className = getClassNameAtIndex(cp, index, true);
            buf.append("    ").append(className).append("\n");
        }

        buf.append("  provides(").append(providesTable.length).append("):\n");
        for (final ModuleProvides module : providesTable) {
            buf.append("    ").append(module.toString(cp)).append("\n");
        }

        return buf.substring(0, buf.length() - 1); // remove the last newline
    }
}

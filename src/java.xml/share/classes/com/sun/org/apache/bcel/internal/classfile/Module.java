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
 * This class is derived from <em>Attribute</em> and represents the list of modules required, exported, opened or provided by a module.
 * There may be at most one Module attribute in a ClassFile structure.
 *
 * @see   Attribute
 * @since 6.4.0
 */
public final class Module extends Attribute {

    private final int module_name_index;
    private final int module_flags;
    private final int module_version_index;

    private ModuleRequires[] requires_table;
    private ModuleExports[] exports_table;
    private ModuleOpens[] opens_table;
    private final int uses_count;
    private final int[] uses_index;
    private ModuleProvides[] provides_table;

    /**
     * Construct object from input stream.
     * @param name_index Index in constant pool
     * @param length Content length in bytes
     * @param input Input stream
     * @param constant_pool Array of constants
     * @throws IOException
     */
    Module(final int name_index, final int length, final DataInput input, final ConstantPool constant_pool) throws IOException {
        super(Const.ATTR_MODULE, name_index, length, constant_pool);

        module_name_index = input.readUnsignedShort();
        module_flags = input.readUnsignedShort();
        module_version_index = input.readUnsignedShort();

        final int requires_count = input.readUnsignedShort();
        requires_table = new ModuleRequires[requires_count];
        for (int i = 0; i < requires_count; i++) {
            requires_table[i] = new ModuleRequires(input);
        }

        final int exports_count = input.readUnsignedShort();
        exports_table = new ModuleExports[exports_count];
        for (int i = 0; i < exports_count; i++) {
            exports_table[i] = new ModuleExports(input);
        }

        final int opens_count = input.readUnsignedShort();
        opens_table = new ModuleOpens[opens_count];
        for (int i = 0; i < opens_count; i++) {
            opens_table[i] = new ModuleOpens(input);
        }

        uses_count = input.readUnsignedShort();
        uses_index = new int[uses_count];
        for (int i = 0; i < uses_count; i++) {
            uses_index[i] = input.readUnsignedShort();
        }

        final int provides_count = input.readUnsignedShort();
        provides_table = new ModuleProvides[provides_count];
        for (int i = 0; i < provides_count; i++) {
            provides_table[i] = new ModuleProvides(input);
        }
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
        v.visitModule(this);
    }

    // TODO add more getters and setters?

    /**
     * @return table of required modules
     * @see ModuleRequires
     */
    public ModuleRequires[] getRequiresTable() {
        return requires_table;
    }


    /**
     * @return table of exported interfaces
     * @see ModuleExports
     */
    public ModuleExports[] getExportsTable() {
        return exports_table;
    }


    /**
     * @return table of provided interfaces
     * @see ModuleOpens
     */
    public ModuleOpens[] getOpensTable() {
        return opens_table;
    }


    /**
     * @return table of provided interfaces
     * @see ModuleProvides
     */
    public ModuleProvides[] getProvidesTable() {
        return provides_table;
    }


    /**
     * Dump Module attribute to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException
     */
    @Override
    public void dump( final DataOutputStream file ) throws IOException {
        super.dump(file);

        file.writeShort(module_name_index);
        file.writeShort(module_flags);
        file.writeShort(module_version_index);

        file.writeShort(requires_table.length);
        for (final ModuleRequires entry : requires_table) {
            entry.dump(file);
        }

        file.writeShort(exports_table.length);
        for (final ModuleExports entry : exports_table) {
            entry.dump(file);
        }

        file.writeShort(opens_table.length);
        for (final ModuleOpens entry : opens_table) {
            entry.dump(file);
        }

        file.writeShort(uses_index.length);
        for (final int entry : uses_index) {
            file.writeShort(entry);
        }

        file.writeShort(provides_table.length);
        for (final ModuleProvides entry : provides_table) {
            entry.dump(file);
        }
    }


    /**
     * @return String representation, i.e., a list of packages.
     */
    @Override
    public String toString() {
        final ConstantPool cp = super.getConstantPool();
        final StringBuilder buf = new StringBuilder();
        buf.append("Module:\n");
        buf.append("  name:    ") .append(cp.getConstantString(module_name_index, Const.CONSTANT_Module).replace('/', '.')).append("\n");
        buf.append("  flags:   ") .append(String.format("%04x", module_flags)).append("\n");
        final String version = module_version_index == 0 ? "0" : cp.getConstantString(module_version_index, Const.CONSTANT_Utf8);
        buf.append("  version: ") .append(version).append("\n");

        buf.append("  requires(").append(requires_table.length).append("):\n");
        for (final ModuleRequires module : requires_table) {
            buf.append("    ").append(module.toString(cp)).append("\n");
        }

        buf.append("  exports(").append(exports_table.length).append("):\n");
        for (final ModuleExports module : exports_table) {
            buf.append("    ").append(module.toString(cp)).append("\n");
        }

        buf.append("  opens(").append(opens_table.length).append("):\n");
        for (final ModuleOpens module : opens_table) {
            buf.append("    ").append(module.toString(cp)).append("\n");
        }

        buf.append("  uses(").append(uses_index.length).append("):\n");
        for (final int index : uses_index) {
            final String class_name = cp.getConstantString(index, Const.CONSTANT_Class);
            buf.append("    ").append(Utility.compactClassName(class_name, false)).append("\n");
        }

        buf.append("  provides(").append(provides_table.length).append("):\n");
        for (final ModuleProvides module : provides_table) {
            buf.append("    ").append(module.toString(cp)).append("\n");
        }

        return buf.substring(0, buf.length()-1); // remove the last newline
    }


    /**
     * @return deep copy of this attribute
     */
    @Override
    public Attribute copy( final ConstantPool _constant_pool ) {
        final Module c = (Module) clone();

        c.requires_table = new ModuleRequires[requires_table.length];
        for (int i = 0; i < requires_table.length; i++) {
            c.requires_table[i] = requires_table[i].copy();
        }

        c.exports_table = new ModuleExports[exports_table.length];
        for (int i = 0; i < exports_table.length; i++) {
            c.exports_table[i] = exports_table[i].copy();
        }

        c.opens_table = new ModuleOpens[opens_table.length];
        for (int i = 0; i < opens_table.length; i++) {
            c.opens_table[i] = opens_table[i].copy();
        }

        c.provides_table = new ModuleProvides[provides_table.length];
        for (int i = 0; i < provides_table.length; i++) {
            c.provides_table[i] = provides_table[i].copy();
        }

        c.setConstantPool(_constant_pool);
        return c;
    }
}

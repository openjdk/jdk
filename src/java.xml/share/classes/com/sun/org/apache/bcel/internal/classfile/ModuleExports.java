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
 * This class represents an entry in the exports table of the Module attribute.
 * Each entry describes a package which may open the parent module.
 *
 * @see   Module
 * @since 6.4.0
 */
public final class ModuleExports implements Cloneable, Node {

    private final int exports_index;  // points to CONSTANT_Package_info
    private final int exports_flags;
    private final int exports_to_count;
    private final int[] exports_to_index;  // points to CONSTANT_Module_info


    /**
     * Construct object from file stream.
     *
     * @param file Input stream
     * @throws IOException if an I/O Exception occurs in readUnsignedShort
     */
    ModuleExports(final DataInput file) throws IOException {
        exports_index = file.readUnsignedShort();
        exports_flags = file.readUnsignedShort();
        exports_to_count = file.readUnsignedShort();
        exports_to_index = new int[exports_to_count];
        for (int i = 0; i < exports_to_count; i++) {
            exports_to_index[i] = file.readUnsignedShort();
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
        v.visitModuleExports(this);
    }

    // TODO add more getters and setters?

    /**
     * Dump table entry to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O Exception occurs in writeShort
     */
    public void dump( final DataOutputStream file ) throws IOException {
        file.writeShort(exports_index);
        file.writeShort(exports_flags);
        file.writeShort(exports_to_count);
        for (final int entry : exports_to_index) {
            file.writeShort(entry);
        }
    }


    /**
     * @return String representation
     */
    @Override
    public String toString() {
        return "exports(" + exports_index + ", " + exports_flags + ", " + exports_to_count + ", ...)";
    }


    /**
     * @return Resolved string representation
     */
    public String toString( final ConstantPool constant_pool ) {
        final StringBuilder buf = new StringBuilder();
        final String package_name = constant_pool.constantToString(exports_index, Const.CONSTANT_Package);
        buf.append(Utility.compactClassName(package_name, false));
        buf.append(", ").append(String.format("%04x", exports_flags));
        buf.append(", to(").append(exports_to_count).append("):\n");
        for (final int index : exports_to_index) {
            final String module_name = constant_pool.getConstantString(index, Const.CONSTANT_Module);
            buf.append("      ").append(Utility.compactClassName(module_name, false)).append("\n");
        }
        return buf.substring(0, buf.length()-1); // remove the last newline
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
}

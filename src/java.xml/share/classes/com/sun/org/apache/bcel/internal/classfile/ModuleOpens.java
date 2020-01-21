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
 * This class represents an entry in the opens table of the Module attribute.
 * Each entry describes a package which the parent module opens.
 *
 * @see   Module
 * @since 6.4.0
 */
public final class ModuleOpens implements Cloneable, Node {

    private final int opens_index;  // points to CONSTANT_Package_info
    private final int opens_flags;
    private final int opens_to_count;
    private final int[] opens_to_index;  // points to CONSTANT_Module_info


    /**
     * Construct object from file stream.
     *
     * @param file Input stream
     * @throws IOException if an I/O Exception occurs in readUnsignedShort
     */
    ModuleOpens(final DataInput file) throws IOException {
        opens_index = file.readUnsignedShort();
        opens_flags = file.readUnsignedShort();
        opens_to_count = file.readUnsignedShort();
        opens_to_index = new int[opens_to_count];
        for (int i = 0; i < opens_to_count; i++) {
            opens_to_index[i] = file.readUnsignedShort();
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
        v.visitModuleOpens(this);
    }

    // TODO add more getters and setters?

    /**
     * Dump table entry to file stream in binary format.
     *
     * @param file Output file stream
     * @throws IOException if an I/O Exception occurs in writeShort
     */
    public void dump( final DataOutputStream file ) throws IOException {
        file.writeShort(opens_index);
        file.writeShort(opens_flags);
        file.writeShort(opens_to_count);
        for (final int entry : opens_to_index) {
            file.writeShort(entry);
        }
    }


    /**
     * @return String representation
     */
    @Override
    public String toString() {
        return "opens(" + opens_index + ", " + opens_flags + ", " + opens_to_count + ", ...)";
    }


    /**
     * @return Resolved string representation
     */
    public String toString( final ConstantPool constant_pool ) {
        final StringBuilder buf = new StringBuilder();
        final String package_name = constant_pool.constantToString(opens_index, Const.CONSTANT_Package);
        buf.append(Utility.compactClassName(package_name, false));
        buf.append(", ").append(String.format("%04x", opens_flags));
        buf.append(", to(").append(opens_to_count).append("):\n");
        for (final int index : opens_to_index) {
            final String module_name = constant_pool.getConstantString(index, Const.CONSTANT_Module);
            buf.append("      ").append(Utility.compactClassName(module_name, false)).append("\n");
        }
        return buf.substring(0, buf.length()-1); // remove the last newline
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
}

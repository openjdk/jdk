/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.classfile;

import java.io.IOException;

/**
 * See JVMS3, section 4.8.14.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class LocalVariableTypeTable_attribute extends Attribute {
    LocalVariableTypeTable_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        local_variable_table_length = cr.readUnsignedShort();
        local_variable_table = new Entry[local_variable_table_length];
        for (int i = 0; i < local_variable_table_length; i++)
            local_variable_table[i] = new Entry(cr);
    }

    public LocalVariableTypeTable_attribute(ConstantPool constant_pool, Entry[] local_variable_table)
            throws ConstantPoolException {
        this(constant_pool.getUTF8Index(Attribute.LocalVariableTypeTable), local_variable_table);
    }

    public LocalVariableTypeTable_attribute(int name_index, Entry[] local_variable_table) {
        super(name_index, 2 + local_variable_table.length * Entry.length());
        this.local_variable_table_length = local_variable_table.length;
        this.local_variable_table = local_variable_table;
    }

    public <R, D> R accept(Visitor<R, D> visitor, D data) {
        return visitor.visitLocalVariableTypeTable(this, data);
    }

    public final int local_variable_table_length;
    public final Entry[] local_variable_table;

    public static class Entry {
        Entry(ClassReader cr) throws IOException {
            start_pc = cr.readUnsignedShort();
            length = cr.readUnsignedShort();
            name_index = cr.readUnsignedShort();
            signature_index = cr.readUnsignedShort();
            index = cr.readUnsignedShort();
        }

        public static int length() {
            return 10;
        }

        public final int start_pc;
        public final int length;
        public final int name_index;
        public final int signature_index;
        public final int index;
    }
}

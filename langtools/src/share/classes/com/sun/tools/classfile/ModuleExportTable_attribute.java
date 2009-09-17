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
 * See JSR 277.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class ModuleExportTable_attribute extends Attribute {
    ModuleExportTable_attribute(ClassReader cr, int name_index, int length) throws IOException {
        super(name_index, length);
        int export_type_length = cr.readUnsignedShort();
        export_type_table = new int[export_type_length];
        for (int i = 0; i < export_type_table.length; i++)
            export_type_table[i] = cr.readUnsignedShort();
    }

    public ModuleExportTable_attribute(ConstantPool cp, int[] export_type_table)
            throws ConstantPoolException {
        this(cp.getUTF8Index(Attribute.ModuleExportTable), export_type_table);
    }

    public ModuleExportTable_attribute(int name_index, int[] export_type_table) {
        super(name_index, 2 + 2 * export_type_table.length);
        this.export_type_table = export_type_table;
    }

    public int getExportTypeCount() {
        return export_type_table.length;
    }

    public String getExportTypeName(int index, ConstantPool constant_pool) throws ConstantPoolException {
        return constant_pool.getUTF8Value(export_type_table[index]);
    }

    public <R, P> R accept(Visitor<R, P> visitor, P p) {
        return visitor.visitModuleExportTable(this, p);
    }

    public final int[] export_type_table;
}

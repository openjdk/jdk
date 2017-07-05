/*
 * Copyright 1994-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.tools.asm;

import sun.tools.java.*;
import java.io.IOException;
import java.io.DataOutputStream;

/**
 * This is a field constant pool data item
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
final
class FieldConstantData extends ConstantPoolData {
    MemberDefinition field;
    NameAndTypeData nt;

    /**
     * Constructor
     */
    FieldConstantData(ConstantPool tab, MemberDefinition field) {
        this.field = field;
        nt = new NameAndTypeData(field);
        tab.put(field.getClassDeclaration());
        tab.put(nt);
    }

    /**
     * Write the constant to the output stream
     */
    void write(Environment env, DataOutputStream out, ConstantPool tab) throws IOException {
        if (field.isMethod()) {
            if (field.getClassDefinition().isInterface()) {
                out.writeByte(CONSTANT_INTERFACEMETHOD);
            } else {
                out.writeByte(CONSTANT_METHOD);
            }
        } else {
            out.writeByte(CONSTANT_FIELD);
        }
        out.writeShort(tab.index(field.getClassDeclaration()));
        out.writeShort(tab.index(nt));
    }

    /**
     * Return the order of the constant
     */
    int order() {
        return 2;
    }
}

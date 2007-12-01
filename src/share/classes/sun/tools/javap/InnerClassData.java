/*
 * Copyright 2002 Sun Microsystems, Inc.  All Rights Reserved.
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


package sun.tools.javap;

import java.io.*;
import java.util.*;

/**
 * Strores InnerClass data informastion.
 *
 * @author  Sucheta Dambalkar (Adopted code from jdis)
 */
class InnerClassData  implements RuntimeConstants {
    ClassData cls;


    int inner_class_info_index
        ,outer_class_info_index
        ,inner_name_index
        ,access
        ;

    public InnerClassData(ClassData cls) {
        this.cls=cls;

    }

    /**
     * Read Innerclass attribute data.
     */
    public void read(DataInputStream in) throws IOException {
        inner_class_info_index = in.readUnsignedShort();
        outer_class_info_index = in.readUnsignedShort();
        inner_name_index = in.readUnsignedShort();
        access = in.readUnsignedShort();
    }  // end read

    /**
     * Returns the access of this class or interface.
     */
    public String[] getAccess(){
        Vector v = new Vector();
        if ((access & ACC_PUBLIC)   !=0) v.addElement("public");
        if ((access & ACC_FINAL)    !=0) v.addElement("final");
        if ((access & ACC_ABSTRACT) !=0) v.addElement("abstract");
        String[] accflags = new String[v.size()];
        v.copyInto(accflags);
        return accflags;
    }

} // end InnerClassData

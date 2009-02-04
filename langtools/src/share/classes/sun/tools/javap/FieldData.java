/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.*;
import java.io.*;

/**
 * Strores field data informastion.
 *
 * @author  Sucheta Dambalkar (Adopted code from jdis)
 */

public class FieldData implements RuntimeConstants  {

    ClassData cls;
    int access;
    int name_index;
    int descriptor_index;
    int attributes_count;
    int value_cpx=0;
    boolean isSynthetic=false;
    boolean isDeprecated=false;
    Vector<AttrData> attrs;

    public FieldData(ClassData cls){
        this.cls=cls;
    }

    /**
     * Read and store field info.
     */
    public void read(DataInputStream in) throws IOException {
        access = in.readUnsignedShort();
        name_index = in.readUnsignedShort();
        descriptor_index = in.readUnsignedShort();
        // Read the attributes
        int attributes_count = in.readUnsignedShort();
        attrs=new Vector<AttrData>(attributes_count);
        for (int i = 0; i < attributes_count; i++) {
            int attr_name_index=in.readUnsignedShort();
            if (cls.getTag(attr_name_index)!=CONSTANT_UTF8) continue;
            String attr_name=cls.getString(attr_name_index);
            if (attr_name.equals("ConstantValue")){
                if (in.readInt()!=2)
                    throw new ClassFormatError("invalid ConstantValue attr length");
                value_cpx=in.readUnsignedShort();
                AttrData attr=new AttrData(cls);
                attr.read(attr_name_index);
                attrs.addElement(attr);
            } else if (attr_name.equals("Synthetic")){
                if (in.readInt()!=0)
                    throw new ClassFormatError("invalid Synthetic attr length");
                isSynthetic=true;
                AttrData attr=new AttrData(cls);
                attr.read(attr_name_index);
                attrs.addElement(attr);
            } else if (attr_name.equals("Deprecated")){
                if (in.readInt()!=0)
                    throw new ClassFormatError("invalid Synthetic attr length");
                isDeprecated = true;
                AttrData attr=new AttrData(cls);
                attr.read(attr_name_index);
                attrs.addElement(attr);
            } else {
                AttrData attr=new AttrData(cls);
                attr.read(attr_name_index, in);
                attrs.addElement(attr);
            }
        }

    }  // end read

    /**
     * Returns access of a field.
     */
    public String[] getAccess(){
        Vector<String> v = new Vector<String>();
        if ((access & ACC_PUBLIC)   !=0) v.addElement("public");
        if ((access & ACC_PRIVATE)   !=0) v.addElement("private");
        if ((access & ACC_PROTECTED)   !=0) v.addElement("protected");
        if ((access & ACC_STATIC)   !=0) v.addElement("static");
        if ((access & ACC_FINAL)    !=0) v.addElement("final");
        if ((access & ACC_VOLATILE) !=0) v.addElement("volatile");
        if ((access & ACC_TRANSIENT) !=0) v.addElement("transient");
        String[] accflags = new String[v.size()];
        v.copyInto(accflags);
        return accflags;
    }

    /**
     * Returns name of a field.
     */
    public String getName(){
        return cls.getStringValue(name_index);
    }

    /**
     * Returns internal signature of a field
     */
    public String getInternalSig(){
        return cls.getStringValue(descriptor_index);
    }

    /**
     * Returns java type signature of a field.
     */
    public String getType(){
        return new TypeSignature(getInternalSig()).getFieldType();
    }

    /**
     * Returns true if field is synthetic.
     */
    public boolean isSynthetic(){
        return isSynthetic;
    }

    /**
     * Returns true if field is deprecated.
     */
    public boolean isDeprecated(){
        return isDeprecated;
    }

    /**
     * Returns index of constant value in cpool.
     */
    public int getConstantValueIndex(){
        return (value_cpx);
    }

    /**
     * Returns list of attributes of field.
     */
    public Vector<?> getAttributes(){
        return attrs;
    }
}

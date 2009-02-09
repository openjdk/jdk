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

import static sun.tools.javap.RuntimeConstants.*;

/**
 * Strores method data informastion.
 *
 * @author  Sucheta Dambalkar (Adopted code from jdis)
 */
public class MethodData {

    ClassData cls;
    int access;
    int name_index;
    int descriptor_index;
    int attributes_count;
    byte[] code;
    Vector<TrapData> exception_table = new Vector<TrapData>(0);
    Vector<LineNumData> lin_num_tb = new Vector<LineNumData>(0);
    Vector<LocVarData> loc_var_tb = new Vector<LocVarData>(0);
    StackMapTableData[] stackMapTable;
    StackMapData[] stackMap;
    int[] exc_index_table=null;
    Vector<AttrData> attrs=new Vector<AttrData>(0);
    Vector<AttrData> code_attrs=new Vector<AttrData>(0);
    int max_stack,  max_locals;
    boolean isSynthetic=false;
    boolean isDeprecated=false;

    public MethodData(ClassData cls){
        this.cls=cls;
    }

    /**
     * Read method info.
     */
    public void read(DataInputStream in) throws IOException {
        access = in.readUnsignedShort();
        name_index=in.readUnsignedShort();
        descriptor_index =in.readUnsignedShort();
        int attributes_count = in.readUnsignedShort();
        for (int i = 0; i < attributes_count; i++) {
            int attr_name_index=in.readUnsignedShort();

        readAttr: {
                if (cls.getTag(attr_name_index)==CONSTANT_UTF8) {
                    String  attr_name=cls.getString(attr_name_index);
                    if ( attr_name.equals("Code")){
                        readCode (in);
                        AttrData attr=new AttrData(cls);
                        attr.read(attr_name_index);
                        attrs.addElement(attr);
                        break readAttr;
                    } else if ( attr_name.equals("Exceptions")){
                        readExceptions(in);
                        AttrData attr=new AttrData(cls);
                        attr.read(attr_name_index);
                        attrs.addElement(attr);
                        break readAttr;
                    } else if (attr_name.equals("Synthetic")){
                        if (in.readInt()!=0)
                            throw new ClassFormatError("invalid Synthetic attr length");
                        isSynthetic=true;
                        AttrData attr=new AttrData(cls);
                        attr.read(attr_name_index);
                        attrs.addElement(attr);
                        break readAttr;
                    } else if (attr_name.equals("Deprecated")){
                        if (in.readInt()!=0)
                            throw new ClassFormatError("invalid Synthetic attr length");
                        isDeprecated = true;
                        AttrData attr=new AttrData(cls);
                        attr.read(attr_name_index);
                        attrs.addElement(attr);
                        break readAttr;
                    }
                }
                AttrData attr=new AttrData(cls);
                attr.read(attr_name_index, in);
                attrs.addElement(attr);
            }
        }
    }

    /**
     * Read code attribute info.
     */
    public void readCode(DataInputStream in) throws IOException {

        int attr_length = in.readInt();
        max_stack=in.readUnsignedShort();
        max_locals=in.readUnsignedShort();
        int codelen=in.readInt();

        code=new byte[codelen];
        int totalread = 0;
        while(totalread < codelen){
            totalread += in.read(code, totalread, codelen-totalread);
        }
        //      in.read(code, 0, codelen);
        int clen = 0;
        readExceptionTable(in);
        int code_attributes_count = in.readUnsignedShort();

        for (int k = 0 ; k < code_attributes_count ; k++) {
            int table_name_index=in.readUnsignedShort();
            int table_name_tag=cls.getTag(table_name_index);
            AttrData attr=new AttrData(cls);
            if (table_name_tag==CONSTANT_UTF8) {
                String table_name_tstr=cls.getString(table_name_index);
                if (table_name_tstr.equals("LineNumberTable")) {
                    readLineNumTable(in);
                    attr.read(table_name_index);
                } else if (table_name_tstr.equals("LocalVariableTable")) {
                    readLocVarTable(in);
                    attr.read(table_name_index);
                } else if (table_name_tstr.equals("StackMapTable")) {
                    readStackMapTable(in);
                    attr.read(table_name_index);
                } else if (table_name_tstr.equals("StackMap")) {
                    readStackMap(in);
                    attr.read(table_name_index);
                } else {
                    attr.read(table_name_index, in);
                }
                code_attrs.addElement(attr);
                continue;
            }

            attr.read(table_name_index, in);
            code_attrs.addElement(attr);
        }
    }

    /**
     * Read exception table info.
     */
    void readExceptionTable (DataInputStream in) throws IOException {
        int exception_table_len=in.readUnsignedShort();
        exception_table=new Vector<TrapData>(exception_table_len);
        for (int l = 0; l < exception_table_len; l++) {
            exception_table.addElement(new TrapData(in, l));
        }
    }

    /**
     * Read LineNumberTable attribute info.
     */
    void readLineNumTable (DataInputStream in) throws IOException {
        int attr_len = in.readInt(); // attr_length
        int lin_num_tb_len = in.readUnsignedShort();
        lin_num_tb=new Vector<LineNumData>(lin_num_tb_len);
        for (int l = 0; l < lin_num_tb_len; l++) {
            lin_num_tb.addElement(new LineNumData(in));
        }
    }

    /**
     * Read LocalVariableTable attribute info.
     */
    void readLocVarTable (DataInputStream in) throws IOException {
        int attr_len=in.readInt(); // attr_length
        int loc_var_tb_len = in.readUnsignedShort();
        loc_var_tb = new Vector<LocVarData>(loc_var_tb_len);
        for (int l = 0; l < loc_var_tb_len; l++) {
            loc_var_tb.addElement(new LocVarData(in));
        }
    }

    /**
     * Read Exception attribute info.
     */
    public void readExceptions(DataInputStream in) throws IOException {
        int attr_len=in.readInt(); // attr_length in prog
        int num_exceptions = in.readUnsignedShort();
        exc_index_table=new int[num_exceptions];
        for (int l = 0; l < num_exceptions; l++) {
            int exc=in.readShort();
            exc_index_table[l]=exc;
        }
    }

    /**
     * Read StackMapTable attribute info.
     */
    void readStackMapTable(DataInputStream in) throws IOException {
        int attr_len = in.readInt();  //attr_length
        int stack_map_tb_len = in.readUnsignedShort();
        stackMapTable = new StackMapTableData[stack_map_tb_len];
        for (int i=0; i<stack_map_tb_len; i++) {
            stackMapTable[i] = StackMapTableData.getInstance(in, this);
        }
    }

    /**
     * Read StackMap attribute info.
     */
    void readStackMap(DataInputStream in) throws IOException {
        int attr_len = in.readInt();  //attr_length
        int stack_map_len = in.readUnsignedShort();
        stackMap = new StackMapData[stack_map_len];
        for (int i = 0; i<stack_map_len; i++) {
            stackMap[i] = new StackMapData(in, this);
        }
    }

    /**
     * Return access of the method.
     */
    public String[] getAccess(){

        Vector<String> v = new Vector<String>();
        if ((access & ACC_PUBLIC)   !=0) v.addElement("public");
        if ((access & ACC_PRIVATE)   !=0) v.addElement("private");
        if ((access & ACC_PROTECTED)   !=0) v.addElement("protected");
        if ((access & ACC_STATIC)   !=0) v.addElement("static");
        if ((access & ACC_FINAL)    !=0) v.addElement("final");
        if ((access & ACC_SYNCHRONIZED) !=0) v.addElement("synchronized");
        if ((access & ACC_NATIVE) !=0) v.addElement("native");
        if ((access & ACC_ABSTRACT) !=0) v.addElement("abstract");
        if ((access & ACC_STRICT) !=0) v.addElement("strictfp");

        String[] accflags = new String[v.size()];
        v.copyInto(accflags);
        return accflags;
    }

    /**
     * Return name of the method.
     */
    public String getName(){
        return cls.getStringValue(name_index);
    }

    /**
     * Return internal siganature of the method.
     */
    public String getInternalSig(){
        return cls.getStringValue(descriptor_index);
    }

    /**
     * Return java return type signature of method.
     */
    public String getReturnType(){

        String rttype = (new TypeSignature(getInternalSig())).getReturnType();
        return rttype;
    }

    /**
     * Return java type parameter signature.
     */
    public String getParameters(){
        String ptype = (new TypeSignature(getInternalSig())).getParameters();

        return ptype;
    }

    /**
     * Return code attribute data of a method.
     */
    public byte[] getCode(){
        return code;
    }

    /**
     * Return LineNumberTable size.
     */
    public int getnumlines(){
        return lin_num_tb.size();
    }

    /**
     * Return LineNumberTable
     */
    public Vector<?> getlin_num_tb(){
        return lin_num_tb;
    }

    /**
     * Return LocalVariableTable size.
     */
    public int getloc_var_tbsize(){
        return loc_var_tb.size();
    }


    /**
     * Return LocalVariableTable.
     */
    public Vector<?> getloc_var_tb(){
        return loc_var_tb;
    }

    /**
     * Return StackMap.
     */
    public StackMapData[] getStackMap() {
        return stackMap;
    }

    /**
     * Return StackMapTable.
     */
    public StackMapTableData[] getStackMapTable() {
        return stackMapTable;
    }

    /**
     * Return number of arguments of that method.
     */
    public int getArgumentlength(){
        return new TypeSignature(getInternalSig()).getArgumentlength();
    }

    /**
     * Return true if method is static
     */
    public boolean isStatic(){
        if ((access & ACC_STATIC)   !=0) return true;
        return false;
    }


    /**
     * Return max depth of operand stack.
     */
    public int getMaxStack(){
        return  max_stack;
    }


    /**
     * Return number of local variables.
     */
    public int getMaxLocals(){
        return max_locals;
    }


    /**
     * Return exception index table in Exception attribute.
     */
    public int []get_exc_index_table(){
        return  exc_index_table;
    }


    /**
     * Return exception table in code attributre.
     */
    public Vector<?> getexception_table(){
        return exception_table;
    }


    /**
     * Return method attributes.
     */
    public Vector<?> getAttributes(){
        return attrs;
    }


    /**
     * Return code attributes.
     */
    public Vector<?> getCodeAttributes(){
        return code_attrs;
    }


    /**
     * Return true if method id synthetic.
     */
    public boolean isSynthetic(){
        return isSynthetic;
    }


    /**
     * Return true if method is deprecated.
     */
    public boolean isDeprecated(){
        return isDeprecated;
    }
}

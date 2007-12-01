/*
 * Copyright 2002-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Central data repository of the Java Disassembler.
 * Stores all the information in java class file.
 *
 * @author  Sucheta Dambalkar (Adopted code from jdis)
 */
public class ClassData implements RuntimeConstants {

    private int magic;
    private int minor_version;
    private int major_version;
    private int cpool_count;
    private Object cpool[];
    private int access;
    private int this_class = 0;;
    private int super_class;
    private int interfaces_count;
    private int[] interfaces = new int[0];;
    private int fields_count;
    private FieldData[] fields;
    private int methods_count;
    private MethodData[] methods;
    private InnerClassData[] innerClasses;
    private int attributes_count;
    private AttrData[] attrs;
    private String classname;
    private String superclassname;
    private int source_cpx=0;
    private byte tags[];
    private Hashtable indexHashAscii = new Hashtable();
    private String pkgPrefix="";
    private int pkgPrefixLen=0;

    /**
     * Read classfile to disassemble.
     */
    public ClassData(InputStream infile){
        try{
            this.read(new DataInputStream(infile));
        }catch (FileNotFoundException ee) {
            error("cant read file");
        }catch (Error ee) {
            ee.printStackTrace();
            error("fatal error");
        } catch (Exception ee) {
            ee.printStackTrace();
            error("fatal exception");
        }
    }

    /**
     * Reads and stores class file information.
     */
    public void read(DataInputStream in) throws IOException {
        // Read the header
        magic = in.readInt();
        if (magic != JAVA_MAGIC) {
            throw new ClassFormatError("wrong magic: " +
                                       toHex(magic) + ", expected " +
                                       toHex(JAVA_MAGIC));
        }
        minor_version = in.readShort();
        major_version = in.readShort();
        if (major_version != JAVA_VERSION) {
        }

        // Read the constant pool
        readCP(in);
        access = in.readUnsignedShort();
        this_class = in.readUnsignedShort();
        super_class = in.readUnsignedShort();

        //Read interfaces.
        interfaces_count = in.readUnsignedShort();
        if(interfaces_count > 0){
            interfaces = new int[interfaces_count];
        }
        for (int i = 0; i < interfaces_count; i++) {
            interfaces[i]=in.readShort();
        }

        // Read the fields
        readFields(in);

        // Read the methods
        readMethods(in);

        // Read the attributes
        attributes_count = in.readUnsignedShort();
        attrs=new AttrData[attributes_count];
        for (int k = 0; k < attributes_count; k++) {
            int name_cpx=in.readUnsignedShort();
            if (getTag(name_cpx)==CONSTANT_UTF8
                && getString(name_cpx).equals("SourceFile")
                ){      if (in.readInt()!=2)
                    throw new ClassFormatError("invalid attr length");
                source_cpx=in.readUnsignedShort();
                AttrData attr=new AttrData(this);
                attr.read(name_cpx);
                attrs[k]=attr;

            } else if (getTag(name_cpx)==CONSTANT_UTF8
                       && getString(name_cpx).equals("InnerClasses")
                       ){       int length=in.readInt();
                       int num=in.readUnsignedShort();
                       if (2+num*8 != length)
                           throw new ClassFormatError("invalid attr length");
                       innerClasses=new InnerClassData[num];
                       for (int j = 0; j < num; j++) {
                           InnerClassData innerClass=new InnerClassData(this);
                           innerClass.read(in);
                           innerClasses[j]=innerClass;
                       }
                       AttrData attr=new AttrData(this);
                       attr.read(name_cpx);
                       attrs[k]=attr;
            } else {
                AttrData attr=new AttrData(this);
                attr.read(name_cpx, in);
                attrs[k]=attr;
            }
        }
        in.close();
    } // end ClassData.read()

    /**
     * Reads and stores constant pool info.
     */
    void readCP(DataInputStream in) throws IOException {
        cpool_count = in.readUnsignedShort();
        tags = new byte[cpool_count];
        cpool = new Object[cpool_count];
        for (int i = 1; i < cpool_count; i++) {
            byte tag = in.readByte();

            switch(tags[i] = tag) {
            case CONSTANT_UTF8:
                String str=in.readUTF();
                indexHashAscii.put(cpool[i] = str, new Integer(i));
                break;
            case CONSTANT_INTEGER:
                cpool[i] = new Integer(in.readInt());
                break;
            case CONSTANT_FLOAT:
                cpool[i] = new Float(in.readFloat());
                break;
            case CONSTANT_LONG:
                cpool[i++] = new Long(in.readLong());
                break;
            case CONSTANT_DOUBLE:
                cpool[i++] = new Double(in.readDouble());
                break;
            case CONSTANT_CLASS:
            case CONSTANT_STRING:
                cpool[i] = new CPX(in.readUnsignedShort());
                break;

            case CONSTANT_FIELD:
            case CONSTANT_METHOD:
            case CONSTANT_INTERFACEMETHOD:
            case CONSTANT_NAMEANDTYPE:
                cpool[i] = new CPX2(in.readUnsignedShort(), in.readUnsignedShort());
                break;

            case 0:
            default:
                throw new ClassFormatError("invalid constant type: " + (int)tags[i]);
            }
        }
    }

    /**
     * Reads and strores field info.
     */
    protected void readFields(DataInputStream in) throws IOException {
        int fields_count = in.readUnsignedShort();
        fields=new FieldData[fields_count];
        for (int k = 0; k < fields_count; k++) {
            FieldData field=new FieldData(this);
            field.read(in);
            fields[k]=field;
        }
    }

    /**
     * Reads and strores Method info.
     */
    protected void readMethods(DataInputStream in) throws IOException {
        int methods_count = in.readUnsignedShort();
        methods=new MethodData[methods_count];
        for (int k = 0; k < methods_count ; k++) {
            MethodData method=new MethodData(this);
            method.read(in);
            methods[k]=method;
        }
    }

    /**
     * get a string
     */
    public String getString(int n) {
        return (n == 0) ? null : (String)cpool[n];
    }

    /**
     * get the type of constant given an index
     */
    public byte getTag(int n) {
        try{
            return tags[n];
        } catch (ArrayIndexOutOfBoundsException e) {
            return (byte)100;
        }
    }

    static final String hexString="0123456789ABCDEF";

    public static char hexTable[]=hexString.toCharArray();

    static String toHex(long val, int width) {
        StringBuffer s = new StringBuffer();
        for (int i=width-1; i>=0; i--)
            s.append(hexTable[((int)(val>>(4*i)))&0xF]);
        return "0x"+s.toString();
    }

    static String toHex(long val) {
        int width;
        for (width=16; width>0; width--) {
            if ((val>>(width-1)*4)!=0) break;
        }
        return toHex(val, width);
    }

    static String toHex(int val) {
        int width;
        for (width=8; width>0; width--) {
            if ((val>>(width-1)*4)!=0) break;
        }
        return toHex(val, width);
    }

    public void error(String msg) {
        System.err.println("ERROR:" +msg);
    }

    /**
     * Returns the name of this class.
     */
    public String getClassName() {
        String res=null;
        if (this_class==0) {
            return res;
        }
        int tcpx;
        try {
            if (tags[this_class]!=CONSTANT_CLASS) {
                return res; //"<CP["+cpx+"] is not a Class> ";
            }
            tcpx=((CPX)cpool[this_class]).cpx;
        } catch (ArrayIndexOutOfBoundsException e) {
            return res; // "#"+cpx+"// invalid constant pool index";
        } catch (Throwable e) {
            return res; // "#"+cpx+"// ERROR IN DISASSEMBLER";
        }

        try {
            return (String)(cpool[tcpx]);
        } catch (ArrayIndexOutOfBoundsException e) {
            return  res; // "class #"+scpx+"// invalid constant pool index";
        } catch (ClassCastException e) {
            return  res; // "class #"+scpx+"// invalid constant pool reference";
        } catch (Throwable e) {
            return res; // "#"+cpx+"// ERROR IN DISASSEMBLER";
        }

    }

    /**
     * Returns the name of class at perticular index.
     */
    public String getClassName(int cpx) {
        String res="#"+cpx;
        if (cpx==0) {
            return res;
        }
        int scpx;
        try {
            if (tags[cpx]!=CONSTANT_CLASS) {
                return res; //"<CP["+cpx+"] is not a Class> ";
            }
            scpx=((CPX)cpool[cpx]).cpx;
        } catch (ArrayIndexOutOfBoundsException e) {
            return res; // "#"+cpx+"// invalid constant pool index";
        } catch (Throwable e) {
            return res; // "#"+cpx+"// ERROR IN DISASSEMBLER";
        }
        res="#"+scpx;
        try {
            return (String)(cpool[scpx]);
        } catch (ArrayIndexOutOfBoundsException e) {
            return  res; // "class #"+scpx+"// invalid constant pool index";
        } catch (ClassCastException e) {
            return  res; // "class #"+scpx+"// invalid constant pool reference";
        } catch (Throwable e) {
            return res; // "#"+cpx+"// ERROR IN DISASSEMBLER";
        }
    }

    /**
     * Returns true if it is a class
     */
    public boolean isClass() {
        if((access & ACC_INTERFACE) == 0) return true;
        return false;
    }

    /**
     * Returns true if it is a interface.
     */
    public boolean isInterface(){
        if((access & ACC_INTERFACE) != 0) return true;
        return false;
    }

    /**
     * Returns true if this member is public, false otherwise.
     */
    public boolean isPublic(){
        return (access & ACC_PUBLIC) != 0;
    }

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

    /**
     * Returns list of innerclasses.
     */
    public InnerClassData[] getInnerClasses(){
        return innerClasses;
    }

    /**
     * Returns list of attributes.
     */
    public AttrData[] getAttributes(){
        return attrs;
    }

    /**
     * Returns true if superbit is set.
     */
    public boolean isSuperSet(){
        if ((access & ACC_SUPER)   !=0) return true;
        return false;
    }

    /**
     * Returns super class name.
     */
    public String getSuperClassName(){
        String res=null;
        if (super_class==0) {
            return res;
        }
        int scpx;
        try {
            if (tags[super_class]!=CONSTANT_CLASS) {
                return res; //"<CP["+cpx+"] is not a Class> ";
            }
            scpx=((CPX)cpool[super_class]).cpx;
        } catch (ArrayIndexOutOfBoundsException e) {
            return res; // "#"+cpx+"// invalid constant pool index";
        } catch (Throwable e) {
            return res; // "#"+cpx+"// ERROR IN DISASSEMBLER";
        }

        try {
            return (String)(cpool[scpx]);
        } catch (ArrayIndexOutOfBoundsException e) {
            return  res; // "class #"+scpx+"// invalid constant pool index";
        } catch (ClassCastException e) {
            return  res; // "class #"+scpx+"// invalid constant pool reference";
        } catch (Throwable e) {
            return res; // "#"+cpx+"// ERROR IN DISASSEMBLER";
        }
    }

    /**
     * Returns list of super interfaces.
     */
    public String[] getSuperInterfaces(){
        String interfacenames[] = new String[interfaces.length];
        int interfacecpx = -1;
        for(int i = 0; i < interfaces.length; i++){
            interfacecpx=((CPX)cpool[interfaces[i]]).cpx;
            interfacenames[i] = (String)(cpool[interfacecpx]);
        }
        return interfacenames;
    }

    /**
     * Returns string at prticular constant pool index.
     */
    public String getStringValue(int cpoolx) {
        try {
            return ((String)cpool[cpoolx]);
        } catch (ArrayIndexOutOfBoundsException e) {
            return "//invalid constant pool index:"+cpoolx;
        } catch (ClassCastException e) {
            return "//invalid constant pool ref:"+cpoolx;
        }
    }

    /**
     * Returns list of field info.
     */
    public  FieldData[] getFields(){
        return fields;
    }

    /**
     * Returns list of method info.
     */
    public  MethodData[] getMethods(){
        return methods;
    }

    /**
     * Returns constant pool entry at that index.
     */
    public CPX2 getCpoolEntry(int cpx){
        return ((CPX2)(cpool[cpx]));
    }

    public Object getCpoolEntryobj(int cpx){
        return (cpool[cpx]);
    }

    /**
     * Returns index of this class.
     */
    public int getthis_cpx(){
        return this_class;
    }

    public String TagString (int tag) {
        String res=Tables.tagName(tag);
        if (res==null)  return "BOGUS_TAG:"+tag;
        return res;
    }

    /**
     * Returns string at that index.
     */
    public String StringValue(int cpx) {
        if (cpx==0) return "#0";
        int tag;
        Object x;
        String suffix="";
        try {
            tag=tags[cpx];
            x=cpool[cpx];
        } catch (IndexOutOfBoundsException e) {
            return "<Incorrect CP index:"+cpx+">";
        }

        if (x==null) return "<NULL>";
        switch (tag) {
        case CONSTANT_UTF8: {
            StringBuffer sb=new StringBuffer();
            String s=(String)x;
            for (int k=0; k<s.length(); k++) {
                char c=s.charAt(k);
                switch (c) {
                case '\t': sb.append('\\').append('t'); break;
                case '\n': sb.append('\\').append('n'); break;
                case '\r': sb.append('\\').append('r'); break;
                case '\"': sb.append('\\').append('\"'); break;
                default: sb.append(c);
                }
            }
            return sb.toString();
        }
        case CONSTANT_DOUBLE: {
            Double d=(Double)x;
            String sd=d.toString();
            return sd+"d";
        }
        case CONSTANT_FLOAT: {
            Float f=(Float)x;
            String sf=(f).toString();
            return sf+"f";
        }
        case CONSTANT_LONG: {
            Long ln = (Long)x;
            return ln.toString()+'l';
        }
        case CONSTANT_INTEGER: {
            Integer in = (Integer)x;
            return in.toString();
        }
        case CONSTANT_CLASS:
            return javaName(getClassName(cpx));
        case CONSTANT_STRING:
            return StringValue(((CPX)x).cpx);
        case CONSTANT_FIELD:
        case CONSTANT_METHOD:
        case CONSTANT_INTERFACEMETHOD:
            //return getShortClassName(((CPX2)x).cpx1)+"."+StringValue(((CPX2)x).cpx2);
             return javaName(getClassName(((CPX2)x).cpx1))+"."+StringValue(((CPX2)x).cpx2);

        case CONSTANT_NAMEANDTYPE:
            return getName(((CPX2)x).cpx1)+":"+StringValue(((CPX2)x).cpx2);
        default:
            return "UnknownTag"; //TBD
        }
    }

    /**
     * Returns resolved java type name.
     */
    public String javaName(String name) {
        if( name==null) return "null";
        int len=name.length();
        if (len==0) return "\"\"";
        int cc='/';
    fullname: { // xxx/yyy/zzz
            int cp;
            for (int k=0; k<len; k += Character.charCount(cp)) {
                cp=name.codePointAt(k);
                if (cc=='/') {
                    if (!Character.isJavaIdentifierStart(cp)) break fullname;
                } else if (cp!='/') {
                    if (!Character.isJavaIdentifierPart(cp)) break fullname;
                }
                cc=cp;
            }
            return name;
        }
        return "\""+name+"\"";
    }

    public String getName(int cpx) {
        String res;
        try {
            return javaName((String)cpool[cpx]); //.replace('/','.');
        } catch (ArrayIndexOutOfBoundsException e) {
            return "<invalid constant pool index:"+cpx+">";
        } catch (ClassCastException e) {
            return "<invalid constant pool ref:"+cpx+">";
        }
    }

    /**
     * Returns unqualified class name.
     */
    public String getShortClassName(int cpx) {
        String classname=javaName(getClassName(cpx));
        pkgPrefixLen=classname.lastIndexOf("/")+1;
        if (pkgPrefixLen!=0) {
            pkgPrefix=classname.substring(0,pkgPrefixLen);
            if (classname.startsWith(pkgPrefix)) {
                return classname.substring(pkgPrefixLen);
            }
        }
        return classname;
    }

    /**
     * Returns source file name.
     */
    public String getSourceName(){
        return getName(source_cpx);
    }

    /**
     * Returns package name.
     */
    public String getPkgName(){
        String classname=getClassName(this_class);
        pkgPrefixLen=classname.lastIndexOf("/")+1;
        if (pkgPrefixLen!=0) {
            pkgPrefix=classname.substring(0,pkgPrefixLen);
            return("package  "+pkgPrefix.substring(0,pkgPrefixLen-1)+";\n");
        }else return null;
    }

    /**
     * Returns total constant pool entry count.
     */
    public int getCpoolCount(){
        return cpool_count;
    }

    public String StringTag(int cpx) {
        byte tag=0;
        String str=null;
        try {
            if (cpx==0) throw new IndexOutOfBoundsException();
            tag=tags[cpx];
            return      TagString(tag);
        } catch (IndexOutOfBoundsException e) {
            str="Incorrect CP index:"+cpx;
        }
        return str;
    }

    /**
     * Returns minor version of class file.
     */
    public int getMinor_version(){
        return minor_version;
    }

    /**
     * Returns major version of class file.
     */
    public int getMajor_version(){
        return major_version;
    }
}

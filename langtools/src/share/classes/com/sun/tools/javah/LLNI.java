/*
 * Copyright 2002-2005 Sun Microsystems, Inc.  All Rights Reserved.
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


package com.sun.tools.javah;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Hashtable;
import com.sun.javadoc.*;

 /*
  * @author  Sucheta Dambalkar(Revised)
  */
public class LLNI extends Gen {

    protected final char  pathChar = File.separatorChar;
    protected final char  innerDelim = '$';     /* For inner classes */
    protected Hashtable<Object, Object>   doneHandleTypes;
    MemberDoc []fields;
    MemberDoc [] methods;
    private boolean       doubleAlign;
    private int           padFieldNum = 0;


    LLNI(boolean doubleAlign, RootDoc root) {
        super(root);
        this.doubleAlign = doubleAlign;
    }


    protected String getIncludes() {
        return "";
    }

    protected void write(OutputStream o, ClassDoc clazz)
        throws ClassNotFoundException {
        String cname     = mangleClassName(clazz.qualifiedName());
        PrintWriter pw   = wrapWriter(o);
        fields = clazz.fields();
        methods = clazz.methods();
        generateDeclsForClass(pw, clazz, cname);
    }

    protected void generateDeclsForClass(PrintWriter pw,
                                         ClassDoc clazz, String cname)
        throws ClassNotFoundException {
        doneHandleTypes  = new Hashtable<Object, Object>();
        /* The following handle types are predefined in "typedefs.h". Suppress
           inclusion in the output by generating them "into the blue" here. */
        genHandleType(null, "java.lang.Class");
        genHandleType(null, "java.lang.ClassLoader");
        genHandleType(null, "java.lang.Object");
        genHandleType(null, "java.lang.String");
        genHandleType(null, "java.lang.Thread");
        genHandleType(null, "java.lang.ThreadGroup");
        genHandleType(null, "java.lang.Throwable");

        pw.println("/* LLNI Header for class " + clazz.qualifiedName() + " */" + lineSep);
        pw.println("#ifndef _Included_" + cname);
        pw.println("#define _Included_" + cname);
        pw.println("#include \"typedefs.h\"");
        pw.println("#include \"llni.h\"");
        pw.println("#include \"jni.h\"" + lineSep);

        forwardDecls(pw, clazz);
        structSectionForClass(pw, clazz, cname);
        methodSectionForClass(pw, clazz, cname);
        pw.println("#endif");
    }

    protected void genHandleType(PrintWriter pw, String clazzname) {
        String cname = mangleClassName(clazzname);
        if (!doneHandleTypes.containsKey(cname)) {
            doneHandleTypes.put(cname, cname);
            if (pw != null) {
                pw.println("#ifndef DEFINED_" + cname);
                pw.println("    #define DEFINED_" + cname);
                pw.println("    GEN_HANDLE_TYPES(" + cname + ");");
                pw.println("#endif" + lineSep);
            }
        }
    }

    protected String mangleClassName(String s) {
        return s.replace('.', '_')
            .replace(pathChar, '_')
            .replace(innerDelim, '_');
    }

    protected void forwardDecls(PrintWriter pw, ClassDoc clazz)
        throws ClassNotFoundException {
        ClassDoc clazzfield = null;

        if (clazz.qualifiedName().equals("java.lang.Object"))
            return;
        genHandleType(pw, clazz.qualifiedName());
        ClassDoc superClass = clazz.superclass();

        if(superClass != null){
            String superClassName = superClass.qualifiedName();
            forwardDecls(pw, superClass);
        }

        for (int i = 0; i < fields.length; i++) {
            FieldDoc field = (FieldDoc)fields[i];

            if (!field.isStatic()) {
                Type t = field.type();
                String tname = t.qualifiedTypeName();
                TypeSignature newTypeSig = new TypeSignature(root);
                String sig = newTypeSig.getTypeSignature(tname);

                if (sig.charAt(0) != '[')
                    forwardDeclsFromSig(pw, sig);
            }
        }

        for (int i = 0; i < methods.length; i++) {
            MethodDoc method = (MethodDoc)methods[i];

            if (method.isNative()) {
                Type retType = method.returnType();
                String typesig = method.signature();
                TypeSignature newTypeSig = new TypeSignature(root);
                String sig = newTypeSig.getTypeSignature(typesig, retType);

                if (sig.charAt(0) != '[')
                    forwardDeclsFromSig(pw, sig);

            }
        }
    }

    protected void forwardDeclsFromSig(PrintWriter pw, String sig) {
        int    len = sig.length();
        int    i   = sig.charAt(0) == '(' ? 1 : 0;

        /* Skip the initial "(". */
        while (i < len) {
            if (sig.charAt(i) == 'L') {
                int j = i + 1;
                while (sig.charAt(j) != ';') j++;
                genHandleType(pw, sig.substring(i + 1, j));
                i = j + 1;
            } else {
                i++;
            }
        }
    }

    protected void structSectionForClass(PrintWriter pw,
                                         ClassDoc jclazz, String cname)
        throws ClassNotFoundException {

        String jname = jclazz.qualifiedName();

        if (cname.equals("java_lang_Object")) {
            pw.println("/* struct java_lang_Object is defined in typedefs.h. */");
            pw.println();
            return;
        }
        pw.println("#if !defined(__i386)");
        pw.println("#pragma pack(4)");
        pw.println("#endif");
        pw.println();
        pw.println("struct " + cname + " {");
        pw.println("    ObjHeader h;");
        pw.print(fieldDefs(jclazz, cname));

        if (jname.equals("java.lang.Class"))
            pw.println("    Class *LLNI_mask(cClass);" +
                       "  /* Fake field; don't access (see oobj.h) */");
        pw.println("};" + lineSep + lineSep + "#pragma pack()");
        pw.println();
        return;
    }

    private static class FieldDefsRes {
        public String className;        /* Name of the current class. */
        public FieldDefsRes parent;
        public String s;
        public int byteSize;
        public boolean bottomMost;
        public boolean printedOne = false;

        FieldDefsRes(ClassDoc clazz, FieldDefsRes parent, boolean bottomMost) {
            this.className = clazz.qualifiedName();
            this.parent = parent;
            this.bottomMost = bottomMost;
            int byteSize = 0;
            if (parent == null) this.s = "";
            else this.s = parent.s;
        }
    }

    /* Returns "true" iff added a field. */
    private boolean doField(FieldDefsRes res, FieldDoc field,
                            String cname, boolean padWord)
        throws ClassNotFoundException {

        String fieldDef = addStructMember(field, cname, padWord);
        if (fieldDef != null) {
            if (!res.printedOne) { /* add separator */
                if (res.bottomMost) {
                    if (res.s.length() != 0)
                        res.s = res.s + "    /* local members: */" + lineSep;
                } else {
                    res.s = res.s + "    /* inherited members from " +
                        res.className + ": */" + lineSep;
                }
                res.printedOne = true;
            }
            res.s = res.s + fieldDef;
            return true;
        }

        // Otherwise.
        return false;
    }

    private int doTwoWordFields(FieldDefsRes res, ClassDoc clazz,
                                int offset, String cname, boolean padWord)
        throws ClassNotFoundException {
        boolean first = true;
        FieldDoc[] fields = clazz.fields();

        for (int i = 0; i <fields.length; i++) {
            FieldDoc field = fields[i];
            String tc =field.type().typeName();
            boolean twoWords = (tc.equals("long") || tc.equals("double"));
            if (twoWords && doField(res, field, cname, first && padWord)) {
                offset += 8; first = false;
            }
        }
        return offset;
    }

    protected String fieldDefs(ClassDoc clazz, String cname)
        throws ClassNotFoundException {
        FieldDefsRes res = fieldDefs(clazz, cname, true);
        return res.s;
    }

    protected FieldDefsRes fieldDefs(ClassDoc clazz, String cname,
                                     boolean bottomMost)
        throws ClassNotFoundException {
        FieldDefsRes res;
        int offset;
        boolean didTwoWordFields = false;
        ClassDoc superclazz = clazz.superclass();

        if (superclazz != null) {
            String supername = superclazz.qualifiedName();
            res = new FieldDefsRes(clazz,
                                   fieldDefs(superclazz, cname, false),
                                   bottomMost);
            offset = res.parent.byteSize;
        } else {
            res = new FieldDefsRes(clazz, null, bottomMost);
            offset = 0;
        }

        FieldDoc[] fields = clazz.fields();

        for (int i = 0; i < fields.length; i++) {
            FieldDoc field = fields[i];

            if (doubleAlign && !didTwoWordFields && (offset % 8) == 0) {
                offset = doTwoWordFields(res, clazz, offset, cname, false);
                didTwoWordFields = true;
            }

            String tc = field.type().typeName();
            boolean twoWords = (tc.equals("long") ||tc.equals("double"));

            if (!doubleAlign || !twoWords) {
                if (doField(res, field, cname, false)) offset += 4;
            }

        }

        if (doubleAlign && !didTwoWordFields) {
            if ((offset % 8) != 0) offset += 4;
            offset = doTwoWordFields(res, clazz, offset, cname, true);
        }

        res.byteSize = offset;
        return res;
    }

    /* OVERRIDE: This method handles instance fields */
    protected String addStructMember(FieldDoc member, String cname,
                                     boolean padWord)
        throws ClassNotFoundException {
        String res = null;

        if (member.isStatic()) {
            res = addStaticStructMember(member, cname);
            //   if (res == null) /* JNI didn't handle it, print comment. */
            //  res = "    /* Inaccessible static: " + member + " */" + lineSep;
        } else {
            if (padWord) res = "    java_int padWord" + padFieldNum++ + ";" + lineSep;
            res = "    " + llniType(member.type(), false, false) + " " + llniFieldName(member);
            if (isLongOrDouble(member.type())) res = res + "[2]";
            res = res + ";" + lineSep;
        }
        return res;
    }

    static private final boolean isWindows =
        System.getProperty("os.name").startsWith("Windows");

    /*
     * This method only handles static final fields.
     */
    protected String addStaticStructMember(FieldDoc field, String cname)
        throws ClassNotFoundException {
        String res = null;
        Object exp = null;

        if (!field.isStatic())
            return res;
        if (!field.isFinal())
            return res;

        exp = field.constantValue();

        if (exp != null) {
            /* Constant. */

            String     cn     = cname + "_" + field.name();
            String     suffix = null;
            long           val = 0;
            /* Can only handle int, long, float, and double fields. */
            if (exp instanceof Integer) {
                suffix = "L";
                val = ((Integer)exp).intValue();
            }
            if (exp instanceof Long) {
                // Visual C++ supports the i64 suffix, not LL
                suffix = isWindows ? "i64" : "LL";
                val = ((Long)exp).longValue();
            }
            if (exp instanceof Float)  suffix = "f";
            if (exp instanceof Double) suffix = "";
            if (suffix != null) {
                // Some compilers will generate a spurious warning
                // for the integer constants for Integer.MIN_VALUE
                // and Long.MIN_VALUE so we handle them specially.
                if ((suffix.equals("L") && (val == Integer.MIN_VALUE)) ||
                    (suffix.equals("LL") && (val == Long.MIN_VALUE))) {
                    res = "    #undef  " + cn + lineSep
                        + "    #define " + cn
                        + " (" + (val + 1) + suffix + "-1)" + lineSep;
                } else {
                    res = "    #undef  " + cn + lineSep
                        + "    #define " + cn + " "+ exp.toString() + suffix + lineSep;
                }
            }
        }
        return res;
    }

    protected void methodSectionForClass(PrintWriter pw,
                                         ClassDoc clazz, String cname)
        throws ClassNotFoundException {
        String methods = methodDecls(clazz, cname);

        if (methods.length() != 0) {
            pw.println("/* Native method declarations: */" + lineSep);
            pw.println("#ifdef __cplusplus");
            pw.println("extern \"C\" {");
            pw.println("#endif" + lineSep);
            pw.println(methods);
            pw.println("#ifdef __cplusplus");
            pw.println("}");
            pw.println("#endif");
        }
    }

    protected String methodDecls(ClassDoc clazz, String cname)
        throws ClassNotFoundException {

        String res = "";
        for (int i = 0; i < methods.length; i++) {
            MethodDoc method = (MethodDoc)methods[i];
            if (method.isNative())
                res = res + methodDecl(method, clazz, cname);
        }
        return res;
    }

    protected String methodDecl(MethodDoc method,
                                ClassDoc clazz, String cname)
        throws ClassNotFoundException {
        String res = null;

        Type retType = method.returnType();
        String typesig = method.signature();
        TypeSignature newTypeSig = new TypeSignature(root);
        String sig = newTypeSig.getTypeSignature(typesig, retType);
        boolean longName = needLongName(method, clazz);

        if (sig.charAt(0) != '(')
            Util.error("invalid.method.signature", sig);


        res = "JNIEXPORT " + jniType(retType) + " JNICALL" + lineSep + jniMethodName(method, cname, longName)
            + "(JNIEnv *, " + cRcvrDecl(method, cname);
        Parameter[] params = method.parameters();
        Type argTypes[] = new Type[params.length];
        for(int p = 0; p <  params.length; p++){
            argTypes[p] =  params[p].type();
        }

        /* It would have been nice to include the argument names in the
           declaration, but there seems to be a bug in the "BinaryField"
           class, causing the getArguments() method to return "null" for
           most (non-constructor) methods. */
        for (int i = 0; i < argTypes.length; i++)
            res = res + ", " + jniType(argTypes[i]);
        res = res + ");" + lineSep;
        return res;
    }

    protected final boolean needLongName(MethodDoc method,
                                         ClassDoc clazz)
        throws ClassNotFoundException {
        String methodName = method.name();
        for (int i = 0; i < methods.length; i++) {
            MethodDoc memberMethod = (MethodDoc) methods[i];
            if ((memberMethod != method) &&
                memberMethod.isNative() && (methodName == memberMethod.name()))
                return true;
        }
        return false;
    }

    protected final String jniMethodName(MethodDoc method, String cname,
                                         boolean longName) {
        String res = "Java_" + cname + "_" + method.name();

        if (longName) {
            Type mType =  method.returnType();
            Parameter[] params = method.parameters();
            Type argTypes[] = new Type[params.length];
            for(int p = 0; p <  params.length; p++){
                argTypes[p] =  params[p].type();
            }

            res = res + "__";
            for (int i = 0; i < argTypes.length; i++){
                Type t = argTypes[i];
                String tname = t.typeName();
                TypeSignature newTypeSig = new TypeSignature(root);
                String sig = newTypeSig.getTypeSignature(tname);
                res = res + nameToIdentifier(sig);
            }
        }
        return res;
    }

    protected final String jniType(Type t) {
        String elmT =t.typeName();
        if (t.dimension().indexOf("[]") != -1) {
            if(elmT.equals("boolean"))return "jbooleanArray";
            else if(elmT.equals("byte"))return "jbyteArray";
            else if(elmT.equals("char"))return "jcharArray";
            else if(elmT.equals("short"))return "jshortArray";
            else if(elmT.equals("int"))return "jintArray";
            else if(elmT.equals("long"))return "jlongArray";
            else if(elmT.equals("float"))return "jfloatArray";
            else if(elmT.equals("double"))return "jdoubleArray";
            else if((t.dimension().indexOf("[][]") != -1) || (t.asClassDoc() != null))  return "jobjectArray";
        } else {
            if(elmT.equals("void"))return "void";
            else if(elmT.equals("boolean"))return "jboolean";
            else if(elmT.equals("byte"))return "jbyte";
            else if(elmT.equals("char"))return "jchar";
            else if(elmT.equals("short"))return "jshort";
            else if(elmT.equals("int"))return "jint";
            else if(elmT.equals("long"))return "jlong";
            else if(elmT.equals("float"))return "jfloat";
            else if(elmT.equals("double"))return "jdouble";
            else if (t.asClassDoc() != null) {
                if (elmT.equals("String"))
                    return "jstring";
                else if (t.asClassDoc().subclassOf(root.classNamed("java.lang.Class")))
                    return "jclass";
                else
                    return "jobject";
            }
        }
        Util.bug("jni.unknown.type");
        return null; /* dead code. */
    }

    protected String llniType(Type t, boolean handleize, boolean longDoubleOK) {
        String res = null;
        String elmt = t.typeName();
        if (t.dimension().indexOf("[]") != -1) {
            if((t.dimension().indexOf("[][]") != -1)
               || (t.asClassDoc() != null)) res = "IArrayOfRef";
            else if(elmt.equals("boolean")) res =  "IArrayOfBoolean";
            else if(elmt.equals("byte")) res =  "IArrayOfByte";
            else if(elmt.equals("char")) res =  "IArrayOfChar";
            else if(elmt.equals("int")) res =  "IArrayOfInt";
            else if(elmt.equals("long")) res =  "IArrayOfLong";
            else if(elmt.equals("float")) res =  "IArrayOfFloat";
            else if(elmt.equals("double")) res =  "IArrayOfDouble";
            if (!handleize) res = "DEREFERENCED_" + res;
        } else {
            if(elmt.equals("void")) res =  "void";
            else if( (elmt.equals("boolean")) || (elmt.equals("byte"))
                     ||(elmt.equals("char")) || (elmt.equals("short"))
                     || (elmt.equals("int")))   res = "java_int";
            else   if(elmt.equals("long")) res = longDoubleOK
                                               ? "java_long" : "val32 /* java_long */";
            else   if(elmt.equals("float")) res =  "java_float";
            else   if(elmt.equals("double")) res =  res = longDoubleOK
                                                 ? "java_double" : "val32 /* java_double */";
            else if(t.asClassDoc() != null) {
                res = "I" +  mangleClassName(t.asClassDoc().qualifiedName());
                if (!handleize) res = "DEREFERENCED_" + res;
            }
        }
        return res;
    }

    protected final String cRcvrDecl(MemberDoc field, String cname) {
        return (field.isStatic() ? "jclass" : "jobject");
    }

    protected String maskName(String s) {
        return "LLNI_mask(" + s + ")";
    }

    protected String llniFieldName(MemberDoc field) {
        return maskName(field.name());
    }

    protected final boolean isLongOrDouble(Type t) {
        String tc = t.typeName();
        return (tc.equals("long") || tc.equals("double"));
    }

    /* Do unicode to ansi C identifier conversion.
       %%% This may not be right, but should be called more often. */
    protected final String nameToIdentifier(String name) {
        int len = name.length();
        StringBuffer buf = new StringBuffer(len);
        for (int i = 0; i < len; i++) {
            char c = name.charAt(i);
            if (isASCIILetterOrDigit(c))
                buf.append(c);
            else if (c == '/')
                buf.append('_');
            else if (c == '.')
                buf.append('_');
            else if (c == '_')
                buf.append("_1");
            else if (c == ';')
                buf.append("_2");
            else if (c == '[')
                buf.append("_3");
            else
                buf.append("_0" + ((int)c));
        }
        return new String(buf);
    }

    protected final boolean isASCIILetterOrDigit(char c) {
        if (((c >= 'A') && (c <= 'Z')) ||
            ((c >= 'a') && (c <= 'z')) ||
            ((c >= '0') && (c <= '9')))
            return true;
        else
            return false;
    }
}

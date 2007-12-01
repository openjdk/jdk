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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Vector;
import java.util.Enumeration;
import com.sun.javadoc.*;


/**
 * Header file generator for JNI.
 *
 * @author  Sucheta Dambalkar(Revised)
 */

public class JNI extends Gen {

    public JNI(RootDoc root){
        super(root);
    }

    public String getIncludes() {
        return "#include <jni.h>";
    }

    public void write(OutputStream o, ClassDoc clazz)
        throws ClassNotFoundException {

        String cname = Mangle.mangle(clazz.qualifiedName(), Mangle.Type.CLASS);
        PrintWriter pw = wrapWriter(o);
        pw.println(guardBegin(cname));
        pw.println(cppGuardBegin());

        /* Write statics. */
        FieldDoc[] classfields = getAllFields(clazz);

        for (int i = 0; i < classfields.length; i++) {
            if (!classfields[i].isStatic())
                continue;
            String s = null;
            s = defineForStatic(clazz, classfields[i]);
            if (s != null) {
                pw.println(s);
            }
        }

        /* Write methods. */
        MethodDoc[] classmethods = clazz.methods();
        for (int i = 0; i < classmethods.length; i++) {
            if(classmethods[i].isNative()){
                MethodDoc md = classmethods[i];
                Type mtr = classmethods[i].returnType();
                String sig = md.signature();
                TypeSignature newtypesig = new TypeSignature(root);
                String methodName = md.name();
                boolean longName = false;
                for (int j = 0; j < classmethods.length; j++) {
                    if ((classmethods[j] != md)
                        && (methodName.equals(classmethods[j].name()))
                        && (classmethods[j].isNative()))
                        longName = true;

                }
                pw.println("/*");
                pw.println(" * Class:     " + cname);
                pw.println(" * Method:    " +
                           Mangle.mangle(methodName, Mangle.Type.FIELDSTUB));
                pw.println(" * Signature: " + newtypesig.getTypeSignature(sig, mtr));
                pw.println(" */");
                pw.println("JNIEXPORT " + jniType(mtr) +
                           " JNICALL " +
                           Mangle.mangleMethod(md, root,clazz,
                                               (longName) ?
                                               Mangle.Type.METHOD_JNI_LONG :
                                               Mangle.Type.METHOD_JNI_SHORT));
                pw.print("  (JNIEnv *, ");
                Parameter[] paramargs = md.parameters();
                Type []args =new Type[ paramargs.length];
                for(int p = 0; p < paramargs.length; p++){
                    args[p] = paramargs[p].type();
                }
                if (md.isStatic())
                    pw.print("jclass");
                else
                    pw.print("jobject");
                if (args.length > 0)
                    pw.print(", ");

                for (int j = 0; j < args.length; j++) {
                    pw.print(jniType(args[j]));
                    if (j != (args.length - 1)) {
                        pw.print(", ");
                    }
                }
                pw.println(");" + lineSep);
            }
        }
        pw.println(cppGuardEnd());
        pw.println(guardEnd(cname));
    }


    protected final String jniType(Type t){

        String elmT = t.typeName();
        ClassDoc throwable = root.classNamed("java.lang.Throwable");
        ClassDoc jClass = root.classNamed("java.lang.Class");
        ClassDoc tclassDoc = t.asClassDoc();

        if((t.dimension()).indexOf("[]") != -1){
            if((t.dimension().indexOf("[][]") != -1)
               || (tclassDoc != null))  return "jobjectArray";
            else if(elmT.equals("boolean"))return  "jbooleanArray";
            else if(elmT.equals("byte"))return  "jbyteArray";
            else if(elmT.equals("char"))return  "jcharArray";
            else if(elmT.equals("short"))return  "jshortArray";
            else if(elmT.equals("int"))return  "jintArray";
            else if(elmT.equals("long"))return  "jlongArray";
            else if(elmT.equals("float"))return  "jfloatArray";
            else if(elmT.equals("double"))return  "jdoubleArray";
        }else{
            if(elmT.equals("void"))return  "void";
            else if(elmT.equals("String"))return  "jstring";
            else if(elmT.equals("boolean"))return  "jboolean";
            else if(elmT.equals("byte"))return  "jbyte";
            else if(elmT.equals("char"))return  "jchar";
            else if(elmT.equals("short"))return  "jshort";
            else if(elmT.equals("int"))return  "jint";
            else if(elmT.equals("long"))return  "jlong";
            else if(elmT.equals("float"))return  "jfloat";
            else if(elmT.equals("double"))return  "jdouble";
            else  if(tclassDoc  != null){
                if(tclassDoc.subclassOf(throwable)) return "jthrowable";
                else if(tclassDoc.subclassOf(jClass)) return "jclass";
                else return "jobject";
            }
        }
        Util.bug("jni.unknown.type");
        return null; /* dead code. */
    }
}

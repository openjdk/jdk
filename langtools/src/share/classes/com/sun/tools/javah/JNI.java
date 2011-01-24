/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javah;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;


/**
 * Header file generator for JNI.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author  Sucheta Dambalkar(Revised)
 */
public class JNI extends Gen {
    JNI(Util util) {
        super(util);
    }

    public String getIncludes() {
        return "#include <jni.h>";
    }

    public void write(OutputStream o, TypeElement clazz) throws Util.Exit {
        try {
            String cname = mangler.mangle(clazz.getQualifiedName(), Mangle.Type.CLASS);
            PrintWriter pw = wrapWriter(o);
            pw.println(guardBegin(cname));
            pw.println(cppGuardBegin());

            /* Write statics. */
            List<VariableElement> classfields = getAllFields(clazz);

            for (VariableElement v: classfields) {
                if (!v.getModifiers().contains(Modifier.STATIC))
                    continue;
                String s = null;
                s = defineForStatic(clazz, v);
                if (s != null) {
                    pw.println(s);
                }
            }

            /* Write methods. */
            List<ExecutableElement> classmethods = ElementFilter.methodsIn(clazz.getEnclosedElements());
            for (ExecutableElement md: classmethods) {
                if(md.getModifiers().contains(Modifier.NATIVE)){
                    TypeMirror mtr = types.erasure(md.getReturnType());
                    String sig = signature(md);
                    TypeSignature newtypesig = new TypeSignature(elems);
                    CharSequence methodName = md.getSimpleName();
                    boolean longName = false;
                    for (ExecutableElement md2: classmethods) {
                        if ((md2 != md)
                            && (methodName.equals(md2.getSimpleName()))
                            && (md2.getModifiers().contains(Modifier.NATIVE)))
                            longName = true;

                    }
                    pw.println("/*");
                    pw.println(" * Class:     " + cname);
                    pw.println(" * Method:    " +
                               mangler.mangle(methodName, Mangle.Type.FIELDSTUB));
                    pw.println(" * Signature: " + newtypesig.getTypeSignature(sig, mtr));
                    pw.println(" */");
                    pw.println("JNIEXPORT " + jniType(mtr) +
                               " JNICALL " +
                               mangler.mangleMethod(md, clazz,
                                                   (longName) ?
                                                   Mangle.Type.METHOD_JNI_LONG :
                                                   Mangle.Type.METHOD_JNI_SHORT));
                    pw.print("  (JNIEnv *, ");
                    List<? extends VariableElement> paramargs = md.getParameters();
                    List<TypeMirror> args = new ArrayList<TypeMirror>();
                    for (VariableElement p: paramargs) {
                        args.add(types.erasure(p.asType()));
                    }
                    if (md.getModifiers().contains(Modifier.STATIC))
                        pw.print("jclass");
                    else
                        pw.print("jobject");

                    for (TypeMirror arg: args) {
                        pw.print(", ");
                        pw.print(jniType(arg));
                    }
                    pw.println(");" + lineSep);
                }
            }
            pw.println(cppGuardEnd());
            pw.println(guardEnd(cname));
        } catch (TypeSignature.SignatureException e) {
            util.error("jni.sigerror", e.getMessage());
        }
    }


    protected final String jniType(TypeMirror t) throws Util.Exit {
        TypeElement throwable = elems.getTypeElement("java.lang.Throwable");
        TypeElement jClass = elems.getTypeElement("java.lang.Class");
        TypeElement jString = elems.getTypeElement("java.lang.String");
        Element tclassDoc = types.asElement(t);


        switch (t.getKind()) {
            case ARRAY: {
                TypeMirror ct = ((ArrayType) t).getComponentType();
                switch (ct.getKind()) {
                    case BOOLEAN:  return "jbooleanArray";
                    case BYTE:     return "jbyteArray";
                    case CHAR:     return "jcharArray";
                    case SHORT:    return "jshortArray";
                    case INT:      return "jintArray";
                    case LONG:     return "jlongArray";
                    case FLOAT:    return "jfloatArray";
                    case DOUBLE:   return "jdoubleArray";
                    case ARRAY:
                    case DECLARED: return "jobjectArray";
                    default: throw new Error(ct.toString());
                }
            }

            case VOID:     return "void";
            case BOOLEAN:  return "jboolean";
            case BYTE:     return "jbyte";
            case CHAR:     return "jchar";
            case SHORT:    return "jshort";
            case INT:      return "jint";
            case LONG:     return "jlong";
            case FLOAT:    return "jfloat";
            case DOUBLE:   return "jdouble";

            case DECLARED: {
                if (tclassDoc.equals(jString))
                    return "jstring";
                else if (types.isAssignable(t, throwable.asType()))
                    return "jthrowable";
                else if (types.isAssignable(t, jClass.asType()))
                    return "jclass";
                else
                    return "jobject";
            }
        }

        util.bug("jni.unknown.type");
        return null; /* dead code. */
    }
}

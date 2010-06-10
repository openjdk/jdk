/*
 * Copyright (c) 2002, 2008, Oracle and/or its affiliates. All rights reserved.
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

import java.util.*;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor7;

/**
 * Returns internal type signature.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author Sucheta Dambalkar
 */

public class TypeSignature{

    Elements elems;

    /* Signature Characters */

    private static final String SIG_VOID                   = "V";
    private static final String SIG_BOOLEAN                = "Z";
    private static final String SIG_BYTE                   = "B";
    private static final String SIG_CHAR                   = "C";
    private static final String SIG_SHORT                  = "S";
    private static final String SIG_INT                    = "I";
    private static final String SIG_LONG                   = "J";
    private static final String SIG_FLOAT                  = "F";
    private static final String SIG_DOUBLE                 = "D";
    private static final String SIG_ARRAY                  = "[";
    private static final String SIG_CLASS                  = "L";



    public TypeSignature(Elements elems){
        this.elems = elems;
    }

    /*
     * Returns the type signature of a field according to JVM specs
     */
    public String getTypeSignature(String javasignature){
        return getParamJVMSignature(javasignature);
    }

    /*
     * Returns the type signature of a method according to JVM specs
     */
    public String getTypeSignature(String javasignature, TypeMirror returnType){
        String signature = null; //Java type signature.
        String typeSignature = null; //Internal type signature.
        List<String> params = new ArrayList<String>(); //List of parameters.
        String paramsig = null; //Java parameter signature.
        String paramJVMSig = null; //Internal parameter signature.
        String returnSig = null; //Java return type signature.
        String returnJVMType = null; //Internal return type signature.
        int dimensions = 0; //Array dimension.

        int startIndex = -1;
        int endIndex = -1;
        StringTokenizer st = null;
        int i = 0;

        // Gets the actual java signature without parentheses.
        if (javasignature != null) {
            startIndex = javasignature.indexOf("(");
            endIndex = javasignature.indexOf(")");
        }

        if (((startIndex != -1) && (endIndex != -1))
            &&(startIndex+1 < javasignature.length())
            &&(endIndex < javasignature.length())) {
            signature = javasignature.substring(startIndex+1, endIndex);
        }

        // Separates parameters.
        if (signature != null) {
            if (signature.indexOf(",") != -1) {
                st = new StringTokenizer(signature, ",");
                if (st != null) {
                    while (st.hasMoreTokens()) {
                        params.add(st.nextToken());
                    }
                }
            } else {
                params.add(signature);
            }
        }

        /* JVM type signature. */
        typeSignature = "(";

        // Gets indivisual internal parameter signature.
        while (params.isEmpty() != true) {
            paramsig = params.remove(i).trim();
            paramJVMSig  = getParamJVMSignature(paramsig);
            if (paramJVMSig != null) {
                typeSignature += paramJVMSig;
            }
        }

        typeSignature += ")";

        // Get internal return type signature.

        returnJVMType = "";
        if (returnType != null) {
            dimensions = dimensions(returnType);
        }

        //Gets array dimension of return type.
        while (dimensions-- > 0) {
            returnJVMType += "[";
        }
        if (returnType != null) {
            returnSig = qualifiedTypeName(returnType);
            returnJVMType += getComponentType(returnSig);
        } else {
            System.out.println("Invalid return type.");
        }

        typeSignature += returnJVMType;

        return typeSignature;
    }

    /*
     * Returns internal signature of a parameter.
     */
    private String getParamJVMSignature(String paramsig) {
        String paramJVMSig = "";
        String componentType ="";

        if(paramsig != null){

            if(paramsig.indexOf("[]") != -1) {
                // Gets array dimension.
                int endindex = paramsig.indexOf("[]");
                componentType = paramsig.substring(0, endindex);
                String dimensionString =  paramsig.substring(endindex);
                if(dimensionString != null){
                    while(dimensionString.indexOf("[]") != -1){
                        paramJVMSig += "[";
                        int beginindex = dimensionString.indexOf("]") + 1;
                        if(beginindex < dimensionString.length()){
                            dimensionString = dimensionString.substring(beginindex);
                        }else
                            dimensionString = "";
                    }
                }
            } else componentType = paramsig;

            paramJVMSig += getComponentType(componentType);
        }
        return paramJVMSig;
    }

    /*
     * Returns internal signature of a component.
     */
    private String getComponentType(String componentType){

        String JVMSig = "";

        if(componentType != null){
            if(componentType.equals("void")) JVMSig += SIG_VOID ;
            else if(componentType.equals("boolean"))  JVMSig += SIG_BOOLEAN ;
            else if(componentType.equals("byte")) JVMSig += SIG_BYTE ;
            else if(componentType.equals("char"))  JVMSig += SIG_CHAR ;
            else if(componentType.equals("short"))  JVMSig += SIG_SHORT ;
            else if(componentType.equals("int"))  JVMSig += SIG_INT ;
            else if(componentType.equals("long"))  JVMSig += SIG_LONG ;
            else if(componentType.equals("float")) JVMSig += SIG_FLOAT ;
            else if(componentType.equals("double"))  JVMSig += SIG_DOUBLE ;
            else {
                if(!componentType.equals("")){
                    TypeElement classNameDoc = elems.getTypeElement(componentType);

                    if(classNameDoc == null){
                        System.out.println("Invalid class type for " + componentType);
                        new Exception().printStackTrace();
                    }else {
                        String classname = classNameDoc.getQualifiedName().toString();
                        String newclassname = classname.replace('.', '/');
                        JVMSig += "L";
                        JVMSig += newclassname;
                        JVMSig += ";";
                    }
                }
            }
        }
        return JVMSig;
    }

    int dimensions(TypeMirror t) {
        if (t.getKind() != TypeKind.ARRAY)
            return 0;
        return 1 + dimensions(((ArrayType) t).getComponentType());
    }


    String qualifiedTypeName(TypeMirror type) {
        TypeVisitor<Name, Void> v = new SimpleTypeVisitor7<Name, Void>() {
            @Override
            public Name visitArray(ArrayType t, Void p) {
                return t.getComponentType().accept(this, p);
            }

            @Override
            public Name visitDeclared(DeclaredType t, Void p) {
                return ((TypeElement) t.asElement()).getQualifiedName();
            }

            @Override
            public Name visitPrimitive(PrimitiveType t, Void p) {
                return elems.getName(t.toString());
            }

            @Override
            public Name visitNoType(NoType t, Void p) {
                if (t.getKind() == TypeKind.VOID)
                    return elems.getName("void");
                return defaultAction(t, p);
            }

            @Override
            public Name visitTypeVariable(TypeVariable t, Void p) {
                return t.getUpperBound().accept(this, p);
            }
        };
        return v.visit(type).toString();
    }
}

/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.javadoc.*;
import java.io.*;
import java.util.*;

/**
 * Returns internal type signature.
 *
 * @author Sucheta Dambalkar
 */

public class TypeSignature{

    RootDoc root  = null;

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



    public TypeSignature(RootDoc root){
        this.root = root;
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
    public String getTypeSignature(String javasignature, Type returnType){

        String signature = null; //Java type signature.
        String typeSignature = null; //Internal type signature.
        Vector<Object> params = new Vector<Object>(); //List of parameters.
        String paramsig = null; //Java parameter signature.
        String paramJVMSig = null; //Internal parameter signature.
        String returnSig = null; //Java return type signature.
        String returnJVMType = null; //Internal return type signature.
        String dimension = null; //Array dimension.

        int startIndex = -1;
        int endIndex = -1;
        StringTokenizer st = null;
        int i = 0;

        // Gets the actual java signature without parentheses.
        if(javasignature != null){
            startIndex = javasignature.indexOf("(");
            endIndex = javasignature.indexOf(")");
        }

        if(((startIndex != -1) && (endIndex != -1))
           &&(startIndex+1 < javasignature.length())
           &&(endIndex < javasignature.length())) {

            signature = javasignature.substring(startIndex+1, endIndex);
        }

        // Separates parameters.
        if(signature != null){
            if(signature.indexOf(",") != -1){
                st = new StringTokenizer(signature, ",");
                if(st != null){
                    while (st.hasMoreTokens()) {
                        params.add(st.nextToken());
                    }
                }
            }else {
                params.add(signature);
            }
        }

        /* JVM type signature. */
        typeSignature = "(";

        // Gets indivisual internal parameter signature.
        while(params.isEmpty() != true){
            paramsig =((String)params.remove(i)).trim();
            paramJVMSig  = getParamJVMSignature(paramsig);
            if(paramJVMSig != null){
                typeSignature += paramJVMSig;
            }
        }

        typeSignature += ")";

        // Get internal return type signature.

        returnJVMType = "";
        if(returnType != null){
            dimension = returnType.dimension();
        }

        if(dimension != null){

            //Gets array dimension of return type.
            while(dimension.indexOf("[]") != -1){
                returnJVMType += "[";
                int stindex = dimension.indexOf("]") + 1;
                if(stindex <= dimension.length()){
                    dimension = dimension.substring(stindex);
                }else dimension = "";
            }
        }
        if(returnType != null){
            returnSig = returnType.qualifiedTypeName();
            returnJVMType += getComponentType(returnSig);
        }else {
            System.out.println("Invalid return type.");
        }

        typeSignature += returnJVMType;
        return typeSignature;
    }

    /*
     * Returns internal signature of a parameter.
     */
    private String getParamJVMSignature(String paramsig){
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
                    ClassDoc classNameDoc = root.classNamed(componentType);

                    if(classNameDoc == null){
                        System.out.println("Invalid class type");
                    }else {
                        String classname = classNameDoc.qualifiedName();
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
}

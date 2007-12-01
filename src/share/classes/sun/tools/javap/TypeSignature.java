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


package sun.tools.javap;

import java.util.*;
import java.io.*;

/**
 * Returns java type signature.
 *
 * @author  Sucheta Dambalkar
 */
public class TypeSignature {

    String parameters = null;
    String returntype = null;
    String fieldtype = null;
    int argumentlength = 0;

    public TypeSignature(String JVMSignature){

        if(JVMSignature != null){
            if(JVMSignature.indexOf("(") == -1){
                //This is a field type.
                this.fieldtype = getFieldTypeSignature(JVMSignature);
            }else {
                String parameterdes = null;
                if((JVMSignature.indexOf(")")-1) > (JVMSignature.indexOf("("))){
                    //Get parameter signature.
                    parameterdes =
                        JVMSignature.substring(JVMSignature.indexOf("(")+1,
                                               JVMSignature.indexOf(")"));
                    this.parameters = getParametersHelper(parameterdes);
                }else this.parameters = "()";
                //Get return type signature.
                String returndes = JVMSignature.substring(JVMSignature.lastIndexOf(")")+1);
                this.returntype = getReturnTypeHelper(returndes);
            }
        }
    }

    /**
     * Returns java type signature of a field.
     */
    public String getFieldTypeSignature(String fielddes){
        if(fielddes.startsWith("L")){
            return(getObjectType(fielddes));
        }else if(fielddes.startsWith("[")){
            return(getArrayType(fielddes));
        }else
            return(getBaseType(fielddes));
    }

    /**
     * Returns java type signature of a parameter.
     */
    public String getParametersHelper(String parameterdes){
        Vector parameters = new Vector();
        int startindex = -1;
        int endindex = -1;
        String param = "";

        while(parameterdes != null){

            if(parameterdes.startsWith("L")){
                //parameter is a object.
                startindex = parameterdes.indexOf("L");
                endindex = parameterdes.indexOf(";");
                if(startindex < parameterdes.length()) {
                    if(endindex == parameterdes.length()-1) {
                        //last parameter
                        param = parameterdes.substring(startindex);
                        parameterdes = null;
                    }else if(endindex+1 < parameterdes.length()){
                        //rest parameters
                        param = parameterdes.substring(startindex, endindex+1);
                        parameterdes = parameterdes.substring(endindex+1);

                    }
                    parameters.add(getObjectType(param));
                }
            }else if(parameterdes.startsWith("[")){
                //parameter is an array.
                String componentType = "";
                int enddim = -1;
                int st = 0;
                while(true){
                    if(st < parameterdes.length()){
                        if(parameterdes.charAt(st) == '['){

                            enddim = st;
                            st++;
                        }
                        else break;
                    }
                    else break;
                }

                if(enddim+1 < parameterdes.length()){
                    /* Array dimension.*/
                    param = parameterdes.substring(0,enddim+1);

                }

                int stotherparam = param.lastIndexOf("[")+1;

                if(stotherparam < parameterdes.length()){
                    componentType =  parameterdes.substring(stotherparam);
                }

                if(componentType.startsWith("L")){
                    //parameter is array of objects.
                    startindex = parameterdes.indexOf("L");
                    endindex = parameterdes.indexOf(";");

                    if(endindex ==  parameterdes.length()-1){
                        //last parameter
                        param += parameterdes.substring(startindex);
                        parameterdes = null;
                    }else if(endindex+1 <  parameterdes.length()){
                        //rest parameters
                        param += parameterdes.substring(startindex, endindex+1);
                        parameterdes = parameterdes.substring(endindex+1);
                    }
                }else{
                    //parameter is array of base type.
                    if(componentType.length() == 1){
                        //last parameter.
                        param += componentType;
                        parameterdes = null;
                    }
                    else if (componentType.length() > 1) {
                        //rest parameters.
                        param += componentType.substring(0,1);
                        parameterdes = componentType.substring(1);
                    }
                }
                parameters.add(getArrayType(param));


            }else {

                //parameter is of base type.
                if(parameterdes.length() == 1){
                    //last parameter
                    param = parameterdes;
                    parameterdes = null;
                }
                else if (parameterdes.length() > 1) {
                    //rest parameters.
                    param = parameterdes.substring(0,1);
                    parameterdes = parameterdes.substring(1);
                }
                parameters.add(getBaseType(param));
            }
        }

        /* number of arguments of a method.*/
        argumentlength =  parameters.size();

        /* java type signature.*/
        String parametersignature = "(";
        int i;

        for(i = 0; i < parameters.size(); i++){
            parametersignature += (String)parameters.elementAt(i);
            if(i != parameters.size()-1){
                parametersignature += ", ";
            }
        }
        parametersignature += ")";
        return parametersignature;
    }

    /**
     * Returns java type signature for a return type.
     */
    public String getReturnTypeHelper(String returndes){
        return getFieldTypeSignature(returndes);
    }

    /**
     * Returns java type signature for a base type.
     */
    public String getBaseType(String baseType){
        if(baseType != null){
            if(baseType.equals("B")) return "byte";
            else if(baseType.equals("C")) return "char";
            else if(baseType.equals("D")) return "double";
            else if(baseType.equals("F")) return "float";
            else if(baseType.equals("I")) return "int";
            else if(baseType.equals("J")) return "long";
            else if(baseType.equals("S")) return "short";
            else if(baseType.equals("Z")) return "boolean";
            else if(baseType.equals("V")) return "void";
        }
        return null;
    }

    /**
     * Returns java type signature for a object type.
     */
    public String getObjectType(String JVMobjectType) {
        String objectType = "";
        int startindex = JVMobjectType.indexOf("L")+1;
        int endindex =  JVMobjectType.indexOf(";");
        if((startindex != -1) && (endindex != -1)){
            if((startindex < JVMobjectType.length()) && (endindex < JVMobjectType.length())){
                objectType = JVMobjectType.substring(startindex, endindex);
            }
            objectType = objectType.replace('/','.');
            return objectType;
        }
        return null;
    }

    /**
     * Returns java type signature for array type.
     */
    public String getArrayType(String arrayType) {
        if(arrayType != null){
            String dimention = "";

            while(arrayType.indexOf("[") != -1){
                dimention += "[]";

                int startindex = arrayType.indexOf("[")+1;
                if(startindex <= arrayType.length()){
                arrayType = arrayType.substring(startindex);
                }
            }

            String componentType = "";
            if(arrayType.startsWith("L")){
                componentType = getObjectType(arrayType);
            }else {
                componentType = getBaseType(arrayType);
            }
            return componentType+dimention;
        }
        return null;
    }

    /**
     * Returns java type signature for parameters.
     */
     public String getParameters(){
        return parameters;
    }

    /**
     * Returns java type signature for return type.
     */
    public String getReturnType(){
        return returntype;
    }

    /**
     * Returns java type signature for field type.
     */
    public String getFieldType(){
        return fieldtype;
    }

    /**
     * Return number of arguments of a method.
     */
    public int getArgumentlength(){
        return argumentlength;
    }
}

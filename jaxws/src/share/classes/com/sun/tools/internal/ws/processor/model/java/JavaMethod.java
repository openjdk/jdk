/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.internal.ws.processor.model.java;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.tools.internal.ws.processor.model.ModelException;
import com.sun.codemodel.internal.JClass;

/**
 *
 * @author WS Development Team
 */
public class JavaMethod {

    public JavaMethod() {}

    public JavaMethod(String name) {
        this.name = name;
        this.returnType = null;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JavaType getReturnType() {
        return returnType;
    }

    public void setReturnType(JavaType returnType) {
        this.returnType = returnType;
    }

    public boolean hasParameter(String paramName) {
        for (int i=0; i<parameters.size();i++) {
            if (paramName.equals(
                ((JavaParameter)parameters.get(i)).getName())) {

                return true;
            }
        }
        return false;
    }

    public void addParameter(JavaParameter param) {
        // verify that this member does not already exist
        if (hasParameter(param.getName())) {
            throw new ModelException("model.uniqueness");
        }
        parameters.add(param);
    }

    public JavaParameter getParameter(String paramName){
        for (int i=0; i<parameters.size();i++) {
            JavaParameter jParam = parameters.get(i);
            if (paramName.equals(jParam.getParameter().getName())) {
                return jParam;
            }
        }
        return null;
    }

    public Iterator<JavaParameter> getParameters() {
        return parameters.iterator();
    }

    public int getParameterCount() {
        return parameters.size();
    }

    /* serialization */
    public List<JavaParameter> getParametersList() {
        return parameters;
    }

    /* serialization */
    public void setParametersList(List<JavaParameter> l) {
        parameters = l;
    }

    public boolean hasException(String exception) {
        return exceptions.contains(exception);
    }

    public void addException(String exception) {

        // verify that this exception does not already exist
        if (hasException(exception)) {
            throw new ModelException("model.uniqueness");
        }
        exceptions.add(exception);
    }

    public Iterator getExceptions() {
        return exceptions.iterator();
    }

    /* serialization */
    public List getExceptionsList() {
        return exceptions;
    }

    /* serialization */
    public void setExceptionsList(List l) {
        exceptions = l;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }
    public void setDeclaringClass(String declaringClass) {
        this.declaringClass = declaringClass;
    }

    // TODO fix model importer/exporter to handle this
    public boolean getThrowsRemoteException() {
        return throwsRemoteException;
    }
    public void setThrowsRemoteException(boolean throwsRemoteException) {
        this.throwsRemoteException = throwsRemoteException;
    }

    public void addExceptionClass(JClass ex){
        exceptionClasses.add(ex);
    }

    public List<JClass> getExceptionClasses(){
        return exceptionClasses;
    }

    private String name;
    private List<JavaParameter> parameters = new ArrayList<JavaParameter>();
    private List<String> exceptions = new ArrayList<String>();
    private List<JClass> exceptionClasses = new ArrayList<JClass>();

    private JavaType returnType;
    private String declaringClass;
    private boolean throwsRemoteException = true;
}

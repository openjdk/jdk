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
package com.sun.xml.internal.ws.model;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.ws.model.soap.SOAPBinding;
import com.sun.xml.internal.ws.pept.presentation.MessageStruct;

/**
 * Build this runtime model using java SEI and annotations
 *
 * @author Vivek Pandey
 */
public class JavaMethod {
    /**
     *
     */
    public JavaMethod(Method method) {
        this.method = method;
    }

    /**
     * @return Returns the method.
     */
    public Method getMethod() {
        return method;
    }

    /**
     * @return Returns the mep.
     */
    public int getMEP() {
        return mep;
    }

    /**
     * @param mep
     *            The mep to set.
     */
    public void setMEP(int mep) {
        this.mep = mep;
    }

    /**
     * @return the Binding object
     */
    public Object getBinding() {
        if (binding == null)
            return new SOAPBinding();
        return binding;
    }

    /**
     * @param binding
     */
    public void setBinding(Object binding) {
        this.binding = binding;
    }


    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }

    public String getOperationName() {
        return operationName;
    }



    /**
     * @return returns unmodifiable list of request parameters
     */
    public List<Parameter> getRequestParameters() {
        return unmReqParams;
    }

    /**
     * @return returns unmodifiable list of response parameters
     */
    public List<Parameter> getResponseParameters() {
        return unmResParams;
    }

    /**
     * @param p
     */
    public void addParameter(Parameter p) {
        if (p.isIN() || p.isINOUT()) {
            if (requestParams.contains(p)) {
                // TODO throw exception
            }
            requestParams.add(p);
        }

        if (p.isOUT() || p.isINOUT()) {
            // this check is only for out parameters
            if (requestParams.contains(p)) {
                // TODO throw exception
            }
            responseParams.add(p);
        }
    }

    public void addRequestParameter(Parameter p){
        if (p.isIN() || p.isINOUT()) {
            requestParams.add(p);
        }
    }

    public void addResponseParameter(Parameter p){
        if (p.isOUT() || p.isINOUT()) {
            responseParams.add(p);
        }
    }

    /**
     * @return Returns number of java method parameters - that will be all the
     *         IN, INOUT and OUT holders
     */
    public int getInputParametersCount() {
        int count = 0;
        for (Parameter param : requestParams) {
            if (param.isWrapperStyle()) {
                count += ((WrapperParameter) param).getWrapperChildren().size();
            } else {
                count++;
            }
        }

        for (Parameter param : responseParams) {
            if (param.isWrapperStyle()) {
                for (Parameter wc : ((WrapperParameter) param).getWrapperChildren()) {
                    if (!wc.isResponse() && wc.isOUT()) {
                        count++;
                    }
                }
            } else if (!param.isResponse() && param.isOUT()) {
                count++;
            }
        }

        return count;
    }

    /**
     * @param ce
     */
    public void addException(CheckedException ce) {
        if (!exceptions.contains(ce))
            exceptions.add(ce);
    }

    /**
     * @param exceptionClass
     * @return CheckedException corresponding to the exceptionClass. Returns
     *         null if not found.
     */
    public CheckedException getCheckedException(Class exceptionClass) {
        for (CheckedException ce : exceptions) {
            if (ce.getExcpetionClass().equals(exceptionClass))
                return ce;
        }
        return null;
    }

    /**
     * @return a list of checked Exceptions thrown by this method
     */
    public List<CheckedException> getCheckedExceptions(){
        return Collections.unmodifiableList(exceptions);
    }
    /**
     * @param detailType
     * @return Gets the CheckedException corresponding to detailType. Returns
     *         null if no CheckedExcpetion with the detailType found.
     */
    public CheckedException getCheckedException(TypeReference detailType) {
        for (CheckedException ce : exceptions) {
            TypeReference actual = ce.getDetailType();
            if (actual.tagName.equals(detailType.tagName)
                    && actual.type.getClass().getName()
                            .equals(detailType.type.getClass().getName())) {
                return ce;
            }
        }
        return null;
    }

    /**
     * Returns if the java method MEP is async
     * @return if this is an Asynch MEP
     */
    public boolean isAsync(){
        return mep == MessageStruct.ASYNC_CALLBACK_MEP || mep == MessageStruct.ASYNC_POLL_MEP;
    }

    private List<CheckedException> exceptions = new ArrayList<CheckedException>();
    private Method method;
    private final List<Parameter> requestParams = new ArrayList<Parameter>();
    private final List<Parameter> responseParams = new ArrayList<Parameter>();
    private final List<Parameter> unmReqParams =
            Collections.unmodifiableList(requestParams);
    private final List<Parameter> unmResParams =
            Collections.unmodifiableList(responseParams);
    private Object binding;
    private int mep;
    private String operationName;
}

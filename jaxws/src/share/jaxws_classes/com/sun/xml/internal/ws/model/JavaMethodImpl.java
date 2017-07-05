/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.model;

import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.ws.api.databinding.MetadataReader;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.model.MEP;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLFault;
import com.sun.xml.internal.ws.api.model.soap.SOAPBinding;
import com.sun.xml.internal.ws.model.soap.SOAPBindingImpl;
import com.sun.xml.internal.ws.spi.db.TypeInfo;
import com.sun.xml.internal.ws.wsdl.ActionBasedOperationSignature;
import com.sun.istack.internal.Nullable;

import javax.xml.namespace.QName;
import javax.xml.ws.Action;
import javax.xml.ws.WebServiceException;
import javax.jws.WebMethod;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Build this runtime model using java SEI and annotations
 *
 * @author Vivek Pandey
 */
public final class JavaMethodImpl implements JavaMethod {

    private String inputAction = "";
    private String outputAction = "";
    private final List<CheckedExceptionImpl> exceptions = new ArrayList<CheckedExceptionImpl>();
    private final Method method;
    /*package*/ final List<ParameterImpl> requestParams = new ArrayList<ParameterImpl>();
    /*package*/ final List<ParameterImpl> responseParams = new ArrayList<ParameterImpl>();
    private final List<ParameterImpl> unmReqParams = Collections.unmodifiableList(requestParams);
    private final List<ParameterImpl> unmResParams = Collections.unmodifiableList(responseParams);
    private SOAPBinding binding;
    private MEP mep;
    private QName operationName;
    private WSDLBoundOperation wsdlOperation;
    /*package*/ final AbstractSEIModelImpl owner;
    private final Method seiMethod;
    private QName requestPayloadName;
    private String soapAction;

    /**
     * @param owner
     * @param method : Implementation class method
     * @param seiMethod : corresponding SEI Method.
     *                  Is there is no SEI, it should be Implementation class method
     */
    public JavaMethodImpl(AbstractSEIModelImpl owner, Method method, Method seiMethod, MetadataReader metadataReader) {
        this.owner = owner;
        this.method = method;
        this.seiMethod = seiMethod;
        setWsaActions(metadataReader);
    }

    private void setWsaActions(MetadataReader metadataReader) {
        Action action = (metadataReader != null)? metadataReader.getAnnotation(Action.class, seiMethod):seiMethod.getAnnotation(Action.class);
        if(action != null) {
            inputAction = action.input();
            outputAction = action.output();
        }

        //@Action(input) =="", get it from @WebMethod(action)
        WebMethod webMethod = (metadataReader != null)? metadataReader.getAnnotation(WebMethod.class, seiMethod):seiMethod.getAnnotation(WebMethod.class);
        soapAction = "";
        if (webMethod != null )
            soapAction = webMethod.action();
        if(!soapAction.equals("")) {
            //non-empty soapAction
            if(inputAction.equals(""))
                // set input action to non-empty soapAction
                inputAction = soapAction;
            else if(!inputAction.equals(soapAction)){
                //both are explicitly set via annotations, make sure @Action == @WebMethod.action
                //http://java.net/jira/browse/JAX_WS-1108
              //throw new WebServiceException("@Action and @WebMethod(action=\"\" does not match on operation "+ method.getName());
            }
        }
    }

    public ActionBasedOperationSignature getOperationSignature() {
        QName qname = getRequestPayloadName();
        if (qname == null) qname = new QName("", "");
        return new ActionBasedOperationSignature(getInputAction(), qname);
    }

    public SEIModel getOwner() {
        return owner;
    }

    /**
     * @see JavaMethod
     *
     * @return Returns the method.
     */
    public Method getMethod() {
        return method;
    }

    /**
     * @see JavaMethod
     *
     * @return Returns the SEI method where annotations are present
     */
    public Method getSEIMethod() {
        return seiMethod;
    }

    /**
     * @return Returns the mep.
     */
    public MEP getMEP() {
        return mep;
    }

    /**
     * @param mep
     *            The mep to set.
     */
    void setMEP(MEP mep) {
        this.mep = mep;
    }

    /**
     * @return the Binding object
     */
    public SOAPBinding getBinding() {
        if (binding == null)
            return new SOAPBindingImpl();
        return binding;
    }

    /**
     * @param binding
     */
    void setBinding(SOAPBinding binding) {
        this.binding = binding;
    }

    /**
     * Returns the {@link WSDLBoundOperation} Operation associated with {@link JavaMethodImpl}
     * operation.
     * @deprecated
     * @return the WSDLBoundOperation for this JavaMethod
     */
    public WSDLBoundOperation getOperation() {
//        assert wsdlOperation != null;
        return wsdlOperation;
    }

    public void setOperationQName(QName name) {
        this.operationName = name;
    }

    public QName getOperationQName() {
        return (wsdlOperation != null)? wsdlOperation.getName(): operationName;
    }

    public String getSOAPAction() {
        return (wsdlOperation != null)? wsdlOperation.getSOAPAction(): soapAction;
    }

    public String getOperationName() {
        return operationName.getLocalPart();
    }

    public String getRequestMessageName() {
        return getOperationName();
    }

    public String getResponseMessageName() {
        if(mep.isOneWay())
            return null;
        return getOperationName()+"Response";
    }

    public void setRequestPayloadName(QName n)  {
        requestPayloadName = n;
    }

    /**
     * @return soap:Body's first child name for request message.
     */
    public @Nullable QName getRequestPayloadName() {
        return (wsdlOperation != null)? wsdlOperation.getRequestPayloadName(): requestPayloadName;
    }

    /**
     * @return soap:Body's first child name for response message.
     */
    public @Nullable QName getResponsePayloadName() {
        return (mep == MEP.ONE_WAY) ? null : wsdlOperation.getResponsePayloadName();
    }

    /**
     * @return returns unmodifiable list of request parameters
     */
    public List<ParameterImpl> getRequestParameters() {
        return unmReqParams;
    }

    /**
     * @return returns unmodifiable list of response parameters
     */
    public List<ParameterImpl> getResponseParameters() {
        return unmResParams;
    }

    void addParameter(ParameterImpl p) {
        if (p.isIN() || p.isINOUT()) {
            assert !requestParams.contains(p);
            requestParams.add(p);
        }

        if (p.isOUT() || p.isINOUT()) {
            // this check is only for out parameters
            assert !responseParams.contains(p);
            responseParams.add(p);
        }
    }

    void addRequestParameter(ParameterImpl p){
        if (p.isIN() || p.isINOUT()) {
            requestParams.add(p);
        }
    }

    void addResponseParameter(ParameterImpl p){
        if (p.isOUT() || p.isINOUT()) {
            responseParams.add(p);
        }
    }

    /**
     * @return Returns number of java method parameters - that will be all the
     *         IN, INOUT and OUT holders
     *
     * @deprecated no longer use in the new architecture
     */
    public int getInputParametersCount() {
        int count = 0;
        for (ParameterImpl param : requestParams) {
            if (param.isWrapperStyle()) {
                count += ((WrapperParameter) param).getWrapperChildren().size();
            } else {
                count++;
            }
        }

        for (ParameterImpl param : responseParams) {
            if (param.isWrapperStyle()) {
                for (ParameterImpl wc : ((WrapperParameter) param).getWrapperChildren()) {
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
    void addException(CheckedExceptionImpl ce) {
        if (!exceptions.contains(ce))
            exceptions.add(ce);
    }

    /**
     * @param exceptionClass
     * @return CheckedException corresponding to the exceptionClass. Returns
     *         null if not found.
     */
    public CheckedExceptionImpl getCheckedException(Class exceptionClass) {
        for (CheckedExceptionImpl ce : exceptions) {
            if (ce.getExceptionClass()==exceptionClass)
                return ce;
        }
        return null;
    }


    /**
     * @return a list of checked Exceptions thrown by this method
     */
    public List<CheckedExceptionImpl> getCheckedExceptions(){
        return Collections.unmodifiableList(exceptions);
    }

    public String getInputAction() {
//        return (wsdlOperation != null)? wsdlOperation.getOperation().getInput().getAction(): inputAction;
        return inputAction;
    }

    public String getOutputAction() {
//        return (wsdlOperation != null)? wsdlOperation.getOperation().getOutput().getAction(): outputAction;
        return outputAction;
    }

    /**
     * @deprecated
     * @param detailType
     * @return Gets the CheckedException corresponding to detailType. Returns
     *         null if no CheckedExcpetion with the detailType found.
     */
    public CheckedExceptionImpl getCheckedException(TypeReference detailType) {
        for (CheckedExceptionImpl ce : exceptions) {
            TypeInfo actual = ce.getDetailType();
            if (actual.tagName.equals(detailType.tagName) && actual.type==detailType.type) {
                return ce;
            }
        }
        return null;
    }



    /**
     * Returns if the java method  is async
     * @return if this is an Asynch
     */
    public boolean isAsync(){
        return mep.isAsync;
    }

    /*package*/ void freeze(WSDLPort portType) {
        this.wsdlOperation = portType.getBinding().get(new QName(portType.getBinding().getPortType().getName().getNamespaceURI(),getOperationName()));
        // TODO: replace this with proper error handling
        if(wsdlOperation ==null)
            throw new WebServiceException("Method "+seiMethod.getName()+" is exposed as WebMethod, but there is no corresponding wsdl operation with name "+operationName+" in the wsdl:portType" + portType.getBinding().getPortType().getName());

        //so far, the inputAction, outputAction and fault actions are set from the @Action and @FaultAction
        //set the values from WSDLModel, if such annotations are not present or defaulted
        if(inputAction.equals("")) {
                inputAction = wsdlOperation.getOperation().getInput().getAction();
        } else if(!inputAction.equals(wsdlOperation.getOperation().getInput().getAction()))
                //TODO input action might be from @Action or WebMethod(action)
                LOGGER.warning("Input Action on WSDL operation "+wsdlOperation.getName().getLocalPart() + " and @Action on its associated Web Method " + seiMethod.getName() +" did not match and will cause problems in dispatching the requests");

        if (!mep.isOneWay()) {
            if (outputAction.equals(""))
                outputAction = wsdlOperation.getOperation().getOutput().getAction();

            for (CheckedExceptionImpl ce : exceptions) {
                if (ce.getFaultAction().equals("")) {
                    QName detailQName = ce.getDetailType().tagName;
                    WSDLFault wsdlfault = wsdlOperation.getOperation().getFault(detailQName);
                    if(wsdlfault == null) {
                        // mismatch between wsdl model and SEI model, log a warning and use  SEI model for Action determination
                        LOGGER.warning("Mismatch between Java model and WSDL model found, For wsdl operation " +
                                wsdlOperation.getName() + ",There is no matching wsdl fault with detail QName " +
                                ce.getDetailType().tagName);
                        ce.setFaultAction(ce.getDefaultFaultAction());
                    } else {
                        ce.setFaultAction(wsdlfault.getAction());
                    }
                }
            }
        }
    }

    final void fillTypes(List<TypeInfo> types) {
        fillTypes(requestParams, types);
        fillTypes(responseParams, types);

        for (CheckedExceptionImpl ce : exceptions) {
            types.add(ce.getDetailType());
        }
    }

    private void fillTypes(List<ParameterImpl> params, List<TypeInfo> types) {
        for (ParameterImpl p : params) {
            p.fillTypes(types);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(com.sun.xml.internal.ws.model.JavaMethodImpl.class.getName());

}

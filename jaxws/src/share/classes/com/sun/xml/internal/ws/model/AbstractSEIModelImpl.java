/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.client.WSServiceDelegate;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.model.wsdl.WSDLBoundOperationImpl;
import com.sun.xml.internal.ws.model.wsdl.WSDLBoundPortTypeImpl;
import com.sun.xml.internal.ws.model.wsdl.WSDLPartImpl;
import com.sun.xml.internal.ws.model.wsdl.WSDLPortImpl;
import com.sun.xml.internal.ws.resources.ModelerMessages;
import com.sun.xml.internal.ws.util.Pool;

import javax.jws.WebParam.Mode;
import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.*;

/**
 * model of the web service.  Used by the runtime marshall/unmarshall
 * web service invocations
 *
 * $author: JAXWS Development Team
 */
public abstract class AbstractSEIModelImpl implements SEIModel {

    void postProcess() {
        // should be called only once.
        if (jaxbContext != null)
            return;
        populateMaps();
        createJAXBContext();
    }

    /**
     * Link {@link SEIModel} to {@link WSDLModel}.
     * Merge it with {@link #postProcess()}.
     */
    public void freeze(WSDLPortImpl port) {
        this.port = port;
        for (JavaMethodImpl m : javaMethods) {
            m.freeze(port);
        }
    }

    /**
     * Populate methodToJM and nameToJM maps.
     */
    abstract protected void populateMaps();

    public Pool.Marshaller getMarshallerPool() {
        return marshallers;
    }

    /**
     * @return the <code>JAXBRIContext</code>
     */
    public JAXBRIContext getJAXBContext() {
        return jaxbContext;
    }

    /**
     * @return the known namespaces from JAXBRIContext
     */
    public List<String> getKnownNamespaceURIs() {
        return knownNamespaceURIs;
    }

    /**
     * @return the <code>Bridge</code> for the <code>type</code>
     */
    public final Bridge getBridge(TypeReference type) {
        Bridge b = bridgeMap.get(type);
        assert b!=null; // we should have created Bridge for all TypeReferences known to this model
        return b;
    }

    private JAXBRIContext createJAXBContext() {
        final List<TypeReference> types = getAllTypeReferences();
        final Class[] cls = new Class[types.size() + additionalClasses.size()];
        int i = 0;
        for(Class additionalClass:additionalClasses){
            cls[i++] = additionalClass;
        }

        for (TypeReference type : types) {
            cls[i++] = (Class) type.type;
        }

        try {
            //jaxbContext = JAXBRIContext.newInstance(cls, types, targetNamespace, false);
            // Need to avoid doPriv block once JAXB is fixed. Afterwards, use the above
            jaxbContext = AccessController.doPrivileged(new PrivilegedExceptionAction<JAXBRIContext>() {
                public JAXBRIContext run() throws Exception {
                    return JAXBRIContext.newInstance(cls, types, null, targetNamespace, false, null);
                }
            });
            createBridgeMap(types);
        } catch (PrivilegedActionException e) {
            throw new WebServiceException(ModelerMessages.UNABLE_TO_CREATE_JAXB_CONTEXT(), e);
        }
        knownNamespaceURIs = new ArrayList<String>();
        for (String namespace : jaxbContext.getKnownNamespaceURIs()) {
            if (namespace.length() > 0) {
                if (!namespace.equals(SOAPNamespaceConstants.XSD) && !namespace.equals(SOAPNamespaceConstants.XMLNS))
                    knownNamespaceURIs.add(namespace);
            }
        }

        marshallers = new Pool.Marshaller(jaxbContext);

        return jaxbContext;
    }

    /**
     * @return returns non-null list of TypeReference
     */
    private List<TypeReference> getAllTypeReferences() {
        List<TypeReference> types = new ArrayList<TypeReference>();
        Collection<JavaMethodImpl> methods = methodToJM.values();
        for (JavaMethodImpl m : methods) {
            m.fillTypes(types);
        }
        return types;
    }

    private void createBridgeMap(List<TypeReference> types) {
        for (TypeReference type : types) {
            Bridge bridge = jaxbContext.createBridge(type);
            bridgeMap.put(type, bridge);
        }
    }

    /**
     * @return the <code>Method</code> for a given WSDLOperation <code>qname</code>
     */
    public Method getDispatchMethod(QName qname) {
        //handle the empty body
        if (qname == null)
            qname = emptyBodyName;
        JavaMethodImpl jm = getJavaMethod(qname);
        if (jm != null) {
            return jm.getMethod();
        }
        return null;
    }

    /**
     * @return true if <code>name</code> is the name
     * of a known fault name for the <code>Method method</code>
     */
    public boolean isKnownFault(QName name, Method method) {
        JavaMethodImpl m = getJavaMethod(method);
        for (CheckedExceptionImpl ce : m.getCheckedExceptions()) {
            if (ce.getDetailType().tagName.equals(name))
                return true;
        }
        return false;
    }

    /**
     * @return true if <code>ex</code> is a Checked Exception
     * for <code>Method m</code>
     */
    public boolean isCheckedException(Method m, Class ex) {
        JavaMethodImpl jm = getJavaMethod(m);
        for (CheckedExceptionImpl ce : jm.getCheckedExceptions()) {
            if (ce.getExceptionClass().equals(ex))
                return true;
        }
        return false;
    }

    /**
     * @return the <code>JavaMethod</code> representing the <code>method</code>
     */
    public JavaMethodImpl getJavaMethod(Method method) {
        return methodToJM.get(method);
    }

    /**
     * @return the <code>JavaMethod</code> associated with the
     * operation named name
     */
    public JavaMethodImpl getJavaMethod(QName name) {
        return nameToJM.get(name);
    }

    /**
     * @return the <code>QName</code> associated with the
     * JavaMethod jm
     */
    public QName getQNameForJM(JavaMethodImpl jm) {
        for (QName key : nameToJM.keySet()) {
            JavaMethodImpl jmethod = nameToJM.get(key);
            if (jmethod.getOperationName().equals(jm.getOperationName())){
               return key;
            }
        }
        return null;
    }

    /**
     * @return a <code>Collection</code> of <code>JavaMethods</code>
     * associated with this <code>RuntimeModel</code>
     */
    public final Collection<JavaMethodImpl> getJavaMethods() {
        return Collections.unmodifiableList(javaMethods);
    }

    void addJavaMethod(JavaMethodImpl jm) {
        if (jm != null)
            javaMethods.add(jm);
    }

    /**
     * Used from {@link WSServiceDelegate}
     * to apply the binding information from WSDL after the model is created frm SEI class on the client side. On the server
     * side all the binding information is available before modeling and this method is not used.
     *
     * @deprecated To be removed once client side new architecture is implemented
     */
    public void applyParameterBinding(WSDLBoundPortTypeImpl wsdlBinding){
        if(wsdlBinding == null)
            return;

        for(JavaMethodImpl method : javaMethods){
            if(method.isAsync())
                continue;
            QName opName = new QName(wsdlBinding.getPortTypeName().getNamespaceURI(), method.getOperationName());

            //patch the soapaction correctly from the WSDL
            WSDLBoundOperationImpl bo = wsdlBinding.get(opName);
            String action = bo.getSOAPAction();
            method.getBinding().setSOAPAction(action);

            boolean isRpclit = method.getBinding().isRpcLit();
            List<ParameterImpl> reqParams = method.requestParams;
            List<ParameterImpl> reqAttachParams = null;
            for(ParameterImpl param:reqParams){
                if(param.isWrapperStyle()){
                    if(isRpclit){
                        WrapperParameter reqParam = (WrapperParameter)param;
                        if(bo.getRequestNamespace() != null){
                            patchRpclitNamespace(bo.getRequestNamespace(), reqParam);
                        }
                        reqAttachParams = applyRpcLitParamBinding(method, (WrapperParameter)param, wsdlBinding, Mode.IN);
                    }
                    continue;
                }
                String partName = param.getPartName();
                if(partName == null)
                    continue;
                ParameterBinding paramBinding = wsdlBinding.getBinding(opName, partName, Mode.IN);
                if(paramBinding != null)
                    param.setInBinding(paramBinding);
            }

            List<ParameterImpl> resAttachParams = null;
            List<ParameterImpl> resParams = method.responseParams;
            for(ParameterImpl param:resParams){
                if(param.isWrapperStyle()){
                    if(isRpclit){
                        WrapperParameter respParam = (WrapperParameter)param;
                        if(bo.getResponseNamespace() != null){
                            patchRpclitNamespace(bo.getResponseNamespace(), respParam);
                        }
                        resAttachParams = applyRpcLitParamBinding(method, (WrapperParameter)param, wsdlBinding, Mode.OUT);
                    }
                    continue;
                }
                //if the parameter is not inout and its header=true then dont get binding from WSDL
//                if(!param.isINOUT() && param.getBinding().isHeader())
//                    continue;
                String partName = param.getPartName();
                if(partName == null)
                    continue;
                ParameterBinding paramBinding = wsdlBinding.getBinding(opName,
                        partName, Mode.OUT);
                if(paramBinding != null)
                    param.setOutBinding(paramBinding);
            }
            if(reqAttachParams != null){
                for(ParameterImpl p : reqAttachParams){
                    method.addRequestParameter(p);
                }
            }
            if(resAttachParams != null){
                for(ParameterImpl p : resAttachParams){
                    method.addResponseParameter(p);
                }
            }

        }
    }

    /**
     * For rpclit wrapper element inside <soapenv:Body>, the targetNamespace should be taked from
     * the soapbind:body@namespace value. Since no annotations on SEI/impl class captures it so we
     * need to get it from WSDL and patch it.     *
     */
    private void patchRpclitNamespace(String namespace, WrapperParameter param){
        TypeReference type = param.getTypeReference();
        TypeReference newType = new TypeReference(new QName(namespace, type.tagName.getLocalPart()), type.type, type.annotations);
        param.setTypeReference(newType);
    }

    /**
     * Applies binding related information to the RpcLitPayload. The payload map is populated correctl
     * @return
     * Returns attachment parameters if/any.
     */
    private List<ParameterImpl> applyRpcLitParamBinding(JavaMethodImpl method, WrapperParameter wrapperParameter, WSDLBoundPortTypeImpl boundPortType, Mode mode) {
        QName opName = new QName(boundPortType.getPortTypeName().getNamespaceURI(), method.getOperationName());
        WSDLBoundOperationImpl bo = boundPortType.get(opName);
        Map<Integer, ParameterImpl> bodyParams = new HashMap<Integer, ParameterImpl>();
        List<ParameterImpl> unboundParams = new ArrayList<ParameterImpl>();
        List<ParameterImpl> attachParams = new ArrayList<ParameterImpl>();
        for(ParameterImpl param : wrapperParameter.wrapperChildren){
            String partName = param.getPartName();
            if(partName == null)
                continue;

            ParameterBinding paramBinding = boundPortType.getBinding(opName,
                    partName, mode);
            if(paramBinding != null){
                if(mode == Mode.IN)
                    param.setInBinding(paramBinding);
                else if(mode == Mode.OUT || mode == Mode.INOUT)
                    param.setOutBinding(paramBinding);

                if(paramBinding.isUnbound()){
                        unboundParams.add(param);
                } else if(paramBinding.isAttachment()){
                    attachParams.add(param);
                }else if(paramBinding.isBody()){
                    if(bo != null){
                        WSDLPartImpl p = bo.getPart(param.getPartName(), mode);
                        if(p != null)
                            bodyParams.put(p.getIndex(), param);
                        else
                            bodyParams.put(bodyParams.size(), param);
                    }else{
                        bodyParams.put(bodyParams.size(), param);
                    }
                }
            }

        }
        wrapperParameter.clear();
        for(int i = 0; i <  bodyParams.size();i++){
            ParameterImpl p = bodyParams.get(i);
            wrapperParameter.addWrapperChild(p);
        }

        //add unbounded parts
        for(ParameterImpl p:unboundParams){
            wrapperParameter.addWrapperChild(p);
        }
        return attachParams;
    }


    void put(QName name, JavaMethodImpl jm) {
        nameToJM.put(name, jm);
    }

    void put(Method method, JavaMethodImpl jm) {
        methodToJM.put(method, jm);
    }

    public String getWSDLLocation() {
        return wsdlLocation;
    }

    void setWSDLLocation(String location) {
        wsdlLocation = location;
    }

    public QName getServiceQName() {
        return serviceName;
    }

    public WSDLPort getPort() {
        return port;
    }

    public QName getPortName() {
        return portName;
    }

    public QName getPortTypeName() {
        return portTypeName;
    }

    void setServiceQName(QName name) {
        serviceName = name;
    }

    void setPortName(QName name) {
        portName = name;
    }

    void setPortTypeName(QName name) {
        portTypeName = name;
    }

    /**
     * This is the targetNamespace for the WSDL containing the PortType
     * definition
     */
    void setTargetNamespace(String namespace) {
        targetNamespace = namespace;
    }

    /**
     * This is the targetNamespace for the WSDL containing the PortType
     * definition
     */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    @NotNull
    public QName getBoundPortTypeName() {
        assert portName != null;
        return new QName(portName.getNamespaceURI(), portName.getLocalPart()+"Binding");
    }

    /**
     * Sets additional classes obtained from {@link javax.xml.bind.annotation.XmlSeeAlso} annotation. In starting
     * from wsdl case these classes would most likely be JAXB ObjectFactory that references other classes.
     */
    public void setAdditionalClasses(Class[] additionalClasses) {
        for(Class cls:additionalClasses){
            this.additionalClasses.add(cls);
        }
    }

    private List<Class> additionalClasses = new ArrayList<Class>();

    private Pool.Marshaller marshallers;
    protected JAXBRIContext jaxbContext;
    private String wsdlLocation;
    private QName serviceName;
    private QName portName;
    private QName portTypeName;
    private Map<Method,JavaMethodImpl> methodToJM = new HashMap<Method, JavaMethodImpl>();
    private Map<QName,JavaMethodImpl> nameToJM = new HashMap<QName, JavaMethodImpl>();
    private List<JavaMethodImpl> javaMethods = new ArrayList<JavaMethodImpl>();
    private final Map<TypeReference, Bridge> bridgeMap = new HashMap<TypeReference, Bridge>();
    protected final QName emptyBodyName = new QName("");
    private String targetNamespace = "";
    private List<String> knownNamespaceURIs = null;
    private WSDLPortImpl port;
}

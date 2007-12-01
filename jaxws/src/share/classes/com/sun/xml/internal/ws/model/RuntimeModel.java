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

import com.sun.xml.internal.ws.pept.ept.MessageInfo;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.api.BridgeContext;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.bind.api.RawAccessor;
import com.sun.xml.internal.ws.encoding.JAXWSAttachmentMarshaller;
import com.sun.xml.internal.ws.encoding.JAXWSAttachmentUnmarshaller;
import com.sun.xml.internal.ws.encoding.jaxb.JAXBBridgeInfo;
import com.sun.xml.internal.ws.encoding.jaxb.RpcLitPayload;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.wsdl.parser.Binding;
import com.sun.xml.internal.ws.wsdl.parser.Part;
import com.sun.xml.internal.ws.wsdl.parser.BindingOperation;
import com.sun.xml.internal.ws.wsdl.writer.WSDLGenerator;
import com.sun.xml.internal.ws.model.soap.SOAPBinding;

import javax.xml.namespace.QName;
import javax.xml.ws.WebServiceException;

import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * model of the web service.  Used by the runtime marshall/unmarshall
 * web service invocations
 *
 * $author: JAXWS Development Team
 */
public abstract class RuntimeModel {

    /**
     *
     */
    public RuntimeModel() {
        super();
        // TODO Auto-generated constructor stub
    }

    public void postProcess() {
        // should be called only once.
        if (jaxbContext != null)
            return;
        populateMaps();
        populateAsyncExceptions();
        createJAXBContext();
        createDecoderInfo();
    }

    /**
     * Populate methodToJM and nameToJM maps.
     */
    protected void populateMaps() {
        for (JavaMethod jm : getJavaMethods()) {
            put(jm.getMethod(), jm);
            for (Parameter p : jm.getRequestParameters()) {
                put(p.getName(), jm);
            }
        }
    }

    protected void populateAsyncExceptions() {
        for (JavaMethod jm : getJavaMethods()) {
            int mep = jm.getMEP();
            if (mep == MessageInfo.ASYNC_CALLBACK_MEP || mep == MessageInfo.ASYNC_POLL_MEP) {
                String opName = jm.getOperationName();
                Method m = jm.getMethod();
                Class[] params = m.getParameterTypes();
                if (mep == MessageInfo.ASYNC_CALLBACK_MEP) {
                    params = new Class[params.length-1];
                    System.arraycopy(m.getParameterTypes(), 0, params, 0, m.getParameterTypes().length-1);
                }
                try {
                    Method om = m.getDeclaringClass().getMethod(opName, params);
                    JavaMethod jm2 = getJavaMethod(om);
                    for (CheckedException ce : jm2.getCheckedExceptions()) {
                        jm.addException(ce);
                    }
                } catch (NoSuchMethodException ex) {
                }
            }
        }
    }

    /**
     * @return the <code>BridgeContext</code> for this <code>RuntimeModel</code>
     */
    public BridgeContext getBridgeContext() {
        if (jaxbContext == null)
            return null;
        BridgeContext bc = bridgeContext.get();
        if (bc == null) {
            bc = jaxbContext.createBridgeContext();
            bc.setAttachmentMarshaller(new JAXWSAttachmentMarshaller(enableMtom));
            bc.setAttachmentUnmarshaller(new JAXWSAttachmentUnmarshaller());
            bridgeContext.set(bc);
        }
        return bc;
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
     * @param type
     * @return the <code>Bridge</code> for the <code>type</code>
     */
    public Bridge getBridge(TypeReference type) {
        return bridgeMap.get(type);
    }

    /**
     * @param name
     * @return either a <code>RpcLitpayload</code> or a <code>JAXBBridgeInfo</code> for
     * an operation named <code>name</code>
     */
    public Object getDecoderInfo(QName name) {
        Object obj = payloadMap.get(name);
        if (obj instanceof RpcLitPayload) {
            return RpcLitPayload.copy((RpcLitPayload) obj);
        } else if (obj instanceof JAXBBridgeInfo) {
            return JAXBBridgeInfo.copy((JAXBBridgeInfo) obj);
        }
        return null;
    }

    /**
     * @param name
     * @param payload
     */
    public void addDecoderInfo(QName name, Object payload) {
        payloadMap.put(name, payload);
    }

    /**
     * @return
     */
    private JAXBRIContext createJAXBContext() {
        final List<TypeReference> types = getAllTypeReferences();
        final Class[] cls = new Class[types.size()];
        final String ns = targetNamespace;
        int i = 0;
        for (TypeReference type : types) {
            cls[i++] = (Class) type.type;
        }
        try {
            //jaxbContext = JAXBRIContext.newInstance(cls, types, targetNamespace, false);
            // Need to avoid doPriv block once JAXB is fixed. Afterwards, use the above
            jaxbContext = (JAXBRIContext)
                 AccessController.doPrivileged(new PrivilegedExceptionAction() {
                     public java.lang.Object run() throws Exception {
                         return JAXBRIContext.newInstance(cls, types, ns, false);
                     }
                 });
            createBridgeMap(types);
        } catch (PrivilegedActionException e) {
            throw new WebServiceException(e.getMessage(), e.getException());
        }
        knownNamespaceURIs = new ArrayList<String>();
        for (String namespace : jaxbContext.getKnownNamespaceURIs()) {
            if (namespace.length() > 0) {
                if (!namespace.equals(SOAPNamespaceConstants.XSD) && !namespace.equals(SOAPNamespaceConstants.XMLNS))
                    knownNamespaceURIs.add(namespace);
            }
        }

        return jaxbContext;
    }

    /**
     * @return returns non-null list of TypeReference
     */
    public List<TypeReference> getAllTypeReferences() {
        List<TypeReference> types = new ArrayList<TypeReference>();
        Collection<JavaMethod> methods = methodToJM.values();
        for (JavaMethod m : methods) {
            fillTypes(m, types);
            fillFaultDetailTypes(m, types);
        }
        return types;
    }

    private void fillFaultDetailTypes(JavaMethod m, List<TypeReference> types) {
        for (CheckedException ce : m.getCheckedExceptions()) {
            types.add(ce.getDetailType());
//            addGlobalType(ce.getDetailType());
        }
    }

    protected void fillTypes(JavaMethod m, List<TypeReference> types) {
        addTypes(m.getRequestParameters(), types);
        addTypes(m.getResponseParameters(), types);
    }

    private void addTypes(List<Parameter> params, List<TypeReference> types) {
        for (Parameter p : params) {
            types.add(p.getTypeReference());
        }
    }

    private void createBridgeMap(List<TypeReference> types) {
        for (TypeReference type : types) {
            Bridge bridge = jaxbContext.createBridge(type);
            bridgeMap.put(type, bridge);
        }
    }

    /**
     * @param qname
     * @return the <code>Method</code> for a given Operation <code>qname</code>
     */
    public Method getDispatchMethod(QName qname) {
        //handle the empty body
        if (qname == null)
            qname = emptyBodyName;
        JavaMethod jm = getJavaMethod(qname);
        if (jm != null) {
            return jm.getMethod();
        }
        return null;
    }

    /**
     * @param name
     * @param method
     * @return true if <code>name</code> is the name
     * of a known fault name for the <code>Method method</code>
     */
    public boolean isKnownFault(QName name, Method method) {
        JavaMethod m = getJavaMethod(method);
        for (CheckedException ce : m.getCheckedExceptions()) {
            if (ce.getDetailType().tagName.equals(name))
                return true;
        }
        return false;
    }

    /**
     * @param m
     * @param ex
     * @return true if <code>ex</code> is a Checked Exception
     * for <code>Method m</code>
     */
    public boolean isCheckedException(Method m, Class ex) {
        JavaMethod jm = getJavaMethod(m);
        for (CheckedException ce : jm.getCheckedExceptions()) {
            if (ce.getExcpetionClass().equals(ex))
                return true;
        }
        return false;
    }

    /**
     * @param method
     * @return the <code>JavaMethod</code> representing the <code>method</code>
     */
    public JavaMethod getJavaMethod(Method method) {
        return methodToJM.get(method);
    }

    /**
     * @param name
     * @return the <code>JavaMethod</code> associated with the
     * operation named name
     */
    public JavaMethod getJavaMethod(QName name) {
        return nameToJM.get(name);
    }

    /**
     * @param jm
     * @return the <code>QName</code> associated with the
     * JavaMethod jm
     */
    public QName getQNameForJM(JavaMethod jm) {
        Set<QName> set = nameToJM.keySet();
        Iterator iter = set.iterator();
        while (iter.hasNext()){
            QName key = (QName) iter.next();
            JavaMethod jmethod = (JavaMethod) nameToJM.get(key);
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
    public Collection<JavaMethod> getJavaMethods() {
        return Collections.unmodifiableList(javaMethods);
    }

    public void addJavaMethod(JavaMethod jm) {
        if (jm != null)
            javaMethods.add(jm);
    }

    public void applyParameterBinding(Binding wsdlBinding){
        if(wsdlBinding == null)
            return;
        wsdlBinding.finalizeBinding();
        for(JavaMethod method : javaMethods){
            if(method.isAsync())
                continue;
            boolean isRpclit = ((SOAPBinding)method.getBinding()).isRpcLit();
            List<Parameter> reqParams = method.getRequestParameters();
            List<Parameter> reqAttachParams = null;
            for(Parameter param:reqParams){
                if(param.isWrapperStyle()){
                    if(isRpclit){
                        WrapperParameter reqParam = (WrapperParameter)param;
                        BindingOperation bo = wsdlBinding.get(method.getOperationName());
                        if(bo != null && bo.getRequestNamespace() != null){
                            patchRpclitNamespace(bo.getRequestNamespace(), reqParam);
                        }
                        reqAttachParams = applyRpcLitParamBinding(method, reqParam, wsdlBinding, Mode.IN);
                    }
                    continue;
                }
                String partName = param.getPartName();
                if(partName == null)
                    continue;
                ParameterBinding paramBinding = wsdlBinding.getBinding(method.getOperationName(),
                        partName, Mode.IN);
                if(paramBinding != null)
                    param.setInBinding(paramBinding);
            }

            List<Parameter> resAttachParams = null;
            List<Parameter> resParams = method.getResponseParameters();
            for(Parameter param:resParams){
                if(param.isWrapperStyle()){
                    if(isRpclit){
                        WrapperParameter respParam = (WrapperParameter)param;
                        BindingOperation bo = wsdlBinding.get(method.getOperationName());
                        if(bo != null && bo.getResponseNamespace() != null){
                            patchRpclitNamespace(bo.getResponseNamespace(), respParam);
                        }
                        resAttachParams = applyRpcLitParamBinding(method, respParam, wsdlBinding, Mode.OUT);
                    }
                    continue;
                }
                //if the parameter is not inout and its header=true then dont get binding from WSDL
//                if(!param.isINOUT() && param.getBinding().isHeader())
//                    continue;
                String partName = param.getPartName();
                if(partName == null)
                    continue;
                ParameterBinding paramBinding = wsdlBinding.getBinding(method.getOperationName(),
                        partName, Mode.OUT);
                if(paramBinding != null)
                    param.setOutBinding(paramBinding);
            }
            if(reqAttachParams != null){
                for(Parameter p : reqAttachParams){
                    method.addRequestParameter(p);
                }
            }
            if(resAttachParams != null){
                for(Parameter p : resAttachParams){
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
        TypeReference newType = new TypeReference(
                new QName(namespace, type.tagName.getLocalPart()), type.type, type.annotations);

        param.setTypeReference(newType);
    }



    /**
     * Applies binding related information to the RpcLitPayload. The payload map is populated correctly.
     * @param method
     * @param wrapperParameter
     * @param wsdlBinding
     * @param mode
     * @return
     *
     * Returns attachment parameters if/any.
     */
    private List<Parameter> applyRpcLitParamBinding(JavaMethod method, WrapperParameter wrapperParameter, Binding wsdlBinding, Mode mode) {
        String opName = method.getOperationName();
        RpcLitPayload payload = new RpcLitPayload(wrapperParameter.getName());
        BindingOperation bo = wsdlBinding.get(opName);

        Map<Integer, Parameter> bodyParams = new HashMap<Integer, Parameter>();
        List<Parameter> unboundParams = new ArrayList<Parameter>();
        List<Parameter> attachParams = new ArrayList<Parameter>();
        for(Parameter param:wrapperParameter.getWrapperChildren()){
            String partName = param.getPartName();
            if(partName == null)
                continue;

            ParameterBinding paramBinding = wsdlBinding.getBinding(opName,
                    partName, mode);
            if(paramBinding != null){
                if(mode == Mode.IN)
                    param.setInBinding(paramBinding);
                else if(mode == Mode.OUT)
                    param.setOutBinding(paramBinding);

                if(paramBinding.isUnbound()){
                        unboundParams.add(param);
                } else if(paramBinding.isAttachment()){
                    attachParams.add(param);
                }else if(paramBinding.isBody()){
                    if(bo != null){
                        Part p = bo.getPart(param.getPartName(), mode);
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
            Parameter p = bodyParams.get(i);
            wrapperParameter.addWrapperChild(p);
            if(((mode == Mode.IN) && p.getInBinding().isBody())||
                    ((mode == Mode.OUT) && p.getOutBinding().isBody())){
                JAXBBridgeInfo bi = new JAXBBridgeInfo(getBridge(p.getTypeReference()), null);
                payload.addParameter(bi);
            }
        }

        for(Parameter p : attachParams){
            JAXBBridgeInfo bi = new JAXBBridgeInfo(getBridge(p.getTypeReference()), null);
            payloadMap.put(p.getName(), bi);
        }

        //add unbounded parts
        for(Parameter p:unboundParams){
            wrapperParameter.addWrapperChild(p);
        }
        payloadMap.put(wrapperParameter.getName(), payload);
        return attachParams;
    }


    /**
     * @param name
     * @param jm
     */
    protected void put(QName name, JavaMethod jm) {
        nameToJM.put(name, jm);
    }

    /**
     * @param method
     * @param jm
     */
    protected void put(Method method, JavaMethod jm) {
        methodToJM.put(method, jm);
    }

    public String getWSDLLocation() {
        return wsdlLocation;
    }

    public void setWSDLLocation(String location) {
        wsdlLocation = location;
    }

    public QName getServiceQName() {
        return serviceName;
    }

    public QName getPortName() {
        return portName;
    }

    public QName getPortTypeName() {
        return portTypeName;
    }

    public void setServiceQName(QName name) {
        serviceName = name;
    }

    public void setPortName(QName name) {
        portName = name;
    }

    public void setPortTypeName(QName name) {
        portTypeName = name;
    }

    /**
     * This is the targetNamespace for the WSDL containing the PortType
     * definition
     */
    public void setTargetNamespace(String namespace) {
        targetNamespace = namespace;
    }

    /**
     * This is the targetNamespace for the WSDL containing the PortType
     * definition
     */
    public String getTargetNamespace() {
        return targetNamespace;
    }

    /**
     * Add a global type.  Global types will be used to generate global
     * elements in the generated schema's
     * @param typeReference
     */
/*    public void addGlobalType(TypeReference typeReference) {

    }*/

    /**
     * Add a global type.  Global types will be used to generate global
     * elements in the generated schema's
     * @return
     */
/*    public Collection<TypeReference> getGlobalTypes() {
        return globalTypes;
    }*/


    /**
     * Mtom processing is disabled by default. To enable it the RuntimeModel creator must call it to enable it.
     * @param enableMtom
     */
    public void enableMtom(boolean enableMtom){
        this.enableMtom = enableMtom;
    }

    public Map<Integer, RawAccessor> getRawAccessorMap() {
        return rawAccessorMap;
    }

    protected abstract void createDecoderInfo();

    private boolean enableMtom = false;
    private ThreadLocal<BridgeContext> bridgeContext = new ThreadLocal<BridgeContext>();
    protected JAXBRIContext jaxbContext;
    private String wsdlLocation;
    private QName serviceName;
    private QName portName;
    private QName portTypeName;
    private Map<Method, JavaMethod> methodToJM = new HashMap<Method, JavaMethod>();
    private Map<QName, JavaMethod> nameToJM = new HashMap<QName, JavaMethod>();
    private List<JavaMethod> javaMethods = new ArrayList<JavaMethod>();
    private final Map<TypeReference, Bridge> bridgeMap = new HashMap<TypeReference, Bridge>();
    private final Map<QName, Object> payloadMap = new HashMap<QName, Object>();
    protected final QName emptyBodyName = new QName("");
    private String targetNamespace = "";
    private final Map<Integer, RawAccessor> rawAccessorMap = new HashMap<Integer, RawAccessor>();
    private List<String> knownNamespaceURIs = null;
//    protected Collection<TypeReference> globalTypes = new ArrayList<TypeReference>();
}

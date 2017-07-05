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
import com.sun.xml.internal.bind.api.CompositeStructure;
import com.sun.xml.internal.bind.api.TypeReference;
import com.sun.xml.internal.bind.v2.model.nav.Navigator;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.model.ExceptionType;
import com.sun.xml.internal.ws.api.model.MEP;
import com.sun.xml.internal.ws.api.model.Parameter;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPart;
import com.sun.xml.internal.ws.model.wsdl.WSDLBoundOperationImpl;
import com.sun.xml.internal.ws.model.wsdl.WSDLPortImpl;
import com.sun.xml.internal.ws.model.wsdl.WSDLInputImpl;
import com.sun.xml.internal.ws.resources.ModelerMessages;
import com.sun.xml.internal.ws.util.localization.Localizable;

import javax.jws.Oneway;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebParam.Mode;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.Style;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.namespace.QName;
import javax.xml.ws.Action;
import javax.xml.ws.AsyncHandler;
import javax.xml.ws.FaultAction;
import javax.xml.ws.Holder;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.Response;
import javax.xml.ws.ResponseWrapper;
import javax.xml.ws.WebFault;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.Future;

/**
 * Creates a runtime model of a SEI (portClass).
 *
 * @author WS Developement Team
 */
public class RuntimeModeler {
    private BindingID bindingId;
    private Class portClass;
    private AbstractSEIModelImpl model;
    private com.sun.xml.internal.ws.model.soap.SOAPBindingImpl defaultBinding;
    // can be empty but never null
    private String packageName;
    private String targetNamespace;
    private boolean isWrapped = true;
    private boolean usesWebMethod = false;
    private ClassLoader classLoader = null;
    //private Object implementor;
    private final WSDLPortImpl binding;
    private QName serviceName;
    private QName portName;
    private Map<Class, Boolean> classUsesWebMethod = new HashMap<Class, Boolean>();

    /**
     *
     */
    public static final String PD_JAXWS_PACKAGE_PD  = ".jaxws.";
    /**
     *
     */
    public static final String JAXWS_PACKAGE_PD     = "jaxws.";
    public static final String RESPONSE             = "Response";
    public static final String RETURN               = "return";
    public static final String BEAN                 = "Bean";
    public static final String SERVICE              = "Service";
    public static final String PORT                 = "Port";
    public static final Class HOLDER_CLASS = Holder.class;
    public static final Class<RemoteException> REMOTE_EXCEPTION_CLASS = RemoteException.class;

    /**
     * creates an instance of RunTimeModeler given a <code>portClass</code> and <code>bindingId</code>
     * @param portClass The SEI class to be modeled.
     * @param serviceName The ServiceName to use instead of one calculated from the implementation class
     * @param bindingId The binding identifier to be used when modeling the <code>portClass</code>.
     */
    public RuntimeModeler(@NotNull Class portClass, @NotNull QName serviceName, @NotNull BindingID bindingId) {
        this.portClass = portClass;
        this.serviceName = serviceName;
        this.binding = null;
        this.bindingId = bindingId;
    }

    /**
     *
     * creates an instance of RunTimeModeler given a <code>sei</code> and <code>binding</code>
     * @param sei The SEI class to be modeled.
     * @param serviceName The ServiceName to use instead of one calculated from the implementation class
     * @param wsdlPort {@link com.sun.xml.internal.ws.api.model.wsdl.WSDLPort}
     */
    public RuntimeModeler(@NotNull Class sei, @NotNull QName serviceName, @NotNull WSDLPortImpl wsdlPort){
        this.portClass = sei;
        this.serviceName = serviceName;
        this.bindingId = wsdlPort.getBinding().getBindingId();

        //If the bindingId is null lets default to SOAP 1.1 binding id. As it looks like this bindingId
        //is used latter on from model to generate binding on the WSDL. So defaulting to SOAP 1.1 maybe
        // safe to do.
        if(this.bindingId == null)
            this.bindingId = BindingID.SOAP11_HTTP;

        this.binding = wsdlPort;
    }

    /**
     * sets the classloader to be used when loading classes by the <code>RuntimeModeler</code>.
     * @param classLoader ClassLoader used to load classes
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * sets the PortName to be used by the <code>RuntimeModeler</code>.
     * @param portName The PortName to be used instead of the PortName
     * retrieved via annotations
     */
    public void setPortName(QName portName) {
        this.portName = portName;
    }

    private static <T extends Annotation> T getPrivClassAnnotation(final Class<?> clazz, final Class<T> T) {
        return AccessController.doPrivileged(new PrivilegedAction<T>() {
           public T run() {
               return clazz.getAnnotation(T);
           }
        });
    }

    private static <T extends Annotation> T getPrivMethodAnnotation(final Method method, final Class<T> T) {
        return AccessController.doPrivileged(new PrivilegedAction<T>() {
           public T run() {
               return method.getAnnotation(T);
           }
        });
    }

    private static Annotation[][] getPrivParameterAnnotations(final Method method) {
        return AccessController.doPrivileged(new PrivilegedAction<Annotation[][]>() {
           public Annotation[][] run() {
               return method.getParameterAnnotations();
           }
        });
    }

    //currently has many local vars which will be eliminated after debugging issues
    //first draft
    /**
     * builds the runtime model from the <code>portClass</code> using the binding ID <code>bindingId</code>.
     * @return the runtime model for the <code>portClass</code>.
     */
    public AbstractSEIModelImpl buildRuntimeModel() {
        model = new SOAPSEIModel();
        Class clazz = portClass;
        WebService webService = getPrivClassAnnotation(portClass, WebService.class);
        if (webService == null) {
            throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                portClass.getCanonicalName());
        }
        if (webService.endpointInterface().length() > 0) {
            clazz = getClass(webService.endpointInterface(), ModelerMessages.localizableRUNTIME_MODELER_CLASS_NOT_FOUND(webService.endpointInterface()));
            WebService seiService = getPrivClassAnnotation(clazz, WebService.class);
            if (seiService == null) {
                throw new RuntimeModelerException("runtime.modeler.endpoint.interface.no.webservice",
                    webService.endpointInterface());
            }
        }
        if (serviceName == null)
            serviceName = getServiceName(portClass);
        model.setServiceQName(serviceName);

        String portLocalName  = portClass.getSimpleName()+PORT;
        if (webService.portName().length() >0) {
            portLocalName = webService.portName();
        } else if (webService.name().length() >0) {
            portLocalName = webService.name()+PORT;
        }

        if (portName == null)
            portName = new QName(serviceName.getNamespaceURI(), portLocalName);
        if (!portName.getNamespaceURI().equals(serviceName.getNamespaceURI())) {
            throw new RuntimeModelerException("runtime.modeler.portname.servicename.namespace.mismatch",
                serviceName, portName);
        }
        model.setPortName(portName);

        processClass(clazz);
        if (model.getJavaMethods().size() == 0)
            throw new RuntimeModelerException("runtime.modeler.no.operations",
                    portClass.getName());
        model.postProcess();

        // TODO: this needs to be fixed properly --
        // when we are building RuntimeModel first before building WSDLModel,
        // we still need to do this correctyl
        if(binding!=null)
            model.freeze(binding);

        return model;
    }

    /**
     * utility method to load classes
     * @param className the name of the class to load
     * @param errorMessage
     *      Error message to use when the resolution fails.
     * @return the class specified by <code>className</code>
     */
    private Class getClass(String className, Localizable errorMessage) {
        try {
            if (classLoader == null)
                return Thread.currentThread().getContextClassLoader().loadClass(className);
            else
                return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeModelerException(errorMessage);
        }
    }

    protected void setUsesWebMethod(Class clazz, Boolean usesWebMethod) {
//        System.out.println("class: "+clazz.getName()+" uses WebMethod: "+usesWebMethod);
        classUsesWebMethod.put(clazz, usesWebMethod);
    }

    protected void determineWebMethodUse(Class clazz) {
        if (clazz == null)
            return;
        if (clazz.isInterface()) {
            setUsesWebMethod(clazz, false);
        }
        else {
            WebMethod webMethod;
            boolean hasWebMethod = false;
            for (Method method : clazz.getMethods()) {
                if (method.getDeclaringClass()!=clazz)
                    continue;
                webMethod = getPrivMethodAnnotation(method, WebMethod.class);
                if (webMethod != null &&
                    !webMethod.exclude()) {
                    hasWebMethod = true;
                    break;
                }
            }
            setUsesWebMethod(clazz, hasWebMethod);
        }
        determineWebMethodUse(clazz.getSuperclass());
    }

    void processClass(Class clazz) {
        determineWebMethodUse(clazz);
        WebService webService = getPrivClassAnnotation(clazz, WebService.class);
        String portTypeLocalName  = clazz.getSimpleName();
        if (webService.name().length() >0)
            portTypeLocalName = webService.name();


        targetNamespace = webService.targetNamespace();
        packageName = "";
        if (clazz.getPackage() != null)
            packageName = clazz.getPackage().getName();
        if (targetNamespace.length() == 0) {
            targetNamespace = getNamespace(packageName);
        }
        model.setTargetNamespace(targetNamespace);
        QName portTypeName = new QName(targetNamespace, portTypeLocalName);
        model.setPortTypeName(portTypeName);
        model.setWSDLLocation(webService.wsdlLocation());

        javax.jws.soap.SOAPBinding soapBinding = getPrivClassAnnotation(clazz, javax.jws.soap.SOAPBinding.class);
        if (soapBinding != null) {
            isWrapped = soapBinding.parameterStyle().equals(
                javax.jws.soap.SOAPBinding.ParameterStyle.WRAPPED);
        }
        defaultBinding = createBinding(soapBinding);
        /*
         * if clazz != portClass then there is an SEI.  If there is an
         * SEI, then all methods should be processed.  However, if there is
         * no SEI, and the implementation class uses at least one
         * WebMethod annotation, then only methods with this annotation
         * will be processed.
         */
/*        if (clazz == portClass) {
            WebMethod webMethod;
            for (Method method : clazz.getMethods()) {
                webMethod = getPrivMethodAnnotation(method, WebMethod.class);
                if (webMethod != null &&
                    !webMethod.exclude()) {
                    usesWebMethod = true;
                    break;
                }
            }
        }*/

        for (Method method : clazz.getMethods()) {
            if (method.getDeclaringClass()==Object.class ||
                !isWebMethod(method, clazz)) {
                continue;
            }
            // TODO: binding can be null. We need to figure out how to post-process
            // RuntimeModel to link to WSDLModel
            processMethod(method, webService);
        }
        //Add additional jaxb classes referenced by {@link XmlSeeAlso}
        XmlSeeAlso xmlSeeAlso = getPrivClassAnnotation(clazz, XmlSeeAlso.class);
        if(xmlSeeAlso != null)
            model.setAdditionalClasses(xmlSeeAlso.value());
    }

    protected boolean isWebMethod(Method method, Class clazz) {
        if (clazz.isInterface()) {
            return true;
        }
        Class declClass = method.getDeclaringClass();
        boolean declHasWebService = getPrivClassAnnotation(declClass, WebService.class) != null;
        WebMethod webMethod = getPrivMethodAnnotation(method, WebMethod.class);
        if (webMethod != null && !webMethod.exclude() &&
            declHasWebService) {
            return true;
        }
        return declHasWebService &&
                !classUsesWebMethod.get(declClass);
    }

    /**
     * creates a runtime model <code>SOAPBinding</code> from a <code>javax.jws.soap.SOAPBinding</code> object
     * @param soapBinding the <code>javax.jws.soap.SOAPBinding</code> to model
     * @return returns the runtime model SOAPBinding corresponding to <code>soapBinding</code>
     */
    protected com.sun.xml.internal.ws.model.soap.SOAPBindingImpl createBinding(javax.jws.soap.SOAPBinding soapBinding) {
        com.sun.xml.internal.ws.model.soap.SOAPBindingImpl rtSOAPBinding =
            new com.sun.xml.internal.ws.model.soap.SOAPBindingImpl();
        Style style = soapBinding!=null ? soapBinding.style() : Style.DOCUMENT;
        rtSOAPBinding.setStyle(style);
        assert bindingId != null;
        SOAPVersion soapVersion = bindingId.getSOAPVersion();
        rtSOAPBinding.setSOAPVersion(soapVersion);
        return rtSOAPBinding;
    }

    /**
     * gets the namespace <code>String</code> for a given <code>packageName</code>
     * @param packageName the name of the package used to find a namespace.
     *      can be empty.
     * @return the namespace for the specified <code>packageName</code>
     */
    public static String getNamespace(@NotNull String packageName) {
        if (packageName.length() == 0)
            return null;

        StringTokenizer tokenizer = new StringTokenizer(packageName, ".");
        String[] tokens;
        if (tokenizer.countTokens() == 0) {
            tokens = new String[0];
        } else {
            tokens = new String[tokenizer.countTokens()];
            for (int i=tokenizer.countTokens()-1; i >= 0; i--) {
                tokens[i] = tokenizer.nextToken();
            }
        }
        StringBuilder namespace = new StringBuilder("http://");
        for (int i=0; i<tokens.length; i++) {
            if (i!=0)
                namespace.append('.');
            namespace.append(tokens[i]);
        }
        namespace.append('/');
        return namespace.toString();
    }

    /**
     * creates the runtime model for a method on the <code>portClass</code>
     * @param method the method to model
     * @param webService the instance of the <code>WebService</code> annotation on the <code>portClass</code>
     */
    protected void processMethod(Method method, WebService webService) {
        int mods = method.getModifiers();
        if (!Modifier.isPublic(mods) || Modifier.isStatic(mods)) {
            if(method.getAnnotation(WebMethod.class)!=null) {
                // if the user put @WebMethod on these non-qualifying method,
                // it's an error
                if(Modifier.isStatic(mods))
                    throw new RuntimeModelerException(ModelerMessages.localizableRUNTIME_MODELER_WEBMETHOD_MUST_BE_NONSTATIC(method));
                else
                    throw new RuntimeModelerException(ModelerMessages.localizableRUNTIME_MODELER_WEBMETHOD_MUST_BE_PUBLIC(method));
            }
            return;
        }

        WebMethod webMethod = getPrivMethodAnnotation(method, WebMethod.class);
        if (webMethod != null && webMethod.exclude())
            return;

        // If one WebMethod is used, then only methods with WebMethod will be
        // processed.
        if (usesWebMethod && webMethod == null) {
            return;
        }

        JavaMethodImpl javaMethod;
        //Class implementorClass = portClass;
        if (method.getDeclaringClass()==portClass) {
            javaMethod = new JavaMethodImpl(model,method,method);
        } else {
            try {
                Method tmpMethod = portClass.getMethod(method.getName(),
                    method.getParameterTypes());
                javaMethod = new JavaMethodImpl(model,tmpMethod,method);
            } catch (NoSuchMethodException e) {
                throw new RuntimeModelerException("runtime.modeler.method.not.found",
                    method.getName(), portClass.getName());
            }
        }

        String methodName = method.getName();

        //set MEP -oneway, async, req/resp
        MEP mep = getMEP(method);
        javaMethod.setMEP(mep);

        String action = null;


        String operationName = method.getName();
        if (webMethod != null ) {
            action = webMethod.action();
            operationName = webMethod.operationName().length() > 0 ?
                webMethod.operationName() :
                operationName;
        }

        //override the @WebMethod.action value by the one from the WSDL
        if(binding != null){
            WSDLBoundOperationImpl bo = binding.getBinding().get(new QName(targetNamespace, operationName));
            if(bo != null){
                WSDLInputImpl wsdlInput = bo.getOperation().getInput();
                String wsaAction = wsdlInput.getAction();
                if(wsaAction != null && !wsdlInput.isDefaultAction())
                    action = wsaAction;
                else
                    action = bo.getSOAPAction();
            }
        }

        javaMethod.setOperationName(operationName);
        SOAPBinding methodBinding =
            method.getAnnotation(SOAPBinding.class);
        if (methodBinding == null && !method.getDeclaringClass().equals(portClass)) {
            if (!method.getDeclaringClass().isInterface()) {
                methodBinding = method.getDeclaringClass().getAnnotation(SOAPBinding.class);
            }
        }
        boolean methodIsWrapped = isWrapped;
        Style style = defaultBinding.getStyle();
        if (methodBinding != null) {
            com.sun.xml.internal.ws.model.soap.SOAPBindingImpl mySOAPBinding = createBinding(methodBinding);
            style = mySOAPBinding.getStyle();
            if (action != null)
                mySOAPBinding.setSOAPAction(action);
            methodIsWrapped = methodBinding.parameterStyle().equals(
                javax.jws.soap.SOAPBinding.ParameterStyle.WRAPPED);
            javaMethod.setBinding(mySOAPBinding);
        } else {
            com.sun.xml.internal.ws.model.soap.SOAPBindingImpl sb = new com.sun.xml.internal.ws.model.soap.SOAPBindingImpl(defaultBinding);
            if (action != null)
                sb.setSOAPAction(action);
            else
                sb.setSOAPAction("");
            javaMethod.setBinding(sb);
        }
        if (!methodIsWrapped) {
            processDocBareMethod(javaMethod, methodName, webMethod, operationName,
                method, webService);
        } else if (style.equals(Style.DOCUMENT)) {
            processDocWrappedMethod(javaMethod, methodName, webMethod, operationName,
                method, webService);
        } else {
            processRpcMethod(javaMethod, methodName, webMethod, operationName,
                method, webService);
        }
        model.addJavaMethod(javaMethod);
    }

    private MEP getMEP(Method m){
        if (m.isAnnotationPresent(Oneway.class)) {
            return MEP.ONE_WAY;
        }
        if(Response.class.isAssignableFrom(m.getReturnType())){
            return MEP.ASYNC_POLL;
        }else if(Future.class.isAssignableFrom(m.getReturnType())){
            return MEP.ASYNC_CALLBACK;
        }
        return MEP.REQUEST_RESPONSE;
    }

    /**
     * models a document/literal wrapped method
     * @param javaMethod the runtime model <code>JavaMethod</code> instance being created
     * @param methodName the runtime model <code>JavaMethod</code> instance being created
     * @param webMethod the runtime model <code>JavaMethod</code> instance being created
     * @param operationName the runtime model <code>JavaMethod</code> instance being created
     * @param method the <code>method</code> to model
     * @param webService The <code>WebService</code> annotation instance on the <code>portClass</code>
     */
    protected void processDocWrappedMethod(JavaMethodImpl javaMethod, String methodName,
                                           WebMethod webMethod, String operationName, Method method, WebService webService) {
        boolean isOneway = method.isAnnotationPresent(Oneway.class);
        RequestWrapper reqWrapper = method.getAnnotation(RequestWrapper.class);
        ResponseWrapper resWrapper = method.getAnnotation(ResponseWrapper.class);
        String beanPackage = packageName + PD_JAXWS_PACKAGE_PD;
        if (packageName == null || (packageName != null && packageName.length() == 0))
            beanPackage = JAXWS_PACKAGE_PD;
        String requestClassName;
        if(reqWrapper != null && reqWrapper.className().length()>0){
            requestClassName = reqWrapper.className();
        }else{
            requestClassName = beanPackage + capitalize(method.getName());
        }


        String responseClassName;
        if(resWrapper != null && resWrapper.className().length()>0){
            responseClassName = resWrapper.className();
        }else{
            responseClassName = beanPackage + capitalize(method.getName()) + RESPONSE;
        }

        Class requestClass = getClass(requestClassName, ModelerMessages.localizableRUNTIME_MODELER_WRAPPER_NOT_FOUND(requestClassName));

        String reqName = operationName;
        String reqNamespace = targetNamespace;
        if (reqWrapper != null) {
            if (reqWrapper.targetNamespace().length() > 0)
                reqNamespace = reqWrapper.targetNamespace();
            if (reqWrapper.localName().length() > 0)
                reqName = reqWrapper.localName();
        }
        QName reqElementName = new QName(reqNamespace, reqName);

        Class responseClass = null;
        String resName = operationName+"Response";
        String resNamespace = targetNamespace;
        if (!isOneway) {
            responseClass = getClass(responseClassName, ModelerMessages.localizableRUNTIME_MODELER_WRAPPER_NOT_FOUND(responseClassName));
            if (resWrapper != null) {
                if (resWrapper.targetNamespace().length() > 0)
                    resNamespace = resWrapper.targetNamespace();
                if (resWrapper.localName().length() > 0)
                    resName = resWrapper.localName();
            }
        }
        QName resElementName = new QName(resNamespace, resName);

        TypeReference typeRef =
                new TypeReference(reqElementName, requestClass);
        WrapperParameter requestWrapper = new WrapperParameter(javaMethod, typeRef,
            Mode.IN, 0);
        requestWrapper.setBinding(ParameterBinding.BODY);
        javaMethod.addParameter(requestWrapper);
        WrapperParameter responseWrapper = null;
        if (!isOneway) {
            typeRef = new TypeReference(resElementName, responseClass);
            responseWrapper = new WrapperParameter(javaMethod, typeRef, Mode.OUT, -1);
            javaMethod.addParameter(responseWrapper);
            responseWrapper.setBinding(ParameterBinding.BODY);
        }

        // return value
        String resultName = RETURN;
        String resultTNS = "";
        QName resultQName = null;
        WebResult webResult = method.getAnnotation(WebResult.class);
        Class returnType = method.getReturnType();
        boolean isResultHeader = false;
        if (webResult != null) {
            if (webResult.name().length() > 0)
                resultName = webResult.name();
            resultTNS = webResult.targetNamespace();
            isResultHeader = webResult.header();
            if (resultTNS.length() == 0 && webResult.header()) {
                // headers must have a namespace
                resultTNS = targetNamespace;
            }
            resultQName = new QName(resultTNS, resultName);
        } else if (!isOneway && !returnType.getName().equals("void") && !javaMethod.isAsync()) {
            if(resultQName == null){
                resultQName = new QName(resultTNS, RETURN);
            }
        }

        if(javaMethod.isAsync()){
            returnType = getAsyncReturnType(method, returnType);
            resultQName = new QName(RETURN);
        }

        if (!isOneway && (returnType != null) && (!returnType.getName().equals("void"))) {
            Annotation[] rann = method.getAnnotations();
            if (resultQName.getLocalPart() != null) {
                TypeReference rTypeReference = new TypeReference(resultQName, returnType, rann);
                ParameterImpl returnParameter = new ParameterImpl(javaMethod, rTypeReference, Mode.OUT, -1);
                if (isResultHeader) {
                    returnParameter.setBinding(ParameterBinding.HEADER);
                    javaMethod.addParameter(returnParameter);
                } else {
                    returnParameter.setBinding(ParameterBinding.BODY);
                    responseWrapper.addWrapperChild(returnParameter);
                }
            }
        }

        //get WebParam
        Class<?>[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] pannotations = getPrivParameterAnnotations(method);
        int pos = 0;
        for (Class clazzType : parameterTypes) {
            String partName=null;
            String paramName = "arg"+pos;
            String paramNamespace = "";
            boolean isHeader = false;

            if(javaMethod.isAsync() && AsyncHandler.class.isAssignableFrom(clazzType)){
                continue;
            }

            boolean isHolder = HOLDER_CLASS.isAssignableFrom(clazzType);
            //set the actual type argument of Holder in the TypeReference
            if (isHolder) {
                if(clazzType==Holder.class){
                    clazzType = Navigator.REFLECTION.erasure(((ParameterizedType)genericParameterTypes[pos]).getActualTypeArguments()[0]);
                }
            }
            Mode paramMode = isHolder ? Mode.INOUT : Mode.IN;
            for (Annotation annotation : pannotations[pos]) {
                if (annotation.annotationType() == WebParam.class) {
                    WebParam webParam = (WebParam) annotation;
                    if (webParam.name().length() > 0)
                        paramName = webParam.name();
                    isHeader = webParam.header();
                    if(webParam.partName().length() > 0)
                        partName = webParam.partName();
                    else
                        partName = paramName;
                    if (isHeader) // headers cannot be in empty namespace
                        paramNamespace = targetNamespace;
                    if (!webParam.targetNamespace().equals("")) {
                        paramNamespace = webParam.targetNamespace();
                    }
                    paramMode = webParam.mode();
                    if (isHolder && paramMode == Mode.IN)
                        paramMode = Mode.INOUT;
                    break;
                }
            }
            QName paramQName = new QName(paramNamespace, paramName);
            typeRef =
                new TypeReference(paramQName, clazzType, pannotations[pos]);
            ParameterImpl param = new ParameterImpl(javaMethod, typeRef, paramMode, pos++);

            if (isHeader) {
                param.setBinding(ParameterBinding.HEADER);
                javaMethod.addParameter(param);
                param.setPartName(partName);
            } else {
                param.setBinding(ParameterBinding.BODY);
                if (paramMode!=Mode.OUT) {
                    requestWrapper.addWrapperChild(param);
                }
                if (paramMode!=Mode.IN) {
                    if (isOneway) {
                        throw new RuntimeModelerException("runtime.modeler.oneway.operation.no.out.parameters",
                            portClass.getCanonicalName(), methodName);
                    }
                    responseWrapper.addWrapperChild(param);
                }
            }
        }
        processExceptions(javaMethod, method);
    }


    /**
     * models a rpc/literal method
     * @param javaMethod the runtime model <code>JavaMethod</code> instance being created
     * @param methodName the name of the <code>method</code> being modeled.
     * @param webMethod the <code>WebMethod</code> annotations instance on the <code>method</code>
     * @param operationName the WSDL operation name for this <code>method</code>
     * @param method the runtime model <code>JavaMethod</code> instance being created
     * @param webService the runtime model <code>JavaMethod</code> instance being created
     */
    protected void processRpcMethod(JavaMethodImpl javaMethod, String methodName,
                                    WebMethod webMethod, String operationName, Method method, WebService webService) {
        boolean isOneway = method.isAnnotationPresent(Oneway.class);

        // use Map to build parameters in the part order when they are known.
        // if part is unbound, we just put them at the end, and for that we
        // use a large index (10000+) to avoid colliding with ordered ones.
        // this assumes that there's no operation with # of parameters > 10000,
        // but I think it's a pretty safe assumption - KK.
        Map<Integer, ParameterImpl> resRpcParams = new TreeMap<Integer, ParameterImpl>();
        Map<Integer, ParameterImpl> reqRpcParams = new TreeMap<Integer, ParameterImpl>();

        //Lets take the service namespace and overwrite it with the one we get it from wsdl
        String reqNamespace = targetNamespace;
        String respNamespace = targetNamespace;

        if(binding != null && binding.getBinding().isRpcLit()){
            QName opQName = new QName(binding.getBinding().getPortTypeName().getNamespaceURI(), operationName);
            WSDLBoundOperationImpl op = binding.getBinding().get(opQName);
            if(op != null){
                //it cant be null, but lets not fail and try to work with service namespce
                if(op.getRequestNamespace() != null){
                    reqNamespace = op.getRequestNamespace();
                }

                //it cant be null, but lets not fail and try to work with service namespce
                if(op.getResponseNamespace() != null){
                    respNamespace = op.getResponseNamespace();
                }
            }
        }

        QName reqElementName = new QName(reqNamespace, operationName);
        QName resElementName = null;
        if (!isOneway) {
            resElementName = new QName(respNamespace, operationName+RESPONSE);
        }

        Class wrapperType = CompositeStructure.class;
        TypeReference typeRef = new TypeReference(reqElementName, wrapperType);
        WrapperParameter requestWrapper = new WrapperParameter(javaMethod, typeRef, Mode.IN, 0);
        requestWrapper.setInBinding(ParameterBinding.BODY);
        javaMethod.addParameter(requestWrapper);
        WrapperParameter responseWrapper = null;
        if (!isOneway) {
            typeRef = new TypeReference(resElementName, wrapperType);
            responseWrapper = new WrapperParameter(javaMethod, typeRef, Mode.OUT, -1);
            responseWrapper.setOutBinding(ParameterBinding.BODY);
            javaMethod.addParameter(responseWrapper);
        }

        Class returnType = method.getReturnType();
        String resultName = RETURN;
        String resultTNS = targetNamespace;
        String resultPartName = resultName;
        boolean isResultHeader = false;
        WebResult webResult = method.getAnnotation(WebResult.class);

        if (webResult != null) {
            isResultHeader = webResult.header();
            if (webResult.name().length() > 0)
                resultName = webResult.name();
            if (webResult.partName().length() > 0) {
                resultPartName = webResult.partName();
                if (!isResultHeader)
                    resultName = resultPartName;
            } else
                resultPartName = resultName;
            if (webResult.targetNamespace().length() > 0)
                resultTNS = webResult.targetNamespace();
            isResultHeader = webResult.header();
        }
        QName resultQName;
        if (isResultHeader)
            resultQName = new QName(resultTNS, resultName);
        else
            resultQName = new QName(resultName);

        if(javaMethod.isAsync()){
            returnType = getAsyncReturnType(method, returnType);
        }

        if (!isOneway && returnType!=null && returnType!=void.class) {
            Annotation[] rann = method.getAnnotations();
            TypeReference rTypeReference = new TypeReference(resultQName, returnType, rann);
            ParameterImpl returnParameter = new ParameterImpl(javaMethod, rTypeReference, Mode.OUT, -1);
            returnParameter.setPartName(resultPartName);
            if(isResultHeader){
                returnParameter.setBinding(ParameterBinding.HEADER);
                javaMethod.addParameter(returnParameter);
            }else{
                ParameterBinding rb = getBinding(operationName, resultPartName, false, Mode.OUT);
                returnParameter.setBinding(rb);
                if(rb.isBody()){
                    WSDLPart p = getPart(new QName(targetNamespace,operationName), resultPartName, Mode.OUT);
                    if(p == null)
                        resRpcParams.put(resRpcParams.size()+10000, returnParameter);
                    else
                        resRpcParams.put(p.getIndex(), returnParameter);
                }else{
                    javaMethod.addParameter(returnParameter);
                }
            }
        }

        //get WebParam
        Class<?>[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] pannotations = getPrivParameterAnnotations(method);
        int pos = 0;
        for (Class clazzType : parameterTypes) {
            String paramName = "";
            String paramNamespace = "";
            String partName = "";
            boolean isHeader = false;

            if(javaMethod.isAsync() && AsyncHandler.class.isAssignableFrom(clazzType)){
                continue;
            }

            boolean isHolder = HOLDER_CLASS.isAssignableFrom(clazzType);
            //set the actual type argument of Holder in the TypeReference
            if (isHolder) {
                if (clazzType==Holder.class)
                    clazzType = Navigator.REFLECTION.erasure(((ParameterizedType)genericParameterTypes[pos]).getActualTypeArguments()[0]);
            }
            Mode paramMode = isHolder ? Mode.INOUT : Mode.IN;
            for (Annotation annotation : pannotations[pos]) {
                if (annotation.annotationType() == javax.jws.WebParam.class) {
                    javax.jws.WebParam webParam = (javax.jws.WebParam) annotation;
                    paramName = webParam.name();
                    partName = webParam.partName();
                    isHeader = webParam.header();
                    WebParam.Mode mode = webParam.mode();
                    paramNamespace = webParam.targetNamespace();
                    if (isHolder && mode == Mode.IN)
                        mode = Mode.INOUT;
                    paramMode = mode;
                    break;
                }
            }

            if (paramName.length() == 0) {
                paramName = "arg"+pos;
            }
            if (partName.length() == 0) {
                partName = paramName;
            } else if (!isHeader) {
                paramName = partName;
            }
            if (partName.length() == 0) {
                partName = paramName;
            }

            QName paramQName;
            if (!isHeader) {
                //its rpclit body param, set namespace to ""
                paramQName = new QName("", paramName);
            } else {
                if (paramNamespace.length() == 0)
                    paramNamespace = targetNamespace;
                paramQName = new QName(paramNamespace, paramName);
            }
            typeRef =
                new TypeReference(paramQName, clazzType, pannotations[pos]);

            ParameterImpl param = new ParameterImpl(javaMethod, typeRef, paramMode, pos++);
            param.setPartName(partName);

            if(paramMode == Mode.INOUT){
                ParameterBinding pb = getBinding(operationName, partName, isHeader, Mode.IN);
                param.setInBinding(pb);
                pb = getBinding(operationName, partName, isHeader, Mode.OUT);
                param.setOutBinding(pb);
            }else{
                if (isHeader) {
                    param.setBinding(ParameterBinding.HEADER);
                } else {
                    ParameterBinding pb = getBinding(operationName, partName, false, paramMode);
                    param.setBinding(pb);
                }
            }
            if(param.getInBinding().isBody()){
                if(!param.isOUT()){
                    WSDLPart p = getPart(new QName(targetNamespace,operationName), partName, Mode.IN);
                    if(p == null)
                        reqRpcParams.put(reqRpcParams.size()+10000, param);
                    else
                        reqRpcParams.put(p.getIndex(), param);
                }

                if(!param.isIN()){
                    if (isOneway) {
                            throw new RuntimeModelerException("runtime.modeler.oneway.operation.no.out.parameters",
                                portClass.getCanonicalName(), methodName);
                    }
                    WSDLPart p = getPart(new QName(targetNamespace,operationName), partName, Mode.OUT);
                    if(p == null)
                        resRpcParams.put(resRpcParams.size()+10000, param);
                    else
                        resRpcParams.put(p.getIndex(), param);
                }
            }else{
                javaMethod.addParameter(param);
            }
        }
        for (ParameterImpl p : reqRpcParams.values())
            requestWrapper.addWrapperChild(p);
        for (ParameterImpl p : resRpcParams.values())
            responseWrapper.addWrapperChild(p);
        processExceptions(javaMethod, method);
    }

    /**
     * models the exceptions thrown by <code>method</code> and adds them to the <code>javaMethod</code>
     * runtime model object
     * @param javaMethod the runtime model object to add the exception model objects to
     * @param method the <code>method</code> from which to find the exceptions to model
     */
    protected void processExceptions(JavaMethodImpl javaMethod, Method method) {
        Action actionAnn = method.getAnnotation(Action.class);
        FaultAction[] faultActions = {};
        if(actionAnn != null)
            faultActions = actionAnn.fault();
        for (Class<?> exception : method.getExceptionTypes()) {
            if (REMOTE_EXCEPTION_CLASS.isAssignableFrom(exception))
                continue;
            Class exceptionBean;
            Annotation[] anns;
            WebFault webFault = getPrivClassAnnotation(exception, WebFault.class);
            Method faultInfoMethod = getWSDLExceptionFaultInfo(exception);
            ExceptionType exceptionType = ExceptionType.WSDLException;
            String namespace = targetNamespace;
            String name = exception.getSimpleName();
            String beanPackage = packageName + PD_JAXWS_PACKAGE_PD;
            if (packageName.length() == 0)
                beanPackage = JAXWS_PACKAGE_PD;
            String className = beanPackage+ name + BEAN;
            if (webFault != null) {
                if (webFault.faultBean().length()>0)
                    className = webFault.faultBean();
                if (webFault.name().length()>0)
                    name = webFault.name();
                if (webFault.targetNamespace().length()>0)
                    namespace = webFault.targetNamespace();
            }
            if (faultInfoMethod == null)  {
                exceptionBean = getClass(className, ModelerMessages.localizableRUNTIME_MODELER_WRAPPER_NOT_FOUND(className));
                exceptionType = ExceptionType.UserDefined;
                anns = exceptionBean.getAnnotations();
            } else {
                exceptionBean = faultInfoMethod.getReturnType();
                anns = faultInfoMethod.getAnnotations();
            }
            QName faultName = new QName(namespace, name);
            TypeReference typeRef = new TypeReference(faultName, exceptionBean, anns);
            CheckedExceptionImpl checkedException =
                new CheckedExceptionImpl(javaMethod, exception, typeRef, exceptionType);
            checkedException.setMessageName(exception.getSimpleName());
            for(FaultAction fa: faultActions) {
                if(fa.className().equals(exception) && !fa.value().equals("")) {
                    checkedException.setFaultAction(fa.value());
                    break;
                }
            }
            javaMethod.addException(checkedException);
        }
    }

    /**
     * returns the method that corresponds to "getFaultInfo".  Returns null if this is not an
     * exception generated from a WSDL
     * @param exception the class to search for the "getFaultInfo" method
     * @return the method named "getFaultInfo" if this is an exception generated from WSDL or an
     * exception that contains the <code>WebFault</code> annotation.  Otherwise it returns null
     */
    protected Method getWSDLExceptionFaultInfo(Class exception) {
        if (!exception.isAnnotationPresent(WebFault.class))
            return null;
        try {
            return exception.getMethod("getFaultInfo");
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * models a document/literal bare method
     * @param javaMethod the runtime model <code>JavaMethod</code> instance being created
     * @param methodName the runtime model <code>JavaMethod</code> instance being created
     * @param webMethod the runtime model <code>JavaMethod</code> instance being created
     * @param operationName the runtime model <code>JavaMethod</code> instance being created
     * @param method the runtime model <code>JavaMethod</code> instance being created
     * @param webService the runtime model <code>JavaMethod</code> instance being created
     */
    protected void processDocBareMethod(JavaMethodImpl javaMethod, String methodName,
                                        WebMethod webMethod, String operationName, Method method, WebService webService) {

        String resultName = operationName+RESPONSE;
        String resultTNS = targetNamespace;
        String resultPartName = null;
        boolean isResultHeader = false;
        WebResult webResult = method.getAnnotation(WebResult.class);
        if (webResult != null) {
            if (webResult.name().length() > 0)
                resultName = webResult.name();
            if (webResult.targetNamespace().length() > 0)
                resultTNS = webResult.targetNamespace();
            resultPartName = webResult.partName();
            isResultHeader = webResult.header();
        }

        Class returnType = method.getReturnType();

        if(javaMethod.isAsync()){
            returnType = getAsyncReturnType(method, returnType);
        }

        if ((returnType != null) && (!returnType.getName().equals("void"))) {
            Annotation[] rann = method.getAnnotations();
            if (resultName != null) {
                QName responseQName = new QName(resultTNS, resultName);
                TypeReference rTypeReference = new TypeReference(responseQName, returnType, rann);
                ParameterImpl returnParameter = new ParameterImpl(javaMethod, rTypeReference, Mode.OUT, -1);

                if(resultPartName == null || (resultPartName.length() == 0)){
                    resultPartName = resultName;
                }
                returnParameter.setPartName(resultPartName);
                if(isResultHeader){
                    returnParameter.setBinding(ParameterBinding.HEADER);
                }else{
                    ParameterBinding rb = getBinding(operationName, resultPartName, false, Mode.OUT);
                    returnParameter.setBinding(rb);
                }
                javaMethod.addParameter(returnParameter);
            }
        }

        //get WebParam
        Class<?>[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] pannotations = getPrivParameterAnnotations(method);
        int pos = 0;
        for (Class clazzType : parameterTypes) {
            String paramName = operationName; //method.getName();
            String partName = null;
            String requestNamespace = targetNamespace;
            boolean isHeader = false;

            //async
            if(javaMethod.isAsync() && AsyncHandler.class.isAssignableFrom(clazzType)){
                continue;
            }

            boolean isHolder = HOLDER_CLASS.isAssignableFrom(clazzType);
            //set the actual type argument of Holder in the TypeReference
            if (isHolder) {
                if (clazzType==Holder.class)
                    clazzType = Navigator.REFLECTION.erasure(((ParameterizedType)genericParameterTypes[pos]).getActualTypeArguments()[0]);
            }

            Mode paramMode = isHolder ? Mode.INOUT : Mode.IN;
            for (Annotation annotation : pannotations[pos]) {
                if (annotation.annotationType() == javax.jws.WebParam.class) {
                    javax.jws.WebParam webParam = (javax.jws.WebParam) annotation;
                    if (webParam.name().length() > 0)
                        paramName = webParam.name();
                    partName = webParam.partName();
                    if (!webParam.targetNamespace().equals("")) {
                        requestNamespace = webParam.targetNamespace();
                    }
                    isHeader = webParam.header();
                    paramMode = webParam.mode();
                    if (isHolder && paramMode == Mode.IN)
                        paramMode = Mode.INOUT;
                    break;
                }
            }

            QName requestQName = new QName(requestNamespace, paramName);
            //doclit/wrapped
            TypeReference typeRef = //operationName with upper 1 char
                new TypeReference(requestQName, clazzType,
                    pannotations[pos]);

            ParameterImpl param = new ParameterImpl(javaMethod, typeRef, paramMode, pos++);
            if(partName == null || (partName.length() == 0)){
                partName = paramName;
            }
            param.setPartName(partName);
            if(paramMode == Mode.INOUT){
                ParameterBinding pb = getBinding(operationName, partName, isHeader, Mode.IN);
                param.setInBinding(pb);
                pb = getBinding(operationName, partName, isHeader, Mode.OUT);
                param.setOutBinding(pb);
            }else{
                if (isHeader){
                    param.setBinding(ParameterBinding.HEADER);
                }else{
                    ParameterBinding pb = getBinding(operationName, partName, false, paramMode);
                    param.setBinding(pb);
                }
            }
            javaMethod.addParameter(param);
        }
        validateDocBare(javaMethod);
        processExceptions(javaMethod, method);
    }

    private void validateDocBare(JavaMethodImpl javaMethod) {
        int numInBodyBindings = 0;
        for(Parameter param : javaMethod.getRequestParameters()){
            if(param.getBinding().equals(ParameterBinding.BODY) && param.isIN()){
                numInBodyBindings++;
            }
            if(numInBodyBindings > 1){
                throw new RuntimeModelerException(ModelerMessages.localizableNOT_A_VALID_BARE_METHOD(portClass.getName(), javaMethod.getMethod().getName()));
            }
        }
    }

    private Class getAsyncReturnType(Method method, Class returnType) {
        if(Response.class.isAssignableFrom(returnType)){
            Type ret = method.getGenericReturnType();
            return Navigator.REFLECTION.erasure(((ParameterizedType)ret).getActualTypeArguments()[0]);
        }else{
            Type[] types = method.getGenericParameterTypes();
            Class[] params = method.getParameterTypes();
            int i = 0;
            for(Class cls : params){
                if(AsyncHandler.class.isAssignableFrom(cls)){
                    return Navigator.REFLECTION.erasure(((ParameterizedType)types[i]).getActualTypeArguments()[0]);
                }
                i++;
            }
        }
        return returnType;
    }

    /**
     * utility to capitalize the first letter in a string
     * @param name the string to capitalize
     * @return the capitalized string
     */
    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    /*
    * Return service QName
    */
    /**
     * gets the <code>wsdl:serviceName</code> for a given implementation class
     * @param implClass the implementation class
     * @return the <code>wsdl:serviceName</code> for the <code>implClass</code>
     */
    public static QName getServiceName(Class<?> implClass) {
        if (implClass.isInterface()) {
            throw new RuntimeModelerException("runtime.modeler.cannot.get.serviceName.from.interface",
                                    implClass.getCanonicalName());
        }

        String name = implClass.getSimpleName()+SERVICE;
        String packageName = "";
        if (implClass.getPackage() != null)
            packageName = implClass.getPackage().getName();

        WebService webService = implClass.getAnnotation(WebService.class);
        if (webService == null) {
            throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                implClass.getCanonicalName());
        }
        if (webService.serviceName().length() > 0) {
            name = webService.serviceName();
        }
        String targetNamespace = getNamespace(packageName);
        if (webService.targetNamespace().length() > 0) {
            targetNamespace = webService.targetNamespace();
        } else if (targetNamespace == null) {
            throw new RuntimeModelerException("runtime.modeler.no.package",
                implClass.getName());
        }



        return new QName(targetNamespace, name);
    }

    /**
     * gets the <code>wsdl:portName</code> for a given implementation class
     * @param implClass the implementation class
     * @param targetNamespace Namespace URI for service name
     * @return the <code>wsdl:portName</code> for the <code>implClass</code>
     */
    public static QName getPortName(Class<?> implClass, String targetNamespace) {
        WebService webService = implClass.getAnnotation(WebService.class);
        if (webService == null) {
            throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                implClass.getCanonicalName());
        }
        String name;
        if (webService.portName().length() > 0) {
            name = webService.portName();
        } else if (webService.name().length() > 0) {
            name = webService.name()+PORT;
        } else {
            name = implClass.getSimpleName()+PORT;
        }

        if (targetNamespace == null) {
            if (webService.targetNamespace().length() > 0) {
                targetNamespace = webService.targetNamespace();
            } else {
                String packageName = null;
                if (implClass.getPackage() != null) {
                    packageName = implClass.getPackage().getName();
                }
                targetNamespace = getNamespace(packageName);
                if (targetNamespace == null) {
                    throw new RuntimeModelerException("runtime.modeler.no.package",
                        implClass.getName());
                }
            }

        }

        return new QName(targetNamespace, name);
    }

    /**
     * Gives portType QName from implementatorClass or SEI
     * @param  implOrSeiClass cant be null
     * @return  <code>wsdl:portType@name</code>, null if it could not find the annotated class.
     */
    public static QName getPortTypeName(Class<?> implOrSeiClass){
        assert(implOrSeiClass != null);
        Class<?> clazz = implOrSeiClass;
        if (!implOrSeiClass.isAnnotationPresent(WebService.class))
                throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                                           implOrSeiClass.getCanonicalName());

        if (!implOrSeiClass.isInterface()) {
            WebService webService = implOrSeiClass.getAnnotation(WebService.class);
            String epi = webService.endpointInterface();
            if (epi.length() > 0) {
                try {
                    clazz = Thread.currentThread().getContextClassLoader().loadClass(epi);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeModelerException("runtime.modeler.class.not.found", epi);
                }
                if (!clazz.isAnnotationPresent(javax.jws.WebService.class)) {
                    throw new RuntimeModelerException("runtime.modeler.endpoint.interface.no.webservice",
                                        webService.endpointInterface());
                }
            }
        }

        WebService webService = clazz.getAnnotation(WebService.class);
        String name = webService.name();
        if(name.length() == 0){
            name = clazz.getSimpleName();
        }

        String tns = webService.targetNamespace();
        if (tns.length() == 0)
            tns = getNamespace(clazz.getPackage().getName());
        if (tns == null) {
            throw new RuntimeModelerException("runtime.modeler.no.package", clazz.getName());
        }
        return new QName(tns, name);
    }

    private ParameterBinding getBinding(String operation, String part, boolean isHeader, Mode mode){
        if(binding == null){
            if(isHeader)
                return ParameterBinding.HEADER;
            else
                return ParameterBinding.BODY;
        }
        QName opName = new QName(binding.getBinding().getPortType().getName().getNamespaceURI(), operation);
        return binding.getBinding().getBinding(opName, part, mode);
    }

    private WSDLPart getPart(QName opName, String partName, Mode mode){
        if(binding != null){
            WSDLBoundOperationImpl bo = binding.getBinding().get(opName);
            if(bo != null)
                return bo.getPart(partName, mode);
        }
        return null;
    }
}

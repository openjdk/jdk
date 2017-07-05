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

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.localization.Localizable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.databinding.DatabindingConfig;
import com.sun.xml.internal.ws.api.databinding.MetadataReader;
import com.sun.xml.internal.ws.api.model.ExceptionType;
import com.sun.xml.internal.ws.api.model.MEP;
import com.sun.xml.internal.ws.api.model.Parameter;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLInput;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPart;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.binding.WebServiceFeatureList;
import com.sun.xml.internal.ws.model.soap.SOAPBindingImpl;
import com.sun.xml.internal.ws.resources.ModelerMessages;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.spi.db.BindingContext;
import com.sun.xml.internal.ws.spi.db.TypeInfo;
import com.sun.xml.internal.ws.spi.db.WrapperComposite;

import static com.sun.xml.internal.ws.binding.WebServiceFeatureList.getSoapVersion;

import javax.jws.*;
import javax.jws.WebParam.Mode;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.Style;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.namespace.QName;
import javax.xml.ws.*;
import javax.xml.ws.soap.MTOM;
import javax.xml.ws.soap.MTOMFeature;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.security.AccessController;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static javax.jws.soap.SOAPBinding.ParameterStyle.WRAPPED;

/**
 * Creates a runtime model of a SEI (portClass).
 *
 * @author WS Developement Team
 */
public class RuntimeModeler {
    private final WebServiceFeatureList features;
    private BindingID bindingId;
    private WSBinding wsBinding;
    private final Class portClass;
    private AbstractSEIModelImpl model;
    private SOAPBindingImpl defaultBinding;
    // can be empty but never null
    private String packageName;
    private String targetNamespace;
    private boolean isWrapped = true;
    private ClassLoader classLoader;
    private final WSDLPort binding;
    private QName serviceName;
    private QName portName;
    private Set<Class> classUsesWebMethod;
    private DatabindingConfig config;
    private MetadataReader metadataReader;
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
    public static final Class<RuntimeException> RUNTIME_EXCEPTION_CLASS = RuntimeException.class;
    public static final Class<Exception> EXCEPTION_CLASS = Exception.class;
    public static final String DecapitalizeExceptionBeanProperties = "com.sun.xml.internal.ws.api.model.DecapitalizeExceptionBeanProperties";
    public static final String SuppressDocLitWrapperGeneration = "com.sun.xml.internal.ws.api.model.SuppressDocLitWrapperGeneration";
    public static final String DocWrappeeNamespapceQualified = "com.sun.xml.internal.ws.api.model.DocWrappeeNamespapceQualified";

  /*public RuntimeModeler(@NotNull Class portClass, @NotNull QName serviceName, @NotNull BindingID bindingId, @NotNull WebServiceFeature... features) {
        this(portClass, serviceName, null, bindingId, features);
    }*/

    /**
     *
     * creates an instance of RunTimeModeler given a <code>sei</code> and <code>binding</code>
     * @param portClass The SEI class to be modeled.
     * @param serviceName The ServiceName to use instead of one calculated from the implementation class
     * @param wsdlPort {@link com.sun.xml.internal.ws.api.model.wsdl.WSDLPort}
     * @param features web service features
     */
  /*public RuntimeModeler(@NotNull Class portClass, @NotNull QName serviceName, @NotNull WSDLPortImpl wsdlPort, @NotNull WebServiceFeature... features){
        this(portClass, serviceName, wsdlPort, wsdlPort.getBinding().getBindingId(), features);
    }*/

  /*private RuntimeModeler(@NotNull Class portClass, @NotNull QName serviceName, WSDLPortImpl binding, BindingID bindingId, @NotNull WebServiceFeature... features) {
        this.portClass = portClass;
        this.serviceName = serviceName;
        this.binding = binding;
        this.bindingId = bindingId;
        this.features = features;
    }*/

    public RuntimeModeler(@NotNull DatabindingConfig config){
        this.portClass = (config.getEndpointClass() != null)? config.getEndpointClass() : config.getContractClass();
        this.serviceName = config.getMappingInfo().getServiceName();
        this.binding = config.getWsdlPort();
        this.classLoader = config.getClassLoader();
        this.portName = config.getMappingInfo().getPortName();
        this.config = config;
        this.wsBinding = config.getWSBinding();
        metadataReader = config.getMetadataReader();
        targetNamespace = config.getMappingInfo().getTargetNamespace();
        if (metadataReader == null) metadataReader = new ReflectAnnotationReader();
        if (wsBinding != null) {
            this.bindingId = wsBinding.getBindingId();
                if (config.getFeatures() != null) wsBinding.getFeatures().mergeFeatures(config.getFeatures(), false);
                if (binding != null) wsBinding.getFeatures().mergeFeatures(binding.getFeatures(), false);
                this.features = WebServiceFeatureList.toList(wsBinding.getFeatures());
        } else {
            this.bindingId = config.getMappingInfo().getBindingID();
            this.features = WebServiceFeatureList.toList(config.getFeatures());
            if (binding != null) bindingId = binding.getBinding().getBindingId();
            if (bindingId == null) bindingId = getDefaultBindingID();
            if (!features.contains(MTOMFeature.class)) {
                MTOM mtomAn = getAnnotation(portClass, MTOM.class);
                if (mtomAn != null) features.add(WebServiceFeatureList.getFeature(mtomAn));
            }
            if (!features.contains(com.oracle.webservices.internal.api.EnvelopeStyleFeature.class)) {
                com.oracle.webservices.internal.api.EnvelopeStyle es = getAnnotation(portClass, com.oracle.webservices.internal.api.EnvelopeStyle.class);
                if (es != null) features.add(WebServiceFeatureList.getFeature(es));
            }
            this.wsBinding = bindingId.createBinding(features);
        }
    }

    private BindingID getDefaultBindingID() {
        BindingType bt = getAnnotation(portClass, BindingType.class);
        if (bt != null) return BindingID.parse(bt.value());
        SOAPVersion ver = getSoapVersion(features);
        boolean mtomEnabled = features.isEnabled(MTOMFeature.class);
        if (SOAPVersion.SOAP_12.equals(ver)) {
            return (mtomEnabled) ? BindingID.SOAP12_HTTP_MTOM : BindingID.SOAP12_HTTP;
        } else {
            return (mtomEnabled) ? BindingID.SOAP11_HTTP_MTOM : BindingID.SOAP11_HTTP;
        }
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

    private <T extends Annotation> T getAnnotation(final Class<?> clazz, final Class<T> T) {
        return metadataReader.getAnnotation(T, clazz);
    }

    private <T extends Annotation> T getAnnotation(final Method method, final Class<T> T) {
        return metadataReader.getAnnotation(T, method);
    }

    private Annotation[] getAnnotations(final Method method) {
        return metadataReader.getAnnotations(method);
    }

    private Annotation[] getAnnotations(final Class<?> c) {
        return metadataReader.getAnnotations(c);
    }
    private Annotation[][] getParamAnnotations(final Method method) {
        return metadataReader.getParameterAnnotations(method);
    }

    private static final Logger logger =
        Logger.getLogger(
            com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server");

    //currently has many local vars which will be eliminated after debugging issues
    //first draft
    /**
     * builds the runtime model from the <code>portClass</code> using the binding ID <code>bindingId</code>.
     * @return the runtime model for the <code>portClass</code>.
     */
    public AbstractSEIModelImpl buildRuntimeModel() {
        model = new SOAPSEIModel(features);
        model.contractClass = config.getContractClass();
        model.endpointClass = config.getEndpointClass();
        model.classLoader = this.classLoader;
        model.wsBinding = wsBinding;
        model.databindingInfo.setWsdlURL(config.getWsdlURL());
        model.databindingInfo.properties().putAll(config.properties());
        if (model.contractClass == null) model.contractClass = portClass;
        if (model.endpointClass == null && !portClass.isInterface()) model.endpointClass = portClass;
        Class<?> seiClass = portClass;
        metadataReader.getProperties(model.databindingInfo.properties(), portClass);
        WebService webService = getAnnotation(portClass, WebService.class);
        if (webService == null) {
            throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                portClass.getCanonicalName());
        }
        Class<?> seiFromConfig = configEndpointInterface();
        if (webService.endpointInterface().length() > 0 || seiFromConfig != null) {
                if (seiFromConfig != null) {
                        seiClass = seiFromConfig;
                } else {
                        seiClass = getClass(webService.endpointInterface(), ModelerMessages.localizableRUNTIME_MODELER_CLASS_NOT_FOUND(webService.endpointInterface()));
                }
            model.contractClass = seiClass;
                model.endpointClass = portClass;
            WebService seiService = getAnnotation(seiClass, WebService.class);
            if (seiService == null) {
                throw new RuntimeModelerException("runtime.modeler.endpoint.interface.no.webservice",
                    webService.endpointInterface());
            }

            //check if @SOAPBinding is defined on the impl class
            SOAPBinding sbPortClass = getAnnotation(portClass, SOAPBinding.class);
            SOAPBinding sbSei = getAnnotation(seiClass, SOAPBinding.class);
            if(sbPortClass != null){
                if(sbSei == null || sbSei.style() != sbPortClass.style()|| sbSei.use() != sbPortClass.use()){
                    logger.warning(ServerMessages.RUNTIMEMODELER_INVALIDANNOTATION_ON_IMPL("@SOAPBinding", portClass.getName(), seiClass.getName()));
                }
            }
        }
        if (serviceName == null)
            serviceName = getServiceName(portClass, metadataReader);
        model.setServiceQName(serviceName);

//        String portLocalName  = portClass.getSimpleName()+PORT;
//        if (webService.portName().length() >0) {
//            portLocalName = webService.portName();
//        } else if (webService.name().length() >0) {
//            portLocalName = webService.name()+PORT;
//        }
//
//        if (portName == null)
//            portName = new QName(serviceName.getNamespaceURI(), portLocalName);
//        if (!portName.getNamespaceURI().equals(serviceName.getNamespaceURI())) {
//            throw new RuntimeModelerException("runtime.modeler.portname.servicename.namespace.mismatch",
//                serviceName, portName);
//        }

        if (portName == null) portName = getPortName(portClass, metadataReader, serviceName.getNamespaceURI());
        model.setPortName(portName);

        // Check if databinding is overridden in annotation.
        com.oracle.webservices.internal.api.databinding.DatabindingMode dbm2 = getAnnotation(portClass, com.oracle.webservices.internal.api.databinding.DatabindingMode.class);
        if (dbm2 != null) model.databindingInfo.setDatabindingMode(dbm2.value());

        processClass(seiClass);
        if (model.getJavaMethods().size() == 0)
            throw new RuntimeModelerException("runtime.modeler.no.operations",
                    portClass.getName());
        model.postProcess();

        // Make the configured databinding mode available to the
        // DatabindingConfig.
        config.properties().put(BindingContext.class.getName(),
                model.bindingContext);

        // TODO: this needs to be fixed properly --
        // when we are building RuntimeModel first before building WSDLModel,
        // we still need to do this correctly
        if(binding!=null)
            model.freeze(binding);
        return model;
    }

    private Class configEndpointInterface() {
                if (config.getEndpointClass() == null ||
                    config.getEndpointClass().isInterface()     ) return null; //client proxy Interface
                return config.getContractClass();
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

    private boolean noWrapperGen() {
        Object o = config.properties().get(SuppressDocLitWrapperGeneration);
        return (o!= null && o instanceof Boolean) ? ((Boolean) o) : false;
    }

    private Class getRequestWrapperClass(String className, Method method, QName reqElemName) {
        ClassLoader loader =  (classLoader == null) ? Thread.currentThread().getContextClassLoader() : classLoader;
        try {
            return loader.loadClass(className);
        } catch (ClassNotFoundException e) {
            if (noWrapperGen()) return WrapperComposite.class;
            logger.fine("Dynamically creating request wrapper Class " + className);
            return WrapperBeanGenerator.createRequestWrapperBean(className, method, reqElemName, loader);
        }
    }

    private Class getResponseWrapperClass(String className, Method method, QName resElemName) {
        ClassLoader loader =  (classLoader == null) ? Thread.currentThread().getContextClassLoader() : classLoader;
        try {
            return loader.loadClass(className);
        } catch (ClassNotFoundException e) {
            if (noWrapperGen()) return WrapperComposite.class;
            logger.fine("Dynamically creating response wrapper bean Class " + className);
            return WrapperBeanGenerator.createResponseWrapperBean(className, method, resElemName, loader);
        }
    }


    private Class getExceptionBeanClass(String className, Class exception, String name, String namespace) {
        boolean decapitalizeExceptionBeanProperties = true;
        Object o = config.properties().get(DecapitalizeExceptionBeanProperties);
        if (o!= null && o instanceof Boolean) decapitalizeExceptionBeanProperties = (Boolean) o;
        ClassLoader loader =  (classLoader == null) ? Thread.currentThread().getContextClassLoader() : classLoader;
        try {
            return loader.loadClass(className);
        } catch (ClassNotFoundException e) {
            logger.fine("Dynamically creating exception bean Class " + className);
            return WrapperBeanGenerator.createExceptionBean(className, exception, targetNamespace, name, namespace, loader, decapitalizeExceptionBeanProperties);
        }
    }

    protected void determineWebMethodUse(Class clazz) {
        if (clazz == null)
            return;
        if (!clazz.isInterface()) {
            if (clazz == Object.class)
                return;
            WebMethod webMethod;
            for (Method method : clazz.getMethods()) {
                if (method.getDeclaringClass()!=clazz)
                    continue;
                webMethod = getAnnotation(method, WebMethod.class);
                if (webMethod != null && !webMethod.exclude()) {
                    classUsesWebMethod.add(clazz);
                    break;
                }
            }
        }
        determineWebMethodUse(clazz.getSuperclass());
    }

    void processClass(Class clazz) {
        classUsesWebMethod = new HashSet<Class>();
        determineWebMethodUse(clazz);
        WebService webService = getAnnotation(clazz, WebService.class);
        QName portTypeName = getPortTypeName(clazz, targetNamespace, metadataReader);
//        String portTypeLocalName  = clazz.getSimpleName();
//        if (webService.name().length() >0)
//            portTypeLocalName = webService.name();
//
//        targetNamespace = webService.targetNamespace();
        packageName = "";
        if (clazz.getPackage() != null)
            packageName = clazz.getPackage().getName();
//        if (targetNamespace.length() == 0) {
//            targetNamespace = getNamespace(packageName);
//        }
//        model.setTargetNamespace(targetNamespace);
//        QName portTypeName = new QName(targetNamespace, portTypeLocalName);
        targetNamespace = portTypeName.getNamespaceURI();
        model.setPortTypeName(portTypeName);
        model.setTargetNamespace(targetNamespace);
        model.defaultSchemaNamespaceSuffix = config.getMappingInfo().getDefaultSchemaNamespaceSuffix();
        model.setWSDLLocation(webService.wsdlLocation());

        SOAPBinding soapBinding = getAnnotation(clazz, SOAPBinding.class);
        if (soapBinding != null) {
            if (soapBinding.style() == SOAPBinding.Style.RPC && soapBinding.parameterStyle() == SOAPBinding.ParameterStyle.BARE) {
                throw new RuntimeModelerException("runtime.modeler.invalid.soapbinding.parameterstyle",
                        soapBinding, clazz);

            }
            isWrapped = soapBinding.parameterStyle()== WRAPPED;
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
            if (!clazz.isInterface()) {     // if clazz is SEI, then all methods are web methods
                if (method.getDeclaringClass() == Object.class) continue;
                if (!getBooleanSystemProperty("com.sun.xml.internal.ws.legacyWebMethod")) {  // legacy webMethod computation behaviour to be used
                    if (!isWebMethodBySpec(method, clazz))
                        continue;
                } else {
                    if (!isWebMethod(method))
                        continue;
                }
            }
            // TODO: binding can be null. We need to figure out how to post-process
            // RuntimeModel to link to WSDLModel
            processMethod(method);
        }
        //Add additional jaxb classes referenced by {@link XmlSeeAlso}
        XmlSeeAlso xmlSeeAlso = getAnnotation(clazz, XmlSeeAlso.class);
        if(xmlSeeAlso != null)
            model.addAdditionalClasses(xmlSeeAlso.value());
    }

    /*
     * Section 3.3 of spec
     * Otherwise, the class implicitly defines a service endpoint interface (SEI) which
     * comprises all of the public methods that satisfy one of the following conditions:
     *  1. They are annotated with the javax.jws.WebMethod annotation with the exclude element set to
     *     false or missing (since false is the default for this annotation element).
     *  2. They are not annotated with the javax.jws.WebMethod annotation but their declaring class has a
     *     javax.jws.WebService annotation.
     *
     * also the method should non-static or non-final
     */
    private boolean isWebMethodBySpec(Method method, Class clazz) {

        int modifiers = method.getModifiers();
        boolean staticFinal = Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers);

        assert Modifier.isPublic(modifiers);
        assert !clazz.isInterface();

        WebMethod webMethod = getAnnotation(method, WebMethod.class);
        if (webMethod != null) {
            if (webMethod.exclude()) {
                return false;       // @WebMethod(exclude="true")
            }
            if (staticFinal) {
                throw new RuntimeModelerException(ModelerMessages.localizableRUNTIME_MODELER_WEBMETHOD_MUST_BE_NONSTATICFINAL(method));
            }
            return true;            // @WebMethod
        }

        if (staticFinal) {
            return false;
        }

        Class declClass = method.getDeclaringClass();
        return getAnnotation(declClass, WebService.class) != null;
    }

    private boolean isWebMethod(Method method) {
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers))
            return false;

        Class clazz = method.getDeclaringClass();
        boolean declHasWebService = getAnnotation(clazz, WebService.class) != null;
        WebMethod webMethod = getAnnotation(method, WebMethod.class);
        if (webMethod != null && !webMethod.exclude() && declHasWebService)
            return true;
        return declHasWebService && !classUsesWebMethod.contains(clazz);
    }

    /**
     * creates a runtime model <code>SOAPBinding</code> from a <code>javax.jws.soap.SOAPBinding</code> object
     * @param soapBinding the <code>javax.jws.soap.SOAPBinding</code> to model
     * @return returns the runtime model SOAPBinding corresponding to <code>soapBinding</code>
     */
    protected SOAPBindingImpl createBinding(SOAPBinding soapBinding) {
        SOAPBindingImpl rtSOAPBinding = new SOAPBindingImpl();
        Style style = soapBinding!=null ? soapBinding.style() : Style.DOCUMENT;
        rtSOAPBinding.setStyle(style);
        assert bindingId != null;
        model.bindingId = bindingId;
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

    /*
     * Returns true if an exception is service specific exception as per JAX-WS rules.
     * @param exception
     * @return
     */
    private boolean isServiceException(Class<?> exception) {
        return EXCEPTION_CLASS.isAssignableFrom(exception) &&
                !(RUNTIME_EXCEPTION_CLASS.isAssignableFrom(exception) || REMOTE_EXCEPTION_CLASS.isAssignableFrom(exception));
    }

    /**
     * creates the runtime model for a method on the <code>portClass</code>
     * @param method the method to model
     */
    private void processMethod(Method method) {
//        int mods = method.getModifiers();
        WebMethod webMethod = getAnnotation(method, WebMethod.class);
        if (webMethod != null && webMethod.exclude()) return;
/*
        validations are already done

        if (!Modifier.isPublic(mods) || Modifier.isStatic(mods)) {
            if(webMethod != null) {
                // if the user put @WebMethod on these non-qualifying method,
                // it's an error
                if(Modifier.isStatic(mods))
                    throw new RuntimeModelerException(ModelerMessages.localizableRUNTIME_MODELER_WEBMETHOD_MUST_BE_NONSTATIC(method));
                else
                    throw new RuntimeModelerException(ModelerMessages.localizableRUNTIME_MODELER_WEBMETHOD_MUST_BE_PUBLIC(method));
            }
            return;
        }

        if (webMethod != null && webMethod.exclude())
            return;
*/

        String methodName = method.getName();
        boolean isOneway = (getAnnotation(method, Oneway.class) != null);

        //Check that oneway methods don't thorw any checked exceptions
        if (isOneway) {
            for (Class<?> exception : method.getExceptionTypes()) {
                if(isServiceException(exception)) {
                       throw new RuntimeModelerException("runtime.modeler.oneway.operation.no.checked.exceptions",
                            portClass.getCanonicalName(), methodName, exception.getName());
                }
            }
        }

        JavaMethodImpl javaMethod;
        //Class implementorClass = portClass;
        if (method.getDeclaringClass()==portClass) {
            javaMethod = new JavaMethodImpl(model,method,method,metadataReader);
        } else {
            try {
                Method tmpMethod = portClass.getMethod(method.getName(),
                    method.getParameterTypes());
                javaMethod = new JavaMethodImpl(model,tmpMethod,method,metadataReader);
            } catch (NoSuchMethodException e) {
                throw new RuntimeModelerException("runtime.modeler.method.not.found",
                    method.getName(), portClass.getName());
            }
        }



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
            WSDLBoundOperation bo = binding.getBinding().get(new QName(targetNamespace, operationName));
            if(bo != null){
                WSDLInput wsdlInput = bo.getOperation().getInput();
                String wsaAction = wsdlInput.getAction();
                if(wsaAction != null && !wsdlInput.isDefaultAction())
                    action = wsaAction;
                else
                    action = bo.getSOAPAction();
            }
        }

        javaMethod.setOperationQName(new QName(targetNamespace,operationName));
        SOAPBinding methodBinding = getAnnotation(method, SOAPBinding.class);
        if(methodBinding != null && methodBinding.style() == SOAPBinding.Style.RPC) {
            logger.warning(ModelerMessages.RUNTIMEMODELER_INVALID_SOAPBINDING_ON_METHOD(methodBinding, method.getName(), method.getDeclaringClass().getName()));
        } else if (methodBinding == null && !method.getDeclaringClass().equals(portClass)) {
            methodBinding = getAnnotation(method.getDeclaringClass(), SOAPBinding.class);
            if (methodBinding != null && methodBinding.style() == SOAPBinding.Style.RPC && methodBinding.parameterStyle() == SOAPBinding.ParameterStyle.BARE) {
                throw new RuntimeModelerException("runtime.modeler.invalid.soapbinding.parameterstyle",
                        methodBinding, method.getDeclaringClass());
            }
        }

        if(methodBinding!= null && defaultBinding.getStyle() != methodBinding.style()) {
             throw new RuntimeModelerException("runtime.modeler.soapbinding.conflict",
                    methodBinding.style(), method.getName(),defaultBinding.getStyle());
        }

        boolean methodIsWrapped = isWrapped;
        Style style = defaultBinding.getStyle();
        if (methodBinding != null) {
            SOAPBindingImpl mySOAPBinding = createBinding(methodBinding);
            style = mySOAPBinding.getStyle();
            if (action != null)
                mySOAPBinding.setSOAPAction(action);
            methodIsWrapped = methodBinding.parameterStyle().equals(
                WRAPPED);
            javaMethod.setBinding(mySOAPBinding);
        } else {
            SOAPBindingImpl sb = new SOAPBindingImpl(defaultBinding);
            if (action != null) {
                sb.setSOAPAction(action);
            } else {
                String defaults = SOAPVersion.SOAP_11 == sb.getSOAPVersion() ? "" : null;
                sb.setSOAPAction(defaults);
            }
            javaMethod.setBinding(sb);
        }
        if (!methodIsWrapped) {
            processDocBareMethod(javaMethod, operationName, method);
        } else if (style.equals(Style.DOCUMENT)) {
            processDocWrappedMethod(javaMethod, methodName, operationName,
                method);
        } else {
            processRpcMethod(javaMethod, methodName, operationName, method);
        }
        model.addJavaMethod(javaMethod);
    }

    private MEP getMEP(Method m){
        if (getAnnotation(m, Oneway.class)!= null) {
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
     * @param operationName the runtime model <code>JavaMethod</code> instance being created
     * @param method the <code>method</code> to model
     */
    protected void processDocWrappedMethod(JavaMethodImpl javaMethod, String methodName,
                                           String operationName, Method method) {
        boolean methodHasHeaderParams = false;
        boolean isOneway = getAnnotation(method, Oneway.class)!= null;
        RequestWrapper reqWrapper = getAnnotation(method,RequestWrapper.class);
        ResponseWrapper resWrapper = getAnnotation(method,ResponseWrapper.class);
        String beanPackage = packageName + PD_JAXWS_PACKAGE_PD;
        if (packageName == null || packageName.length() == 0) {
            beanPackage = JAXWS_PACKAGE_PD;
        }
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

        String reqName = operationName;
        String reqNamespace = targetNamespace;
        String reqPartName = "parameters";
        if (reqWrapper != null) {
            if (reqWrapper.targetNamespace().length() > 0)
                reqNamespace = reqWrapper.targetNamespace();
            if (reqWrapper.localName().length() > 0)
                reqName = reqWrapper.localName();
            try {
                if (reqWrapper.partName().length() > 0)
                    reqPartName = reqWrapper.partName();
            } catch(LinkageError e) {
                //2.1 API dopes n't have this method
                //Do nothing, just default to "parameters"
            }
        }
        QName reqElementName = new QName(reqNamespace, reqName);
        javaMethod.setRequestPayloadName(reqElementName);
        Class requestClass = getRequestWrapperClass(requestClassName, method, reqElementName);

        Class responseClass = null;
        String resName = operationName+"Response";
        String resNamespace = targetNamespace;
        QName resElementName = null;
        String resPartName = "parameters";
        if (!isOneway) {
            if (resWrapper != null) {
                if (resWrapper.targetNamespace().length() > 0)
                    resNamespace = resWrapper.targetNamespace();
                if (resWrapper.localName().length() > 0)
                    resName = resWrapper.localName();
                try {
                    if (resWrapper.partName().length() > 0)
                        resPartName = resWrapper.partName();
                } catch (LinkageError e) {
                    //2.1 API does n't have this method
                    //Do nothing, just default to "parameters"
                }
            }
            resElementName = new QName(resNamespace, resName);
            responseClass = getResponseWrapperClass(responseClassName, method, resElementName);
        }

        TypeInfo typeRef =
                new TypeInfo(reqElementName, requestClass);
        typeRef.setNillable(false);
        WrapperParameter requestWrapper = new WrapperParameter(javaMethod, typeRef,
            Mode.IN, 0);
        requestWrapper.setPartName(reqPartName);
        requestWrapper.setBinding(ParameterBinding.BODY);
        javaMethod.addParameter(requestWrapper);
        WrapperParameter responseWrapper = null;
        if (!isOneway) {
            typeRef = new TypeInfo(resElementName, responseClass);
            typeRef.setNillable(false);
            responseWrapper = new WrapperParameter(javaMethod, typeRef, Mode.OUT, -1);
            javaMethod.addParameter(responseWrapper);
            responseWrapper.setBinding(ParameterBinding.BODY);
        }

        // return value


        WebResult webResult = getAnnotation(method, WebResult.class);
        XmlElement xmlElem = getAnnotation(method, XmlElement.class);
        QName resultQName = getReturnQName(method, webResult, xmlElem);
        Class returnType = method.getReturnType();
        boolean isResultHeader = false;
        if (webResult != null) {
            isResultHeader = webResult.header();
            methodHasHeaderParams = isResultHeader || methodHasHeaderParams;
            if (isResultHeader && xmlElem != null) {
                throw new RuntimeModelerException("@XmlElement cannot be specified on method "+method+" as the return value is bound to header");
            }
            if (resultQName.getNamespaceURI().length() == 0 && webResult.header()) {
                // headers must have a namespace
                resultQName = new QName(targetNamespace, resultQName.getLocalPart());
            }
        }

        if(javaMethod.isAsync()){
            returnType = getAsyncReturnType(method, returnType);
            resultQName = new QName(RETURN);
        }
        resultQName = qualifyWrappeeIfNeeded(resultQName, resNamespace);
        if (!isOneway && (returnType != null) && (!returnType.getName().equals("void"))) {
            Annotation[] rann = getAnnotations(method);
            if (resultQName.getLocalPart() != null) {
                TypeInfo rTypeReference = new TypeInfo(resultQName, returnType, rann);
                metadataReader.getProperties(rTypeReference.properties(), method);
                rTypeReference.setGenericType(method.getGenericReturnType());
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
        Annotation[][] pannotations = getParamAnnotations(method);
        int pos = 0;
        for (Class clazzType : parameterTypes) {
            String partName=null;
            String paramName = "arg"+pos;
            //String paramNamespace = "";
            boolean isHeader = false;

            if(javaMethod.isAsync() && AsyncHandler.class.isAssignableFrom(clazzType)){
                continue;
            }

            boolean isHolder = HOLDER_CLASS.isAssignableFrom(clazzType);
            //set the actual type argument of Holder in the TypeReference
            if (isHolder) {
                if(clazzType==Holder.class){
                    clazzType = (Class) Utils.REFLECTION_NAVIGATOR.erasure(((ParameterizedType)genericParameterTypes[pos]).getActualTypeArguments()[0]);
                }
            }
            Mode paramMode = isHolder ? Mode.INOUT : Mode.IN;
            WebParam webParam = null;
            xmlElem = null;
            for (Annotation annotation : pannotations[pos]) {
                if (annotation.annotationType() == WebParam.class)
                    webParam = (WebParam)annotation;
                else if (annotation.annotationType() == XmlElement.class)
                    xmlElem = (XmlElement)annotation;
            }

            QName paramQName = getParameterQName(method, webParam, xmlElem, paramName);
            if (webParam != null) {
                isHeader = webParam.header();
                methodHasHeaderParams = isHeader || methodHasHeaderParams;
                if (isHeader && xmlElem != null) {
                    throw new RuntimeModelerException("@XmlElement cannot be specified on method "+method+" parameter that is bound to header");
                }
                if(webParam.partName().length() > 0)
                    partName = webParam.partName();
                else
                    partName = paramQName.getLocalPart();
                if (isHeader && paramQName.getNamespaceURI().equals("")) { // headers cannot be in empty namespace
                    paramQName = new QName(targetNamespace, paramQName.getLocalPart());
                }
                paramMode = webParam.mode();
                if (isHolder && paramMode == Mode.IN)
                    paramMode = Mode.INOUT;
            }
            paramQName = qualifyWrappeeIfNeeded(paramQName, reqNamespace);
            typeRef =
                new TypeInfo(paramQName, clazzType, pannotations[pos]);
            metadataReader.getProperties(typeRef.properties(), method, pos);
            typeRef.setGenericType(genericParameterTypes[pos]);
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

        //If the method has any parameter or return type that is bound to a header, use "result" as part name to avoid
        // name collison of same input part name and output part name ("parameters") shown up as param names on the
        // client mapping.
        if(methodHasHeaderParams) {
            resPartName = "result";
        }
        if(responseWrapper != null)
            responseWrapper.setPartName(resPartName);
        processExceptions(javaMethod, method);
    }

    private QName qualifyWrappeeIfNeeded(QName resultQName, String ns) {
        Object o = config.properties().get(DocWrappeeNamespapceQualified);
        boolean qualified = (o!= null && o instanceof Boolean) ? ((Boolean) o) : false;
        if (qualified) {
            if (resultQName.getNamespaceURI() == null || "".equals(resultQName.getNamespaceURI())) {
                return new QName(ns, resultQName.getLocalPart());
            }
        }
        return resultQName;
    }

    /**
     * models a rpc/literal method
     * @param javaMethod the runtime model <code>JavaMethod</code> instance being created
     * @param methodName the name of the <code>method</code> being modeled.
     * @param operationName the WSDL operation name for this <code>method</code>
     * @param method the runtime model <code>JavaMethod</code> instance being created
     */
    protected void processRpcMethod(JavaMethodImpl javaMethod, String methodName,
                                    String operationName, Method method) {
        boolean isOneway = getAnnotation(method, Oneway.class) != null;

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

        if(binding != null && Style.RPC.equals(binding.getBinding().getStyle())){
            QName opQName = new QName(binding.getBinding().getPortTypeName().getNamespaceURI(), operationName);
            WSDLBoundOperation op = binding.getBinding().get(opQName);
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
        javaMethod.setRequestPayloadName(reqElementName);
        QName resElementName = null;
        if (!isOneway) {
            resElementName = new QName(respNamespace, operationName+RESPONSE);
        }

        Class wrapperType = WrapperComposite.class;
        TypeInfo typeRef = new TypeInfo(reqElementName, wrapperType);
        WrapperParameter requestWrapper = new WrapperParameter(javaMethod, typeRef, Mode.IN, 0);
        requestWrapper.setInBinding(ParameterBinding.BODY);
        javaMethod.addParameter(requestWrapper);
        WrapperParameter responseWrapper = null;
        if (!isOneway) {
            typeRef = new TypeInfo(resElementName, wrapperType);
            responseWrapper = new WrapperParameter(javaMethod, typeRef, Mode.OUT, -1);
            responseWrapper.setOutBinding(ParameterBinding.BODY);
            javaMethod.addParameter(responseWrapper);
        }

        Class returnType = method.getReturnType();
        String resultName = RETURN;
        String resultTNS = targetNamespace;
        String resultPartName = resultName;
        boolean isResultHeader = false;
        WebResult webResult = getAnnotation(method, WebResult.class);

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
            Annotation[] rann = getAnnotations(method);
            TypeInfo rTypeReference = new TypeInfo(resultQName, returnType, rann);
            metadataReader.getProperties(rTypeReference.properties(), method);
            rTypeReference.setGenericType(method.getGenericReturnType());
            ParameterImpl returnParameter = new ParameterImpl(javaMethod, rTypeReference, Mode.OUT, -1);
            returnParameter.setPartName(resultPartName);
            if(isResultHeader){
                returnParameter.setBinding(ParameterBinding.HEADER);
                javaMethod.addParameter(returnParameter);
                rTypeReference.setGlobalElement(true);
            }else{
                ParameterBinding rb = getBinding(operationName, resultPartName, false, Mode.OUT);
                returnParameter.setBinding(rb);
                if(rb.isBody()){
                    rTypeReference.setGlobalElement(false);
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
        Annotation[][] pannotations = getParamAnnotations(method);
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
                    clazzType = (Class) Utils.REFLECTION_NAVIGATOR.erasure(((ParameterizedType)genericParameterTypes[pos]).getActualTypeArguments()[0]);
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
                new TypeInfo(paramQName, clazzType, pannotations[pos]);
            metadataReader.getProperties(typeRef.properties(), method, pos);
            typeRef.setGenericType(genericParameterTypes[pos]);
            ParameterImpl param = new ParameterImpl(javaMethod, typeRef, paramMode, pos++);
            param.setPartName(partName);

            if(paramMode == Mode.INOUT){
                ParameterBinding pb = getBinding(operationName, partName, isHeader, Mode.IN);
                param.setInBinding(pb);
                pb = getBinding(operationName, partName, isHeader, Mode.OUT);
                param.setOutBinding(pb);
            }else{
                if (isHeader) {
                    typeRef.setGlobalElement(true);
                    param.setBinding(ParameterBinding.HEADER);
                } else {
                    ParameterBinding pb = getBinding(operationName, partName, false, paramMode);
                    param.setBinding(pb);
                }
            }
            if(param.getInBinding().isBody()){
                typeRef.setGlobalElement(false);
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
        Action actionAnn = getAnnotation(method, Action.class);
        FaultAction[] faultActions = {};
        if(actionAnn != null)
            faultActions = actionAnn.fault();
        for (Class<?> exception : method.getExceptionTypes()) {

            //Exclude RuntimeException, RemoteException and Error etc
            if (!EXCEPTION_CLASS.isAssignableFrom(exception))
                continue;
            if (RUNTIME_EXCEPTION_CLASS.isAssignableFrom(exception) || REMOTE_EXCEPTION_CLASS.isAssignableFrom(exception))
                continue;

            Class exceptionBean;
            Annotation[] anns;
            WebFault webFault = getAnnotation(exception, WebFault.class);
            Method faultInfoMethod = getWSDLExceptionFaultInfo(exception);
            ExceptionType exceptionType = ExceptionType.WSDLException;
            String namespace = targetNamespace;
            String name = exception.getSimpleName();
            String beanPackage = packageName + PD_JAXWS_PACKAGE_PD;
            if (packageName.length() == 0)
                beanPackage = JAXWS_PACKAGE_PD;
            String className = beanPackage+ name + BEAN;
            String messageName = exception.getSimpleName();
            if (webFault != null) {
                if (webFault.faultBean().length()>0)
                    className = webFault.faultBean();
                if (webFault.name().length()>0)
                    name = webFault.name();
                if (webFault.targetNamespace().length()>0)
                    namespace = webFault.targetNamespace();
                if (webFault.messageName().length()>0)
                    messageName = webFault.messageName();
            }
            if (faultInfoMethod == null)  {
                exceptionBean = getExceptionBeanClass(className, exception, name, namespace);
                exceptionType = ExceptionType.UserDefined;
                anns = getAnnotations(exceptionBean);
            } else {
                exceptionBean = faultInfoMethod.getReturnType();
                anns = getAnnotations(faultInfoMethod);
            }
            QName faultName = new QName(namespace, name);
            TypeInfo typeRef = new TypeInfo(faultName, exceptionBean, anns);
            CheckedExceptionImpl checkedException =
                new CheckedExceptionImpl(javaMethod, exception, typeRef, exceptionType);
            checkedException.setMessageName(messageName);
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
//      if (!exception.isAnnotationPresent(WebFault.class))
        if (getAnnotation(exception, WebFault.class) == null)
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
     * @param operationName the runtime model <code>JavaMethod</code> instance being created
     * @param method the runtime model <code>JavaMethod</code> instance being created
     */
    protected void processDocBareMethod(JavaMethodImpl javaMethod,
                                       String operationName, Method method) {

        String resultName = operationName+RESPONSE;
        String resultTNS = targetNamespace;
        String resultPartName = null;
        boolean isResultHeader = false;
        WebResult webResult = getAnnotation(method, WebResult.class);
        if (webResult != null) {
            if (webResult.name().length() > 0)
                resultName = webResult.name();
            if (webResult.targetNamespace().length() > 0)
                resultTNS = webResult.targetNamespace();
            resultPartName = webResult.partName();
            isResultHeader = webResult.header();
        }

        Class returnType = method.getReturnType();
        Type gReturnType = method.getGenericReturnType();
        if(javaMethod.isAsync()){
            returnType = getAsyncReturnType(method, returnType);
        }

        if ((returnType != null) && (!returnType.getName().equals("void"))) {
            Annotation[] rann = getAnnotations(method);
            if (resultName != null) {
                QName responseQName = new QName(resultTNS, resultName);
                TypeInfo rTypeReference = new TypeInfo(responseQName, returnType, rann);
                rTypeReference.setGenericType(gReturnType);
                metadataReader.getProperties(rTypeReference.properties(), method);
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
        Annotation[][] pannotations = getParamAnnotations(method);
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
                    clazzType = (Class) Utils.REFLECTION_NAVIGATOR.erasure(((ParameterizedType)genericParameterTypes[pos]).getActualTypeArguments()[0]);
            }

            Mode paramMode = isHolder ? Mode.INOUT : Mode.IN;
            for (Annotation annotation : pannotations[pos]) {
                if (annotation.annotationType() == javax.jws.WebParam.class) {
                    javax.jws.WebParam webParam = (javax.jws.WebParam) annotation;
                    paramMode = webParam.mode();
                    if (isHolder && paramMode == Mode.IN)
                        paramMode = Mode.INOUT;
                    isHeader = webParam.header();
                    if(isHeader)
                        paramName = "arg"+pos;
                    if(paramMode == Mode.OUT && !isHeader)
                        paramName = operationName+RESPONSE;
                    if (webParam.name().length() > 0)
                        paramName = webParam.name();
                    partName = webParam.partName();
                    if (!webParam.targetNamespace().equals("")) {
                        requestNamespace = webParam.targetNamespace();
                    }
                    break;
                }
            }

            QName requestQName = new QName(requestNamespace, paramName);
            if (!isHeader && paramMode != Mode.OUT) javaMethod.setRequestPayloadName(requestQName);
            //doclit/wrapped
            TypeInfo typeRef = //operationName with upper 1 char
                new TypeInfo(requestQName, clazzType,
                    pannotations[pos]);
            metadataReader.getProperties(typeRef.properties(), method, pos);
            typeRef.setGenericType(genericParameterTypes[pos]);
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

    // Does a conservative check if there is only one BODY part for input
    // and output message. We are not considering INOUT parameters at this
    // time since binding information is not applied. Also, there isn't
    // anyway to represent some cases in SEI. For example, a INOUT parameter
    // could be bound to body for input message, header for OUTPUT message
    // in wsdl:binding
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

        int numOutBodyBindings = 0;
        for(Parameter param : javaMethod.getResponseParameters()){
            if(param.getBinding().equals(ParameterBinding.BODY) && param.isOUT()){
                numOutBodyBindings++;
            }
            if(numOutBodyBindings > 1){
                throw new RuntimeModelerException(ModelerMessages.localizableNOT_A_VALID_BARE_METHOD(portClass.getName(), javaMethod.getMethod().getName()));
            }
        }
    }

    private Class getAsyncReturnType(Method method, Class returnType) {
        if(Response.class.isAssignableFrom(returnType)){
            Type ret = method.getGenericReturnType();
            return (Class) Utils.REFLECTION_NAVIGATOR.erasure(((ParameterizedType)ret).getActualTypeArguments()[0]);
        }else{
            Type[] types = method.getGenericParameterTypes();
            Class[] params = method.getParameterTypes();
            int i = 0;
            for(Class cls : params){
                if(AsyncHandler.class.isAssignableFrom(cls)){
                    return (Class) Utils.REFLECTION_NAVIGATOR.erasure(((ParameterizedType)types[i]).getActualTypeArguments()[0]);
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
        return getServiceName(implClass, null);
    }

    public static QName getServiceName(Class<?> implClass, boolean isStandard) {
        return getServiceName(implClass, null, isStandard);
    }

    public static QName getServiceName(Class<?> implClass, MetadataReader reader) {
        return getServiceName(implClass, reader, true);
    }

    public static QName getServiceName(Class<?> implClass, MetadataReader reader, boolean isStandard) {
        if (implClass.isInterface()) {
            throw new RuntimeModelerException("runtime.modeler.cannot.get.serviceName.from.interface",
                                    implClass.getCanonicalName());
        }

        String name = implClass.getSimpleName()+SERVICE;
        String packageName = "";
        if (implClass.getPackage() != null)
            packageName = implClass.getPackage().getName();

        WebService webService = getAnnotation(WebService.class, implClass, reader);
        if (isStandard && webService == null) {
            throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                implClass.getCanonicalName());
        }
        if (webService != null && webService.serviceName().length() > 0) {
            name = webService.serviceName();
        }
        String targetNamespace = getNamespace(packageName);
        if (webService != null && webService.targetNamespace().length() > 0) {
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
        return getPortName(implClass, null, targetNamespace);
    }

    public static QName getPortName(Class<?> implClass, String targetNamespace, boolean isStandard) {
        return getPortName(implClass, null, targetNamespace, isStandard);
    }

    public static QName getPortName(Class<?> implClass, MetadataReader reader, String targetNamespace) {
        return getPortName(implClass, reader, targetNamespace, true);
    }

    public static QName getPortName(Class<?> implClass, MetadataReader reader, String targetNamespace, boolean isStandard) {
        WebService webService = getAnnotation(WebService.class, implClass, reader);
        if (isStandard && webService == null) {
            throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                implClass.getCanonicalName());
        }
        String name;
        if (webService != null && webService.portName().length() > 0) {
            name = webService.portName();
        } else if (webService != null && webService.name().length() > 0) {
            name = webService.name()+PORT;
        } else {
            name = implClass.getSimpleName()+PORT;
        }

        if (targetNamespace == null) {
            if (webService != null && webService.targetNamespace().length() > 0) {
                targetNamespace = webService.targetNamespace();
            } else {
                String packageName = null;
                if (implClass.getPackage() != null) {
                    packageName = implClass.getPackage().getName();
                }
                if (packageName != null) {
                    targetNamespace = getNamespace(packageName);
                }
                if (targetNamespace == null) {
                    throw new RuntimeModelerException("runtime.modeler.no.package",
                        implClass.getName());
                }
            }

        }

        return new QName(targetNamespace, name);
    }

    static <A extends Annotation> A getAnnotation(Class<A> t, Class<?> cls, MetadataReader reader) {
        return (reader == null)? cls.getAnnotation(t) : reader.getAnnotation(t, cls);
    }

    /**
     * Gives portType QName from implementatorClass or SEI
     * @param  implOrSeiClass cant be null
     * @return  <code>wsdl:portType@name</code>, null if it could not find the annotated class.
     */
    public static QName getPortTypeName(Class<?> implOrSeiClass){
        return getPortTypeName(implOrSeiClass, null, null);
    }

    public static QName getPortTypeName(Class<?> implOrSeiClass, MetadataReader metadataReader){
        return getPortTypeName(implOrSeiClass, null, metadataReader);
    }

    public static QName getPortTypeName(Class<?> implOrSeiClass, String tns, MetadataReader reader){
        assert(implOrSeiClass != null);
        WebService webService = getAnnotation(WebService.class, implOrSeiClass, reader);
        Class<?> clazz = implOrSeiClass;
        if (webService == null)
                throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                                           implOrSeiClass.getCanonicalName());

        if (!implOrSeiClass.isInterface()) {
            String epi = webService.endpointInterface();
            if (epi.length() > 0) {
                try {
                    clazz = Thread.currentThread().getContextClassLoader().loadClass(epi);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeModelerException("runtime.modeler.class.not.found", epi);
                }
                WebService ws = getAnnotation(WebService.class, clazz, reader);
                if (ws == null) {
                    throw new RuntimeModelerException("runtime.modeler.endpoint.interface.no.webservice",
                                        webService.endpointInterface());
                }
            }
        }

        webService = getAnnotation(WebService.class, clazz, reader);
        String name = webService.name();
        if(name.length() == 0){
            name = clazz.getSimpleName();
        }
        if (tns == null || "".equals(tns.trim())) tns = webService.targetNamespace();
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
            WSDLBoundOperation bo = binding.getBinding().get(opName);
            if(bo != null)
                return bo.getPart(partName, mode);
        }
        return null;
    }

    private static Boolean getBooleanSystemProperty(final String prop) {
        return AccessController.doPrivileged(
            new java.security.PrivilegedAction<Boolean>() {
                public Boolean run() {
                    String value = System.getProperty(prop);
                    return value != null ? Boolean.valueOf(value) : Boolean.FALSE;
                }
            }
        );
    }

    private static QName getReturnQName(Method method, WebResult webResult, XmlElement xmlElem) {
        String webResultName = null;
        if (webResult != null && webResult.name().length() > 0) {
            webResultName = webResult.name();
        }
        String xmlElemName = null;
        if (xmlElem != null && !xmlElem.name().equals("##default")) {
            xmlElemName = xmlElem.name();
        }
        if (xmlElemName != null && webResultName != null && !xmlElemName.equals(webResultName)) {
            throw new RuntimeModelerException("@XmlElement(name)="+xmlElemName+" and @WebResult(name)="+webResultName+" are different for method " +method);
        }
        String localPart = RETURN;
        if (webResultName != null) {
            localPart = webResultName;
        } else if (xmlElemName != null) {
            localPart =  xmlElemName;
        }

        String webResultNS = null;
        if (webResult != null && webResult.targetNamespace().length() > 0) {
            webResultNS = webResult.targetNamespace();
        }
        String xmlElemNS = null;
        if (xmlElem != null && !xmlElem.namespace().equals("##default")) {
            xmlElemNS = xmlElem.namespace();
        }
        if (xmlElemNS != null && webResultNS != null && !xmlElemNS.equals(webResultNS)) {
            throw new RuntimeModelerException("@XmlElement(namespace)="+xmlElemNS+" and @WebResult(targetNamespace)="+webResultNS+" are different for method " +method);
        }
        String ns = "";
        if (webResultNS != null) {
            ns = webResultNS;
        } else if (xmlElemNS != null) {
            ns =  xmlElemNS;
        }

        return new QName(ns, localPart);
    }

    private static QName getParameterQName(Method method, WebParam webParam, XmlElement xmlElem, String paramDefault) {
        String webParamName = null;
        if (webParam != null && webParam.name().length() > 0) {
            webParamName = webParam.name();
        }
        String xmlElemName = null;
        if (xmlElem != null && !xmlElem.name().equals("##default")) {
            xmlElemName = xmlElem.name();
        }
        if (xmlElemName != null && webParamName != null && !xmlElemName.equals(webParamName)) {
            throw new RuntimeModelerException("@XmlElement(name)="+xmlElemName+" and @WebParam(name)="+webParamName+" are different for method " +method);
        }
        String localPart = paramDefault;
        if (webParamName != null) {
            localPart = webParamName;
        } else if (xmlElemName != null) {
            localPart =  xmlElemName;
        }

        String webParamNS = null;
        if (webParam != null && webParam.targetNamespace().length() > 0) {
            webParamNS = webParam.targetNamespace();
        }
        String xmlElemNS = null;
        if (xmlElem != null && !xmlElem.namespace().equals("##default")) {
            xmlElemNS = xmlElem.namespace();
        }
        if (xmlElemNS != null && webParamNS != null && !xmlElemNS.equals(webParamNS)) {
            throw new RuntimeModelerException("@XmlElement(namespace)="+xmlElemNS+" and @WebParam(targetNamespace)="+webParamNS+" are different for method " +method);
        }
        String ns = "";
        if (webParamNS != null) {
            ns = webParamNS;
        } else if (xmlElemNS != null) {
            ns =  xmlElemNS;
        }

        return new QName(ns, localPart);
    }


}

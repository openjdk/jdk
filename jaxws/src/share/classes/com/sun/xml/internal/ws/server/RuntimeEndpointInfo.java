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

package com.sun.xml.internal.ws.server;

import com.sun.xml.internal.ws.server.provider.ProviderModel;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.concurrent.Executor;

import javax.annotation.Resource;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Provider;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.soap.SOAPBinding;
import javax.xml.transform.Source;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.xml.ws.WebServiceProvider;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.Endpoint;

import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.binding.soap.SOAPBindingImpl;
import com.sun.xml.internal.ws.model.RuntimeModel;
import com.sun.xml.internal.ws.modeler.RuntimeModeler;
import com.sun.xml.internal.ws.server.DocInfo.DOC_TYPE;
import com.sun.xml.internal.ws.spi.runtime.Binding;
import com.sun.xml.internal.ws.spi.runtime.WebServiceContext;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.localization.Localizer;
import com.sun.xml.internal.ws.util.HandlerAnnotationInfo;
import com.sun.xml.internal.ws.util.HandlerAnnotationProcessor;
import com.sun.xml.internal.ws.wsdl.parser.RuntimeWSDLParser;
import com.sun.xml.internal.ws.wsdl.parser.Service;
import com.sun.xml.internal.ws.wsdl.parser.WSDLDocument;
import com.sun.xml.internal.ws.wsdl.writer.WSDLGenerator;

import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;




/**
 * modeled after the javax.xml.ws.Endpoint class in API.
 * Contains all the information about Binding, handler chain, Implementor object,
 * WSDL & Schema Metadata
 * @author WS Development Team
 */
public class RuntimeEndpointInfo implements com.sun.xml.internal.ws.spi.runtime.RuntimeEndpointInfo {

    private String name;
    private QName portName;
    private QName serviceName;
    private String wsdlFileName;
    private boolean deployed;
    private String urlPattern;
    private List<Source> metadata;
    private Binding binding;
    private RuntimeModel runtimeModel;
    private Object implementor;
    private Class implementorClass;
    private Map<String, DocInfo> docs;      // /WEB-INF/wsdl/xxx.wsdl -> DocInfo
    private Map<String, DocInfo> query2Doc;     // (wsdl=a) --> DocInfo
    private WebServiceContext wsContext;
    private boolean beginServiceDone;
    private boolean endServiceDone;
    private boolean injectedContext;
    private URL wsdlUrl;
    private EntityResolver wsdlResolver;
    private QName portTypeName;
    private Integer mtomThreshold;
    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.endpoint");
    private static final Localizer localizer = new Localizer();
    private static final LocalizableMessageFactory messageFactory =
        new LocalizableMessageFactory("com.sun.xml.internal.ws.resources.server");
    private WebService ws;
    private WebServiceProvider wsProvider;
    private ProviderModel providerModel;

    public String getName() {
        return name;
    }

    public void setName(String s) {
        name = s;
    }

    public String getWSDLFileName() {
        return wsdlFileName;
    }

    public void setWSDLFileName(String s) {
        wsdlFileName = s;
    }

    /**
     * set the URL for primary WSDL, and an EntityResolver to resolve all
     * imports/references
     */
    public void setWsdlInfo(URL wsdlUrl, EntityResolver wsdlResolver) {
        this.wsdlUrl = wsdlUrl;
        this.wsdlResolver = wsdlResolver;
    }

    public EntityResolver getWsdlResolver() {
        return wsdlResolver;
    }

    public URL getWsdlUrl() {
        return wsdlUrl;
    }

    public boolean isDeployed() {
        return deployed;
    }

    public void createProviderModel() {
        providerModel = new ProviderModel(implementorClass, binding);
    }

    public void createSEIModel() {
        // Create runtime model for non Provider endpoints

        // wsdlURL will be null, means we will generate WSDL. Hence no need to apply
        // bindings or need to look in the WSDL
        if(wsdlUrl == null){
            RuntimeModeler rap = new RuntimeModeler(getImplementorClass(),
                getImplementor(), getServiceName(), ((BindingImpl)binding).getBindingId());
            if (getPortName() != null) {
                rap.setPortName(getPortName());
            }
            runtimeModel = rap.buildRuntimeModel();
        }else {
            try {
                WSDLDocument wsdlDoc = RuntimeWSDLParser.parse(getWsdlUrl(), getWsdlResolver());
                com.sun.xml.internal.ws.wsdl.parser.Binding wsdlBinding = null;
                if(serviceName == null)
                    serviceName = RuntimeModeler.getServiceName(getImplementorClass());
                if(getPortName() != null){
                    wsdlBinding = wsdlDoc.getBinding(getServiceName(), getPortName());
                    if(wsdlBinding == null)
                        throw new ServerRtException("runtime.parser.wsdl.incorrectserviceport", new Object[]{serviceName, portName, getWsdlUrl()});
                }else{
                    Service service = wsdlDoc.getService(serviceName);
                    if(service == null)
                        throw new ServerRtException("runtime.parser.wsdl.noservice", new Object[]{serviceName, getWsdlUrl()});

                    String bindingId = ((BindingImpl)binding).getBindingId();
                    List<com.sun.xml.internal.ws.wsdl.parser.Binding> bindings = wsdlDoc.getBindings(service, bindingId);
                    if(bindings.size() == 0)
                        throw new ServerRtException("runtime.parser.wsdl.nobinding", new Object[]{bindingId, serviceName, getWsdlUrl()});

                    if(bindings.size() > 1)
                        throw new ServerRtException("runtime.parser.wsdl.multiplebinding", new Object[]{bindingId, serviceName, getWsdlUrl()});
                }
                //now we got the Binding so lets build the model
                RuntimeModeler rap = new RuntimeModeler(getImplementorClass(), getImplementor(), getServiceName(), wsdlBinding);
                if (getPortName() != null) {
                    rap.setPortName(getPortName());
                }
                runtimeModel = rap.buildRuntimeModel();
            } catch (IOException e) {
                throw new ServerRtException("runtime.parser.wsdl", getWsdlUrl().toString());
            } catch (XMLStreamException e) {
                throw new ServerRtException("runtime.saxparser.exception",
                        new Object[]{e.getMessage(), e.getLocation()});
            } catch (SAXException e) {
                throw new ServerRtException("runtime.parser.wsdl", getWsdlUrl().toString());
            }
        }
    }


    public boolean isProviderEndpoint() {
        Annotation ann = getImplementorClass().getAnnotation(
            WebServiceProvider.class);
        return (ann != null);
    }

    /*
     * If serviceName is not already set via DD or programmatically, it uses
     * annotations on implementorClass to set ServiceName.
     */
    public void doServiceNameProcessing() {
        if (getServiceName() == null) {
            if (isProviderEndpoint()) {
                WebServiceProvider wsProvider =
                    (WebServiceProvider)getImplementorClass().getAnnotation(
                        WebServiceProvider.class);
                String tns = wsProvider.targetNamespace();
                String local = wsProvider.serviceName();
                // create QName("", ""), if the above values are default
                setServiceName(new QName(tns, local));
            } else {
                setServiceName(RuntimeModeler.getServiceName(getImplementorClass()));
            }
        }
    }

    /*
     * If portName is not already set via DD or programmatically, it uses
     * annotations on implementorClass to set PortName.
     */
    public void doPortNameProcessing() {
        if (getPortName() == null) {
            if (isProviderEndpoint()) {
                WebServiceProvider wsProvider =
                    (WebServiceProvider)getImplementorClass().getAnnotation(
                        WebServiceProvider.class);
                String tns = wsProvider.targetNamespace();
                String local = wsProvider.portName();
                // create QName("", ""), if the above values are default
                setPortName(new QName(tns, local));

            } else {
                setPortName(RuntimeModeler.getPortName(getImplementorClass(),
                    getServiceName().getNamespaceURI()));
            }
        } else {
            String serviceNS = getServiceName().getNamespaceURI();
            String portNS = getPortName().getNamespaceURI();
            if (!serviceNS.equals(portNS)) {
                throw new ServerRtException("wrong.tns.for.port",
                    new Object[] { portNS, serviceNS });

            }
        }
    }

    /*
     * Sets PortType QName
     */
    public void doPortTypeNameProcessing() {
        if (getPortTypeName() == null) {
            if (!isProviderEndpoint()) {
                setPortTypeName(RuntimeModeler.getPortTypeName(getImplementorClass()));
            }
        }
    }


    /**
     * creates a RuntimeModel using @link com.sun.xml.internal.ws.modeler.RuntimeModeler.
     * The modeler creates the model by reading annotations on ImplementorClassobject.
     * RuntimeModel is read only and is accessed from multiple threads afterwards.

     */
    public void init() {
        if (implementor == null) {
            throw new ServerRtException("null.implementor");
        }
        if (implementorClass == null) {
            setImplementorClass(getImplementor().getClass());
        }

        // verify if implementor class has @WebService or @WebServiceProvider
        verifyImplementorClass();

        // ServiceName processing
        doServiceNameProcessing();

        // Port Name processing
        doPortNameProcessing();

        // PortType Name processing
        //doPortTypeNameProcessing();

        // setting a default binding
        if (binding == null) {
            String bindingId = RuntimeModeler.getBindingId(getImplementorClass());
            setBinding(new SOAPBindingImpl(SOAPBinding.SOAP11HTTP_BINDING));
        }

        if (isProviderEndpoint()) {
            checkProvider();
            createProviderModel();
        } else {
            // Create runtime model for non Provider endpoints
            createSEIModel();
            if (getServiceName() == null) {
                setServiceName(runtimeModel.getServiceQName());
            }
            if (getPortName() == null) {
                setPortName(runtimeModel.getPortName());
            }
            //set mtom processing
            if(binding instanceof SOAPBindingImpl){
                runtimeModel.enableMtom(((SOAPBinding)binding).isMTOMEnabled());
            }
        }
        // Process @HandlerChain, if handler-chain is not set via Deployment
        // Descriptor
        if (getBinding().getHandlerChain() == null) {
                String bindingId = ((BindingImpl) binding).getActualBindingId();
                HandlerAnnotationInfo chainInfo =
                    HandlerAnnotationProcessor.buildHandlerInfo(
                    implementorClass, getServiceName(),
                    getPortName(), bindingId);
                if (chainInfo != null) {
                    getBinding().setHandlerChain(chainInfo.getHandlers());
                    if (getBinding() instanceof SOAPBinding) {
                        ((SOAPBinding) getBinding()).setRoles(
                            chainInfo.getRoles());
                    }
                }
        }
        deployed = true;
    }

    public boolean needWSDLGeneration() {
        if (isProviderEndpoint()) {
            return false;
        }
        return (getWsdlUrl() == null);
    }

    /*
     * Generates the WSDL and XML Schema for the endpoint if necessary
     * It generates WSDL only for SOAP1.1, and for XSOAP1.2 bindings
     */
    public void generateWSDL() {
        BindingImpl bindingImpl = (BindingImpl)getBinding();
        String bindingId = bindingImpl.getActualBindingId();
        if (!bindingId.equals(SOAPBinding.SOAP11HTTP_BINDING) &&
            !bindingId.equals(SOAPBinding.SOAP11HTTP_MTOM_BINDING) &&
            !bindingId.equals(SOAPBindingImpl.X_SOAP12HTTP_BINDING)) {
            throw new ServerRtException("can.not.generate.wsdl", bindingId);
        }

        if (bindingId.equals(SOAPBindingImpl.X_SOAP12HTTP_BINDING)) {
            String msg = localizer.localize(
                messageFactory.getMessage("generate.non.standard.wsdl"));
            logger.warning(msg);
        }

        // Generate WSDL and schema documents using runtime model
        if (getDocMetadata() == null) {
            setMetadata(new HashMap<String, DocInfo>());
        }
        WSDLGenResolver wsdlResolver = new WSDLGenResolver(getDocMetadata());
        WSDLGenerator wsdlGen = new WSDLGenerator(runtimeModel, wsdlResolver,
                ((BindingImpl)binding).getBindingId());
        try {
            wsdlGen.doGeneration();
        } catch(Exception e) {
            throw new ServerRtException("server.rt.err",e);
        }
        setWSDLFileName(wsdlResolver.getWSDLFile());
    }

    /*
     * Provider endpoint validation
     */
    private void checkProvider() {
        if (!Provider.class.isAssignableFrom(getImplementorClass())) {
            throw new ServerRtException("not.implement.provider",
                new Object[] {getImplementorClass()});
        }
    }

    public QName getPortName() {
        return portName;
    }

    public void setPortName(QName n) {
        portName = n;
    }

    public QName getPortTypeName() {
        return portTypeName;
    }

    public void setPortTypeName(QName n) {
        portTypeName = n;
    }

    public QName getServiceName() {
        return serviceName;
    }

    public void setServiceName(QName n) {
        serviceName = n;
    }

    public String getUrlPattern() {
        return urlPattern;
    }

    public String getUrlPatternWithoutStar() {
        if (urlPattern.endsWith("/*")) {
            return urlPattern.substring(0, urlPattern.length() - 2);
        } else {
            return urlPattern;
        }
    }


    public void setUrlPattern(String s) {
        urlPattern = s;
    }

    public void setBinding(Binding binding){
        this.binding = binding;
    }

    public Binding getBinding() {
        return binding;
    }

    public java.util.List<Source> getMetadata() {
        return metadata;
    }

    public void setMetadata(java.util.List<Source> metadata) {

        this.metadata = metadata;
    }

    public RuntimeModel getRuntimeModel() {
        return runtimeModel;
    }

    public Object getImplementor() {
        return implementor;
    }

    public void setImplementor(Object implementor) {
        this.implementor = implementor;
    }

    public Class getImplementorClass() {
        if (implementorClass == null) {
            implementorClass = implementor.getClass();
        }
        return implementorClass;
    }

    public void setImplementorClass(Class implementorClass) {
        this.implementorClass = implementorClass;
    }

    public void setMetadata(Map<String, DocInfo> docs) {
        this.docs = docs;
    }

    private void updateQuery2DocInfo() {
        // update (wsdl, xsd=1 )-->DocInfo map
        if (query2Doc != null) {
            query2Doc.clear();
        } else {
            query2Doc = new HashMap<String, DocInfo>();
        }
        Set<Map.Entry<String, DocInfo>> entries = docs.entrySet();
        for(Map.Entry<String, DocInfo> entry : entries) {
            DocInfo docInfo = entry.getValue();
            // Check to handle ?WSDL
            if (docInfo.getQueryString().equals("wsdl")) {
                query2Doc.put("WSDL", docInfo);
            }
            query2Doc.put(docInfo.getQueryString(), docInfo);
        }
    }

    public WebServiceContext getWebServiceContext() {
        return wsContext;
    }

    public void setWebServiceContext(WebServiceContext wsContext) {
        this.wsContext = wsContext;
    }


    /*
     * key - /WEB-INF/wsdl/xxx.wsdl
     */
    public Map<String, DocInfo> getDocMetadata() {
        return docs;
    }

    /*
     * path - /WEB-INF/wsdl/xxx.wsdl
     * return - xsd=a | wsdl | wsdl=b etc
     */
    public String getQueryString(URL url) {
        Set<Entry<String, DocInfo>> entries = getDocMetadata().entrySet();
        for(Entry<String, DocInfo> entry : entries) {
            // URLs are not matching. servlet container bug ?
            if (entry.getValue().getUrl().toString().equals(url.toString())) {
                return entry.getValue().getQueryString();
            }
        }
        return null;
    }

    /*
     * queryString - xsd=a | wsdl | wsdl=b etc
     * return - /WEB-INF/wsdl/xxx.wsdl
     */
    public String getPath(String queryString) {
        if (query2Doc != null) {
            DocInfo docInfo = query2Doc.get(queryString);
            return (docInfo == null) ? null : docInfo.getUrl().toString();
        }
        return null;
    }

    /*
     * Injects the WebServiceContext. Called from Servlet.init(), or
     * Endpoint.publish(). Used synchronized because multiple servlet
     * instances may call this in their init()
     */
    public synchronized void injectContext()
    throws IllegalAccessException, InvocationTargetException {
        if (injectedContext) {
            return;
        }
        try {
            doFieldsInjection();
            doMethodsInjection();
        } finally {
            injectedContext = true;
        }
    }

    private void doFieldsInjection() {
        Class c = getImplementorClass();
        Field[] fields = c.getDeclaredFields();
        for(final Field field: fields) {
            Resource resource = field.getAnnotation(Resource.class);
            if (resource != null) {
                Class resourceType = resource.type();
                Class fieldType = field.getType();
                if (resourceType.equals(Object.class)) {
                    if (fieldType.equals(javax.xml.ws.WebServiceContext.class)) {
                        injectField(field);
                    }
                } else if (resourceType.equals(javax.xml.ws.WebServiceContext.class)) {
                    if (fieldType.isAssignableFrom(javax.xml.ws.WebServiceContext.class)) {
                        injectField(field);
                    } else {
                        throw new ServerRtException("wrong.field.type",
                            field.getName());
                    }
                }
            }
        }
    }

    private void doMethodsInjection() {
        Class c = getImplementorClass();
        Method[] methods = c.getDeclaredMethods();
        for(final Method method : methods) {
            Resource resource = method.getAnnotation(Resource.class);
            if (resource != null) {
                Class[] paramTypes = method.getParameterTypes();
                if (paramTypes.length != 1) {
                    throw new ServerRtException("wrong.no.parameters",
                        method.getName());
                }
                Class resourceType = resource.type();
                Class argType = paramTypes[0];
                if (resourceType.equals(Object.class)
                    && argType.equals(javax.xml.ws.WebServiceContext.class)) {
                    invokeMethod(method, new Object[] { wsContext });
                } else if (resourceType.equals(javax.xml.ws.WebServiceContext.class)) {
                    if (argType.isAssignableFrom(javax.xml.ws.WebServiceContext.class)) {
                        invokeMethod(method, new Object[] { wsContext });
                    } else {
                        throw new ServerRtException("wrong.parameter.type",
                            method.getName());
                    }
                }
            }
        }
    }

    /*
     * injects a resource into a Field
     */
    private void injectField(final Field field) {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                public Object run() throws IllegalAccessException,
                    InvocationTargetException {
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    field.set(implementor, wsContext);
                    return null;
                }
            });
        } catch(PrivilegedActionException e) {
            throw new ServerRtException("server.rt.err",e.getException());
        }
    }

    /*
     * Helper method to invoke a Method
     */
    private void invokeMethod(final Method method, final Object[] args) {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                public Object run() throws IllegalAccessException,
                InvocationTargetException {
                    if (!method.isAccessible()) {
                        method.setAccessible(true);
                    }
                    method.invoke(implementor, args);
                    return null;
                }
            });
        } catch(PrivilegedActionException e) {
            throw new ServerRtException("server.rt.err",e.getException());
        }
    }

    /*
     * Calls the first method in the implementor object that has @BeginService
     * annotation. Servlet.init(), or Endpoint.publish() may call this. Used
     * synchronized because multiple servlet instances may call this in their
     * init()
     */
    public synchronized void beginService() {
        if (beginServiceDone) {
            return;                 // Already called for this endpoint object
        }
        try {
            invokeOnceMethod(PostConstruct.class);
        } finally {
            beginServiceDone = true;
        }
    }

    /*
     * Calls the first method in the implementor object that has @EndService
     * annotation. Servlet.destory(), or Endpoint.stop() may call this. Used
     * synchronized because multiple servlet instances may call this in their
     * destroy()
     */
    public synchronized void endService() {
        if (endServiceDone) {
            return;                 // Already called for this endpoint object
        }
        try {
            invokeOnceMethod(PreDestroy.class);
            destroy();
        } finally {
            endServiceDone = true;
        }
    }

    /*
     * Helper method to invoke methods which don't take any arguments
     * Also the annType annotation should be set only on one method
     */
    private void invokeOnceMethod(Class annType) {
        Class c = getImplementorClass();
        Method[] methods = c.getDeclaredMethods();
        boolean once = false;
        for(final Method method : methods) {
            if (method.getAnnotation(annType) != null) {
                if (once) {
                    // Err: Multiple methods have annType annotation
                    throw new ServerRtException("annotation.only.once",
                        new Object[] { annType } );
                }
                if (method.getParameterTypes().length != 0) {
                    throw new ServerRtException("not.zero.parameters",
                        method.getName());
                }
                invokeMethod(method, new Object[]{ });
                once = true;
            }
        }
    }

    /*
     * Called when the container calls endService(). Used for any
     * cleanup. Currently calls @PreDestroy method on existing
     * handlers. This should not throw an exception, but we ignore
     * it if it happens and continue with the next handler.
     */
    public void destroy() {
        Binding binding = getBinding();
        if (binding != null) {
            List<Handler> handlers = binding.getHandlerChain();
            if (handlers != null) {
                for (Handler handler : handlers) {
                    for (Method method : handler.getClass().getMethods()) {
                        if (method.getAnnotation(PreDestroy.class) == null) {
                            continue;
                        }
                        try {
                            method.invoke(handler, new Object [0]);
                        } catch (Exception e) {
                            logger.warning("exception ignored from handler " +
                                "@PreDestroy method: " +
                                e.getMessage());
                        }
                        break;
                    }
                }
            }
        }
    }

    /**
     * @return returns null if no motm-threshold-value is specified in the descriptor
     */

    public Integer getMtomThreshold() {
        return mtomThreshold;
    }

    public void setMtomThreshold(int mtomThreshold) {
        this.mtomThreshold = mtomThreshold;
    }

    // Fill DocInfo with document info : WSDL or Schema, targetNS etc.
    public static void fillDocInfo(RuntimeEndpointInfo endpointInfo)
    throws XMLStreamException {
        Map<String, DocInfo> metadata = endpointInfo.getDocMetadata();
        if (metadata != null) {
            for(Entry<String, DocInfo> entry: metadata.entrySet()) {
                RuntimeWSDLParser.fillDocInfo(entry.getValue(),
                    endpointInfo.getServiceName(),
                    endpointInfo.getPortTypeName());
            }
        }
    }

    public static void publishWSDLDocs(RuntimeEndpointInfo endpointInfo) {
        // Set queryString for the documents
        Map<String, DocInfo> docs = endpointInfo.getDocMetadata();
        if (docs == null) {
            return;
        }
        Set<Entry<String, DocInfo>> entries = docs.entrySet();
        List<String> wsdlSystemIds = new ArrayList<String>();
        List<String> schemaSystemIds = new ArrayList<String>();
        for(Entry<String, DocInfo> entry : entries) {
            DocInfo docInfo = (DocInfo)entry.getValue();
            DOC_TYPE docType = docInfo.getDocType();
            String query = docInfo.getQueryString();
            if (query == null && docType != null) {
                switch(docType) {
                    case WSDL :
                        wsdlSystemIds.add(entry.getKey());
                        break;
                    case SCHEMA :
                        schemaSystemIds.add(entry.getKey());
                        break;
                    case OTHER :
                        //(docInfo.getUrl()+" is not a WSDL or Schema file.");
                }
            }
        }

        Collections.sort(wsdlSystemIds);
        int wsdlnum = 1;
        for(String wsdlSystemId : wsdlSystemIds) {
            DocInfo docInfo = docs.get(wsdlSystemId);
            docInfo.setQueryString("wsdl="+(wsdlnum++));
        }
        Collections.sort(schemaSystemIds);
        int xsdnum = 1;
        for(String schemaSystemId : schemaSystemIds) {
            DocInfo docInfo = docs.get(schemaSystemId);
            docInfo.setQueryString("xsd="+(xsdnum++));
        }
        endpointInfo.updateQuery2DocInfo();
    }

    public void verifyImplementorClass() {
        if (wsProvider == null) {
            wsProvider = (WebServiceProvider)implementorClass.getAnnotation(
                    WebServiceProvider.class);
        }
        if (ws == null) {
            ws = (WebService)implementorClass.getAnnotation(WebService.class);
        }
        if (wsProvider == null && ws == null) {
            throw new ServerRtException("no.ws.annotation", implementorClass);

        }
        if (wsProvider != null && ws != null) {
            throw new ServerRtException("both.ws.annotations", implementorClass);
        }
    }

    public String getWsdlLocation() {
        if (wsProvider != null && wsProvider.wsdlLocation().length() > 0) {
            return wsProvider.wsdlLocation();
        } else if (ws != null && ws.wsdlLocation().length() > 0) {
            return ws.wsdlLocation();
        }
        return null;
    }

    public ProviderModel getProviderModel() {
        return providerModel;
    }


}

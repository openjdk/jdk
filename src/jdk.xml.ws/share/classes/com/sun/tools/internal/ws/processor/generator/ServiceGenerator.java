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

package com.sun.tools.internal.ws.processor.generator;

import com.sun.codemodel.internal.ClassType;
import com.sun.codemodel.internal.JAnnotationUse;
import com.sun.codemodel.internal.JBlock;
import com.sun.codemodel.internal.JCatchBlock;
import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JClassAlreadyExistsException;
import com.sun.codemodel.internal.JCommentPart;
import com.sun.codemodel.internal.JConditional;
import com.sun.codemodel.internal.JDefinedClass;
import com.sun.codemodel.internal.JDocComment;
import com.sun.codemodel.internal.JExpr;
import com.sun.codemodel.internal.JFieldVar;
import com.sun.codemodel.internal.JInvocation;
import com.sun.codemodel.internal.JMethod;
import com.sun.codemodel.internal.JMod;
import com.sun.codemodel.internal.JTryBlock;
import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.JVar;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.processor.model.ModelProperties;
import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.processor.model.Service;
import com.sun.tools.internal.ws.processor.model.java.JavaInterface;
import com.sun.tools.internal.ws.resources.GeneratorMessages;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.Options;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.tools.internal.ws.wsdl.document.PortType;
import com.sun.xml.internal.ws.spi.db.BindingHelper;

import org.xml.sax.Locator;

import javax.xml.namespace.QName;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.WebServiceException;
import java.net.MalformedURLException;
import java.net.URL;

import com.sun.xml.internal.ws.util.ServiceFinder;
import java.util.Locale;

/**
 * @author WS Development Team
 * @author Jitendra Kotamraju
 */
public class ServiceGenerator extends GeneratorBase {

    public static void generate(Model model, WsimportOptions options, ErrorReceiver receiver) {
        ServiceGenerator serviceGenerator = new ServiceGenerator(model, options, receiver);
        serviceGenerator.doGeneration();
    }

    private ServiceGenerator(Model model, WsimportOptions options, ErrorReceiver receiver) {
        init(model, options, receiver);
    }

    @Override
    public void visit(Service service) {
        JavaInterface intf = service.getJavaInterface();
        String className = Names.customJavaTypeClassName(intf);
        if (donotOverride && GeneratorUtil.classExists(options, className)) {
            log("Class " + className + " exists. Not overriding.");
            return;
        }

        JDefinedClass cls;
        try {
            cls = getClass(className, ClassType.CLASS);
        } catch (JClassAlreadyExistsException e) {
            receiver.error(service.getLocator(), GeneratorMessages.GENERATOR_SERVICE_CLASS_ALREADY_EXIST(className, service.getName()));
            return;
        }

        cls._extends(javax.xml.ws.Service.class);
        String serviceFieldName = BindingHelper.mangleNameToClassName(service.getName().getLocalPart()).toUpperCase(Locale.ENGLISH);
        String wsdlLocationName = serviceFieldName + "_WSDL_LOCATION";
        JFieldVar urlField = cls.field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL, URL.class, wsdlLocationName);

        JFieldVar exField = cls.field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL, WebServiceException.class, serviceFieldName+"_EXCEPTION");


        String serviceName = serviceFieldName + "_QNAME";
        cls.field(JMod.PRIVATE | JMod.STATIC | JMod.FINAL, QName.class, serviceName,
            JExpr._new(cm.ref(QName.class)).arg(service.getName().getNamespaceURI()).arg(service.getName().getLocalPart()));

        JClass qNameCls = cm.ref(QName.class);
        JInvocation inv;
        inv = JExpr._new(qNameCls);
        inv.arg("namespace");
        inv.arg("localpart");

        if (options.useBaseResourceAndURLToLoadWSDL) {
            writeClassLoaderBaseResourceWSDLLocation(className, cls, urlField, exField);
        } else if (wsdlLocation.startsWith("http://") || wsdlLocation.startsWith("https://") || wsdlLocation.startsWith("file:/")) {
            writeAbsWSDLLocation(cls, urlField, exField);
        } else if (wsdlLocation.startsWith("META-INF/")) {
            writeClassLoaderResourceWSDLLocation(className, cls, urlField, exField);
        } else {
            writeResourceWSDLLocation(className, cls, urlField, exField);
        }

        //write class comment - JAXWS warning
        JDocComment comment = cls.javadoc();

        if (service.getJavaDoc() != null) {
            comment.add(service.getJavaDoc());
            comment.add("\n\n");
        }

        for (String doc : getJAXWSClassComment()) {
            comment.add(doc);
        }

        // Generating constructor
        // for e.g:  public ExampleService()
        JMethod constructor1 = cls.constructor(JMod.PUBLIC);
        String constructor1Str = String.format("super(__getWsdlLocation(), %s);", serviceName);
        constructor1.body().directStatement(constructor1Str);

        // Generating constructor
        // for e.g:  public ExampleService(WebServiceFeature ... features)
        if (options.target.isLaterThan(Options.Target.V2_2)) {
            JMethod constructor2 = cls.constructor(JMod.PUBLIC);
            constructor2.varParam(WebServiceFeature.class, "features");
            String constructor2Str = String.format("super(__getWsdlLocation(), %s, features);", serviceName);
            constructor2.body().directStatement(constructor2Str);
        }

        // Generating constructor
        // for e.g:  public ExampleService(URL wsdlLocation)
        if (options.target.isLaterThan(Options.Target.V2_2)) {
            JMethod constructor3 = cls.constructor(JMod.PUBLIC);
            constructor3.param(URL.class, "wsdlLocation");
            String constructor3Str = String.format("super(wsdlLocation, %s);", serviceName);
            constructor3.body().directStatement(constructor3Str);
        }

        // Generating constructor
        // for e.g:  public ExampleService(URL wsdlLocation, WebServiceFeature ... features)
        if (options.target.isLaterThan(Options.Target.V2_2)) {
            JMethod constructor4 = cls.constructor(JMod.PUBLIC);
            constructor4.param(URL.class, "wsdlLocation");
            constructor4.varParam(WebServiceFeature.class, "features");
            String constructor4Str = String.format("super(wsdlLocation, %s, features);", serviceName);
            constructor4.body().directStatement(constructor4Str);
        }

        // Generating constructor
        // for e.g:  public ExampleService(URL wsdlLocation, QName serviceName)
        JMethod constructor5 = cls.constructor(JMod.PUBLIC);
        constructor5.param(URL.class, "wsdlLocation");
        constructor5.param(QName.class, "serviceName");
        constructor5.body().directStatement("super(wsdlLocation, serviceName);");

        // Generating constructor
        // for e.g:  public ExampleService(URL, QName, WebServiceFeature ...)
        if (options.target.isLaterThan(Options.Target.V2_2)) {
            JMethod constructor6 = cls.constructor(JMod.PUBLIC);
            constructor6.param(URL.class, "wsdlLocation");
            constructor6.param(QName.class, "serviceName");
            constructor6.varParam(WebServiceFeature.class, "features");
            constructor6.body().directStatement("super(wsdlLocation, serviceName, features);");
        }

        //@WebService
        JAnnotationUse webServiceClientAnn = cls.annotate(cm.ref(WebServiceClient.class));
        writeWebServiceClientAnnotation(service, webServiceClientAnn);

        // additional annotations
        for (GeneratorExtension f:ServiceFinder.find(GeneratorExtension.class)) {
            f.writeWebServiceClientAnnotation(options, cm, cls);
        }


        //@HandlerChain
        writeHandlerConfig(Names.customJavaTypeClassName(service.getJavaInterface()), cls, options);

        for (Port port : service.getPorts()) {
            if (port.isProvider()) {
                continue;  // No getXYZPort() for porvider based endpoint
            }

            //Get the SEI class
            JType retType;
            try {
                retType = getClass(port.getJavaInterface().getName(), ClassType.INTERFACE);
            } catch (JClassAlreadyExistsException e) {
                QName portTypeName =
                        (QName) port.getProperty(
                                ModelProperties.PROPERTY_WSDL_PORT_TYPE_NAME);
                Locator loc = null;
                if (portTypeName != null) {
                    PortType pt = port.portTypes.get(portTypeName);
                    if (pt != null) {
                        loc = pt.getLocator();
                    }
                }
                receiver.error(loc, GeneratorMessages.GENERATOR_SEI_CLASS_ALREADY_EXIST(port.getJavaInterface().getName(), portTypeName));
                return;
            }

            //write getXyzPort()
            writeDefaultGetPort(port, retType, cls);

            //write getXyzPort(WebServicesFeature...)
            if (options.target.isLaterThan(Options.Target.V2_1)) {
                writeGetPort(port, retType, cls);
            }
        }

        writeGetWsdlLocation(cm.ref(URL.class), cls, urlField, exField);
    }

    private void writeGetPort(Port port, JType retType, JDefinedClass cls) {
        JMethod m = cls.method(JMod.PUBLIC, retType, port.getPortGetter());
        JDocComment methodDoc = m.javadoc();
        if (port.getJavaDoc() != null) {
            methodDoc.add(port.getJavaDoc());
        }
        JCommentPart ret = methodDoc.addReturn();
        JCommentPart paramDoc = methodDoc.addParam("features");
        paramDoc.append("A list of ");
        paramDoc.append("{@link " + WebServiceFeature.class.getName() + "}");
        paramDoc.append("to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.");
        ret.add("returns " + retType.name());
        m.varParam(WebServiceFeature.class, "features");
        JBlock body = m.body();
        StringBuilder statement = new StringBuilder("return ");
        statement.append("super.getPort(new QName(\"").append(port.getName().getNamespaceURI()).append("\", \"").append(port.getName().getLocalPart()).append("\"), ");
        statement.append(retType.name());
        statement.append(".class, features);");
        body.directStatement(statement.toString());
        writeWebEndpoint(port, m);
    }


    /*
       Generates the code to create URL for absolute WSDL location

       for e.g.:
       static {
           URL url = null;
           WebServiceException e = null;
           try {
                url = new URL("http://ExampleService.wsdl");
           } catch (MalformedURLException ex) {
                e = new WebServiceException(ex);
           }
           EXAMPLESERVICE_WSDL_LOCATION = url;
           EXAMPLESERVICE_EXCEPTION = e;
       }
    */
    private void writeAbsWSDLLocation(JDefinedClass cls, JFieldVar urlField, JFieldVar exField) {
        JBlock staticBlock = cls.init();
        JVar urlVar = staticBlock.decl(cm.ref(URL.class), "url", JExpr._null());
        JVar exVar = staticBlock.decl(cm.ref(WebServiceException.class), "e", JExpr._null());

        JTryBlock tryBlock = staticBlock._try();
        tryBlock.body().assign(urlVar, JExpr._new(cm.ref(URL.class)).arg(wsdlLocation));
        JCatchBlock catchBlock = tryBlock._catch(cm.ref(MalformedURLException.class));
        catchBlock.param("ex");
        catchBlock.body().assign(exVar, JExpr._new(cm.ref(WebServiceException.class)).arg(JExpr.ref("ex")));

        staticBlock.assign(urlField, urlVar);
        staticBlock.assign(exField, exVar);
    }

    /*
       Generates the code to create URL for WSDL location as resource

       for e.g.:
       static {
           EXAMPLESERVICE_WSDL_LOCATION = ExampleService.class.getResource(...);
           Exception e = null;
           if (EXAMPLESERVICE_WSDL_LOCATION == null) {
               e = new WebServiceException("...");
           }
           EXAMPLESERVICE_EXCEPTION = e;
       }
     */
    private void writeResourceWSDLLocation(String className, JDefinedClass cls, JFieldVar urlField, JFieldVar exField) {
        JBlock staticBlock = cls.init();
        staticBlock.assign(urlField, JExpr.dotclass(cm.ref(className)).invoke("getResource").arg(wsdlLocation));
        JVar exVar = staticBlock.decl(cm.ref(WebServiceException.class), "e", JExpr._null());
        JConditional ifBlock = staticBlock._if(urlField.eq(JExpr._null()));
        ifBlock._then().assign(exVar, JExpr._new(cm.ref(WebServiceException.class)).arg(
                "Cannot find "+JExpr.quotify('\'', wsdlLocation)+" wsdl. Place the resource correctly in the classpath."));
        staticBlock.assign(exField, exVar);
    }

    /*
       Generates the code to create URL for WSDL location as classloader resource

       for e.g.:
       static {
           EXAMPLESERVICE_WSDL_LOCATION = ExampleService.class.getClassLoader().getResource(...);
           Exception e = null;
           if (EXAMPLESERVICE_WSDL_LOCATION == null) {
               e = new WebServiceException("...");
           }
           EXAMPLESERVICE_EXCEPTION = e;
       }
     */
    private void writeClassLoaderResourceWSDLLocation(String className, JDefinedClass cls, JFieldVar urlField, JFieldVar exField) {
        JBlock staticBlock = cls.init();
        staticBlock.assign(urlField, JExpr.dotclass(cm.ref(className)).invoke("getClassLoader").invoke("getResource").arg(wsdlLocation));
        JVar exVar = staticBlock.decl(cm.ref(WebServiceException.class), "e", JExpr._null());
        JConditional ifBlock = staticBlock._if(urlField.eq(JExpr._null()));
        ifBlock._then().assign(exVar, JExpr._new(cm.ref(WebServiceException.class)).arg(
                "Cannot find "+JExpr.quotify('\'', wsdlLocation)+" wsdl. Place the resource correctly in the classpath."));
        staticBlock.assign(exField, exVar);
    }

    /*
       Generates the code to create URL for WSDL location from classloader base resource

       for e.g.:
       static {
           Exception e = null;
           URL url = null;
           try {
               url = new URL(ExampleService.class.getClassLoader().getResource("."), ...);
           } catch (MalformedURLException murl) {
               e = new WebServiceException(murl);
           }
           EXAMPLESERVICE_WSDL_LOCATION = url;
           EXAMPLESERVICE_EXCEPTION = e;
       }
     */
     private void writeClassLoaderBaseResourceWSDLLocation(String className, JDefinedClass cls, JFieldVar urlField, JFieldVar exField) {
         JBlock staticBlock = cls.init();
         JVar exVar = staticBlock.decl(cm.ref(WebServiceException.class), "e", JExpr._null());
         JVar urlVar = staticBlock.decl(cm.ref(URL.class), "url", JExpr._null());
         JTryBlock tryBlock = staticBlock._try();
         tryBlock.body().assign(urlVar, JExpr._new(cm.ref(URL.class)).arg(JExpr.dotclass(cm.ref(className)).invoke("getResource").arg(".")).arg(wsdlLocation));
         JCatchBlock catchBlock = tryBlock._catch(cm.ref(MalformedURLException.class));
         JVar murlVar = catchBlock.param("murl");
         catchBlock.body().assign(exVar, JExpr._new(cm.ref(WebServiceException.class)).arg(murlVar));
         staticBlock.assign(urlField, urlVar);
         staticBlock.assign(exField, exVar);
     }

    /*
       Generates code that gives wsdl URL. If there is an exception in
       creating the URL, it throws an exception.

       for example:

       private URL __getWsdlLocation() {
           if (EXAMPLESERVICE_EXCEPTION != null) {
               throw EXAMPLESERVICE_EXCEPTION;
           }
           return EXAMPLESERVICE_WSDL_LOCATION;
       }
     */
    private void writeGetWsdlLocation(JType retType, JDefinedClass cls, JFieldVar urlField, JFieldVar exField) {
        JMethod m = cls.method(JMod.PRIVATE|JMod.STATIC , retType, "__getWsdlLocation");
        JConditional ifBlock = m.body()._if(exField.ne(JExpr._null()));
        ifBlock._then()._throw(exField);
        m.body()._return(urlField);
    }

    private void writeDefaultGetPort(Port port, JType retType, JDefinedClass cls) {
        String portGetter = port.getPortGetter();
        JMethod m = cls.method(JMod.PUBLIC, retType, portGetter);
        JDocComment methodDoc = m.javadoc();
        if (port.getJavaDoc() != null) {
            methodDoc.add(port.getJavaDoc());
        }
        JCommentPart ret = methodDoc.addReturn();
        ret.add("returns " + retType.name());
        JBlock body = m.body();
        StringBuilder statement = new StringBuilder("return ");
        statement.append("super.getPort(new QName(\"").append(port.getName().getNamespaceURI()).append("\", \"").append(port.getName().getLocalPart()).append("\"), ");
        statement.append(retType.name());
        statement.append(".class);");
        body.directStatement(statement.toString());
        writeWebEndpoint(port, m);
    }

    private void writeWebServiceClientAnnotation(Service service, JAnnotationUse wsa) {
        String serviceName = service.getName().getLocalPart();
        String serviceNS = service.getName().getNamespaceURI();
        wsa.param("name", serviceName);
        wsa.param("targetNamespace", serviceNS);
        wsa.param("wsdlLocation", wsdlLocation);
    }

    private void writeWebEndpoint(Port port, JMethod m) {
        JAnnotationUse webEndpointAnn = m.annotate(cm.ref(WebEndpoint.class));
        webEndpointAnn.param("name", port.getName().getLocalPart());
    }
}

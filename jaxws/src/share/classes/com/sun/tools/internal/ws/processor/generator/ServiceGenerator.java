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

package com.sun.tools.internal.ws.processor.generator;

import com.sun.codemodel.internal.*;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.processor.model.Service;
import com.sun.tools.internal.ws.processor.model.java.JavaInterface;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.Options;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.tools.internal.ws.resources.GeneratorMessages;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.ws.util.JAXWSUtils;

import javax.xml.namespace.QName;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;


/**
 *
 * @author WS Development Team
 */
public class ServiceGenerator extends GeneratorBase{

    public static void generate(Model model, WsimportOptions options, ErrorReceiver receiver){
        ServiceGenerator serviceGenerator = new ServiceGenerator(model, options, receiver);
        serviceGenerator.doGeneration();
    }
    private ServiceGenerator(Model model, WsimportOptions options, ErrorReceiver receiver) {
        super(model, options, receiver);
    }

    private JInvocation createURL(URL url) {
        return JExpr._new(cm.ref(URL.class)).arg(url.toExternalForm());
    }

    @Override
    public void visit(Service service) {
        try {
            JavaInterface intf = service.getJavaInterface();
            String className = Names.customJavaTypeClassName(intf);
            if (donotOverride && GeneratorUtil.classExists(options, className)) {
                log("Class " + className + " exists. Not overriding.");
                return;
            }

            JDefinedClass cls = getClass(className, ClassType.CLASS);

            cls._extends(javax.xml.ws.Service.class);
            String serviceFieldName = JAXBRIContext.mangleNameToClassName(service.getName().getLocalPart()).toUpperCase();
            String wsdlLocationName = serviceFieldName+"_WSDL_LOCATION";
            JFieldVar urlField = cls.field(JMod.PRIVATE|JMod.STATIC|JMod.FINAL, URL.class, wsdlLocationName);
            JClass qNameCls = cm.ref(QName.class);
            JInvocation inv;
            inv = JExpr._new(qNameCls);
            inv.arg("namespace");
            inv.arg("localpart");


            JBlock staticBlock = cls.init();
            URL url = new URL(JAXWSUtils.absolutize(JAXWSUtils.getFileOrURLName(wsdlLocation)));
            JVar urlVar = staticBlock.decl(cm.ref(URL.class),"url", JExpr._null());
            JTryBlock tryBlock = staticBlock._try();
            tryBlock.body().assign(urlVar, createURL(url));
            JCatchBlock catchBlock = tryBlock._catch(cm.ref(MalformedURLException.class));
            catchBlock.param("e");
            catchBlock.body().directStatement("e.printStackTrace();");
            staticBlock.assign(urlField, urlVar);

            //write class comment - JAXWS warning
            JDocComment comment = cls.javadoc();

            if(service.getJavaDoc() != null){
                comment.add(service.getJavaDoc());
                comment.add("\n\n");
            }

            for (String doc : getJAXWSClassComment()) {
                comment.add(doc);
            }

            JMethod constructor = cls.constructor(JMod.PUBLIC);
            constructor.param(URL.class, "wsdlLocation");
            constructor.param(QName.class, "serviceName");
            constructor.body().directStatement("super(wsdlLocation, serviceName);");

            constructor = cls.constructor(JMod.PUBLIC);
            constructor.body().directStatement("super("+wsdlLocationName+", new QName(\""+service.getName().getNamespaceURI()+"\", \""+service.getName().getLocalPart()+"\"));");

            //@WebService
            JAnnotationUse webServiceClientAnn = cls.annotate(cm.ref(WebServiceClient.class));
            writeWebServiceClientAnnotation(service, webServiceClientAnn);

            //@HandlerChain
            writeHandlerConfig(Names.customJavaTypeClassName(service.getJavaInterface()), cls, options);

            for (Port port: service.getPorts()) {
                if (port.isProvider()) {
                    continue;  // No getXYZPort() for porvider based endpoint
                }

                //write getXyzPort()
                writeDefaultGetPort(port, cls);

                //write getXyzPort(WebServicesFeature...)
                if(options.target.isLaterThan(Options.Target.V2_1))
                    writeGetPort(port, cls);
            }
        } catch (IOException e) {
            receiver.error(e);
        }
    }

    private void writeGetPort(Port port, JDefinedClass cls) {
        JType retType = getClass(port.getJavaInterface().getName(), ClassType.INTERFACE);
        JMethod m = cls.method(JMod.PUBLIC, retType, port.getPortGetter());
        JDocComment methodDoc = m.javadoc();
        if(port.getJavaDoc() != null)
            methodDoc.add(port.getJavaDoc());
        JCommentPart ret = methodDoc.addReturn();
        JCommentPart paramDoc = methodDoc.addParam("features");
        paramDoc.append("A list of ");
        paramDoc.append("{@link "+WebServiceFeature.class.getName()+"}");
        paramDoc.append("to configure on the proxy.  Supported features not in the <code>features</code> parameter will have their default values.");
        ret.add("returns "+retType.name());
        m.varParam(WebServiceFeature.class, "features");
        JBlock body = m.body();
        StringBuffer statement = new StringBuffer("return (");
        statement.append(retType.name());
        statement.append(")super.getPort(new QName(\"").append(port.getName().getNamespaceURI()).append("\", \"").append(port.getName().getLocalPart()).append("\"), ");
        statement.append(retType.name());
        statement.append(".class, features);");
        body.directStatement(statement.toString());
        writeWebEndpoint(port, m);
    }

    private void writeDefaultGetPort(Port port, JDefinedClass cls) {
        JType retType = getClass(port.getJavaInterface().getName(), ClassType.INTERFACE);
        String portGetter = port.getPortGetter();
        JMethod m = cls.method(JMod.PUBLIC, retType, portGetter);
        JDocComment methodDoc = m.javadoc();
        if(port.getJavaDoc() != null)
            methodDoc.add(port.getJavaDoc());
        JCommentPart ret = methodDoc.addReturn();
        ret.add("returns "+retType.name());
        JBlock body = m.body();
        StringBuffer statement = new StringBuffer("return (");
        statement.append(retType.name());
        statement.append(")super.getPort(new QName(\"").append(port.getName().getNamespaceURI()).append("\", \"").append(port.getName().getLocalPart()).append("\"), ");
        statement.append(retType.name());
        statement.append(".class);");
        body.directStatement(statement.toString());
        writeWebEndpoint(port, m);
    }


    protected JDefinedClass getClass(String className, ClassType type) {
        JDefinedClass cls;
        try {
            cls = cm._class(className, type);
        } catch (JClassAlreadyExistsException e){
            cls = cm._getClass(className);
        }
        return cls;
    }

    private void writeWebServiceClientAnnotation(Service service, JAnnotationUse wsa) {
        String serviceName = service.getName().getLocalPart();
        String serviceNS= service.getName().getNamespaceURI();
        wsa.param("name", serviceName);
        wsa.param("targetNamespace", serviceNS);
        wsa.param("wsdlLocation", wsdlLocation);
    }

    private void writeWebEndpoint(Port port, JMethod m) {
        JAnnotationUse webEndpointAnn = m.annotate(cm.ref(WebEndpoint.class));
        webEndpointAnn.param("name", port.getName().getLocalPart());
    }
}

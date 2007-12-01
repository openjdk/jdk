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

package com.sun.tools.internal.ws.processor.generator;

import com.sun.codemodel.internal.JClassAlreadyExistsException;
import com.sun.codemodel.internal.ClassType;
import com.sun.codemodel.internal.CodeWriter;
import com.sun.codemodel.internal.JAnnotationUse;
import com.sun.codemodel.internal.JClass;
import com.sun.codemodel.internal.JCommentPart;
import com.sun.codemodel.internal.JDefinedClass;
import com.sun.codemodel.internal.JDocComment;
import com.sun.codemodel.internal.JExpression;
import com.sun.codemodel.internal.JFieldVar;
import com.sun.codemodel.internal.JMethod;
import com.sun.codemodel.internal.JMod;
import com.sun.codemodel.internal.JType;
import com.sun.codemodel.internal.*;
import com.sun.codemodel.internal.writer.ProgressCodeWriter;
import java.util.Properties;

import com.sun.tools.internal.xjc.api.XJC;
import com.sun.tools.internal.ws.processor.ProcessorAction;
import com.sun.tools.internal.ws.processor.config.Configuration;
import com.sun.tools.internal.ws.processor.config.WSDLModelInfo;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.processor.model.Service;
import com.sun.tools.internal.ws.processor.model.java.JavaInterface;
import com.sun.tools.internal.ws.wscompile.WSCodeWriter;
import com.sun.xml.internal.ws.encoding.soap.SOAPVersion;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.xml.internal.ws.util.StringUtils;
import com.sun.xml.internal.bind.api.JAXBRIContext;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.MalformedURLException;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.namespace.QName;


/**
 *
 * @author WS Development Team
 */
public class ServiceGenerator extends GeneratorBase implements ProcessorAction {
    private String serviceNS;
    private WSDLModelInfo wsdlModelInfo;

    public ServiceGenerator() {
        super();
    }

    private ServiceGenerator(
        Model model,
        Configuration config,
        Properties properties) {
        super(model, config, properties);
        this.wsdlModelInfo = (WSDLModelInfo)config.getModelInfo();
    }

    public GeneratorBase getGenerator(
        Model model,
        Configuration config,
        Properties properties) {
        return new ServiceGenerator(model, config, properties);
    }

    public GeneratorBase getGenerator(
        Model model,
        Configuration config,
        Properties properties,
        SOAPVersion ver) {
        return new ServiceGenerator(model, config, properties);
    }

    /**
     * Generates an expression that evaluates to "new QName(...)"
     */
    private JInvocation createQName(QName name) {
        return JExpr._new(cm.ref(QName.class)).arg(name.getNamespaceURI()).arg(name.getLocalPart());
    }

    private JInvocation createURL(URL url) {
        return JExpr._new(cm.ref(URL.class)).arg(url.toExternalForm());
    }

    protected void visitService(Service service) {
        try {
            JavaInterface intf = (JavaInterface) service.getJavaInterface();
            String className = env.getNames().customJavaTypeClassName(intf);
            if (donotOverride && GeneratorUtil.classExists(env, className)) {
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
            writeHandlerConfig(env.getNames().customJavaTypeClassName(service.getJavaInterface()), cls, wsdlModelInfo);

            for (Port port: service.getPorts()) {
                if (port.isProvider()) {
                    continue;  // No getXYZPort() for porvider based endpoint
                }
                //@WebEndpoint
                JMethod m = null;
                JDocComment methodDoc = null;
                JType retType = getClass(port.getJavaInterface().getName(), ClassType.INTERFACE);
                m = cls.method(JMod.PUBLIC, retType, port.getPortGetter());
                methodDoc = m.javadoc();
                if(port.getJavaDoc() != null)
                    methodDoc.add(port.getJavaDoc());
                JCommentPart ret = methodDoc.addReturn();
                ret.add("returns "+retType.name());
                JBlock body = m.body();
                StringBuffer statement = new StringBuffer("return (");
                statement.append(retType.name());
                statement.append(")super.getPort(new QName(\""+port.getName().getNamespaceURI()+"\", \""+ port.getName().getLocalPart()+"\"), ");
                statement.append(retType.name());
                statement.append(".class);");
                body.directStatement(statement.toString());
                writeWebEndpoint(port, m);
            }
            CodeWriter cw = new WSCodeWriter(sourceDir,env);

            if(env.verbose())
                cw = new ProgressCodeWriter(cw, System.out);
            cm.build(cw);

        } catch (IOException e) {
            throw new GeneratorException(
                "generator.nestedGeneratorError",
                e);
        }
    }

    protected JDefinedClass getClass(String className, ClassType type) {
        JDefinedClass cls = null;
        try {
            cls = cm._class(className, type);
        } catch (JClassAlreadyExistsException e){
            cls = cm._getClass(className);
        }
        return cls;
    }

    private void writeWebServiceClientAnnotation(Service service, JAnnotationUse wsa) {
        String serviceName = service.getName().getLocalPart();
        serviceNS = service.getName().getNamespaceURI();
        wsa.param("name", serviceName);
        wsa.param("targetNamespace", serviceNS);
        wsa.param("wsdlLocation", wsdlLocation);
    }

    private void writeWebEndpoint(Port port, JMethod m) {
        JAnnotationUse webEndpointAnn = m.annotate(cm.ref(WebEndpoint.class));
        webEndpointAnn.param("name", port.getName().getLocalPart());
    }
}

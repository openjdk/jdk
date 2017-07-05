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

import com.sun.codemodel.internal.*;
import com.sun.tools.internal.ws.processor.model.*;
import com.sun.tools.internal.ws.processor.model.java.JavaInterface;
import com.sun.tools.internal.ws.processor.model.java.JavaMethod;
import com.sun.tools.internal.ws.processor.model.java.JavaParameter;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBTypeAndAnnotation;
import com.sun.tools.internal.ws.wsdl.document.Definitions;
import com.sun.tools.internal.ws.wsdl.document.Binding;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAP12Binding;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPBinding;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPConstants;
import com.sun.tools.internal.ws.api.wsdl.TWSDLExtension;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.processor.model.ModelProperties;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.codemodel.internal.JClassAlreadyExistsException;
import com.sun.xml.internal.ws.api.SOAPVersion;

import com.sun.xml.internal.ws.util.ServiceFinder;

import javax.jws.WebService;
import javax.xml.ws.BindingType;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;
import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;

/**
 * Generator for placeholder JWS implementations
 *
 * @since 2.2.6
 */
public final class JwsImplGenerator extends GeneratorBase {
        private static final Map<String, String> TRANSLATION_MAP = new HashMap<String, String>(
      1);
        static
  {
    TRANSLATION_MAP.put(SOAPConstants.URI_SOAP_TRANSPORT_HTTP,
                javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING);
  }
        // save the generated impl files' info
        private final List<String> implFiles = new ArrayList<String>();

        public static List<String> generate(Model model, WsimportOptions options,
            ErrorReceiver receiver) {
                // options check

                // Generate it according the implDestDir option
                if (options.implDestDir == null)
                        return null;

                JwsImplGenerator jwsImplGenerator = new JwsImplGenerator();
                jwsImplGenerator.init(model, options, receiver);
                jwsImplGenerator.doGeneration();
                // print a warning message while implFiles.size() is zero
                if (jwsImplGenerator.implFiles.isEmpty()) {
                        StringBuilder msg = new StringBuilder();
                        if (options.implServiceName != null)
                                msg.append("serviceName=[").append(options.implServiceName).append("] ");
                        if (options.implPortName != null)
                                msg.append("portName=[").append(options.implPortName).append("] ");

                        if (msg.length() > 0)
                                msg.append(", Not found in wsdl file.\n");

                        msg.append("No impl files generated!");
                        receiver.warning(null, msg.toString());
                }

                return jwsImplGenerator.implFiles;
        }

        /**
         * Move impl files to implDestDir
         */
        public static boolean moveToImplDestDir(List<String> gImplFiles,
            WsimportOptions options, ErrorReceiver receiver) {
                if (options.implDestDir == null || gImplFiles == null
                    || gImplFiles.isEmpty())
                        return true;

                List<ImplFile> generatedImplFiles = ImplFile.toImplFiles(gImplFiles);

                try {
                        File implDestDir = makePackageDir(options);

                        File movedF;
                        File f;
                        for (ImplFile implF : generatedImplFiles) {
                                movedF = findFile(options, implF.qualifiedName);
                                if (movedF == null) {
                                        // should never happen
                                        receiver.warning(null, "Class " + implF.qualifiedName
                                            + " is not generated. Not moving.");
                                        return false;
                                }

                                f = new File(implDestDir, implF.name);
                            if (!movedF.equals(f)) {    //bug 10102169

                    if (f.exists())
                    {
                        if (!f.delete()){
                            receiver.error("Class " + implF.qualifiedName
                                    + " has existed in destImplDir, and it "
                                    + "can not be written!", null);
                        }
                    }
                    if(!movedF.renameTo(f))
                    {
                        throw new Exception();
                    }
                }
                        }
                } catch (Exception e) {
                        receiver.error("Moving WebService Impl files failed!", e);
                        return false;
                }
                return true;
        }

        private JwsImplGenerator() {
                donotOverride = true;
        }

        @Override
        public void visit(Service service) {
                QName serviceName = service.getName();
                // process the ordered service only if it is defined
                if (options.implServiceName != null
                    && !equalsNSOptional(options.implServiceName, serviceName))
                        return;

                for (Port port : service.getPorts()) {
                        if (port.isProvider()) {
                                continue; // Not generating for Provider based endpoint
                        }

                        // Generate the impl class name according to
                        // Xpath(/definitions/service/port[@name]);
                        QName portName = port.getName();

                        // process the ordered port only if it is defined
                        if (options.implPortName != null
                            && !equalsNSOptional(options.implPortName, portName))
                                continue;

                        String simpleClassName = serviceName.getLocalPart() + "_"
                            + portName.getLocalPart() + "Impl";
                        String className = makePackageQualified(simpleClassName);
                        implFiles.add(className);

                        if (donotOverride && GeneratorUtil.classExists(options, className)) {
                                log("Class " + className + " exists. Not overriding.");
                                return;
                        }

                        JDefinedClass cls = null;
                        try {
                                cls = getClass(className, ClassType.CLASS);
                        } catch (JClassAlreadyExistsException e) {
                                log("Class " + className
                                    + " generates failed. JClassAlreadyExistsException[" + className
                                    + "].");
                                return;
                        }

                        // Each serviceImpl will implements one port interface
                        JavaInterface portIntf = port.getJavaInterface();
                        String portClassName = Names.customJavaTypeClassName(portIntf);
                        JDefinedClass portCls = null;
                        try {
                                portCls = getClass(portClassName, ClassType.INTERFACE);
                        } catch (JClassAlreadyExistsException e) {
                                log("Class " + className
                                    + " generates failed. JClassAlreadyExistsException["
                                    + portClassName + "].");
                                return;
                        }
                        cls._implements(portCls);

                        // create a default constructor
                        cls.constructor(JMod.PUBLIC);

                        // write class comment - JAXWS warning
                        JDocComment comment = cls.javadoc();

                        if (service.getJavaDoc() != null) {
                                comment.add(service.getJavaDoc());
                                comment.add("\n\n");
                        }

                        for (String doc : getJAXWSClassComment()) {
                                comment.add(doc);
                        }

                        // @WebService
                        JAnnotationUse webServiceAnn = cls.annotate(cm.ref(WebService.class));
                        writeWebServiceAnnotation(service, port, webServiceAnn);

                        // @BindingType
                        JAnnotationUse bindingTypeAnn = cls.annotate(cm.ref(BindingType.class));
                        writeBindingTypeAnnotation(port, bindingTypeAnn);

                        // extra annotation
                        for( GeneratorExtension f : ServiceFinder.find(GeneratorExtension.class) ) {
                            f.writeWebServiceAnnotation(model, cm, cls, port);
                        }

                        // WebMethods
                        for (Operation operation : port.getOperations()) {
                                JavaMethod method = operation.getJavaMethod();

                                // @WebMethod
                                JMethod m;
                                JDocComment methodDoc;
                                String methodJavaDoc = operation.getJavaDoc();
                                if (method.getReturnType().getName().equals("void")) {
                                        m = cls.method(JMod.PUBLIC, void.class, method.getName());
                                        methodDoc = m.javadoc();
                                } else {
                                        JAXBTypeAndAnnotation retType = method.getReturnType().getType();
                                        m = cls.method(JMod.PUBLIC, retType.getType(), method.getName());
                                        retType.annotate(m);
                                        methodDoc = m.javadoc();
                                        JCommentPart ret = methodDoc.addReturn();
                                        ret.add("returns " + retType.getName());
                                }

                                if (methodJavaDoc != null)
                                        methodDoc.add(methodJavaDoc);

                                JClass holder = cm.ref(Holder.class);
                                for (JavaParameter parameter : method.getParametersList()) {
                                        JVar var;
                                        JAXBTypeAndAnnotation paramType = parameter.getType().getType();
                                        if (parameter.isHolder()) {
                                                var = m.param(holder.narrow(paramType.getType().boxify()),
                                                    parameter.getName());
                                        } else {
                                                var = m.param(paramType.getType(), parameter.getName());
                                        }
                                        methodDoc.addParam(var);
                                }

                                com.sun.tools.internal.ws.wsdl.document.Operation wsdlOp = operation
                                    .getWSDLPortTypeOperation();
                                for (Fault fault : operation.getFaultsSet()) {
                                        m._throws(fault.getExceptionClass());
                                        methodDoc.addThrows(fault.getExceptionClass());
                                        wsdlOp.putFault(fault.getWsdlFaultName(), fault.getExceptionClass());
                                }
                                m.body().block().directStatement("//replace with your impl here");
                                m.body().block().directStatement(
                                    getReturnString(method.getReturnType().getName()));
                        }
                }
        }

        /**
         * Generate return statement according to return type.
         *
         * @param type
         *          The method's return type
         * @return The whole return statement
         */
        private String getReturnString(String type) {
                final String nullReturnStr = "return null;";
                // complex type or array
                if (type.indexOf('.') > -1 || type.indexOf('[') > -1) {
                        return nullReturnStr;
                }

                // primitive type
                if (type.equals("void")) {
                        return "return;";
                }
                if (type.equals("boolean")) {
                        return "return false;";
                }
                if (type.equals("int") || type.equals("byte") || type.equals("short")
                    || type.equals("long") || type.equals("double") || type.equals("float")) {
                        return "return 0;";
                }
                if (type.equals("char")) {
                        return "return '0';";
                }

                return nullReturnStr;
        }

        /**
         *
         * @param service
         * @param port
         * @param webServiceAnn
         * @param options
         */
        private void writeWebServiceAnnotation(Service service, Port port,
            JAnnotationUse webServiceAnn) {
                webServiceAnn.param("portName", port.getName().getLocalPart());
                webServiceAnn.param("serviceName", service.getName().getLocalPart());
                webServiceAnn.param("targetNamespace", service.getName().getNamespaceURI());
                webServiceAnn.param("wsdlLocation", wsdlLocation);
                webServiceAnn.param("endpointInterface", port.getJavaInterface().getName());
        }

        //CR373098 To transform the java class name as validate.
        private String transToValidJavaIdentifier(String s) {
            if (s == null) {
                return null;
            }
            final int len = s.length();
            StringBuilder retSB = new StringBuilder();
            if (len == 0 || !Character.isJavaIdentifierStart(s.charAt(0))) {
                retSB.append("J"); //update to a default start char
            } else {
                retSB.append(s.charAt(0));
            }

            for (int i = 1; i < len; i++) {
                if (!Character.isJavaIdentifierPart(s.charAt(i)))
                  ; //delete it if it is illegal //TODO: It might conflict "a-b" vs. "ab"
                else {
                    retSB.append(s.charAt(i));
                }
            }
            return retSB.toString();
        }

        private String makePackageQualified(String s) {
                s = transToValidJavaIdentifier(s);
                if (options.defaultPackage != null && !options.defaultPackage.equals("")) {
                        return options.defaultPackage + "." + s;
                } else {
                        return s;
                }
        }


        /**
         * TODO
         *
         * @param port
         * @param bindingTypeAnn
         */
        private void writeBindingTypeAnnotation(Port port,
            JAnnotationUse bindingTypeAnn) {
                QName bName = (QName) port
                    .getProperty(ModelProperties.PROPERTY_WSDL_BINDING_NAME);
                if (bName == null)
                        return;

                String v = getBindingType(bName);

                // TODO: How to decide if it is a mtom?
                if (v != null) {
                        // transport = translate(transport);
                        bindingTypeAnn.param("value", v);
                }

        }

        private String resolveBindingValue(TWSDLExtension wsdlext) {
                if (wsdlext.getClass().equals(SOAPBinding.class)) {
                        SOAPBinding sb = (SOAPBinding) wsdlext;
                        if(javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_MTOM_BINDING.equals(sb.getTransport()))
                                return javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_MTOM_BINDING;
                        else {
                            for(GeneratorExtension f : ServiceFinder.find(GeneratorExtension.class) ) {
                                String bindingValue = f.getBindingValue(sb.getTransport(), SOAPVersion.SOAP_11);
                                if(bindingValue!=null) {
                                    return bindingValue;
                                }
                            }
                                return javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING;
                        }
                }
                if (wsdlext.getClass().equals(SOAP12Binding.class)) {
                        SOAP12Binding sb = (SOAP12Binding) wsdlext;
                        if(javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_MTOM_BINDING.equals(sb.getTransport()))
                                return javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_MTOM_BINDING;
                    else {
                        for(GeneratorExtension f : ServiceFinder.find(GeneratorExtension.class) ) {
                            String bindingValue = f.getBindingValue(sb.getTransport(), SOAPVersion.SOAP_12);
                            if(bindingValue!=null) {
                                return bindingValue;
                            }
                        }
                            return javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING;
                    }
                }
                return null;
        }

        private String getBindingType(QName bName) {

                String value = null;
                // process the bindings in definitions of model.entity
                if (model.getEntity() instanceof Definitions) {
                        Definitions definitions = (Definitions) model.getEntity();
                        if (definitions != null) {
                                Iterator bindings = definitions.bindings();
                                if (bindings != null) {
                                        while (bindings.hasNext()) {
                                                Binding binding = (Binding) bindings.next();
                                                if (bName.getLocalPart().equals(binding.getName())
                                                    && bName.getNamespaceURI().equals(binding.getNamespaceURI())) {
                                                        List<TWSDLExtension> bindextends = (List<TWSDLExtension>) binding
                                                            .extensions();
                                                        for (TWSDLExtension wsdlext : bindextends) {
                                                                value = resolveBindingValue(wsdlext);
                                                                if (value != null)
                                                                        break;
                                                        }
                                                        break;
                                                }
                                        }
                                }
                        }
                }

                // process the bindings in whole document
                if (value == null) {
                        if (model.getEntity() instanceof Definitions) {
                            Definitions definitions = (Definitions) model.getEntity();
                            Binding b = (Binding) definitions.resolveBindings().get(bName);
                            if (b != null) {
                                List<TWSDLExtension> bindextends = (List<TWSDLExtension>) b
                                    .extensions();
                                for (TWSDLExtension wsdlext : bindextends) {
                                    value = resolveBindingValue(wsdlext);
                                    if (value != null)
                                    break;
                                }
                            }
                        }
                }

                return value;
        }

  /**
   * Since the SOAP 1.1 binding transport URI defined in WSDL 1.1 specification
   * is different with the SOAPBinding URI defined by JAX-WS 2.0 specification.
   * We must translate the wsdl version into JAX-WS version. If the given
   * transport URI is NOT one of the predefined transport URIs, it is returned
   * as is.
   *
   * @param transportURI
   *          retrieved from WSDL
   * @return Standard BindingType URI defined by JAX-WS 2.0 specification.
   */
//  private String translate(String transportURI)
//  {
//    String translatedBindingId = TRANSLATION_MAP.get(transportURI);
//    if (translatedBindingId == null)
//      translatedBindingId = transportURI;
//
//    return translatedBindingId;
//  }

        /*****************************************************************************
         * Inner classes definition
         */
        static final class ImplFile {
                public String qualifiedName; // package+"."+simpleClassName + ".java"

                public String name; // simpleClassName + ".java"

                private ImplFile(String qualifiedClassName) {
                        this.qualifiedName = qualifiedClassName + ".java";

                        String simpleClassName = qualifiedClassName;
                        int i = qualifiedClassName.lastIndexOf(".");
                        if (i != -1)
                                simpleClassName = qualifiedClassName.substring(i + 1);

                        this.name = simpleClassName + ".java";
                }

                public static List<ImplFile> toImplFiles(List<String> qualifiedClassNames) {
                        List<ImplFile> ret = new ArrayList<ImplFile>();

                        for (String qualifiedClassName : qualifiedClassNames)
                                ret.add(new ImplFile(qualifiedClassName));

                        return ret;
                }
        }

        /*****************************************************************************
         * Other utility methods
         */

        private static File makePackageDir(WsimportOptions options) {
                File ret = null;
                if (options.defaultPackage != null && !options.defaultPackage.equals("")) {
                        String subDir = options.defaultPackage.replace('.', '/');
                        ret = new File(options.implDestDir, subDir);
                } else {
                        ret = options.implDestDir;
                }

                boolean created = ret.mkdirs();
                if (options.verbose && !created) {
                    System.out.println(MessageFormat.format("Directory not created: {0}", ret));
                }
                return ret;
        }

        private static String getQualifiedFileName(String canonicalBaseDir, File f)
            throws java.io.IOException {
                String fp = f.getCanonicalPath();
                if (fp == null)
                        return null;
                fp = fp.replace(canonicalBaseDir, "");
                fp = fp.replace('\\', '.');
                fp = fp.replace('/', '.');
                if (fp.startsWith("."))
                        fp = fp.substring(1);

                return fp;
        }

        private static File findFile(WsimportOptions options, String qualifiedFileName)
            throws java.io.IOException {
                String baseDir = options.sourceDir.getCanonicalPath();
                String fp = null;
                for (File f : options.getGeneratedFiles()) {
                        fp = getQualifiedFileName(baseDir, f);
                        if (qualifiedFileName.equals(fp))
                                return f;
                }

                return null;
        }

        private static boolean equalsNSOptional(String strQName, QName checkQN) {
                if (strQName == null)
                        return false;
                strQName = strQName.trim();
                QName reqQN = QName.valueOf(strQName);

                if (reqQN.getNamespaceURI() == null || reqQN.getNamespaceURI().equals(""))
                        return reqQN.getLocalPart().equals(checkQN.getLocalPart());

                return reqQN.equals(checkQN);
        }
}

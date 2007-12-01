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

import com.sun.codemodel.internal.*;
import com.sun.codemodel.internal.writer.ProgressCodeWriter;
import com.sun.tools.internal.ws.processor.ProcessorAction;
import com.sun.tools.internal.ws.processor.config.Configuration;
import com.sun.tools.internal.ws.processor.config.WSDLModelInfo;
import com.sun.tools.internal.ws.processor.model.*;
import com.sun.tools.internal.ws.processor.model.java.JavaInterface;
import com.sun.tools.internal.ws.processor.model.java.JavaMethod;
import com.sun.tools.internal.ws.processor.model.java.JavaParameter;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBType;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBTypeAndAnnotation;
import com.sun.tools.internal.ws.processor.util.DirectoryUtil;
import com.sun.tools.internal.ws.processor.util.GeneratedFileInfo;
import com.sun.tools.internal.ws.processor.util.IndentingWriter;
import com.sun.tools.internal.ws.wscompile.WSCodeWriter;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPStyle;
import com.sun.tools.internal.ws.wsdl.document.PortType;
import com.sun.tools.internal.xjc.api.TypeAndAnnotation;
import com.sun.xml.internal.ws.encoding.soap.SOAPVersion;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.jws.HandlerChain;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.Holder;
import javax.xml.namespace.QName;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Properties;
import java.util.Iterator;

public class SeiGenerator extends GeneratorBase implements ProcessorAction {
    private WSDLModelInfo wsdlModelInfo;
    private String serviceNS;

    public SeiGenerator() {
    }

    protected void doGeneration() {
        try {
            model.accept(this);
        } catch (Exception e) {
            if (env.verbose())
                e.printStackTrace();
            throw new GeneratorException(
                "generator.nestedGeneratorError",
                e);
        }
    }

    public GeneratorBase getGenerator(Model model, Configuration config, Properties properties) {
        return new SeiGenerator(model, config, properties);
    }

    public SeiGenerator(Model model, Configuration config, Properties properties) {
        super(model, config, properties);
        this.model = model;
        this.wsdlModelInfo = (WSDLModelInfo)config.getModelInfo();
    }

    public GeneratorBase getGenerator(Model model, Configuration config, Properties properties, SOAPVersion ver) {
        return new SeiGenerator(model, config, properties);
    }


    private void write(Service service, Port port) throws Exception{
        JavaInterface intf = port.getJavaInterface();
        String className = env.getNames().customJavaTypeClassName(intf);

        if (donotOverride && GeneratorUtil.classExists(env, className)) {
            log("Class " + className + " exists. Not overriding.");
            return;
        }


        JDefinedClass cls = getClass(className, ClassType.INTERFACE);
        if (cls == null)
            return;

        // If the class has methods it has already been defined
        // so skip it.
        if (!cls.methods().isEmpty())
            return;

        //write class comment - JAXWS warning
        JDocComment comment = cls.javadoc();

        String ptDoc = intf.getJavaDoc();
        if(ptDoc != null){
            comment.add(ptDoc);
            comment.add("\n\n");
        }

        for(String doc:getJAXWSClassComment()){
                comment.add(doc);
        }


        //@WebService
        JAnnotationUse webServiceAnn = cls.annotate(cm.ref(WebService.class));
        writeWebServiceAnnotation(service, port, webServiceAnn);

        //@HandlerChain
        writeHandlerConfig(env.getNames().customJavaTypeClassName(port.getJavaInterface()), cls, wsdlModelInfo);

        //@SOAPBinding
        writeSOAPBinding(port, cls);

        for (Operation operation: port.getOperations()) {
            JavaMethod method = operation.getJavaMethod();

            //@WebMethod
            JMethod m = null;
            JDocComment methodDoc = null;
            String methodJavaDoc = operation.getJavaDoc();
            if(method.getReturnType().getName().equals("void")){
                m = cls.method(JMod.PUBLIC, void.class, method.getName());
                methodDoc = m.javadoc();
            }else {
                JAXBTypeAndAnnotation retType = method.getReturnType().getType();
                m = cls.method(JMod.PUBLIC, retType.getType(), method.getName());
                retType.annotate(m);
                methodDoc = m.javadoc();
                JCommentPart ret = methodDoc.addReturn();
                ret.add("returns "+retType.getName());
            }
            if(methodJavaDoc != null)
                methodDoc.add(methodJavaDoc);

            writeWebMethod(operation, m);
            JClass holder = cm.ref(Holder.class);
            for (JavaParameter parameter: method.getParametersList()) {
                JVar var = null;
                JAXBTypeAndAnnotation paramType = parameter.getType().getType();
                if (parameter.isHolder()) {
                    var = m.param(holder.narrow(paramType.getType().boxify()), parameter.getName());
                }else{
                    var = m.param(paramType.getType(), parameter.getName());
                }

                //annotate parameter with JAXB annotations
                paramType.annotate(var);
                methodDoc.addParam(var);
                JAnnotationUse paramAnn = var.annotate(cm.ref(WebParam.class));
                writeWebParam(operation, parameter, paramAnn);
            }
            for(Fault fault:operation.getFaultsSet()){
                m._throws(fault.getExceptionClass());
                methodDoc.addThrows(fault.getExceptionClass());
            }
        }
        CodeWriter cw = new WSCodeWriter(sourceDir,env);

        if(env.verbose())
            cw = new ProgressCodeWriter(cw, System.out);
        cm.build(cw);
    }

    private void writeWebMethod(Operation operation, JMethod m) {
        Response response = operation.getResponse();
        JAnnotationUse webMethodAnn = m.annotate(cm.ref(WebMethod.class));;
        String operationName = (operation instanceof AsyncOperation)?
                ((AsyncOperation)operation).getNormalOperation().getName().getLocalPart():
                operation.getName().getLocalPart();

        if(!m.name().equals(operationName)){
            webMethodAnn.param("operationName", operationName);
        }

        if (operation.getSOAPAction() != null && operation.getSOAPAction().length() > 0){
            webMethodAnn.param("action", operation.getSOAPAction());
        }

        if (operation.getResponse() == null){
            m.annotate(javax.jws.Oneway.class);
        }else if (!operation.getJavaMethod().getReturnType().getName().equals("void") &&
                 operation.getResponse().getParametersList().size() > 0){
            Block block = null;
            String resultName = null;
            String nsURI = null;
            if (operation.getResponse().getBodyBlocks().hasNext()) {
                block = operation.getResponse().getBodyBlocks().next();
                resultName = block.getName().getLocalPart();
                if(isDocStyle || block.getLocation() == Block.HEADER){
                    nsURI = block.getName().getNamespaceURI();
                }
            }

            for (Parameter parameter : operation.getResponse().getParametersList()) {
                if (parameter.getParameterIndex() == -1) {
                    if(operation.isWrapped()||!isDocStyle){
                        if(parameter.getBlock().getLocation() == Block.HEADER){
                            resultName = parameter.getBlock().getName().getLocalPart();
                        }else{
                            resultName = parameter.getName();
                        }
                        if (isDocStyle || (parameter.getBlock().getLocation() == Block.HEADER)) {
                            nsURI = parameter.getType().getName().getNamespaceURI();
                        }
                    }else if(isDocStyle){
                        JAXBType t = (JAXBType)parameter.getType();
                        resultName = t.getName().getLocalPart();
                        nsURI = t.getName().getNamespaceURI();
                    }
                    if(!(operation instanceof AsyncOperation)){
                        JAnnotationUse wr = null;

                        if(!resultName.equals("return")){
                            if(wr == null)
                                wr = m.annotate(javax.jws.WebResult.class);
                            wr.param("name", resultName);
                        }
                        //if (operation.getStyle().equals(SOAPStyle.DOCUMENT) && !(nsURI.equals(serviceNS))) {
                        if((nsURI != null) && (!nsURI.equals(serviceNS) || (isDocStyle && operation.isWrapped()))){
                            if(wr == null)
                                wr = m.annotate(javax.jws.WebResult.class);
                            wr.param("targetNamespace", nsURI);
                        }
                        //doclit wrapped could have additional headers
                        if(!(isDocStyle && operation.isWrapped()) ||
                                (parameter.getBlock().getLocation() == Block.HEADER)){
                            if(wr == null)
                                wr = m.annotate(javax.jws.WebResult.class);
                            wr.param("partName", parameter.getName());
                        }
                        if(parameter.getBlock().getLocation() == Block.HEADER){
                            if(wr == null)
                                wr = m.annotate(javax.jws.WebResult.class);
                            wr.param("header",true);
                        }
                    }
                }

            }
        }

        //DOC/BARE
        if (!sameParamStyle) {
            if(!operation.isWrapped()) {
               JAnnotationUse sb = m.annotate(SOAPBinding.class);
               sb.param("parameterStyle", SOAPBinding.ParameterStyle.BARE);
            }
        }

        if (operation.isWrapped() && operation.getStyle().equals(SOAPStyle.DOCUMENT)) {
            Block reqBlock = operation.getRequest().getBodyBlocks().next();
            JAnnotationUse reqW = m.annotate(javax.xml.ws.RequestWrapper.class);
            reqW.param("localName", reqBlock.getName().getLocalPart());
            reqW.param("targetNamespace", reqBlock.getName().getNamespaceURI());
            reqW.param("className", reqBlock.getType().getJavaType().getName());

            if (response != null) {
                JAnnotationUse resW = m.annotate(javax.xml.ws.ResponseWrapper.class);
                Block resBlock = response.getBodyBlocks().next();
                resW.param("localName", resBlock.getName().getLocalPart());
                resW.param("targetNamespace", resBlock.getName().getNamespaceURI());
                resW.param("className", resBlock.getType().getJavaType().getName());
            }
        }
    }

    //TODO: JAXB should expose the annotations so that it can be added to JAnnotationUse
    protected void writeJAXBTypeAnnotations(JAnnotationUse annUse, Parameter param) throws IOException{
        List<String> annotations = param.getAnnotations();
        if(annotations == null)
            return;

        for(String annotation:param.getAnnotations()){
            //p.pln(annotation);
            //annUse.
        }
    }

    private boolean isMessageParam(Parameter param, Message message) {
        Block block = param.getBlock();

        return (message.getBodyBlockCount() > 0 && block.equals(message.getBodyBlocks().next())) ||
               (message.getHeaderBlockCount() > 0 &&
               block.equals(message.getHeaderBlocks().next()));
    }

    private boolean isHeaderParam(Parameter param, Message message) {
        if (message.getHeaderBlockCount() == 0)
            return false;

        for (Block headerBlock : message.getHeaderBlocksMap().values())
            if (param.getBlock().equals(headerBlock))
                return true;

        return false;
    }

    private boolean isAttachmentParam(Parameter param, Message message){
        if (message.getAttachmentBlockCount() == 0)
            return false;

        for (Block attBlock : message.getAttachmentBlocksMap().values())
            if (param.getBlock().equals(attBlock))
                return true;

        return false;
    }

    private boolean isUnboundParam(Parameter param, Message message){
        if (message.getUnboundBlocksCount() == 0)
            return false;

        for (Block unboundBlock : message.getUnboundBlocksMap().values())
            if (param.getBlock().equals(unboundBlock))
                return true;

        return false;
    }

    private void writeWebParam(Operation operation, JavaParameter javaParameter, JAnnotationUse paramAnno) {
        Parameter param = javaParameter.getParameter();
        Request req = operation.getRequest();
        Response res = operation.getResponse();

        boolean header = isHeaderParam(param, req) ||
            (res != null ? isHeaderParam(param, res) : false);

        String name;
        boolean isWrapped = operation.isWrapped();

        if((param.getBlock().getLocation() == Block.HEADER) || (isDocStyle && !isWrapped))
            name = param.getBlock().getName().getLocalPart();
        else
            name = param.getName();

        paramAnno.param("name", name);

        String ns= null;

        if (isDocStyle) {
            ns = param.getBlock().getName().getNamespaceURI(); // its bare nsuri
            if(isWrapped){
                ns = ((JAXBType)param.getType()).getName().getNamespaceURI();
            }
        }else if(!isDocStyle && header){
            ns = param.getBlock().getName().getNamespaceURI();
        }

        if((ns != null) && (!ns.equals(serviceNS) || (isDocStyle && isWrapped)))
            paramAnno.param("targetNamespace", ns);

        if (header) {
            paramAnno.param("header", true);
        }

        if (param.isINOUT()){
            paramAnno.param("mode", javax.jws.WebParam.Mode.INOUT);
        }else if ((res != null) && (isMessageParam(param, res) || isHeaderParam(param, res) || isAttachmentParam(param, res) ||
                isUnboundParam(param,res))){
            paramAnno.param("mode", javax.jws.WebParam.Mode.OUT);
        }

        //doclit wrapped could have additional headers
        if(!(isDocStyle && isWrapped) || header)
            paramAnno.param("partName", javaParameter.getParameter().getName());
    }

    boolean isDocStyle = true;
    boolean sameParamStyle = true;
    private void writeSOAPBinding(Port port, JDefinedClass cls) {
        JAnnotationUse soapBindingAnn = null;
        isDocStyle = port.getStyle() != null ? port.getStyle().equals(SOAPStyle.DOCUMENT) : true;
        if(!isDocStyle){
            if(soapBindingAnn == null)
                soapBindingAnn = cls.annotate(SOAPBinding.class);
            soapBindingAnn.param("style", SOAPBinding.Style.RPC);
            port.setWrapped(true);
        }
        if(isDocStyle){
            boolean first = true;
            boolean isWrapper = true;
            for(Operation operation:port.getOperations()){
                if(first){
                    isWrapper = operation.isWrapped();
                    first = false;
                    continue;
                }
                sameParamStyle = (isWrapper == operation.isWrapped());
                if(!sameParamStyle)
                    break;
            }
            if(sameParamStyle)
                port.setWrapped(isWrapper);
        }
        if(sameParamStyle && !port.isWrapped()){
            if(soapBindingAnn == null)
                soapBindingAnn = cls.annotate(SOAPBinding.class);
            soapBindingAnn.param("parameterStyle", SOAPBinding.ParameterStyle.BARE);
        }
    }

    private void writeWebServiceAnnotation(Service service, Port port, JAnnotationUse wsa) {
        QName name = (QName) port.getProperty(ModelProperties.PROPERTY_WSDL_PORT_TYPE_NAME);
        wsa.param("name", name.getLocalPart());
        wsa.param("targetNamespace", name.getNamespaceURI());
    }




    public void visit(Model model) throws Exception {
        for(Service s:model.getServices()){
            s.accept(this);
        }
    }

    public void visit(Service service) throws Exception {
        String jd = model.getJavaDoc();
        if(jd != null){
            JPackage pkg = cm._package(wsdlModelInfo.getJavaPackageName());
            pkg.javadoc().add(jd);
        }

        for(Port p:service.getPorts()){
            visitPort(service, p);
        }
    }

    private void visitPort(Service service, Port port) {
        if (port.isProvider()) {
            return;                // Not generating for Provider based endpoint
        }


        try {
            write(service, port);
        } catch (Exception e) {
            throw new GeneratorException(
                "generator.nestedGeneratorError",
                e);
        }
    }
}

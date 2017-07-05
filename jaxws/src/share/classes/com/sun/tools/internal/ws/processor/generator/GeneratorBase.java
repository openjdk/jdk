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

import com.sun.codemodel.internal.ClassType;
import com.sun.codemodel.internal.JAnnotationUse;
import com.sun.codemodel.internal.JClassAlreadyExistsException;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JDefinedClass;
import com.sun.tools.internal.ws.ToolVersion;
import com.sun.tools.internal.ws.processor.model.Block;
import com.sun.tools.internal.ws.processor.model.Fault;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.processor.model.ModelVisitor;
import com.sun.tools.internal.ws.processor.model.Operation;
import com.sun.tools.internal.ws.processor.model.Parameter;
import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.processor.model.Request;
import com.sun.tools.internal.ws.processor.model.Response;
import com.sun.tools.internal.ws.processor.model.Service;
import com.sun.tools.internal.ws.processor.util.DirectoryUtil;
import com.sun.tools.internal.ws.processor.util.IndentingWriter;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.jws.HandlerChain;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author WS Development Team
 */
public abstract class GeneratorBase
    implements
        GeneratorConstants,
        ModelVisitor{
    private File destDir;
    private String targetVersion;
    protected boolean donotOverride;
    protected JCodeModel cm;
    protected final Model model;
    protected final String wsdlLocation;
    protected final ErrorReceiver receiver;
    protected final WsimportOptions options;

    protected GeneratorBase(Model model, WsimportOptions options, ErrorReceiver receiver){
        this.model = model;
        this.options = options;
        this.destDir = options.destDir;
        this.receiver = receiver;
        this.wsdlLocation = options.wsdlLocation;
        this.targetVersion = options.target.getVersion();
        this.cm = options.getCodeModel();
    }

    protected void doGeneration() {
        try {
            model.accept(this);
        } catch (Exception e) {
            receiver.error(e);
        }
    }

    public void visit(Model model) throws Exception {
        for (Service service : model.getServices()) {
            service.accept(this);
        }
    }

    public void visit(Service service) throws Exception {
        for (Port port : service.getPorts()) {
            port.accept(this);
        }
    }

    public void visit(Port port) throws Exception {
        for (Operation operation : port.getOperations()) {
            operation.accept(this);
        }
    }

    public void visit(Operation operation) throws Exception {
        operation.getRequest().accept(this);
        if (operation.getResponse() != null)
            operation.getResponse().accept(this);
        Iterator faults = operation.getFaultsSet().iterator();
        if (faults != null) {
            Fault fault;
            while (faults.hasNext()) {
                fault = (Fault) faults.next();
                fault.accept(this);
            }
        }
    }

    public void visit(Parameter param) throws Exception {
    }

    public void visit(Block block) throws Exception {
    }

    public void visit(Response response) throws Exception {
    }


    public void visit(Request request) throws Exception {
    }

    public void visit(Fault fault) throws Exception {
    }

    public List<String> getJAXWSClassComment(){
        return getJAXWSClassComment(targetVersion);
    }

    public static List<String> getJAXWSClassComment(String targetVersion) {
        List<String> comments = new ArrayList<String>();
        comments.add("This class was generated by the JAX-WS RI.\n");
        comments.add(ToolVersion.VERSION.BUILD_VERSION+"\n");
        comments.add("Generated source version: " + targetVersion);
        return comments;
    }

    protected JDefinedClass getClass(String className, ClassType type) throws JClassAlreadyExistsException {
        JDefinedClass cls;
        try {
            cls = cm._class(className, type);
        } catch (JClassAlreadyExistsException e){
            cls = cm._getClass(className);
            if(cls == null)
                throw e;
        }
        return cls;
    }

    protected void log(String msg) {
        if (options.verbose) {
            System.out.println(
                "["
                    + Names.stripQualifier(this.getClass().getName())
                    + ": "
                    + msg
                    + "]");
        }
    }

    protected void writeHandlerConfig(String className, JDefinedClass cls, WsimportOptions options) {
        Element e = options.getHandlerChainConfiguration();
        if(e == null)
            return;
        JAnnotationUse handlerChainAnn = cls.annotate(cm.ref(HandlerChain.class));
        NodeList nl = e.getElementsByTagNameNS(
            "http://java.sun.com/xml/ns/javaee", "handler-chain");
        if(nl.getLength() > 0){
            String fName = getHandlerConfigFileName(className);
            handlerChainAnn.param("file", fName);
            generateHandlerChainFile(e, className);
        }
    }

     private String getHandlerConfigFileName(String fullName){
        String name = Names.stripQualifier(fullName);
        return name+"_handler.xml";
    }

    private void generateHandlerChainFile(Element hChains, String name) {
        String hcName = getHandlerConfigFileName(name);

        File packageDir = DirectoryUtil.getOutputDirectoryFor(name, destDir);
        File hcFile = new File(packageDir, hcName);

        options.addGeneratedFile(hcFile);

        try {
            IndentingWriter p =
                new IndentingWriter(
                    new OutputStreamWriter(new FileOutputStream(hcFile)));
            Transformer it = XmlUtil.newTransformer();

            it.setOutputProperty(OutputKeys.METHOD, "xml");
            it.setOutputProperty(OutputKeys.INDENT, "yes");
            it.setOutputProperty(
                "{http://xml.apache.org/xslt}indent-amount",
                "2");
            it.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            it.transform( new DOMSource(hChains), new StreamResult(p) );
            p.close();
        } catch (Exception e) {
            throw new GeneratorException(
                    "generator.nestedGeneratorError",
                    e);
        }
    }

}

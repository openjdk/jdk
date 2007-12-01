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

import java.util.Properties;

import org.xml.sax.SAXParseException;

import com.sun.codemodel.internal.CodeWriter;
import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.writer.ProgressCodeWriter;
//import com.sun.tools.internal.xjc.addon.Augmenter;
import com.sun.tools.internal.xjc.api.ErrorListener;
import com.sun.tools.internal.xjc.api.JAXBModel;
import com.sun.tools.internal.xjc.api.S2JJAXBModel;
import com.sun.tools.internal.ws.processor.config.Configuration;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBType;
import com.sun.tools.internal.ws.processor.model.jaxb.RpcLitStructure;
import com.sun.tools.internal.ws.processor.modeler.wsdl.ConsoleErrorReporter;
import com.sun.tools.internal.ws.processor.ProcessorOptions;
import com.sun.xml.internal.ws.encoding.soap.SOAPVersion;
import com.sun.tools.internal.ws.wscompile.WSCodeWriter;

/**
 * @author Vivek Pandey
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class JAXBTypeGenerator extends GeneratorBase {

    /**
     * @author Vivek Pandey
     *
     * To change the template for this generated type comment go to
     * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
     */
    public static class JAXBErrorListener implements ErrorListener {

        /**
         *
         */
        public JAXBErrorListener() {
            super();
        }

        /* (non-Javadoc)
         * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
         */
        public void error(SAXParseException arg0) {
            // TODO Auto-generated method stub

        }

        /* (non-Javadoc)
         * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
         */
        public void fatalError(SAXParseException arg0) {
            // TODO Auto-generated method stub

        }

        /* (non-Javadoc)
         * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
         */
        public void warning(SAXParseException arg0) {
            // TODO Auto-generated method stub

        }

        /* (non-Javadoc)
         * @see com.sun.tools.internal.xjc.api.ErrorListener#info(org.xml.sax.SAXParseException)
         */
        public void info(SAXParseException arg0) {
            // TODO Auto-generated method stub

        }

    }
    /**
     *
     */
    public JAXBTypeGenerator() {
        super();
        // TODO Auto-generated constructor stub
    }
    /**
     * @param model
     * @param config
     * @param properties
     */
    public JAXBTypeGenerator(Model model, Configuration config,
            Properties properties) {
        super(model, config, properties);
    }
    /* (non-Javadoc)
     * @see GeneratorBase#getGenerator(com.sun.xml.internal.ws.processor.model.Model, com.sun.xml.internal.ws.processor.config.Configuration, java.util.Properties)
     */
    public GeneratorBase getGenerator(Model model, Configuration config,
            Properties properties) {
        return new JAXBTypeGenerator(model, config, properties);
    }
    /* (non-Javadoc)
     * @see cGeneratorBase#getGenerator(com.sun.xml.internal.ws.processor.model.Model, com.sun.xml.internal.ws.processor.config.Configuration, java.util.Properties, com.sun.xml.internal.ws.soap.SOAPVersion)
     */
    public GeneratorBase getGenerator(Model model, Configuration config,
            Properties properties, SOAPVersion ver) {
        return new JAXBTypeGenerator(model, config, properties);
    }

    /* (non-Javadoc)
     * @see JAXBTypeVisitor#visit(JAXBType)
     */
    public void visit(JAXBType type) throws Exception {
        //this is a raw type, probably from rpclit
        if(type.getJaxbModel() == null)
            return;
        S2JJAXBModel model = type.getJaxbModel().getS2JJAXBModel();
        if (model != null)
            generateJAXBClasses(model);
    }


    /* (non-Javadoc)
     * @see JAXBTypeVisitor#visit(com.sun.xml.internal.ws.processor.model.jaxb.RpcLitStructure)
     */
    public void visit(RpcLitStructure type) throws Exception {
        S2JJAXBModel model = type.getJaxbModel().getS2JJAXBModel();
        generateJAXBClasses(model);
    }

    private static boolean doneGeneration = true;
    private void generateJAXBClasses(S2JJAXBModel model) throws Exception{
        if(doneGeneration)
            return;
        JCodeModel cm = null;

        // get the list of jaxb source files
        CodeWriter cw = new WSCodeWriter(sourceDir,env);

        if(env.verbose())
            cw = new ProgressCodeWriter(cw, System.out); // TODO this should not be System.out, should be
                                                         // something from ProcessorEnvironment
        //TODO:set package level javadoc in JPackage
        cm = model.generateCode(null, new ConsoleErrorReporter(env, printStackTrace));
        cm.build(cw);
        doneGeneration = true;
    }


}

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
package com.sun.tools.internal.ws.processor.modeler.wsdl;

import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.xml.namespace.QName;

import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import com.sun.tools.internal.xjc.api.ErrorListener;
import com.sun.tools.internal.xjc.api.SchemaCompiler;
import com.sun.tools.internal.xjc.api.XJC;
import com.sun.tools.internal.xjc.api.TypeAndAnnotation;
import com.sun.tools.internal.ws.processor.ProcessorOptions;
import com.sun.tools.internal.ws.processor.config.ModelInfo;
import com.sun.tools.internal.ws.processor.config.WSDLModelInfo;
import com.sun.tools.internal.ws.processor.model.ModelException;
import com.sun.tools.internal.ws.processor.model.java.JavaSimpleType;
import com.sun.tools.internal.ws.processor.model.java.JavaType;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBMapping;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBModel;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBType;
import com.sun.tools.internal.ws.processor.modeler.JavaSimpleTypeCreator;
import com.sun.tools.internal.ws.processor.util.ClassNameCollector;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;

/**
 * @author Kathy Walsh, Vivek Pandey
 *
 * Uses JAXB XJC apis to build JAXBModel and resolves xml to java type mapping from JAXBModel
 */
public class JAXBModelBuilder {
    public JAXBModelBuilder(ModelInfo modelInfo,
                            Properties options, ClassNameCollector classNameCollector, List elements) {
        _messageFactory =
            new LocalizableMessageFactory("com.sun.tools.internal.ws.resources.model");
        _modelInfo = modelInfo;
        _env = (ProcessorEnvironment) modelInfo.getParent().getEnvironment();
        _classNameAllocator = new ClassNameAllocatorImpl(classNameCollector);

        printstacktrace = Boolean.valueOf(options.getProperty(ProcessorOptions.PRINT_STACK_TRACE_PROPERTY));
        consoleErrorReporter = new ConsoleErrorReporter(_env, false);
        internalBuildJAXBModel(elements);
    }

    /**
     * Builds model from WSDL document. Model contains abstraction which is used by the
     * generators to generate the stub/tie/serializers etc. code.
     *
     * @see com.sun.tools.internal.ws.processor.modeler.Modeler#buildModel()
     */

    private void internalBuildJAXBModel(List elements){
        try {
            schemaCompiler = XJC.createSchemaCompiler();
            schemaCompiler.setClassNameAllocator(_classNameAllocator);
            schemaCompiler.setErrorListener(consoleErrorReporter);
            schemaCompiler.setEntityResolver(_modelInfo.getEntityResolver());
            int schemaElementCount = 1;
            for(Iterator iter = elements.iterator(); iter.hasNext();){
                Element schemaElement = (Element)iter.next();
                String location = schemaElement.getOwnerDocument().getDocumentURI();
                String systemId = new String(location + "#types?schema"+schemaElementCount++);
                schemaCompiler.parseSchema(systemId,schemaElement);
            }

            //feed external jaxb:bindings file
            Set<InputSource> externalBindings = ((WSDLModelInfo)_modelInfo).getJAXBBindings();
            if(externalBindings != null){
                for(InputSource jaxbBinding : externalBindings){
                    schemaCompiler.parseSchema(jaxbBinding);
                }
            }
        } catch (Exception e) {
            throw new ModelException(e);
        }
    }

    public JAXBType  getJAXBType(QName qname){
        JAXBMapping mapping = jaxbModel.get(qname);
        if (mapping == null){
            fail("model.schema.elementNotFound", new Object[]{qname});
        }

        JavaType javaType = new JavaSimpleType(mapping.getType());
        JAXBType type =  new JAXBType(qname, javaType, mapping, jaxbModel);
        return type;
    }

    public TypeAndAnnotation getElementTypeAndAnn(QName qname){
        JAXBMapping mapping = jaxbModel.get(qname);
        if (mapping == null){
            fail("model.schema.elementNotFound", new Object[]{qname});
        }
        return mapping.getType().getTypeAnn();
    }

    protected void bind(){
        com.sun.tools.internal.xjc.api.JAXBModel rawJaxbModel = schemaCompiler.bind();
        if(consoleErrorReporter.hasError()){
            throw new ModelException(consoleErrorReporter.getException());
        }
        jaxbModel = new JAXBModel(rawJaxbModel);
        jaxbModel.setGeneratedClassNames(_classNameAllocator.getJaxbGeneratedClasses());
    }

    protected SchemaCompiler getJAXBSchemaCompiler(){
        return schemaCompiler;
    }

    protected void fail(String key, Object[] arg) {
        throw new ModelException(key, arg);
    }

    protected void error(String key, Object[] args){
        _env.error(_messageFactory.getMessage(key, args));
    }

    protected void warn(String key, Object[] args) {
        _env.warn(_messageFactory.getMessage(key, args));
    }

    protected void inform(String key, Object[] args) {
        _env.info(_messageFactory.getMessage(key, args));
    }

    public JAXBModel getJAXBModel(){
        return jaxbModel;
    }

    private JAXBModel jaxbModel;
    private SchemaCompiler schemaCompiler;
    private final LocalizableMessageFactory _messageFactory;
    private final ModelInfo _modelInfo;
    private final ProcessorEnvironment _env;
    private final boolean printstacktrace;
    private final ClassNameAllocatorImpl _classNameAllocator;
    private final ConsoleErrorReporter consoleErrorReporter;
}

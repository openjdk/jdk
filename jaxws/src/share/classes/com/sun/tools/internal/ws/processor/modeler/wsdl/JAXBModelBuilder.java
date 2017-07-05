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
package com.sun.tools.internal.ws.processor.modeler.wsdl;

import com.sun.tools.internal.ws.processor.model.ModelException;
import com.sun.tools.internal.ws.processor.model.java.JavaSimpleType;
import com.sun.tools.internal.ws.processor.model.java.JavaType;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBMapping;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBModel;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBType;
import com.sun.tools.internal.ws.processor.util.ClassNameCollector;
import com.sun.tools.internal.ws.wscompile.AbortException;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.tools.internal.ws.wsdl.parser.DOMForestScanner;
import com.sun.tools.internal.ws.wsdl.parser.MetadataFinder;
import com.sun.tools.internal.xjc.api.S2JJAXBModel;
import com.sun.tools.internal.xjc.api.SchemaCompiler;
import com.sun.tools.internal.xjc.api.TypeAndAnnotation;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.namespace.QName;

/**
 * @author  Vivek Pandey
 *
 * Uses JAXB XJC apis to build JAXBModel and resolves xml to java type mapping from JAXBModel
 */
public class JAXBModelBuilder {

    private final ErrorReceiver errReceiver;
    private final WsimportOptions options;
    private final MetadataFinder forest;

    public JAXBModelBuilder(WsimportOptions options, ClassNameCollector classNameCollector, MetadataFinder finder, ErrorReceiver errReceiver) {
        this._classNameAllocator = new ClassNameAllocatorImpl(classNameCollector);
        this.errReceiver = errReceiver;
        this.options = options;
        this.forest = finder;

        internalBuildJAXBModel();
    }

    /**
     * Builds model from WSDL document. Model contains abstraction which is used by the
     * generators to generate the stub/tie/serializers etc. code.
     *
     * @see com.sun.tools.internal.ws.processor.modeler.Modeler#buildModel()
     */

    private void internalBuildJAXBModel(){
        try {
            schemaCompiler =  options.getSchemaCompiler();
            schemaCompiler.resetSchema();
            schemaCompiler.setEntityResolver(options.entityResolver);
            schemaCompiler.setClassNameAllocator(_classNameAllocator);
            schemaCompiler.setErrorListener(errReceiver);
            int schemaElementCount = 1;

            for (Element element : forest.getInlinedSchemaElement()) {
                String location = element.getOwnerDocument().getDocumentURI();
                String systemId = location + "#types?schema" + schemaElementCount++;
                if(forest.isMexMetadata)
                    schemaCompiler.parseSchema(systemId,element);
                else
                    new DOMForestScanner(forest).scan(element,schemaCompiler.getParserHandler(systemId));
            }

            //feed external jaxb:bindings file
            InputSource[] externalBindings = options.getSchemaBindings();
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
            return null;
        }
        JavaType javaType = new JavaSimpleType(mapping.getType());
        return new JAXBType(qname, javaType, mapping, jaxbModel);
    }

    public TypeAndAnnotation getElementTypeAndAnn(QName qname){
        JAXBMapping mapping = jaxbModel.get(qname);
        if (mapping == null){
            return null;
        }
        return mapping.getType().getTypeAnn();
    }

    protected void bind(){
        S2JJAXBModel rawJaxbModel = schemaCompiler.bind();
        if(rawJaxbModel == null)
            throw new AbortException();
        options.setCodeModel(rawJaxbModel.generateCode(null, errReceiver));
        jaxbModel = new JAXBModel(rawJaxbModel);
        jaxbModel.setGeneratedClassNames(_classNameAllocator.getJaxbGeneratedClasses());
    }

    protected SchemaCompiler getJAXBSchemaCompiler(){
        return schemaCompiler;
    }

    public JAXBModel getJAXBModel(){
        return jaxbModel;
    }

    private JAXBModel jaxbModel;
    private SchemaCompiler schemaCompiler;
    private final ClassNameAllocatorImpl _classNameAllocator;
    protected static final LocatorImpl NULL_LOCATOR = new LocatorImpl();

}

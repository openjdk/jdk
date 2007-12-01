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
package com.sun.tools.internal.ws.processor.config.parser;

import java.net.URL;
import java.util.*;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.EntityResolver;

import com.sun.tools.internal.ws.processor.ProcessorOptions;
import com.sun.tools.internal.ws.processor.config.Configuration;
import com.sun.tools.internal.ws.processor.config.WSDLModelInfo;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBindingsConstants;

import javax.xml.stream.XMLStreamReader;

/**
 * @author Vivek Pandey
 *
 */
public class CustomizationParser extends InputParser {

    /**
     * @param entityResolver
     * @param env
     * @param options
     */
    public CustomizationParser(EntityResolver entityResolver, ProcessorEnvironment env, Properties options) {
        super(env, options);
        this.entityResolver = entityResolver;
    }


    /* (non-Javadoc)
     * @see com.sun.xml.internal.ws.processor.config.parser.InputParser#parse(java.io.File[], java.lang.String)
     */
    protected Configuration parse(List<String> inputFiles) throws Exception{
        //File wsdlFile = inputFiles[0];
        Configuration configuration = new Configuration(getEnv());
        wsdlModelInfo = new WSDLModelInfo();
        wsdlModelInfo.setLocation(inputFiles.get(0));
        if(_options.get(ProcessorOptions.WSDL_LOCATION) == null)
            _options.setProperty(ProcessorOptions.WSDL_LOCATION, inputFiles.get(0));

        //modelInfoParser = (JAXWSBindingInfoParser)getModelInfoParsers().get(JAXWSBindingsConstants.JAXWS_BINDINGS);
        modelInfoParser = new JAXWSBindingInfoParser(getEnv());

        //get the jaxws bindingd file and add it to the modelInfo
        Set<String> bindingFiles = (Set<String>)_options.get(ProcessorOptions.BINDING_FILES);
        for(String bindingFile : bindingFiles){
            addBinding(bindingFile);
        }


        for(InputSource jaxwsBinding : jaxwsBindings){
            Document doc = modelInfoParser.parse(jaxwsBinding);
            if(doc != null){
                wsdlModelInfo.putJAXWSBindings(jaxwsBinding.getSystemId(), doc);
            }
        }

        //copy jaxb binding sources in modelInfo
        for(InputSource jaxbBinding : jaxbBindings){
            wsdlModelInfo.addJAXBBIndings(jaxbBinding);
        }

        addHandlerChainInfo();
        configuration.setModelInfo(wsdlModelInfo);
        return configuration;
    }

    private void addBinding(String bindingLocation) throws Exception{
        JAXWSUtils.checkAbsoluteness(bindingLocation);
        InputSource is = null;
        if(entityResolver != null){
            is = entityResolver.resolveEntity(null, bindingLocation);
        }
        if(is == null)
            is = new InputSource(bindingLocation);

        XMLStreamReader reader =
                XMLStreamReaderFactory.createFreshXMLStreamReader(is, true);
        XMLStreamReaderUtil.nextElementContent(reader);
        if(reader.getName().equals(JAXWSBindingsConstants.JAXWS_BINDINGS)){
            jaxwsBindings.add(is);
        }else if(reader.getName().equals(JAXWSBindingsConstants.JAXB_BINDINGS)){
            jaxbBindings.add(is);
        }else{
            warn("configuration.notBindingFile");
        }
    }

    private void addHandlerChainInfo() throws Exception{
        //setup handler chain info
        for(Map.Entry<String, Document> entry:wsdlModelInfo.getJAXWSBindings().entrySet()){
            Element e = entry.getValue().getDocumentElement();
            NodeList nl = e.getElementsByTagNameNS(
                "http://java.sun.com/xml/ns/javaee", "handler-chains");
            if(nl.getLength()== 0)
                continue;
            //take the first one, anyway its 1 handler-config per customization
            Element hc = (Element)nl.item(0);
            wsdlModelInfo.setHandlerConfig(hc);
            return;
        }
    }

    private WSDLModelInfo wsdlModelInfo;
    private JAXWSBindingInfoParser modelInfoParser;
    private Set<InputSource> jaxwsBindings = new HashSet<InputSource>();
    private Set<InputSource> jaxbBindings = new HashSet<InputSource>();
    private EntityResolver entityResolver;

}

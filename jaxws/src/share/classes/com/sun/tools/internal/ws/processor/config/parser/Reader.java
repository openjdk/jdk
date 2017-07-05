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
import java.util.List;
import java.util.Properties;

import com.sun.tools.internal.ws.processor.config.Configuration;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.xml.internal.ws.util.JAXWSUtils;
import com.sun.tools.internal.ws.wsdl.document.WSDLConstants;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;

import javax.xml.stream.XMLStreamReader;

import org.xml.sax.EntityResolver;

/**
 * @author Vivek Pandey
 *
 * Main entry point from CompileTool
 */
public class Reader {

    /**
     *
     */
    public Reader(ProcessorEnvironment env, Properties options) {
        this._env = env;
        this._options = options;
    }

    public Configuration parse(EntityResolver entityResolver, List<String> inputSources)
            throws Exception {
        //reset the input type flags before parsing
        isClassFile = false;

        InputParser parser = null;
        //now its just the first file. do we expect more than one input files?
        validateInput(inputSources.get(0));

        if(isClassFile){
            parser = new ClassModelParser(_env, _options);
        } else {
            parser = new CustomizationParser(entityResolver, _env, _options);
        }
        return parser.parse(inputSources);
    }

    protected void validateInput(String file) throws Exception{
        if(isClass(file)){
            isClassFile = true;
            return;
        }

//        JAXWSUtils.checkAbsoluteness(file);
//        URL url = new URL(file);
//
//        XMLStreamReader reader =
//                XMLStreamReaderFactory.createXMLStreamReader(url.openStream(), true);
//
//        XMLStreamReaderUtil.nextElementContent(reader);
//        if(!reader.getName().equals(WSDLConstants.QNAME_DEFINITIONS)){
//            //we are here, means invalid element
//            ParserUtil.failWithFullName("configuration.invalidElement", file, reader);
//        }
    }

    public boolean isClass(String className) {
        try {
            _env.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean isClassFile;

    protected ProcessorEnvironment _env;

    protected Properties _options;
}

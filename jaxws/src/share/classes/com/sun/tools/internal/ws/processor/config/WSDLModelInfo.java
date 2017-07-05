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

package com.sun.tools.internal.ws.processor.config;


import java.util.*;

import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import com.sun.tools.internal.ws.processor.modeler.Modeler;
import com.sun.tools.internal.ws.util.JAXWSClassFactory;

/**
 *
 * @author WS Development Team
 */
public class WSDLModelInfo extends ModelInfo {

    public WSDLModelInfo() {}

    protected Modeler getModeler(Properties options) {
        return JAXWSClassFactory.newInstance().createWSDLModeler(this, options);
    }

    public String getLocation() {
        return _location;
    }

    public void setLocation(String s) {
        _location = s;
    }

    public Map<String, Document> getJAXWSBindings(){
        return _jaxwsBindings;
    }

    public void putJAXWSBindings(String systemId, Document binding){
        _jaxwsBindings.put(systemId, binding);
    }

    public Set<InputSource> getJAXBBindings(){
        return _jaxbBindings;
    }

    public void addJAXBBIndings(InputSource jaxbBinding){
        _jaxbBindings.add(jaxbBinding);
    }

    public void setHandlerConfig(Element handlerConfig){
        this.handlerConfig = handlerConfig;
    }

    public Element getHandlerConfig(){
        return handlerConfig;
    }

    private Element handlerConfig;

    private String _location;

    //external jaxws:bindings elements
    private Map<String, Document> _jaxwsBindings = new HashMap<String, Document>();

    //we need an array of jaxb:binding elements, they are children of jaxws:bindings
    //and could come from an external customization file or wsdl.
    private Set<InputSource> _jaxbBindings = new HashSet<InputSource>();
}

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



package com.sun.tools.internal.ws.wsdl.parser;

import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBindingsConstants;
import com.sun.tools.internal.xjc.reader.internalizer.LocatorTable;
import com.sun.xml.internal.bind.marshaller.SAX2DOMEx;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;

import java.util.Set;

/**
 * Builds DOM while keeping the location information.
 *
 * <p>
 * This class also looks for outer most &lt;jaxws:bindings>
 * customizations.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 *     Vivek Pandey
 */
class DOMBuilder extends SAX2DOMEx {
    /**
     * Grows a DOM tree under the given document, and
     * stores location information to the given table.
     *
     * @param outerMostBindings
     *      This set will receive newly found outermost
     *      jaxb:bindings customizations.
     */
    public DOMBuilder( Document dom, LocatorTable ltable, Set outerMostBindings ) {
        super( dom );
        this.locatorTable = ltable;
        this.outerMostBindings = outerMostBindings;
    }

    /** Location information will be stored into this object. */
    private final LocatorTable locatorTable;

    private final Set outerMostBindings;

    private Locator locator;

    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
        super.setDocumentLocator(locator);
    }


    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) {
        super.startElement(namespaceURI, localName, qName, atts);

        Element e = getCurrentElement();
        locatorTable.storeStartLocation( e, locator );

        // check if this element is an outer-most <jaxb:bindings>
        if( JAXWSBindingsConstants.JAXWS_BINDINGS.getNamespaceURI().equals(e.getNamespaceURI())
        &&  "bindings".equals(e.getLocalName()) ) {

            // if this is the root node (meaning that this file is an
            // external binding file) or if the parent is XML Schema element
            // (meaning that this is an "inlined" external binding)
            Node p = e.getParentNode();
            if( p instanceof Document) {
                outerMostBindings.add(e);   // remember this value
            }
        }
    }

    public void endElement(String namespaceURI, String localName, String qName) {
        locatorTable.storeEndLocation( getCurrentElement(), locator );
        super.endElement(namespaceURI, localName, qName);
    }
}

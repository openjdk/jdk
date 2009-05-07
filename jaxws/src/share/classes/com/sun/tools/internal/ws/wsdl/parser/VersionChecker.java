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

import com.sun.tools.internal.ws.resources.WsdlMessages;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBindingsConstants;
import org.xml.sax.*;
import org.xml.sax.helpers.LocatorImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Checks the jaxb:version attribute on a XML Schema document.
 *
 * jaxws:version is optional, if absent its value is assumed to be "2.0" and if present its value must be
 * "2.0" or more.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 *     Vivek Pandey
 */
public class VersionChecker extends XMLFilterImpl {

    /**
     * We store the value of the version attribute in this variable
     * when we hit the root element.
     */
    private String version = null ;

    /** Will be set to true once we hit the root element. */
    private boolean seenRoot = false;

    /** Will be set to true once we hit a binding declaration. */
    private boolean seenBindings = false;

    private Locator locator;

    /**
     * Stores the location of the start tag of the root tag.
     */
    private Locator rootTagStart;

    public VersionChecker( XMLReader parent ) {
        setParent(parent);
    }

    public VersionChecker( ContentHandler handler, ErrorHandler eh, EntityResolver er ) {
        setContentHandler(handler);
        if(eh!=null)    setErrorHandler(eh);
        if(er!=null)    setEntityResolver(er);
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
        throws SAXException {

        super.startElement(namespaceURI, localName, qName, atts);

        if(!seenRoot) {
            // if this is the root element
            seenRoot = true;
            rootTagStart = new LocatorImpl(locator);

            version = atts.getValue(JAXWSBindingsConstants.NS_JAXWS_BINDINGS,"version");
            if( namespaceURI.equals(JAXWSBindingsConstants.NS_JAXWS_BINDINGS) ) {
                String version2 = atts.getValue("","version");
                if( version!=null && version2!=null ) {
                    // we have both @version and @jaxb:version. error.
                    SAXParseException e = new SAXParseException(
                        WsdlMessages.INTERNALIZER_TWO_VERSION_ATTRIBUTES(), locator);
                    getErrorHandler().error(e);
                }
                //According to JAXWS 2.0 spec, if version attribute is missing its assumed to be "2.0"
                if( version==null)
                    version = (version2!=null)?version2:"2.0";
            }

        }

        if( JAXWSBindingsConstants.NS_JAXWS_BINDINGS.equals(namespaceURI)){
            seenBindings = true;
            if(version == null)
                version = "2.0";
        }

    }

    public void endDocument() throws SAXException {
        super.endDocument();

        if( seenBindings && version==null ) {
            // if we see a binding declaration but not version attribute
            SAXParseException e = new SAXParseException(WsdlMessages.INTERNALIZER_VERSION_NOT_PRESENT(), rootTagStart);
            getErrorHandler().error(e);
        }

        // if present, the value must be >= 2.0
        if( version!=null && !VERSIONS.contains(version) ) {
            SAXParseException e = new SAXParseException(WsdlMessages.INTERNALIZER_INCORRECT_VERSION(), rootTagStart);
            getErrorHandler().error(e);
        }
    }

    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }

    private static final Set<String> VERSIONS = new HashSet<String>(Arrays.asList("2.0","2.1"));

}

/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.internal.ws.wsdl.parser;

import com.sun.istack.internal.SAXParseException2;
import com.sun.tools.internal.ws.resources.WsdlMessages;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.XMLFilterImpl;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * XMLFilter that finds references to other schema files from
 * SAX events.
 *
 * This implementation is a base implementation for typical case
 * where we just need to look for a particular attribute which
 * contains an URL to another schema file.
 *
 * @author
 *  Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 *  Vivek Pandey
 */
public abstract class AbstractReferenceFinderImpl extends XMLFilterImpl {
    protected final DOMForest parent;

    protected AbstractReferenceFinderImpl( DOMForest _parent ) {
        this.parent = _parent;
    }

    /**
     * IF the given element contains a reference to an external resource,
     * return its URL.
     *
     * @param nsURI
     *      Namespace URI of the current element
     * @param localName
     *      Local name of the current element
     * @return
     *      It's OK to return a relative URL.
     */
    protected abstract String findExternalResource( String nsURI, String localName, Attributes atts);

    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
        throws SAXException {
        super.startElement(namespaceURI, localName, qName, atts);

        String relativeRef = findExternalResource(namespaceURI,localName,atts);
        if(relativeRef==null)   return; // non found

        try {
            // absolutize URL.
            assert locator != null;
            String lsi = locator.getSystemId();
            String ref;
            if (lsi.startsWith("jar:")) {
                    int bangIdx = lsi.indexOf('!');
                    if (bangIdx > 0) {
                            ref = new URL(new URL(lsi), relativeRef).toString();
                    } else
                            ref = relativeRef;
            } else
                    ref = new URI(lsi).resolve(new URI(relativeRef)).toString();

            // then parse this schema as well,
            // but don't mark this document as a root.
            parent.parse(ref,false);
        } catch( URISyntaxException e ) {
            SAXParseException spe = new SAXParseException2(
                    WsdlMessages.ABSTRACT_REFERENCE_FINDER_IMPL_UNABLE_TO_PARSE(relativeRef,e.getMessage()),
                locator, e );

            fatalError(spe);
            throw spe;
        } catch( IOException e ) {
            SAXParseException spe = new SAXParseException2(
                WsdlMessages.ABSTRACT_REFERENCE_FINDER_IMPL_UNABLE_TO_PARSE(relativeRef,e.getMessage()),
                locator, e );

            fatalError(spe);
            throw spe;
        }
    }

    private Locator locator;

    @Override
    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }
}

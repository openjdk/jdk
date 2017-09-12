/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime;

import com.sun.istack.internal.FinalArrayList;
import com.sun.istack.internal.SAXException2;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

/**
 * Receives SAX2 events and send the equivalent events to
 * {@link XMLSerializer}
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class ContentHandlerAdaptor extends DefaultHandler {

    /** Stores newly declared prefix-URI mapping. */
    private final FinalArrayList<String> prefixMap = new FinalArrayList<String>();

    /** Events will be sent to this object. */
    private final XMLSerializer serializer;

    private final StringBuffer text = new StringBuffer();


    ContentHandlerAdaptor( XMLSerializer _serializer ) {
        this.serializer = _serializer;
    }

    public void startDocument() {
        prefixMap.clear();
    }

    public void startPrefixMapping(String prefix, String uri) {
        prefixMap.add(prefix);
        prefixMap.add(uri);
    }

    private boolean containsPrefixMapping(String prefix, String uri) {
        for( int i=0; i<prefixMap.size(); i+=2 ) {
            if(prefixMap.get(i).equals(prefix)
            && prefixMap.get(i+1).equals(uri))
                return true;
        }
        return false;
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
        throws SAXException {
        try {
            flushText();

            int len = atts.getLength();

            String p = getPrefix(qName);

            // is this prefix going to be declared on this element?
            if(containsPrefixMapping(p,namespaceURI))
                serializer.startElementForce(namespaceURI,localName,p,null);
            else
                serializer.startElement(namespaceURI,localName, p,null);

            // declare namespace events
            for (int i = 0; i < prefixMap.size(); i += 2) {
                // forcibly set this binding, instead of using declareNsUri.
                // this guarantees that namespaces used in DOM will show up
                // as-is in the marshalled output (instead of reassigned to something else,
                // which may happen if you'd use declareNsUri.)
                serializer.getNamespaceContext().force(
                        prefixMap.get(i + 1), prefixMap.get(i));
            }

            // make sure namespaces needed by attributes are bound
            for( int i=0; i<len; i++ ) {
                String qname = atts.getQName(i);
                if(qname.startsWith("xmlns") || atts.getURI(i).length() == 0)
                    continue;
                String prefix = getPrefix(qname);

                serializer.getNamespaceContext().declareNamespace(
                    atts.getURI(i), prefix, true );
            }

            serializer.endNamespaceDecls(null);
            // fire attribute events
            for( int i=0; i<len; i++ ) {
                // be defensive.
                if(atts.getQName(i).startsWith("xmlns"))
                    continue;
                serializer.attribute( atts.getURI(i), atts.getLocalName(i), atts.getValue(i));
            }
            prefixMap.clear();
            serializer.endAttributes();
        } catch (IOException e) {
            throw new SAXException2(e);
        } catch (XMLStreamException e) {
            throw new SAXException2(e);
        }
    }

    private String getPrefix(String qname) {
        int idx = qname.indexOf(':');
        String prefix = (idx == -1) ? "" : qname.substring(0, idx);
        return prefix;
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        try {
            flushText();
            serializer.endElement();
        } catch (IOException e) {
            throw new SAXException2(e);
        } catch (XMLStreamException e) {
            throw new SAXException2(e);
        }
    }

    private void flushText() throws SAXException, IOException, XMLStreamException {
        if( text.length()!=0 ) {
            serializer.text(text.toString(),null);
            text.setLength(0);
        }
    }

    public void characters(char[] ch, int start, int length) {
        text.append(ch,start,length);
    }
}

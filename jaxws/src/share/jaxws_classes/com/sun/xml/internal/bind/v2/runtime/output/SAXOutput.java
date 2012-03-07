/*
 * Copyright (c) 1997, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.bind.v2.runtime.output;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.util.AttributesImpl;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.LocatorImpl;

/**
 * {@link XmlOutput} implementation that writes to SAX {@link ContentHandler}.
 *
 * @author Kohsuke Kawaguchi
 */
public class SAXOutput extends XmlOutputAbstractImpl {
    protected final ContentHandler out;

    public SAXOutput(ContentHandler out) {
        this.out = out;
        out.setDocumentLocator(new LocatorImpl());
    }

    private String elementNsUri,elementLocalName,elementQName;

    private char[] buf = new char[256];

    private final AttributesImpl atts = new AttributesImpl();


    // not called if we are generating fragments
    @Override
    public void startDocument(XMLSerializer serializer, boolean fragment, int[] nsUriIndex2prefixIndex, NamespaceContextImpl nsContext) throws SAXException, IOException, XMLStreamException {
        super.startDocument(serializer, fragment,nsUriIndex2prefixIndex,nsContext);
        if(!fragment)
            out.startDocument();
    }

    public void endDocument(boolean fragment) throws SAXException, IOException, XMLStreamException {
        if(!fragment)
            out.endDocument();
        super.endDocument(fragment);
    }

    public void beginStartTag(int prefix, String localName) {
        elementNsUri = nsContext.getNamespaceURI(prefix);
        elementLocalName = localName;
        elementQName = getQName(prefix,localName);
        atts.clear();
    }

    public void attribute(int prefix, String localName, String value) {
        String qname;
        String nsUri;
        if(prefix==-1) {
            nsUri = "";
            qname = localName;
        } else {
            nsUri = nsContext.getNamespaceURI(prefix);
            String p = nsContext.getPrefix(prefix);
            if(p.length()==0)
                // this is more likely a bug in the application code (NamespacePrefixMapper implementation)
                // this only happens when it tries to assign "" prefix to a non-"" URI,
                // which is by itself violation of namespace rec. But let's just be safe.
                // See http://forums.java.net/jive/thread.jspa?messageID=212598#212598
                qname = localName;
            else
                qname = p +':'+localName;
        }
        atts.addAttribute( nsUri, localName, qname, "CDATA", value );
    }

    public void endStartTag() throws SAXException {
        NamespaceContextImpl.Element ns = nsContext.getCurrent();
        if(ns!=null) {
            int sz = ns.count();
            for( int i=0; i<sz; i++ ) {
                String p = ns.getPrefix(i);
                String uri = ns.getNsUri(i);
                if(uri.length()==0 && ns.getBase()==1)
                    continue;   // no point in defining xmlns='' on the root
                out.startPrefixMapping(p,uri);
            }
        }
        out.startElement(elementNsUri,elementLocalName,elementQName,atts);
    }

    public void endTag(int prefix, String localName) throws SAXException {
        out.endElement(
            nsContext.getNamespaceURI(prefix),
            localName,
            getQName(prefix, localName)
        );

        NamespaceContextImpl.Element ns = nsContext.getCurrent();
        if(ns!=null) {
            int sz = ns.count();
            for( int i=sz-1; i>=0; i-- ) {
                String p = ns.getPrefix(i);
                String uri = ns.getNsUri(i);
                if(uri.length()==0 && ns.getBase()==1)
                    continue;   // no point in definint xmlns='' on the root
                out.endPrefixMapping(p);
            }
        }
    }

    private String getQName(int prefix, String localName) {
        String qname;
        String p = nsContext.getPrefix(prefix);
        if(p.length()==0)
            qname = localName;
        else
            qname = p+':'+localName;
        return qname;
    }

    public void text(String value, boolean needsSP) throws IOException, SAXException, XMLStreamException {
        int vlen = value.length();
        if(buf.length<=vlen) {
            buf = new char[Math.max(buf.length*2,vlen+1)];
        }
        if(needsSP) {
            value.getChars(0,vlen,buf,1);
            buf[0] = ' ';
        } else {
            value.getChars(0,vlen,buf,0);
        }
        out.characters(buf,0,vlen+(needsSP?1:0));
    }

    public void text(Pcdata value, boolean needsSP) throws IOException, SAXException, XMLStreamException {
        int vlen = value.length();
        if(buf.length<=vlen) {
            buf = new char[Math.max(buf.length*2,vlen+1)];
        }
        if(needsSP) {
            value.writeTo(buf,1);
            buf[0] = ' ';
        } else {
            value.writeTo(buf,0);
        }
        out.characters(buf,0,vlen+(needsSP?1:0));
    }
}

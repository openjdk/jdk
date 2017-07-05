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

package com.sun.xml.internal.bind.v2.runtime.output;

import java.io.IOException;

import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.stream.XMLStreamException;

import com.sun.xml.internal.bind.v2.WellKnownNamespace;
import com.sun.xml.internal.bind.v2.runtime.Name;
import com.sun.xml.internal.bind.v2.runtime.XMLSerializer;
import com.sun.xml.internal.bind.v2.runtime.unmarshaller.Base64Data;

import org.xml.sax.SAXException;

/**
 * {@link XmlOutput} decorator that supports MTOM.
 *
 * @author Kohsuke Kawaguchi
 */
public final class MTOMXmlOutput extends XmlOutputAbstractImpl {

    private final XmlOutput next;

    /**
     * Remembers the last namespace URI and local name so that we can pass them to
     * {@link AttachmentMarshaller}.
     */
    private String nsUri,localName;

    public MTOMXmlOutput(XmlOutput next) {
        this.next = next;
    }

    public void startDocument(XMLSerializer serializer, boolean fragment, int[] nsUriIndex2prefixIndex, NamespaceContextImpl nsContext) throws IOException, SAXException, XMLStreamException {
        super.startDocument(serializer,fragment,nsUriIndex2prefixIndex, nsContext);
        next.startDocument(serializer, fragment, nsUriIndex2prefixIndex, nsContext);
    }

    public void endDocument(boolean fragment) throws IOException, SAXException, XMLStreamException {
        next.endDocument(fragment);
        super.endDocument(fragment);
    }

    public void beginStartTag(Name name) throws IOException, XMLStreamException {
        next.beginStartTag(name);
        this.nsUri = name.nsUri;
        this.localName = name.localName;
    }

    public void beginStartTag(int prefix, String localName) throws IOException, XMLStreamException {
        next.beginStartTag(prefix, localName);
        this.nsUri = nsContext.getNamespaceURI(prefix);
        this.localName = localName;
    }

    public void attribute( Name name, String value ) throws IOException, XMLStreamException {
        next.attribute(name, value);
    }

    public void attribute( int prefix, String localName, String value ) throws IOException, XMLStreamException {
        next.attribute(prefix, localName, value);
    }

    public void endStartTag() throws IOException, SAXException {
        next.endStartTag();
    }

    public void endTag(Name name) throws IOException, SAXException, XMLStreamException {
        next.endTag(name);
    }

    public void endTag(int prefix, String localName) throws IOException, SAXException, XMLStreamException {
        next.endTag(prefix, localName);
    }

    public void text( String value, boolean needsSeparatingWhitespace ) throws IOException, SAXException, XMLStreamException {
        next.text(value,needsSeparatingWhitespace);
    }

    public void text( Pcdata value, boolean needsSeparatingWhitespace ) throws IOException, SAXException, XMLStreamException {
        if(value instanceof Base64Data && !serializer.getInlineBinaryFlag()) {
            Base64Data b64d = (Base64Data) value;
            String cid;
            if(b64d.hasData())
                cid = serializer.attachmentMarshaller.addMtomAttachment(
                                b64d.get(),0,b64d.getDataLen(),b64d.getMimeType(),nsUri,localName);
            else
                cid = serializer.attachmentMarshaller.addMtomAttachment(
                    b64d.getDataHandler(),nsUri,localName);

            if(cid!=null) {
                nsContext.getCurrent().push();
                int prefix = nsContext.declareNsUri(WellKnownNamespace.XOP,"xop",false);
                beginStartTag(prefix,"Include");
                attribute(-1,"href",cid);
                endStartTag();
                endTag(prefix,"Include");
                nsContext.getCurrent().pop();
                return;
            }
        }
        next.text(value, needsSeparatingWhitespace);
    }
}

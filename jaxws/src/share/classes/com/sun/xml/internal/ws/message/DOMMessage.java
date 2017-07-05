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

package com.sun.xml.internal.ws.message;

import com.sun.istack.internal.FragmentContentHandler;
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.bind.unmarshaller.DOMScanner;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.streaming.DOMStreamReader;
import com.sun.xml.internal.ws.util.DOMUtil;
import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.ws.WebServiceException;

/**
 * {@link Message} backed by a DOM {@link Element} that represents the payload.
 *
 * @author Kohsuke Kawaguchi
 */
public final class DOMMessage extends AbstractMessageImpl {
    private HeaderList headers;
    private final Element payload;

    public DOMMessage(SOAPVersion ver, Element payload) {
        this(ver,null,payload);
    }

    public DOMMessage(SOAPVersion ver, HeaderList headers, Element payload) {
        super(ver);
        this.headers = headers;
        this.payload = payload;
        assert payload!=null;
    }

    /**
     * This constructor is a convenience and called by the {@link #copy}
     */
    private DOMMessage(DOMMessage that) {
        super(that);
        this.headers = HeaderList.copy(that.headers);
        this.payload = that.payload;
    }

    public boolean hasHeaders() {
        return getHeaders().size() > 0;
    }

    public HeaderList getHeaders() {
        if (headers == null)
            headers = new HeaderList();

        return headers;
    }

    public String getPayloadLocalPart() {
        return payload.getLocalName();
    }

    public String getPayloadNamespaceURI() {
        return payload.getNamespaceURI();
    }

    public boolean hasPayload() {
        return true;
    }

    public Source readPayloadAsSource() {
        return new DOMSource(payload);
    }

    public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        if(hasAttachments())
            unmarshaller.setAttachmentUnmarshaller(new AttachmentUnmarshallerImpl(getAttachments()));
        try {
            return (T)unmarshaller.unmarshal(payload);
        } finally{
            unmarshaller.setAttachmentUnmarshaller(null);
        }
    }

    public <T> T readPayloadAsJAXB(Bridge<T> bridge) throws JAXBException {
        return bridge.unmarshal(payload,
            hasAttachments()? new AttachmentUnmarshallerImpl(getAttachments()) : null);
    }

    public XMLStreamReader readPayload() throws XMLStreamException {
        DOMStreamReader dss = new DOMStreamReader();
        dss.setCurrentNode(payload);
        dss.nextTag();
        assert dss.getEventType()==XMLStreamReader.START_ELEMENT;
        return dss;
    }

    public void writePayloadTo(XMLStreamWriter sw) {
        try {
            if (payload != null)
                DOMUtil.serializeNode(payload, sw);
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        }
    }

    protected void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
        if(fragment)
            contentHandler = new FragmentContentHandler(contentHandler);
        DOMScanner ds = new DOMScanner();
        ds.setContentHandler(contentHandler);
        ds.scan(payload);
    }

    public Message copy() {
        return new DOMMessage(this);
    }
}

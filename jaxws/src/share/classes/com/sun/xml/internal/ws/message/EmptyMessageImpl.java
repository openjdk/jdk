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

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.AttachmentSet;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;

/**
 * {@link Message} that has no body.
 *
 * @author Kohsuke Kawaguchi
 */
public class EmptyMessageImpl extends AbstractMessageImpl {

    /**
     * If a message has no payload, it's more likely to have
     * some header, so we create it eagerly here.
     */
    private final HeaderList headers;
    private final AttachmentSet attachmentSet;

    public EmptyMessageImpl(SOAPVersion version) {
        super(version);
        this.headers = new HeaderList();
        this.attachmentSet = new AttachmentSetImpl();
    }

    public EmptyMessageImpl(HeaderList headers, @NotNull AttachmentSet attachmentSet, SOAPVersion version){
        super(version);
        if(headers==null)
            headers = new HeaderList();
        this.attachmentSet = attachmentSet;
        this.headers = headers;
    }

    /**
     * Copy constructor.
     */
    private EmptyMessageImpl(EmptyMessageImpl that) {
        super(that);
        this.headers = new HeaderList(that.headers);
        this.attachmentSet = that.attachmentSet;
    }

    public boolean hasHeaders() {
        return !headers.isEmpty();
    }

    @Override
    public @NotNull AttachmentSet getAttachments() {
        return attachmentSet;
    }

    public HeaderList getHeaders() {
        return headers;
    }

    public String getPayloadLocalPart() {
        return null;
    }

    public String getPayloadNamespaceURI() {
        return null;
    }

    public boolean hasPayload() {
        return false;
    }

    public Source readPayloadAsSource() {
        return null;
    }

    public XMLStreamReader readPayload() throws XMLStreamException {
        return null;
    }

    public void writePayloadTo(XMLStreamWriter sw) throws XMLStreamException {
        // noop
    }

    public void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler, boolean fragment) throws SAXException {
        // noop
    }

    public Message copy() {
        return new EmptyMessageImpl(this);
    }
}

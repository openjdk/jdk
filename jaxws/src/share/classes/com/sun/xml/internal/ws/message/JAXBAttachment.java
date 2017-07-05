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
import com.sun.xml.internal.bind.api.Bridge;
import com.sun.xml.internal.messaging.saaj.util.ByteOutputStream;
import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.util.ASCIIUtility;
import com.sun.xml.internal.ws.util.ByteArrayDataSource;
import java.io.ByteArrayInputStream;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.bind.JAXBException;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.xml.ws.WebServiceException;

/**
 * @author Jitendra Kotamraju
 */
public final class JAXBAttachment implements Attachment, DataSource {

    private final String contentId;
    private final String mimeType;
    private final Object jaxbObject;
    private final Bridge bridge;

    public JAXBAttachment(@NotNull String contentId, Object jaxbObject, Bridge bridge, String mimeType) {
        this.contentId = contentId;
        this.jaxbObject = jaxbObject;
        this.bridge = bridge;
        this.mimeType = mimeType;
    }

    public String getContentId() {
        return contentId;
    }

    public String getContentType() {
        return mimeType;
    }

    public byte[] asByteArray() {
        try {
            return ASCIIUtility.getBytes(asInputStream());
        } catch (IOException e) {
            throw new WebServiceException(e);
        }
    }

    public DataHandler asDataHandler() {
        return new DataHandler(this);
    }

    public Source asSource() {
        return new StreamSource(asInputStream());
    }

    public InputStream asInputStream() {
        ByteOutputStream bos = new ByteOutputStream();
        try {
            writeTo(bos);
        } catch (IOException e) {
            throw new WebServiceException(e);
        }
        return bos.newInputStream();
    }

    public void writeTo(OutputStream os) throws IOException {
        try {
            bridge.marshal(jaxbObject, os, null);
        } catch (JAXBException e) {
            throw new WebServiceException(e);
        }
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        AttachmentPart part = saaj.createAttachmentPart();
        part.setDataHandler(asDataHandler());
        part.setContentId(contentId);
        saaj.addAttachmentPart(part);
    }

    public InputStream getInputStream() throws IOException {
        return asInputStream();
    }

    public OutputStream getOutputStream() throws IOException {
        throw new UnsupportedOperationException();
    }

    public String getName() {
        return null;
    }

}

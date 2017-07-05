/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.message.stream;

import com.sun.xml.internal.ws.api.message.Attachment;
import com.sun.xml.internal.ws.util.ByteArrayDataSource;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import com.sun.xml.internal.ws.encoding.DataSourceStreamingDataHandler;

import javax.activation.DataHandler;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.AttachmentPart;
import javax.xml.soap.SOAPException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import com.sun.xml.internal.org.jvnet.staxex.Base64Data;

/**
 * Attachment created from raw bytes.
 *
 * @author Vivek Pandey
 */
public class StreamAttachment implements Attachment {
    private final String contentId;
    private final String contentType;
    private final ByteArrayBuffer byteArrayBuffer;
    private final byte[] data;
    private final int len;

    public StreamAttachment(ByteArrayBuffer buffer, String contentId, String contentType) {
        this.contentId = contentId;
        this.contentType = contentType;
        this.byteArrayBuffer = buffer;
        this.data = byteArrayBuffer.getRawData();
        this.len = byteArrayBuffer.size();
    }

    public String getContentId() {
        return contentId;
    }

    public String getContentType() {
        return contentType;
    }


    public byte[] asByteArray() {
        //we got to reallocate and give the exact byte[]
        return byteArrayBuffer.toByteArray();
    }

    public DataHandler asDataHandler() {
        return new DataSourceStreamingDataHandler(new ByteArrayDataSource(data,0,len,getContentType()));
    }

    public Source asSource() {
        return new StreamSource(new ByteArrayInputStream(data,0,len));
    }

    public InputStream asInputStream() {
        return byteArrayBuffer.newInputStream();
    }

    public Base64Data asBase64Data(){
        Base64Data base64Data = new Base64Data();
        base64Data.set(data, len, contentType);
        return base64Data;
    }

    public void writeTo(OutputStream os) throws IOException {
        byteArrayBuffer.writeTo(os);
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        AttachmentPart part = saaj.createAttachmentPart();
        part.setRawContentBytes(data,0,len,getContentType());
        part.setContentId(contentId);
        saaj.addAttachmentPart(part);
    }
}

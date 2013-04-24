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

package com.sun.xml.internal.ws.encoding.xml;

import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.api.streaming.XMLStreamWriterFactory;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.WSFeatureList;
import com.sun.xml.internal.ws.encoding.ContentTypeImpl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

public final class XMLCodec implements Codec {
    public static final String XML_APPLICATION_MIME_TYPE = "application/xml";

    public static final String XML_TEXT_MIME_TYPE = "text/xml";

    private static final ContentType contentType = new ContentTypeImpl(XML_TEXT_MIME_TYPE);

//  private final WSBinding binding;
    private WSFeatureList features;

    public XMLCodec(WSFeatureList f) {
//        this.binding = binding;
        features = f;
    }

    public String getMimeType() {
        return XML_APPLICATION_MIME_TYPE;
    }

    public ContentType getStaticContentType(Packet packet) {
        return contentType;
    }

    public ContentType encode(Packet packet, OutputStream out) {
                String encoding = (String) packet.invocationProperties
                .get(XMLConstants.OUTPUT_XML_CHARACTER_ENCODING);

        XMLStreamWriter writer = null;

                if (encoding != null && encoding.length() > 0) {
            writer = XMLStreamWriterFactory.create(out, encoding);
        } else {
            writer = XMLStreamWriterFactory.create(out);
        }

        try {
            if (packet.getMessage().hasPayload()){
                writer.writeStartDocument();
                packet.getMessage().writePayloadTo(writer);
                writer.flush();
            }
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        }
        return contentType;
    }

    public ContentType encode(Packet packet, WritableByteChannel buffer) {
        //TODO: not yet implemented
        throw new UnsupportedOperationException();
    }

    public Codec copy() {
        return this;
    }

    public void decode(InputStream in, String contentType, Packet packet) throws IOException {
        Message message = XMLMessage.create(contentType, in, features);
        packet.setMessage(message);
    }

    public void decode(ReadableByteChannel in, String contentType, Packet packet) {
        // TODO
        throw new UnsupportedOperationException();
    }
}

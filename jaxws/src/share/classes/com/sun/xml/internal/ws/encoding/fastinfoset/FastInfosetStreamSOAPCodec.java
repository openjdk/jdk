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
package com.sun.xml.internal.ws.encoding.fastinfoset;

import com.sun.xml.internal.fastinfoset.stax.StAXDocumentSerializer;
import com.sun.xml.internal.fastinfoset.stax.StAXDocumentParser;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.pipe.StreamSOAPCodec;
import com.sun.xml.internal.ws.message.stream.StreamHeader;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.ws.encoding.ContentTypeImpl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.WebServiceException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;

/**
 * A stream SOAP codec for handling SOAP message infosets to fast
 * infoset documents.
 *
 * <p>
 * This implementation currently defers to {@link StreamSOAPCodec} for the decoding
 * using {@link XMLStreamReader}.
 *
 * @author Paul Sandoz
 */
public abstract class FastInfosetStreamSOAPCodec implements Codec {
    private static final FastInfosetStreamReaderFactory READER_FACTORY = FastInfosetStreamReaderFactory.getInstance();

    private StAXDocumentParser _statefulParser;
    private StAXDocumentSerializer _serializer;

    private final StreamSOAPCodec _soapCodec;

    private final boolean _retainState;

    protected final ContentType _defaultContentType;

    /* package */ FastInfosetStreamSOAPCodec(StreamSOAPCodec soapCodec, SOAPVersion soapVersion, boolean retainState, String mimeType) {
//        _soapCodec = StreamSOAPCodec.create(soapVersion);
        _soapCodec = soapCodec;
        _retainState = retainState;
        _defaultContentType = new ContentTypeImpl(mimeType);
    }

    /* package */ FastInfosetStreamSOAPCodec(FastInfosetStreamSOAPCodec that) {
        this._soapCodec = (StreamSOAPCodec) that._soapCodec.copy();
        this._retainState = that._retainState;
        this._defaultContentType = that._defaultContentType;
    }

    public String getMimeType() {
        return _defaultContentType.getContentType();
    }

    public ContentType getStaticContentType(Packet packet) {
        return getContentType(packet.soapAction);
    }

    public ContentType encode(Packet packet, OutputStream out) {
        if (packet.getMessage() != null) {
            final XMLStreamWriter writer = getXMLStreamWriter(out);
            try {
                packet.getMessage().writeTo(writer);
                writer.flush();
            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
        }
        return getContentType(packet.soapAction);
    }

    public ContentType encode(Packet packet, WritableByteChannel buffer) {
        //TODO: not yet implemented
        throw new UnsupportedOperationException();
    }

    public void decode(InputStream in, String contentType, Packet response) throws IOException {
        response.setMessage(
                _soapCodec.decode(getXMLStreamReader(in)));
    }

    public void decode(ReadableByteChannel in, String contentType, Packet response) {
        throw new UnsupportedOperationException();
    }

    protected abstract StreamHeader createHeader(XMLStreamReader reader, XMLStreamBuffer mark);

    protected abstract ContentType getContentType(String soapAction);

    private XMLStreamWriter getXMLStreamWriter(OutputStream out) {
        if (_serializer != null) {
            _serializer.setOutputStream(out);
            return _serializer;
        } else {
            return _serializer = FastInfosetCodec.createNewStreamWriter(out, _retainState);
        }
    }

    private XMLStreamReader getXMLStreamReader(InputStream in) {
        // If the _retainState is true (FI stateful) then pick up Codec assiciated XMLStreamReader
        if (_retainState) {
            if (_statefulParser != null) {
                _statefulParser.setInputStream(in);
                return _statefulParser;
            } else {
                return _statefulParser = FastInfosetCodec.createNewStreamReader(in, _retainState);
            }
        }

        // Otherwise thread assiciated XMLStreamReader
        return READER_FACTORY.doCreate(null, in, false);
    }

    /**
     * Creates a new {@link FastInfosetStreamSOAPCodec} instance.
     *
     * @param version the SOAP version of the codec.
     * @return a new {@link FastInfosetStreamSOAPCodec} instance.
     */
    public static FastInfosetStreamSOAPCodec create(StreamSOAPCodec soapCodec, SOAPVersion version) {
        return create(soapCodec, version, false);
    }

    /**
     * Creates a new {@link FastInfosetStreamSOAPCodec} instance.
     *
     * @param version the SOAP version of the codec.
     * @param retainState if true the Codec should retain the state of
     *        vocabulary tables for multiple encode/decode invocations.
     * @return a new {@link FastInfosetStreamSOAPCodec} instance.
     */
    public static FastInfosetStreamSOAPCodec create(StreamSOAPCodec soapCodec,
            SOAPVersion version, boolean retainState) {
        if(version==null)
            // this decoder is for SOAP, not for XML/HTTP
            throw new IllegalArgumentException();
        switch(version) {
            case SOAP_11:
                return new FastInfosetStreamSOAP11Codec(soapCodec, retainState);
            case SOAP_12:
                return new FastInfosetStreamSOAP12Codec(soapCodec, retainState);
            default:
                throw new AssertionError();
        }
    }
}

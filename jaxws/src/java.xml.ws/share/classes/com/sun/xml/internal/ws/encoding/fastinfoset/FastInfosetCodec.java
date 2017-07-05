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

package com.sun.xml.internal.ws.encoding.fastinfoset;

import com.sun.xml.internal.fastinfoset.stax.StAXDocumentSerializer;
import com.sun.xml.internal.fastinfoset.stax.StAXDocumentParser;
import com.sun.xml.internal.fastinfoset.vocab.ParserVocabulary;
import com.sun.xml.internal.fastinfoset.vocab.SerializerVocabulary;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.ContentType;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.encoding.ContentTypeImpl;
import java.io.BufferedInputStream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.ws.WebServiceException;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;
import com.sun.xml.internal.org.jvnet.fastinfoset.FastInfosetSource;

/**
 * A codec for encoding/decoding XML infosets to/from fast
 * infoset documents.
 *
 * @author Paul Sandoz
 */
public class FastInfosetCodec implements Codec {
    private static final int DEFAULT_INDEXED_STRING_SIZE_LIMIT = 32;
    private static final int DEFAULT_INDEXED_STRING_MEMORY_LIMIT = 4 * 1024 * 1024; //4M limit

    private StAXDocumentParser _parser;

    private StAXDocumentSerializer _serializer;

    private final boolean _retainState;

    private final ContentType _contentType;

    /* package */ FastInfosetCodec(boolean retainState) {
        _retainState = retainState;
        _contentType = (retainState) ? new ContentTypeImpl(FastInfosetMIMETypes.STATEFUL_INFOSET) :
            new ContentTypeImpl(FastInfosetMIMETypes.INFOSET);
    }

    public String getMimeType() {
        return _contentType.getContentType();
    }

    public Codec copy() {
        return new FastInfosetCodec(_retainState);
    }

    public ContentType getStaticContentType(Packet packet) {
        return _contentType;
    }

    public ContentType encode(Packet packet, OutputStream out) {
        Message message = packet.getMessage();
        if (message != null && message.hasPayload()) {
            final XMLStreamWriter writer = getXMLStreamWriter(out);
            try {
                writer.writeStartDocument();
                packet.getMessage().writePayloadTo(writer);
                writer.writeEndDocument();
                writer.flush();
            } catch (XMLStreamException e) {
                throw new WebServiceException(e);
            }
        }

        return _contentType;
    }

    public ContentType encode(Packet packet, WritableByteChannel buffer) {
        //TODO: not yet implemented
        throw new UnsupportedOperationException();
    }

    public void decode(InputStream in, String contentType, Packet packet) throws IOException {
        /* Implements similar logic as the XMLMessage.create(String, InputStream).
         * But it's faster, as we know the InputStream has FastInfoset content*/
        Message message;
        in = hasSomeData(in);
        if (in != null) {
            message = Messages.createUsingPayload(new FastInfosetSource(in),
                    SOAPVersion.SOAP_11);
        } else {
            message = Messages.createEmpty(SOAPVersion.SOAP_11);
        }

        packet.setMessage(message);
    }

    public void decode(ReadableByteChannel in, String contentType, Packet response) {
        throw new UnsupportedOperationException();
    }

    private XMLStreamWriter getXMLStreamWriter(OutputStream out) {
        if (_serializer != null) {
            _serializer.setOutputStream(out);
            return _serializer;
        } else {
            return _serializer = createNewStreamWriter(out, _retainState);
        }
    }

    /**
     * Creates a new {@link FastInfosetCodec} instance.
     *
     * @return a new {@link FastInfosetCodec} instance.
     */
    public static FastInfosetCodec create() {
        return create(false);
    }

    /**
     * Creates a new {@link FastInfosetCodec} instance.
     *
     * @param retainState if true the Codec should retain the state of
     *        vocabulary tables for multiple encode/decode invocations.
     * @return a new {@link FastInfosetCodec} instance.
     */
    public static FastInfosetCodec create(boolean retainState) {
        return new FastInfosetCodec(retainState);
    }

    /**
     * Create a new (@link StAXDocumentSerializer} instance.
     *
     * @param in the OutputStream to serialize to.
     * @param retainState if true the serializer should retain the state of
     *        vocabulary tables for multiple serializations.
     * @return a new {@link StAXDocumentSerializer} instance.
     */
    /* package */ static StAXDocumentSerializer createNewStreamWriter(OutputStream out, boolean retainState) {
        return createNewStreamWriter(out, retainState, DEFAULT_INDEXED_STRING_SIZE_LIMIT, DEFAULT_INDEXED_STRING_MEMORY_LIMIT);
    }

    /**
     * Create a new (@link StAXDocumentSerializer} instance.
     *
     * @param in the OutputStream to serialize to.
     * @param retainState if true the serializer should retain the state of
     *        vocabulary tables for multiple serializations.
     * @return a new {@link StAXDocumentSerializer} instance.
     */
    /* package */ static StAXDocumentSerializer createNewStreamWriter(OutputStream out,
            boolean retainState, int indexedStringSizeLimit, int stringsMemoryLimit) {
        StAXDocumentSerializer serializer = new StAXDocumentSerializer(out);
        if (retainState) {
            /**
             * Create a serializer vocabulary external to the serializer.
             * This will ensure that the vocabulary will never be cleared
             * for each serialization and will be retained (and will grow)
             * for each serialization
             */
            SerializerVocabulary vocabulary = new SerializerVocabulary();
            serializer.setVocabulary(vocabulary);
            serializer.setMinAttributeValueSize(0);
            serializer.setMaxAttributeValueSize(indexedStringSizeLimit);
            serializer.setMinCharacterContentChunkSize(0);
            serializer.setMaxCharacterContentChunkSize(indexedStringSizeLimit);
            serializer.setAttributeValueMapMemoryLimit(stringsMemoryLimit);
            serializer.setCharacterContentChunkMapMemoryLimit(stringsMemoryLimit);
        }
        return serializer;
    }

    /**
     * Create a new (@link StAXDocumentParser} instance.
     *
     * @param in the InputStream to parse from.
     * @param retainState if true the parser should retain the state of
     *        vocabulary tables for multiple parses.
     * @return a new {@link StAXDocumentParser} instance.
     */
    /* package */ static StAXDocumentParser createNewStreamReader(InputStream in, boolean retainState) {
        StAXDocumentParser parser = new StAXDocumentParser(in);
        parser.setStringInterning(true);
        if (retainState) {
            /**
             * Create a parser vocabulary external to the parser.
             * This will ensure that the vocabulary will never be cleared
             * for each parse and will be retained (and will grow)
             * for each parse.
             */
            ParserVocabulary vocabulary = new ParserVocabulary();
            parser.setVocabulary(vocabulary);
        }
        return parser;
    }

    /**
     * Create a new (@link StAXDocumentParser} recyclable instance.
     *
     * @param in the InputStream to parse from.
     * @param retainState if true the parser should retain the state of
     *        vocabulary tables for multiple parses.
     * @return a new recyclable {@link StAXDocumentParser} instance.
     */
    /* package */ static StAXDocumentParser createNewStreamReaderRecyclable(InputStream in, boolean retainState) {
        StAXDocumentParser parser = new FastInfosetStreamReaderRecyclable(in);
        parser.setStringInterning(true);
        parser.setForceStreamClose(true);
        if (retainState) {
            /**
             * Create a parser vocabulary external to the parser.
             * This will ensure that the vocabulary will never be cleared
             * for each parse and will be retained (and will grow)
             * for each parse.
             */
            ParserVocabulary vocabulary = new ParserVocabulary();
            parser.setVocabulary(vocabulary);
        }
        return parser;
    }

    /**
     * Method is copied from com.sun.xml.internal.ws.encoding.xml.XMLMessage
     * @TODO method should be public in some util package?
     *
     * Finds if the stream has some content or not
     *
     * @return null if there is no data
     *         else stream to be used
     */
    private static InputStream hasSomeData(InputStream in) throws IOException {
        if (in != null) {
            if (in.available() < 1) {
                if (!in.markSupported()) {
                    in = new BufferedInputStream(in);
                }
                in.mark(1);
                if (in.read() != -1) {
                    in.reset();
                } else {
                    in = null;          // No data
                }
            }
        }
        return in;
    }
}

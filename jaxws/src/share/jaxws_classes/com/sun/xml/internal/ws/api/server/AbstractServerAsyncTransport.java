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

package com.sun.xml.internal.ws.api.server;

import com.oracle.webservices.internal.api.message.PropertySet;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.util.Pool;

import java.io.IOException;


/**
 * Partial server side async transport implementation. It manages pooling of
 * {@link Codec} and other details.
 *
 * @author Jitendra Kotamraju
 */
public abstract class AbstractServerAsyncTransport<T> {

    private final WSEndpoint endpoint;
    private final CodecPool codecPool;

    /**
     * {@link WSEndpoint#setExecutor} should be called before creating the
     * transport
     *
     * @param endpoint webservices requests are directed towards this endpoint
     */
    public AbstractServerAsyncTransport(WSEndpoint endpoint) {
        this.endpoint = endpoint;
        codecPool = new CodecPool(endpoint);
    }

    /**
     * decodes the transport data to Packet
     *
     * @param connection that carries the web service request
     * @param codec for encoding/decoding {@link Message}
     * @return decoded {@link Packet}
     * @throws IOException if an i/o error happens while encoding/decoding
     */
    protected Packet decodePacket(T connection, @NotNull Codec codec) throws IOException {
        Packet packet = new Packet();
        packet.acceptableMimeTypes = getAcceptableMimeTypes(connection);
        packet.addSatellite(getPropertySet(connection));
        packet.transportBackChannel = getTransportBackChannel(connection);
        return packet;
    }

    /**
     * Encodes the {@link Packet} to infoset and writes on the connection.
     *
     * @param connection that carries the web service request
     * @param packet that needs to encoded to infoset
     * @param codec that does the encoding of Packet
     * @throws IOException if an i/o error happens while encoding/decoding
     */
    protected abstract void encodePacket(T connection, @NotNull Packet packet, @NotNull Codec codec) throws IOException;

    /**
     * If the request has Accept header, return that value
     *
     * @param connection that carries the web service request
     * @return Accept MIME types
     */
    protected abstract @Nullable String getAcceptableMimeTypes(T connection);

    /**
     * {@link TransportBackChannel} used by jax-ws runtime to close the connection
     * while the processing of the request is still continuing. In oneway HTTP case, a
     * response code needs to be sent before invoking the endpoint.
     *
     * @param connection that carries the web service request
     * @return TransportBackChannel instance using the connection
     */
    protected abstract @Nullable TransportBackChannel getTransportBackChannel(T connection);

    /**
     * If there are any properties associated with the connection, those will
     * be added to {@link Packet}
     *
     * @param connection that carries the web service request
     * @return {@link PropertySet} for the connection
     */
    protected abstract @NotNull PropertySet getPropertySet(T connection);

    /**
     * Return a {@link WebServiceContextDelegate} using the underlying connection.
     *
     * @param connection that carries the web service request
     * @return non-null WebServiceContextDelegate instance
     */
    protected abstract @NotNull WebServiceContextDelegate getWebServiceContextDelegate(T connection);

    /**
     * Reads and decodes infoset from the connection and invokes the endpoints. The
     * response is encoded and written to the connection. The response could be
     * written using a different thread.
     *
     * @param connection that carries the web service request
     * @throws IOException if an i/o error happens while encoding/decoding
     */
    protected void handle(final T connection) throws IOException {
        final Codec codec = codecPool.take();
        Packet request = decodePacket(connection, codec);
        if (!request.getMessage().isFault()) {
            endpoint.schedule(request, new WSEndpoint.CompletionCallback() {
                public void onCompletion(@NotNull Packet response) {
                    try {
                        encodePacket(connection, response, codec);
                    } catch(IOException ioe) {
                        ioe.printStackTrace();
                    }
                    codecPool.recycle(codec);
                }
            });
        }
    }

    private static final class CodecPool extends Pool<Codec> {
        WSEndpoint endpoint;

        CodecPool(WSEndpoint endpoint) {
            this. endpoint = endpoint;
        }

        protected Codec create() {
            return endpoint.createCodec();
        }
    }

}

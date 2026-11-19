/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLParameters;

import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.packets.QuicPacket;
import jdk.internal.net.quic.QuicTLSContext;
import jdk.internal.net.quic.QuicVersion;

/**
 * A {@code QuicInstance} represents a common abstraction which is
 * either a {@code QuicClient} or a {@code QuicServer}, or possibly
 * both. It defines the subset of public methods that a
 * {@code QuicEndpoint} and a {@code QuicSelector} need to operate
 * with a quic client, or a quic server;
 */
public interface QuicInstance {

    /**
     * The executor used by this quic instance when a task needs to
     * be offloaded to a separate thread.
     * @implNote This is the HttpClientImpl internal executor.
     * @return the executor used by this QuicClient.
     */
    Executor executor();

    /**
     * {@return  an endpoint to associate with a connection}
     * @throws IOException
     */
    QuicEndpoint getEndpoint() throws IOException;

    /**
     * This method is called when a quic packet that couldn't be attributed
     * to a registered connection is received.
     * @param source   the source address of the datagram
     * @param type     the packet type
     * @param buffer   A buffer positioned at the start of the quic packet
     */
    void unmatchedQuicPacket(SocketAddress source, QuicPacket.HeadersType type, ByteBuffer buffer);

    /**
     * {@return true if the passed version is available for use on this instance, false otherwise}
     */
    boolean isVersionAvailable(QuicVersion quicVersion);

    /**
     * {@return the versions that are available for use on this instance}
     */
    List<QuicVersion> getAvailableVersions();

    /**
     * Instance ID used for debugging traces.
     * @return A string uniquely identifying this instance.
     */
    String instanceId();

    /**
     * Get the QuicTLSContext used by this quic instance.
     * @return the QuicTLSContext used by this quic instance.
     */
    QuicTLSContext getQuicTLSContext();

    QuicTransportParameters getTransportParameters();

    /**
     * The {@link SSLParameters} for this Quic instance.
     * May be {@code null} if no parameters have been specified.
     *
     * @implSpec
     * The default implementation of this method returns {@code null}.
     *
     * @return The {@code SSLParameters} for this quic instance or {@code null}.
     */
    default SSLParameters getSSLParameters() { return new SSLParameters(); }

    /**
     * {@return the configured {@linkplain java.net.StandardSocketOptions#SO_RCVBUF
     * UDP receive buffer} size this instance should use}
     */
    default int getReceiveBufferSize() {
        return Utils.getIntegerNetProperty(
                "jdk.httpclient.quic.receiveBufferSize",
                0 // only set the size if > 0
        );
    }

    /**
     * {@return the configured {@linkplain java.net.StandardSocketOptions#SO_SNDBUF
     * UDP send buffer} size this instance should use}
     */
    default int getSendBufferSize() {
        return Utils.getIntegerNetProperty(
                "jdk.httpclient.quic.sendBufferSize",
                0 // only set the size if > 0
        );
    }

    /**
     * {@return a string describing the given application error code}
     * @param errorCode an application error code
     * @implSpec By default, this method returns a generic
     * string containing the hexadecimal value of the given errorCode.
     * Subclasses built for supporting a given application protocol,
     * such as HTTP/3, may override this method to return more
     * specific names, such as for instance, {@code "H3_REQUEST_CANCELLED"}
     * for {@code 0x010c}.
     * @apiNote This method is typically used for logging and/or debugging
     * purposes, to generate a more user-friendly log message.
     */
    default String appErrorToString(long errorCode) {
        return "ApplicationError(code=0x" + HexFormat.of().toHexDigits(errorCode) + ")";
    }

    default String name() {
        return String.format("%s(%s)", this.getClass().getSimpleName(), instanceId());
    }

}

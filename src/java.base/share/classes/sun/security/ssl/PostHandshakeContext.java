/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A compact implementation of HandshakeContext for post-handshake messages
 */
final class PostHandshakeContext extends HandshakeContext {
    private final static Map<Byte, SSLConsumer> consumers = Map.of(
        SSLHandshake.KEY_UPDATE.id, SSLHandshake.KEY_UPDATE,
        SSLHandshake.NEW_SESSION_TICKET.id, SSLHandshake.NEW_SESSION_TICKET);

    PostHandshakeContext(TransportContext context) throws IOException {
        super(context);

        if (!negotiatedProtocol.useTLS13PlusSpec()) {
            throw conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                "Post-handshake not supported in " + negotiatedProtocol.name);
        }

        this.localSupportedSignAlgs = new ArrayList<SignatureScheme>(
            context.conSession.getLocalSupportedSignatureSchemes());

        handshakeConsumers = new LinkedHashMap<>(consumers);
        handshakeFinished = true;
    }

    @Override
    void kickstart() throws IOException {
        SSLHandshake.kickstart(this);
    }

    @Override
    void dispatch(byte handshakeType, ByteBuffer fragment) throws IOException {
        SSLConsumer consumer = handshakeConsumers.get(handshakeType);
        if (consumer == null) {
            throw conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Unexpected post-handshake message: " +
                            SSLHandshake.nameOf(handshakeType));
        }

        try {
            consumer.consume(this, fragment);
        } catch (UnsupportedOperationException unsoe) {
            throw conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                    "Unsupported post-handshake message: " +
                            SSLHandshake.nameOf(handshakeType), unsoe);
        } catch (BufferUnderflowException | BufferOverflowException be) {
            throw conContext.fatal(Alert.DECODE_ERROR,
                    "Illegal handshake message: " +
                    SSLHandshake.nameOf(handshakeType), be);
        }
    }
}

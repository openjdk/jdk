/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.SSLSessionContext;

import static sun.security.ssl.SSLExtension.CH_SESSION_TICKET;
import static sun.security.ssl.SSLExtension.SH_SESSION_TICKET;

import sun.security.ssl.SSLExtension.ExtensionConsumer;
import sun.security.ssl.SSLExtension.SSLExtensionSpec;
import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.util.HexDumpEncoder;

/**
 * SessionTicketExtension is an implementation of RFC 5077 with some internals
 * that are used for stateless operation in TLS 1.3.
 *
 * {@systemProperty jdk.tls.server.statelessKeyTimeout} can override the default
 * amount of time, in seconds, for how long a randomly-generated key and
 * parameters can be used before being regenerated.  The key material is used
 * to encrypt the stateless session ticket that is sent to the client that will
 * be used during resumption.  Default is 3600 seconds (1 hour)
 *
 */

final class SessionTicketExtension {

    static final HandshakeProducer chNetworkProducer =
            new T12CHSessionTicketProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new T12CHSessionTicketConsumer();
    static final HandshakeProducer shNetworkProducer =
            new T12SHSessionTicketProducer();
    static final ExtensionConsumer shOnLoadConsumer =
            new T12SHSessionTicketConsumer();
    static final HandshakeAbsence shOnLoadAbsence =
            new T12SHSessionTicketOnLoadAbsence();

    static final SSLStringizer steStringizer = new SessionTicketStringizer();
    // No need to compress a ticket if it can fit in a single packet.
    // Besides, small buffers often end up to be larger when compressed.
    static final int MIN_COMPRESS_SIZE = 600;

    // Time in milliseconds until key is changed for encrypting session state
    private static final int TIMEOUT_DEFAULT = 3600 * 1000;
    private static final int keyTimeout;
    private static final int KEYLEN = 256;

    static {
        String s = System.getProperty("jdk.tls.server.statelessKeyTimeout");
        if (s != null) {
            int kt;
            try {
                kt = Integer.parseInt(s) * 1000;  // change to ms
                if (kt < 0 ||
                        kt > NewSessionTicket.MAX_TICKET_LIFETIME) {
                    if (SSLLogger.isOn() &&
                            SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                        SSLLogger.warning("Invalid timeout for " +
                                "jdk.tls.server.statelessKeyTimeout: " +
                                kt + ".  Set to default value " +
                                TIMEOUT_DEFAULT + "sec");
                    }
                    kt = TIMEOUT_DEFAULT;
                }
            } catch (NumberFormatException e) {
                kt = TIMEOUT_DEFAULT;
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                    SSLLogger.warning("Invalid timeout for " +
                            "jdk.tls.server.statelessKeyTimeout: " + s +
                            ".  Set to default value " + TIMEOUT_DEFAULT +
                            "sec");
                }
            }
            keyTimeout = kt;
        } else {
            keyTimeout = TIMEOUT_DEFAULT;
        }
    }

    // Crypto key context for session state.  Used with stateless operation.
    static final class StatelessKey {
        final long timeout;
        final SecretKey key;
        final int num;

        // package-private, used only by SSLContextImpl
        StatelessKey(HandshakeContext hc, int num) {
            SecretKey k = null;
            try {
                KeyGenerator kg = KeyGenerator.getInstance("AES");
                kg.init(KEYLEN, hc.sslContext.getSecureRandom());
                k = kg.generateKey();
            } catch (NoSuchAlgorithmException e) {
                // should not happen;
            }
            key = k;
            timeout = System.currentTimeMillis() + keyTimeout;
            this.num = num;
        }

        // Check if key needs to be changed
        boolean isExpired() {
            return ((System.currentTimeMillis()) > timeout);
        }

        // Check if this key is ready for deletion.
        boolean isInvalid(SSLSessionContext sslSessionContext) {
            int sessionTimeout = sslSessionContext.getSessionTimeout() * 1000;
            return ((System.currentTimeMillis()) > (timeout + sessionTimeout));
        }
    }

    private static final class KeyState {

        // Get a key with a specific key number
        static StatelessKey getKey(HandshakeContext hc, int num)  {
            SSLSessionContextImpl serverCache =
                (SSLSessionContextImpl)hc.sslContext.engineGetServerSessionContext();
            StatelessKey ssk = serverCache.getKey(num);

            if (ssk == null || ssk.isInvalid(serverCache)) {
                return null;
            }
            return ssk;
        }

        // Get the current valid key, this will generate a new key if needed
        static StatelessKey getCurrentKey(HandshakeContext hc) {
            SSLSessionContextImpl serverCache =
                (SSLSessionContextImpl)hc.sslContext.engineGetServerSessionContext();
            return serverCache.getKey(hc);
        }
    }

    /**
     * This class contains the session state that is in the session ticket.
     * Using the key associated with the ticket, the class encrypts and
     * decrypts the data, but does not interpret the data.
     */
    static final class SessionTicketSpec implements SSLExtensionSpec {
        private static final int GCM_TAG_LEN = 128;
        private static final int GCM_IV_LEN = 12;
        ByteBuffer data;
        static final ByteBuffer zero = ByteBuffer.wrap(new byte[0]);

        SessionTicketSpec() {
            data = zero;
        }

        SessionTicketSpec(HandshakeContext hc, byte[] b) throws IOException {
            this(hc, ByteBuffer.wrap(b));
        }

        SessionTicketSpec(HandshakeContext hc,
                ByteBuffer buf) throws IOException {
            if (buf == null) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "SessionTicket buffer too small"));
            }
            if (buf.remaining() > 65536) {
                throw hc.conContext.fatal(Alert.DECODE_ERROR,
                        new SSLProtocolException(
                    "SessionTicket buffer too large. " + buf.remaining()));
            }

            data = buf;
        }

        byte[] encrypt(HandshakeContext hc, SSLSessionImpl session) {
            byte[] encrypted;

            if (!hc.statelessResumption ||
                    !hc.handshakeSession.isStatelessable()) {
                return new byte[0];
            }

            try {
                StatelessKey key = KeyState.getCurrentKey(hc);
                byte[] iv = new byte[GCM_IV_LEN];

                SecureRandom random = hc.sslContext.getSecureRandom();
                random.nextBytes(iv);
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, key.key,
                        new GCMParameterSpec(GCM_TAG_LEN, iv));

                byte[] data = session.write();
                if (data.length == 0) {
                    return data;
                }

                // Compress the session before encryption if needed.
                byte compressed = 0;
                if (data.length >= MIN_COMPRESS_SIZE) {
                    data = compress(data);
                    compressed = 1;
                }

                ByteBuffer aad = ByteBuffer.allocate(Integer.BYTES + 1);
                aad.putInt(key.num).put(compressed);
                c.updateAAD(aad);

                encrypted = c.doFinal(data);
                byte[] result = new byte[encrypted.length + Integer.BYTES +
                        iv.length + 1];
                result[0] = (byte)(key.num >>> 24);
                result[1] = (byte)(key.num >>> 16);
                result[2] = (byte)(key.num >>> 8);
                result[3] = (byte)(key.num);
                System.arraycopy(iv, 0, result, Integer.BYTES, iv.length);
                result[Integer.BYTES + iv.length] = compressed;
                System.arraycopy(encrypted, 0, result,
                        Integer.BYTES + iv.length + 1, encrypted.length);
                return result;
            } catch (Exception e) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine("Encryption failed." + e);
                }
                return new byte[0];
            }
        }

        ByteBuffer decrypt(HandshakeContext hc) {
            int keyID;
            byte[] iv;
            try {
                keyID = data.getInt();
                StatelessKey key = KeyState.getKey(hc, keyID);
                if (key == null) {
                    return null;
                }

                iv = new byte[GCM_IV_LEN];
                data.get(iv);
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.DECRYPT_MODE, key.key,
                        new GCMParameterSpec(GCM_TAG_LEN, iv));

                byte compressed = data.get();
                ByteBuffer aad = ByteBuffer.allocate(Integer.BYTES + 1);
                aad.putInt(keyID).put(compressed);
                c.updateAAD(aad);

                // use getOutputSize to avoid a ShortBufferException
                // from providers that require oversized buffers. See JDK-8368514.
                ByteBuffer out = ByteBuffer.allocate(
                        c.getOutputSize(data.remaining()));
                c.doFinal(data, out);
                out.flip();

                // Decompress the session after decryption if needed.
                if (compressed == 1) {
                    out = decompress(out);
                }

                return out;
            } catch (Exception e) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine("Decryption failed." + e);
                }
            }

            return null;
        }

        private static byte[] compress(byte[] input) throws IOException {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    GZIPOutputStream gos = new GZIPOutputStream(baos)) {
                final int decompressedLen = input.length;
                gos.write(input, 0, decompressedLen);
                gos.finish();

                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine("decompressed bytes: " + decompressedLen
                            + "; compressed bytes: " + baos.size());
                }

                return baos.toByteArray();
            }
        }

        private static ByteBuffer decompress(ByteBuffer input)
                throws IOException {
            final int compressedLen = input.remaining();
            byte[] bytes = new byte[compressedLen];
            input.get(bytes);

            try (GZIPInputStream gis = new GZIPInputStream(
                    new ByteArrayInputStream(bytes))) {
                byte[] out = gis.readAllBytes();

                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine("compressed bytes: " + compressedLen
                            + "; decompressed bytes: " + out.length);
                }

                return ByteBuffer.wrap(out);
            }
        }

        byte[] getEncoded() {
            byte[] out = new byte[data.capacity()];
            data.duplicate().get(out);
            return out;
        }

        @Override
        public String toString() {
            if (data == null) {
                return "<null>";
            }
            if (data.capacity() == 0) {
                return "<empty>";
            }

            MessageFormat messageFormat = new MessageFormat(
                    """
                              "ticket" : '{'
                            {0}
                              '}'""",
                    Locale.ENGLISH);
            HexDumpEncoder hexEncoder = new HexDumpEncoder();

            Object[] messageFields = {
                    Utilities.indent(hexEncoder.encode(data.duplicate()),
                            "    "),
            };

            return messageFormat.format(messageFields);
        }
    }

    static final class SessionTicketStringizer implements SSLStringizer {
        @Override
        public String toString(HandshakeContext hc, ByteBuffer buffer) {
            try {
                return new SessionTicketSpec(hc, buffer).toString();
            } catch (IOException e) {
                return e.getMessage();
            }
        }
    }

    private static final class T12CHSessionTicketProducer
            implements HandshakeProducer {
        T12CHSessionTicketProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {

            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            // If the context does not allow stateless tickets, exit
            if (!((SSLSessionContextImpl)chc.sslContext.
                    engineGetClientSessionContext()).statelessEnabled()) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine("Stateless resumption not supported");
                }
                return null;
            }

            chc.statelessResumption = true;

            // If resumption is not in progress, return an empty value
            if (!chc.isResumption || chc.resumingSession == null
                    || chc.resumingSession.getPskIdentity() == null
                    || chc.resumingSession.getProtocolVersion().useTLS13PlusSpec()) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine("Stateless resumption supported");
                }
                return new byte[0];
            }

            SignatureScheme.updateHandshakeLocalSupportedAlgs(chc);

            return chc.resumingSession.getPskIdentity();
        }

    }

    private static final class T12CHSessionTicketConsumer
            implements ExtensionConsumer {
        T12CHSessionTicketConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
                HandshakeMessage message, ByteBuffer buffer)
                throws IOException {
            ServerHandshakeContext shc = (ServerHandshakeContext) context;

            // Skip if extension is not provided
            if (!shc.sslConfig.isAvailable(CH_SESSION_TICKET)) {
                return;
            }

            // Skip consumption if we are already in stateless resumption
            if (shc.statelessResumption) {
                return;
            }
            // If the context does not allow stateless tickets, exit
            SSLSessionContextImpl cache = (SSLSessionContextImpl)shc.sslContext
                    .engineGetServerSessionContext();
            if (!cache.statelessEnabled()) {
                return;
            }

            // Regardless of session ticket contents, client allows stateless
            shc.statelessResumption = true;

            if (buffer.remaining() == 0) {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine("Client accepts session tickets.");
                }
                return;
            }

            // Parse the extension.
            SessionTicketSpec spec = new SessionTicketSpec(shc, buffer);
            ByteBuffer b = spec.decrypt(shc);
            if (b != null) {
                shc.resumingSession = new SSLSessionImpl(shc, b);
                shc.isResumption = true;
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine("Valid stateless session ticket found");
                }
            } else {
                if (SSLLogger.isOn() &&
                        SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.fine("Invalid stateless session ticket found");
                }
            }
        }
    }


    private static final class T12SHSessionTicketProducer
            implements HandshakeProducer {
        T12SHSessionTicketProducer() {
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) {

            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // If boolean is false, the CH did not have this extension
            if (!shc.statelessResumption) {
                return null;
            }
            // If the client has sent a SessionTicketExtension and stateless
            // is enabled on the server, return an empty message.
            // If the context does not allow stateless tickets, exit
            SSLSessionContextImpl cache = (SSLSessionContextImpl)shc.sslContext
                    .engineGetServerSessionContext();
            if (cache.statelessEnabled()) {
                return new byte[0];
            }

            shc.statelessResumption = false;
            return null;
        }
    }

    private static final class T12SHSessionTicketConsumer
            implements ExtensionConsumer {
        T12SHSessionTicketConsumer() {
        }

        @Override
        public void consume(ConnectionContext context,
                HandshakeMessage message, ByteBuffer buffer)
                throws IOException {
            ClientHandshakeContext chc = (ClientHandshakeContext) context;

            // Skip if extension is not provided
            if (!chc.sslConfig.isAvailable(SH_SESSION_TICKET)) {
                chc.statelessResumption = false;
                return;
            }

            // If the context does not allow stateless tickets, exit
            if (!((SSLSessionContextImpl)chc.sslContext.
                    engineGetClientSessionContext()).statelessEnabled()) {
                chc.statelessResumption = false;
                return;
            }

            SessionTicketSpec spec = new SessionTicketSpec(chc, buffer);
            chc.statelessResumption = true;
        }
    }

    /**
     * The absence processing if a "session_ticket" extension is
     * not present in the ServerHello handshake message.
     */
    private static final class T12SHSessionTicketOnLoadAbsence
            implements HandshakeAbsence {

        @Override
        public void absent(ConnectionContext context,
                HandshakeMessage message) {
            ClientHandshakeContext chc = (ClientHandshakeContext) context;

            // Disable stateless resumption if server doesn't send the extension.
            if (chc.statelessResumption) {
                if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.HANDSHAKE)) {
                    SSLLogger.info(
                            "Server doesn't support stateless resumption");
                }
                chc.statelessResumption = false;
            }
        }
    }
}

/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLHandshakeException;
import sun.security.ssl.PskKeyExchangeModesExtension.PskKeyExchangeMode;
import sun.security.ssl.PskKeyExchangeModesExtension.PskKeyExchangeModesSpec;
import sun.security.ssl.SessionTicketExtension.SessionTicketSpec;
import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.util.HexDumpEncoder;

import static sun.security.ssl.SSLHandshake.NEW_SESSION_TICKET;

/**
 * Pack of the NewSessionTicket handshake message.
 */
final class NewSessionTicket {
    static final int MAX_TICKET_LIFETIME = 604800;  // seconds, 7 days
    static final SSLConsumer handshakeConsumer =
        new T13NewSessionTicketConsumer();
    static final SSLConsumer handshake12Consumer =
        new T12NewSessionTicketConsumer();
    static final SSLProducer t13PosthandshakeProducer =
        new T13NewSessionTicketProducer();
    static final HandshakeProducer handshake12Producer =
        new T12NewSessionTicketProducer();

    /**
     * The NewSessionTicketMessage handshake messages.
     */
    abstract static class NewSessionTicketMessage extends HandshakeMessage {
        int ticketLifetime;
        byte[] ticket = new byte[0];

        NewSessionTicketMessage(HandshakeContext context) {
            super(context);
        }

        @Override
        public SSLHandshake handshakeType() {
            return NEW_SESSION_TICKET;
        }

        // For TLS 1.3 only
        int getTicketAgeAdd() throws IOException {
            throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "TicketAgeAdd not part of RFC 5077.");
        }

        // For TLS 1.3 only
        byte[] getTicketNonce() throws IOException {
            throw handshakeContext.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "TicketNonce not part of RFC 5077.");
        }

        boolean isValid() {
            return (ticket.length > 0);
        }
    }
    /**
     * NewSessionTicket for TLS 1.2 and below (RFC 5077)
     */
    static final class T12NewSessionTicketMessage extends NewSessionTicketMessage {

        T12NewSessionTicketMessage(HandshakeContext context,
                int ticketLifetime, byte[] ticket) {
            super(context);

            this.ticketLifetime = ticketLifetime;
            this.ticket = ticket;
        }

        T12NewSessionTicketMessage(HandshakeContext context,
                ByteBuffer m) throws IOException {

            // RFC5077 struct {
            //     uint32 ticket_lifetime;
            //     opaque ticket<0..2^16-1>;
            // } NewSessionTicket;

            super(context);
            if (m.remaining() < 6) {
                throw context.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Invalid NewSessionTicket message: insufficient data");
            }

            this.ticketLifetime = Record.getInt32(m);
            this.ticket = Record.getBytes16(m);
        }

        @Override
        public int messageLength() {
            return 4 + // ticketLifetime
                    2 + ticket.length;  // len of ticket + ticket
        }

        @Override
        public void send(HandshakeOutStream hos) throws IOException {
            hos.putInt32(ticketLifetime);
            hos.putBytes16(ticket);
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                    """
                            "NewSessionTicket": '{'
                              "ticket_lifetime"      : "{0}",
                              "ticket"               : '{'
                            {1}
                              '}''}'""",
                Locale.ENGLISH);

            HexDumpEncoder hexEncoder = new HexDumpEncoder();
            Object[] messageFields = {
                    ticketLifetime,
                    Utilities.indent(hexEncoder.encode(ticket), "    "),
            };
            return messageFormat.format(messageFields);
        }
    }

    /**
     * NewSessionTicket defined by the TLS 1.3
     */
    static final class T13NewSessionTicketMessage extends NewSessionTicketMessage {
        int ticketAgeAdd;
        byte[] ticketNonce;
        SSLExtensions extensions;

        T13NewSessionTicketMessage(HandshakeContext context,
                int ticketLifetime, SecureRandom generator,
                byte[] ticketNonce, byte[] ticket) {
            super(context);

            this.ticketLifetime = ticketLifetime;
            this.ticketAgeAdd = generator.nextInt();
            this.ticketNonce = ticketNonce;
            this.ticket = ticket;
            this.extensions = new SSLExtensions(this);
        }

        T13NewSessionTicketMessage(HandshakeContext context,
                ByteBuffer m) throws IOException {
            super(context);

            // struct {
            //     uint32 ticket_lifetime;
            //     uint32 ticket_age_add;
            //     opaque ticket_nonce<0..255>;
            //     opaque ticket<1..2^16-1>;
            //     Extension extensions<0..2^16-2>;
            // } NewSessionTicket;

            if (m.remaining() < 14) {
                throw context.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Invalid NewSessionTicket message: insufficient data");
            }

            this.ticketLifetime = Record.getInt32(m);
            this.ticketAgeAdd = Record.getInt32(m);
            this.ticketNonce = Record.getBytes8(m);

            if (m.remaining() < 5) {
                throw context.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Invalid NewSessionTicket message: insufficient ticket" +
                            " data");
            }

            this.ticket = Record.getBytes16(m);
            if (ticket.length == 0) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "No ticket in the NewSessionTicket handshake message");
                }
            }

            if (m.remaining() < 2) {
                throw context.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                    "Invalid NewSessionTicket message: extra data");
            }

            SSLExtension[] supportedExtensions =
                    context.sslConfig.getEnabledExtensions(
                            NEW_SESSION_TICKET);
            this.extensions = new SSLExtensions(this, m, supportedExtensions);
        }

        int getTicketAgeAdd() {
            return ticketAgeAdd;
        }

        byte[] getTicketNonce() {
            return ticketNonce;
        }

        @Override
        public int messageLength() {

            int extLen = extensions.length();
            if (extLen == 0) {
                extLen = 2;     // empty extensions
            }

            return 4 +// ticketLifetime
                    4 + // ticketAgeAdd
                    1 + ticketNonce.length + // len of nonce + nonce
                    2 + ticket.length + // len of ticket + ticket
                    extLen;
        }

        @Override
        public void send(HandshakeOutStream hos) throws IOException {
            hos.putInt32(ticketLifetime);
            hos.putInt32(ticketAgeAdd);
            hos.putBytes8(ticketNonce);
            hos.putBytes16(ticket);

            // Is it an empty extensions?
            if (extensions.length() == 0) {
                hos.putInt16(0);
            } else {
                extensions.send(hos);
            }
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                    """
                            "NewSessionTicket": '{'
                              "ticket_lifetime"      : "{0}",
                              "ticket_age_add"       : "{1}",
                              "ticket_nonce"         : "{2}",
                              "ticket"               : '{'
                            {3}
                              '}'  "extensions"           : [
                            {4}
                              ]
                            '}'""",
                Locale.ENGLISH);

            HexDumpEncoder hexEncoder = new HexDumpEncoder();
            Object[] messageFields = {
                ticketLifetime,
                "<omitted>",    //ticketAgeAdd should not be logged
                Utilities.toHexString(ticketNonce),
                Utilities.indent(hexEncoder.encode(ticket), "    "),
                Utilities.indent(extensions.toString(), "    ")
            };

            return messageFormat.format(messageFields);
        }
    }

    private static SecretKey derivePreSharedKey(CipherSuite.HashAlg hashAlg,
            SecretKey resumptionMasterSecret, byte[] nonce) throws IOException {
        try {
            KDF hkdf = KDF.getInstance(hashAlg.hkdfAlgorithm);

            byte[] hkdfInfo = SSLSecretDerivation.createHkdfInfo(
                    "tls13 resumption".getBytes(), nonce, hashAlg.hashLength);
            // SSLSessionImpl.write() uses the PreSharedKey encoding for
            // the stateless session ticket; use SecretKeySpec instead of opaque
            // Key objects
            return new SecretKeySpec(hkdf.deriveData(
                    HKDFParameterSpec.expandOnly(resumptionMasterSecret,
                    hkdfInfo, hashAlg.hashLength)), "TlsPreSharedKey");
        } catch (GeneralSecurityException gse) {
            throw new SSLHandshakeException("Could not derive PSK", gse);
        }
    }

    private static final
            class T13NewSessionTicketProducer implements SSLProducer {
        // Prevent instantiation of this class.
        private T13NewSessionTicketProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context) throws IOException {
            HandshakeContext hc = (HandshakeContext)context;

            // See note on TransportContext.needHandshakeFinishedStatus.
            //
            // Set to need handshake finished status.  Reset it later if a
            // session ticket get delivered.
            if (hc.conContext.hasDelegatedFinished) {
                // Reset, as the delegated finished case will be handled later.
                hc.conContext.hasDelegatedFinished = false;
                hc.conContext.needHandshakeFinishedStatus = true;
            }

            // The producing happens in server side only.
            if (hc instanceof ServerHandshakeContext) {
                // Is this session resumable?
                if (!hc.handshakeSession.isRejoinable()) {
                    if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.fine("No session ticket produced: " +
                                "session is not resumable");
                    }

                    return null;
                }

                // What's the requested PSK key exchange modes?
                //
                // Note that currently, the NewSessionTicket post-handshake is
                // produced and delivered only in the current handshake context
                // if required.
                PskKeyExchangeModesSpec pkemSpec =
                        (PskKeyExchangeModesSpec) hc.handshakeExtensions.get(
                                SSLExtension.PSK_KEY_EXCHANGE_MODES);
                if (pkemSpec == null ||
                        !pkemSpec.contains(PskKeyExchangeMode.PSK_DHE_KE)) {
                    if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.fine("No session ticket produced: " +
                                "client does not support psk_dhe_ke");
                    }

                    return null;
                }
            } else {     // PostHandshakeContext
                // Check if we have sent a PSK already, then we know it is
                // using an allowable PSK exchange key mode.
                if (!hc.handshakeSession.isPSKable()) {
                    if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.fine("No session ticket produced: " +
                                "No session ticket allowed in this session");
                    }

                    return null;
                }
            }

            // get a new session ID
            SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
                hc.sslContext.engineGetServerSessionContext();
            int sessionTimeoutSeconds = sessionCache.getSessionTimeout();
            if (sessionTimeoutSeconds > MAX_TICKET_LIFETIME) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine("No session ticket produced: " +
                            "session timeout is too long");
                }

                return null;
            }

            // Send NewSessionTickets to the client based
            if (SSLConfiguration.serverNewSessionTicketCount > 0) {
                int i = 0;
                NewSessionTicketMessage nstm;
                while (i < SSLConfiguration.serverNewSessionTicketCount) {
                    nstm = generateNST(hc, sessionCache);
                    if (nstm == null) {
                        break;
                    }
                    nstm.write(hc.handshakeOutput);
                    i++;
                }

                hc.handshakeOutput.flush();
            }
            /*
             * With large NST counts, a client that quickly closes after
             * TLS Finished completes can cause SocketExceptions such as:
             * Windows servers read-side throwing SocketException:
             *   "An established connection was aborted by the software in
             *    your host machine", which relates to error WSAECONNABORTED.
             * A SocketException caused by a "broken pipe" has been observed on
             * other systems.
             * These are very unlikely situations when client and server are on
             * different machines.
             *
             * RFC 8446 does not put requirements when an NST needs to be
             * sent, but it should be sent very soon after TLS Finished for
             * clients that will quickly resume to create more sessions.
             * TLS 1.3 is different from TLS 1.2, there is more data the client
             * should be aware of
             */

            // See note on TransportContext.needHandshakeFinishedStatus.
            //
            // Reset the needHandshakeFinishedStatus flag.  The delivery
            // of this post-handshake message will indicate the FINISHED
            // handshake status.  It is not needed to have a follow-on
            // SSLEngine.wrap() any longer.
            if (hc.conContext.needHandshakeFinishedStatus) {
                hc.conContext.needHandshakeFinishedStatus = false;
            }

            // clean the post handshake context
            hc.conContext.finishPostHandshake();

            // The message has been delivered.
            return null;
        }

        private NewSessionTicketMessage generateNST(HandshakeContext hc,
            SSLSessionContextImpl sessionCache) throws IOException {

            NewSessionTicketMessage nstm;
            SessionId newId = new SessionId(true,
                hc.sslContext.getSecureRandom());

            // construct the PSK and handshake message
            byte[] nonce = hc.handshakeSession.incrTicketNonceCounter();

            SSLSessionImpl sessionCopy =
                new SSLSessionImpl(hc.handshakeSession, newId);
            sessionCopy.setPreSharedKey(derivePreSharedKey(
                hc.negotiatedCipherSuite.hashAlg,
                hc.handshakeSession.getResumptionMasterSecret(), nonce));
            sessionCopy.setPskIdentity(newId.getId());

            // If a stateless ticket is allowed, attempt to make one
            if (hc.statelessResumption &&
                    hc.handshakeSession.isStatelessable()) {
                nstm = new T13NewSessionTicketMessage(hc,
                        sessionCache.getSessionTimeout(),
                        hc.sslContext.getSecureRandom(),
                        nonce,
                        new SessionTicketSpec().encrypt(hc, sessionCopy));
                // If ticket construction failed, switch to session cache
                if (!nstm.isValid()) {
                    hc.statelessResumption = false;
                } else {
                    if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                        SSLLogger.fine("Produced NewSessionTicket stateless " +
                            "post-handshake message", nstm);
                    }
                }
                return nstm;
            }

            // If a session cache ticket is being used, make one
            if (!hc.statelessResumption ||
                    !hc.handshakeSession.isStatelessable()) {
                nstm = new T13NewSessionTicketMessage(hc,
                    sessionCache.getSessionTimeout(),
                    hc.sslContext.getSecureRandom(), nonce,
                    newId.getId());
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine("Produced NewSessionTicket " +
                        "post-handshake message", nstm);
                }

                // create and cache the new session
                // The new session must be a child of the existing session so
                // they will be invalidated together, etc.
                hc.handshakeSession.addChild(sessionCopy);
                sessionCopy.setTicketAgeAdd(nstm.getTicketAgeAdd());
                sessionCache.put(sessionCopy);
                return nstm;
            }

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("No NewSessionTicket created");
            }

            return null;
        }
    }

    /**
     * The "NewSessionTicket" handshake message producer for RFC 5077
     */
    private static final class T12NewSessionTicketProducer
            implements HandshakeProducer {

        // Prevent instantiation of this class.
        private T12NewSessionTicketProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {

            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // Are new tickets allowed?  If so, is this session resumable?
            if (SSLConfiguration.serverNewSessionTicketCount == 0 ||
                !shc.handshakeSession.isRejoinable()) {
                return null;
            }

            // get a new session ID
            SessionId newId = shc.handshakeSession.getSessionId();

            SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
                    shc.sslContext.engineGetServerSessionContext();
            int sessionTimeoutSeconds = sessionCache.getSessionTimeout();
            if (sessionTimeoutSeconds > MAX_TICKET_LIFETIME) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "Session timeout is too long. No ticket sent.");
                }
                return null;
            }

            SSLSessionImpl sessionCopy =
                    new SSLSessionImpl(shc.handshakeSession, newId);
            sessionCopy.setPskIdentity(newId.getId());

            NewSessionTicketMessage nstm = new T12NewSessionTicketMessage(shc,
                    sessionTimeoutSeconds,
                    new SessionTicketSpec().encrypt(shc, sessionCopy));
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                    "Produced NewSessionTicket stateless handshake message",
                    nstm);
            }

            // Output the handshake message.
            nstm.write(shc.handshakeOutput);
            shc.handshakeOutput.flush();

            // The message has been delivered.
            return null;
        }
    }

    private static final
    class T13NewSessionTicketConsumer implements SSLConsumer {
        // Prevent instantiation of this class.
        private T13NewSessionTicketConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {

            // Note: Although the resumption master secret depends on the
            // client's second flight, servers which do not request client
            // authentication MAY compute the remainder of the transcript
            // independently and then send a NewSessionTicket immediately
            // upon sending its Finished rather than waiting for the client
            // Finished.
            //
            // The consuming happens in client side only and is received after
            // the server's Finished message with PostHandshakeContext.

            HandshakeContext hc = (HandshakeContext)context;
            NewSessionTicketMessage nstm =
                    new T13NewSessionTicketMessage(hc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Consuming NewSessionTicket message", nstm);
            }

            SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
                    hc.sslContext.engineGetClientSessionContext();

            // discard tickets with timeout 0
            if (nstm.ticketLifetime <= 0 ||
                nstm.ticketLifetime > MAX_TICKET_LIFETIME) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Discarding NewSessionTicket with lifetime " +
                            nstm.ticketLifetime, nstm);
                }
                return;
            }

            if (sessionCache.getSessionTimeout() > MAX_TICKET_LIFETIME) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "Session cache lifetime is too long. " +
                        "Discarding ticket.");
                }
                return;
            }

            SSLSessionImpl sessionToSave = hc.conContext.conSession;
            SecretKey resumptionMasterSecret =
                    sessionToSave.getResumptionMasterSecret();
            if (resumptionMasterSecret == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Session has no resumption master secret. " +
                            "Ignoring ticket.");
                }
                return;
            }

            // derive the PSK
            SecretKey psk = derivePreSharedKey(
                    sessionToSave.getSuite().hashAlg,
                    resumptionMasterSecret, nstm.getTicketNonce());

            // create and cache the new session
            // The new session must be a child of the existing session so
            // they will be invalidated together, etc.
            SessionId newId =
                    new SessionId(true, hc.sslContext.getSecureRandom());
            SSLSessionImpl sessionCopy = new SSLSessionImpl(sessionToSave,
                    newId);
            sessionToSave.addChild(sessionCopy);
            sessionCopy.setPreSharedKey(psk);
            sessionCopy.setTicketAgeAdd(nstm.getTicketAgeAdd());
            sessionCopy.setPskIdentity(nstm.ticket);
            sessionCache.put(sessionCopy, sessionCopy.isPSK());

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("MultiNST PSK (Server): " +
                    Utilities.toHexString(Arrays.copyOf(nstm.ticket, 16)));
            }

            // clean the post handshake context
            hc.conContext.finishPostHandshake();
        }
    }

    /* TLS 1.2 spec does not specify multiple NST behavior.*/
    private static final
    class T12NewSessionTicketConsumer implements SSLConsumer {
        // Prevent instantiation of this class.
        private T12NewSessionTicketConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {

            HandshakeContext hc = (HandshakeContext)context;
            hc.handshakeConsumers.remove(NEW_SESSION_TICKET.id);

            NewSessionTicketMessage nstm = new T12NewSessionTicketMessage(hc,
                    message);
            if (nstm.ticket.length == 0) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine("NewSessionTicket ticket was empty");
                }
                return;
            }

            // discard tickets with timeout 0
            if (nstm.ticketLifetime <= 0 ||
                nstm.ticketLifetime > MAX_TICKET_LIFETIME) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                            "Discarding NewSessionTicket with lifetime " +
                            nstm.ticketLifetime, nstm);
                }
                return;
            }

            SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
                    hc.sslContext.engineGetClientSessionContext();

            if (sessionCache.getSessionTimeout() > MAX_TICKET_LIFETIME) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                        "Session cache lifetime is too long. " +
                        "Discarding ticket.");
                }
                return;
            }

            hc.handshakeSession.setPskIdentity(nstm.ticket);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine("Consuming NewSessionTicket\n" + nstm);
            }
        }
    }
}


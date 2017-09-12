/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import javax.net.ssl.SSLProtocolException;

import static sun.security.ssl.CipherSuite.KeyExchange;
import static sun.security.ssl.CipherSuite.KeyExchange.*;
import static sun.security.ssl.HandshakeStateManager.HandshakeState.*;
import static sun.security.ssl.HandshakeMessage.*;

/*
 * Handshake state manager.
 *
 * Messages flow for a full handshake:
 *
 *      -                                                         -
 *      |          HelloRequest       (No.0, RFC 5246) [*]        |
 *      |     <--------------------------------------------       |
 *      |                                                         |
 *      |          ClientHello        (No.1, RFC 5246)            |
 *      |     -------------------------------------------->       |
 *      |                                                         |
 *      |   -      HelloVerifyRequest (No.3, RFC 6347)      -     |
 *      | D | <-------------------------------------------- | D   |
 *      | T |                                               | T   |
 *      | L |      ClientHello        (No.1, RFC 5246)      | L   |
 *      | S | --------------------------------------------> | S   |
 *      |   -                                               -     |
 *      |                                                         |
 *   C  |          ServerHello        (No.2, RFC 5246)            |  S
 *   L  |          SupplementalData   (No.23, RFC4680) [*]        |  E
 *   I  |          Certificate        (No.11, RFC 5246) [*]       |  R
 *   E  |          CertificateStatus  (No.22, RFC 6066) [*]       |  V
 *   N  |          ServerKeyExchange  (No.12, RFC 5246) [*]       |  E
 *   T  |          CertificateRequest (No.13, RFC 5246) [*]       |  R
 *      |          ServerHelloDone    (No.14, RFC 5246)           |
 *      |     <--------------------------------------------       |
 *      |                                                         |
 *      |          SupplementalData   (No.23, RFC4680) [*]        |
 *      |          Certificate        (No.11, RFC 5246) [*] Or    |
 *      |              CertificateURL (No.21, RFC6066) [*]        |
 *      |          ClientKeyExchange  (No.16, RFC 5246)           |
 *      |          CertificateVerify  (No.15, RFC 5246) [*]       |
 *      |          [ChangeCipherSpec] (RFC 5246)                  |
 *      |          Finished           (No.20, RFC 5246)           |
 *      |     -------------------------------------------->       |
 *      |                                                         |
 *      |          NewSessionTicket   (No.4, RFC4507) [*]         |
 *      |          [ChangeCipherSpec] (RFC 5246)                  |
 *      |          Finished           (No.20, RFC 5246)           |
 *      |     <--------------------------------------------       |
 *      -                                                         -
 * [*] Indicates optional or situation-dependent messages that are not
 * always sent.
 *
 * Message flow for an abbreviated handshake:
 *      -                                                         -
 *      |          ClientHello        (No.1, RFC 5246)            |
 *      |     -------------------------------------------->       |
 *      |                                                         |
 *   C  |          ServerHello        (No.2, RFC 5246)            |  S
 *   L  |          NewSessionTicket   (No.4, RFC4507) [*]         |  E
 *   I  |          [ChangeCipherSpec] (RFC 5246)                  |  R
 *   E  |          Finished           (No.20, RFC 5246)           |  V
 *   N  |     <--------------------------------------------       |  E
 *   T  |                                                         |  R
 *      |          [ChangeCipherSpec] (RFC 5246)                  |
 *      |          Finished           (No.20, RFC 5246)           |
 *      |     -------------------------------------------->       |
 *      -                                                         -
 *
 *
 * State machine of handshake states:
 *
 *                   +--------------+
 *      START -----> | HelloRequest |
 *        |          +--------------+
 *        |               |
 *        v               v
 *     +---------------------+   -->  +---------------------+
 *     |    ClientHello      |        | HelloVerifyRequest  |
 *     +---------------------+   <--  +---------------------+
 *               |
 *               |
 * =========================================================================
 *               |
 *               v
 *     +---------------------+
 *     |    ServerHello      |  ----------------------------------+------+
 *     +---------------------+  -->  +-------------------------+  |      |
 *                    |              | Server SupplementalData |  |      |
 *                    |              +-------------------------+  |      |
 *                    |                |                          |      |
 *                    v                v                          |      |
 *                +---------------------+                         |      |
 *         +----  | Server Certificate  |                         |      |
 *         |      +---------------------+                         |      |
 *         |          |                                           |      |
 *         |          |   +--------------------+                  |      |
 *         |          +-> | CertificateStatus  |                  |      |
 *         |          |   +--------------------+                  v      |
 *         |          |      |          |     +--------------------+     |
 *         |          v      v          +-->  | ServerKeyExchange  |     |
 *         |  +---------------------+   |     +--------------------+     |
 *         |  | CertificateRequest  |   |         |                      |
 *         |  +---------------------+ <-+---------+                      |
 *         |            |               |         |                      |
 *         v            v               |         |                      |
 *     +---------------------+  <-------+         |                      |
 *     |  ServerHelloDone    |  <-----------------+                      |
 *     +---------------------+                                           |
 *       |         |                                                     |
 *       |         |                                                     |
 *       |         |                                                     |
 * =========================================================================
 *       |         |                                                     |
 *       |         v                                                     |
 *       |   +-------------------------+                                 |
 *       |   | Client SupplementalData | --------------+                 |
 *       |   +-------------------------+               |                 |
 *       |             |                               |                 |
 *       |             v                               |                 |
 *       |   +--------------------+                    |                 |
 *       +-> | Client Certificate | ALT.               |                 |
 *       |   +--------------------+----------------+   |                 |
 *       |                        | CertificateURL |   |                 |
 *       |                        +----------------+   |                 |
 *       v                                             |                 |
 *     +-------------------+  <------------------------+                 |
 *     | ClientKeyExchange |                                             |
 *     +-------------------+                                             |
 *          |           |                                                |
 *          |           v                                                |
 *          |      +-------------------+                                 |
 *          |      | CertificateVerify |                                 |
 *          |      +-------------------+                                 |
 *          |          |                                                 |
 *          v          v                                                 |
 *     +-------------------------+                                       |
 *     | Client ChangeCipherSpec |  <---------------+                    |
 *     +-------------------------+                  |                    |
 *               |                                  |                    |
 *               v                                  |                    |
 *     +-----------------+  (abbreviated)           |                    |
 *     | Client Finished |  -------------> END      |                    |
 *     +-----------------+  (Abbreviated handshake) |                    |
 *                      |                           |                    |
 *                      | (full)                    |                    |
 *                      |                           |                    |
 * ================================                 |                    |
 *                      |                           |                    |
 *                      |                   ================================
 *                      |                           |                    |
 *                      v                           |                    |
 *                 +------------------+             |    (abbreviated)   |
 *                 | NewSessionTicket | <--------------------------------+
 *                 +------------------+             |                    |
 *                      |                           |                    |
 *                      v                           |                    |
 *     +-------------------------+                  |    (abbreviated)   |
 *     | Server ChangeCipherSpec | <-------------------------------------+
 *     +-------------------------+                  |
 *               |                                  |
 *               v                                  |
 *     +-----------------+    (abbreviated)         |
 *     | Server Finished | -------------------------+
 *     +-----------------+
 *            | (full)
 *            v
 *        END (Full handshake)
 *
 *
 * The scenarios of the use of this class:
 * 1. Create an instance of HandshakeStateManager during the initializtion
 *    handshake.
 * 2. If receiving a handshake message, call HandshakeStateManager.check()
 *    to make sure that the message is of the expected handshake type.  And
 *    then call HandshakeStateManager.update() in case handshake states may
 *    be impacted by this new incoming handshake message.
 * 3. On delivering a handshake message, call HandshakeStateManager.update()
 *    in case handshake states may by thie new outgoing handshake message.
 * 4. On receiving and delivering ChangeCipherSpec message, call
 *    HandshakeStateManager.changeCipherSpec() to check the present sequence
 *    of this message, and update the states if necessary.
 */
final class HandshakeStateManager {
    // upcoming handshake states.
    private LinkedList<HandshakeState> upcomingStates;
    private LinkedList<HandshakeState> alternatives;

    private boolean isDTLS;

    private static final boolean debugIsOn;

    private static final HashMap<Byte, String> handshakeTypes;

    static {
        debugIsOn = (Handshaker.debug != null) &&
                Debug.isOn("handshake") && Debug.isOn("verbose");
        handshakeTypes = new HashMap<>(15);

        handshakeTypes.put(ht_hello_request,            "hello_request");
        handshakeTypes.put(ht_client_hello,             "client_hello");
        handshakeTypes.put(ht_server_hello,             "server_hello");
        handshakeTypes.put(ht_hello_verify_request,     "hello_verify_request");
        handshakeTypes.put(ht_new_session_ticket,       "session_ticket");
        handshakeTypes.put(ht_certificate,              "certificate");
        handshakeTypes.put(ht_server_key_exchange,      "server_key_exchange");
        handshakeTypes.put(ht_certificate_request,      "certificate_request");
        handshakeTypes.put(ht_server_hello_done,        "server_hello_done");
        handshakeTypes.put(ht_certificate_verify,       "certificate_verify");
        handshakeTypes.put(ht_client_key_exchange,      "client_key_exchange");
        handshakeTypes.put(ht_finished,                 "finished");
        handshakeTypes.put(ht_certificate_url,          "certificate_url");
        handshakeTypes.put(ht_certificate_status,       "certificate_status");
        handshakeTypes.put(ht_supplemental_data,        "supplemental_data");
    }

    HandshakeStateManager(boolean isDTLS) {
        this.upcomingStates = new LinkedList<>();
        this.alternatives = new LinkedList<>();
        this.isDTLS = isDTLS;
    }

    //
    // enumation of handshake type
    //
    static enum HandshakeState {
        HS_HELLO_REQUEST(
                "hello_request",
                HandshakeMessage.ht_hello_request),
        HS_CLIENT_HELLO(
                "client_hello",
                HandshakeMessage.ht_client_hello),
        HS_HELLO_VERIFY_REQUEST(
                "hello_verify_request",
                HandshakeMessage.ht_hello_verify_request),
        HS_SERVER_HELLO(
                "server_hello",
                HandshakeMessage.ht_server_hello),
        HS_SERVER_SUPPLEMENTAL_DATA(
                "server supplemental_data",
                HandshakeMessage.ht_supplemental_data, true),
        HS_SERVER_CERTIFICATE(
                "server certificate",
                HandshakeMessage.ht_certificate),
        HS_CERTIFICATE_STATUS(
                "certificate_status",
                HandshakeMessage.ht_certificate_status, true),
        HS_SERVER_KEY_EXCHANGE(
                "server_key_exchange",
                HandshakeMessage.ht_server_key_exchange, true),
        HS_CERTIFICATE_REQUEST(
                "certificate_request",
                HandshakeMessage.ht_certificate_request, true),
        HS_SERVER_HELLO_DONE(
                "server_hello_done",
                HandshakeMessage.ht_server_hello_done),
        HS_CLIENT_SUPPLEMENTAL_DATA(
                "client supplemental_data",
                HandshakeMessage.ht_supplemental_data, true),
        HS_CLIENT_CERTIFICATE(
                "client certificate",
                HandshakeMessage.ht_certificate, true),
        HS_CERTIFICATE_URL(
                "certificate_url",
                HandshakeMessage.ht_certificate_url, true),
        HS_CLIENT_KEY_EXCHANGE(
                "client_key_exchange",
                HandshakeMessage.ht_client_key_exchange),
        HS_CERTIFICATE_VERIFY(
                "certificate_verify",
                HandshakeMessage.ht_certificate_verify, true),
        HS_CLIENT_CHANGE_CIPHER_SPEC(
                "client change_cipher_spec",
                HandshakeMessage.ht_not_applicable),
        HS_CLEINT_FINISHED(
                "client finished",
                HandshakeMessage.ht_finished),
        HS_NEW_SESSION_TICKET(
                "session_ticket",
                HandshakeMessage.ht_new_session_ticket),
        HS_SERVER_CHANGE_CIPHER_SPEC(
                "server change_cipher_spec",
                HandshakeMessage.ht_not_applicable),
        HS_SERVER_FINISHED(
                "server finished",
                HandshakeMessage.ht_finished);

        final String description;
        final byte handshakeType;
        final boolean isOptional;

        HandshakeState(String description, byte handshakeType) {
            this.description = description;
            this.handshakeType = handshakeType;
            this.isOptional = false;
        }

        HandshakeState(String description,
                byte handshakeType, boolean isOptional) {

            this.description = description;
            this.handshakeType = handshakeType;
            this.isOptional = isOptional;
        }

        public String toString() {
            return description + "[" + handshakeType + "]" +
                    (isOptional ? "(optional)" : "");
        }
    }

    boolean isEmpty() {
        return upcomingStates.isEmpty();
    }

    List<Byte> check(byte handshakeType) throws SSLProtocolException {
        List<Byte> ignoredOptional = new LinkedList<>();
        String exceptionMsg =
                 "Handshake message sequence violation, " + handshakeType;

        if (debugIsOn) {
            System.out.println(
                    "check handshake state: " + toString(handshakeType));
        }

        if (upcomingStates.isEmpty()) {
            // Is it a kickstart message?
            if ((handshakeType != HandshakeMessage.ht_hello_request) &&
                (handshakeType != HandshakeMessage.ht_client_hello)) {

                throw new SSLProtocolException(
                    "Handshake message sequence violation, " + handshakeType);
            }

            // It is a kickstart message.
            return Collections.emptyList();
        }

        // Ignore the checking for HelloRequest messages as they
        // may be sent by the server at any time.
        if (handshakeType == HandshakeMessage.ht_hello_request) {
            return Collections.emptyList();
        }

        for (HandshakeState handshakeState : upcomingStates) {
            if (handshakeState.handshakeType == handshakeType) {
                // It's the expected next handshake type.
                return ignoredOptional;
            }

            if (handshakeState.isOptional) {
                ignoredOptional.add(handshakeState.handshakeType);
                continue;
            } else {
                for (HandshakeState alternative : alternatives) {
                    if (alternative.handshakeType == handshakeType) {
                        return ignoredOptional;
                    }

                    if (alternative.isOptional) {
                        continue;
                    } else {
                        throw new SSLProtocolException(exceptionMsg);
                    }
                }
            }

            throw new SSLProtocolException(exceptionMsg);
        }

        // Not an expected Handshake message.
        throw new SSLProtocolException(
                "Handshake message sequence violation, " + handshakeType);
    }

    void update(HandshakeMessage handshakeMessage,
            boolean isAbbreviated) throws SSLProtocolException {

        byte handshakeType = (byte)handshakeMessage.messageType();
        String exceptionMsg =
                 "Handshake message sequence violation, " + handshakeType;

        if (debugIsOn) {
            System.out.println(
                    "update handshake state: " + toString(handshakeType));
        }

        boolean hasPresentState = false;
        switch (handshakeType) {
        case HandshakeMessage.ht_hello_request:
            //
            // State machine:
            //     PRESENT: START
            //        TO  : ClientHello
            //

            // No old state to update.

            // Add the upcoming states.
            if (!upcomingStates.isEmpty()) {
                // A ClientHello message should be followed.
                upcomingStates.add(HS_CLIENT_HELLO);

            }   // Otherwise, ignore this HelloRequest message.

            break;

        case HandshakeMessage.ht_client_hello:
            //
            // State machine:
            //     PRESENT: START
            //              HS_CLIENT_HELLO
            //        TO  : HS_HELLO_VERIFY_REQUEST (DTLS)
            //              HS_SERVER_HELLO
            //

            // Check and update the present state.
            if (!upcomingStates.isEmpty()) {
                // The current state should be HS_CLIENT_HELLO.
                HandshakeState handshakeState = upcomingStates.pop();
                if (handshakeState != HS_CLIENT_HELLO) {
                    throw new SSLProtocolException(exceptionMsg);
                }
            }

            // Add the upcoming states.
            ClientHello clientHello = (ClientHello)handshakeMessage;
            if (isDTLS) {
                // Is it an initial ClientHello message?
                if (clientHello.cookie == null ||
                        clientHello.cookie.length == 0) {
                    // Is it an abbreviated handshake?
                    if (clientHello.sessionId.length() != 0) {
                        // A HelloVerifyRequest message or a ServerHello
                        // message may follow the abbreviated session
                        // resuming handshake request.
                        upcomingStates.add(HS_HELLO_VERIFY_REQUEST);
                        alternatives.add(HS_SERVER_HELLO);
                    } else {
                        // A HelloVerifyRequest message should follow
                        // the initial ClientHello message.
                        upcomingStates.add(HS_HELLO_VERIFY_REQUEST);
                    }
                } else {
                    // A HelloVerifyRequest may be followed if the cookie
                    // cannot be verified.
                    upcomingStates.add(HS_SERVER_HELLO);
                    alternatives.add(HS_HELLO_VERIFY_REQUEST);
                }
            } else {
                upcomingStates.add(HS_SERVER_HELLO);
            }

            break;

        case HandshakeMessage.ht_hello_verify_request:
            //
            // State machine:
            //     PRESENT: HS_HELLO_VERIFY_REQUEST
            //        TO  : HS_CLIENT_HELLO
            //
            // Note that this state may have an alternative option.

            // Check and update the present state.
            if (!upcomingStates.isEmpty()) {
                // The current state should be HS_HELLO_VERIFY_REQUEST.
                HandshakeState handshakeState = upcomingStates.pop();
                HandshakeState alternative = null;
                if (!alternatives.isEmpty()) {
                    alternative = alternatives.pop();
                }

                if ((handshakeState != HS_HELLO_VERIFY_REQUEST) &&
                        (alternative != HS_HELLO_VERIFY_REQUEST)) {

                    throw new SSLProtocolException(exceptionMsg);
                }
            } else {
                // No present state.
                throw new SSLProtocolException(exceptionMsg);
            }

            // Add the upcoming states.
            upcomingStates.add(HS_CLIENT_HELLO);

            break;

        case HandshakeMessage.ht_server_hello:
            //
            // State machine:
            //     PRESENT: HS_SERVER_HELLO
            //        TO  :
            //          Full handshake state stacks
            //              (ServerHello Flight)
            //              HS_SERVER_SUPPLEMENTAL_DATA [optional]
            //          --> HS_SERVER_CERTIFICATE [optional]
            //          --> HS_CERTIFICATE_STATUS [optional]
            //          --> HS_SERVER_KEY_EXCHANGE [optional]
            //          --> HS_CERTIFICATE_REQUEST [optional]
            //          --> HS_SERVER_HELLO_DONE
            //              (Client ClientKeyExchange Flight)
            //          --> HS_CLIENT_SUPPLEMENTAL_DATA [optional]
            //          --> HS_CLIENT_CERTIFICATE or
            //              HS_CERTIFICATE_URL
            //          --> HS_CLIENT_KEY_EXCHANGE
            //          --> HS_CERTIFICATE_VERIFY [optional]
            //          --> HS_CLIENT_CHANGE_CIPHER_SPEC
            //          --> HS_CLEINT_FINISHED
            //              (Server Finished Flight)
            //          --> HS_CLIENT_SUPPLEMENTAL_DATA [optional]
            //
            //          Abbreviated handshake state stacks
            //              (Server Finished Flight)
            //              HS_NEW_SESSION_TICKET
            //          --> HS_SERVER_CHANGE_CIPHER_SPEC
            //          --> HS_SERVER_FINISHED
            //              (Client Finished Flight)
            //          --> HS_CLIENT_CHANGE_CIPHER_SPEC
            //          --> HS_CLEINT_FINISHED
            //
            // Note that this state may have an alternative option.

            // Check and update the present state.
            if (!upcomingStates.isEmpty()) {
                // The current state should be HS_SERVER_HELLO
                HandshakeState handshakeState = upcomingStates.pop();
                HandshakeState alternative = null;
                if (!alternatives.isEmpty()) {
                    alternative = alternatives.pop();
                }

                if ((handshakeState != HS_SERVER_HELLO) &&
                        (alternative != HS_SERVER_HELLO)) {

                    throw new SSLProtocolException(exceptionMsg);
                }
            } else {
                // No present state.
                throw new SSLProtocolException(exceptionMsg);
            }

            // Add the upcoming states.
            ServerHello serverHello = (ServerHello)handshakeMessage;
            HelloExtensions hes = serverHello.extensions;


            // Not support SessionTicket extension yet.
            //
            // boolean hasSessionTicketExt =
            //     (hes.get(HandshakeMessage.ht_new_session_ticket) != null);

            if (isAbbreviated) {
                // Not support SessionTicket extension yet.
                //
                // // Mandatory NewSessionTicket message
                // if (hasSessionTicketExt) {
                //     upcomingStates.add(HS_NEW_SESSION_TICKET);
                // }

                // Mandatory server ChangeCipherSpec and Finished messages
                upcomingStates.add(HS_SERVER_CHANGE_CIPHER_SPEC);
                upcomingStates.add(HS_SERVER_FINISHED);

                // Mandatory client ChangeCipherSpec and Finished messages
                upcomingStates.add(HS_CLIENT_CHANGE_CIPHER_SPEC);
                upcomingStates.add(HS_CLEINT_FINISHED);
            } else {
                // Not support SupplementalData extension yet.
                //
                // boolean hasSupplementalDataExt =
                //     (hes.get(HandshakeMessage.ht_supplemental_data) != null);

                // Not support CertificateURL extension yet.
                //
                // boolean hasCertificateUrlExt =
                //     (hes.get(ExtensionType EXT_CLIENT_CERTIFICATE_URL)
                //          != null);

                // Not support SupplementalData extension yet.
                //
                // // Optional SupplementalData message
                // if (hasSupplementalDataExt) {
                //     upcomingStates.add(HS_SERVER_SUPPLEMENTAL_DATA);
                // }

                // Need server Certificate message or not?
                KeyExchange keyExchange = serverHello.cipherSuite.keyExchange;
                if ((keyExchange != K_KRB5) &&
                        (keyExchange != K_KRB5_EXPORT) &&
                        (keyExchange != K_DH_ANON) &&
                        (keyExchange != K_ECDH_ANON)) {
                    // Mandatory Certificate message
                    upcomingStates.add(HS_SERVER_CERTIFICATE);
                }

                // Optional CertificateStatus message
                if (hes.get(ExtensionType.EXT_STATUS_REQUEST) != null ||
                        hes.get(ExtensionType.EXT_STATUS_REQUEST_V2) != null) {
                    upcomingStates.add(HS_CERTIFICATE_STATUS);
                }

                // Need ServerKeyExchange message or not?
                if ((keyExchange == K_RSA_EXPORT) ||
                        (keyExchange == K_DHE_RSA) ||
                        (keyExchange == K_DHE_DSS) ||
                        (keyExchange == K_DH_ANON) ||
                        (keyExchange == K_ECDHE_RSA) ||
                        (keyExchange == K_ECDHE_ECDSA) ||
                        (keyExchange == K_ECDH_ANON)) {
                    // Optional ServerKeyExchange message
                    upcomingStates.add(HS_SERVER_KEY_EXCHANGE);
                }

                // Optional CertificateRequest message
                upcomingStates.add(HS_CERTIFICATE_REQUEST);

                // Mandatory ServerHelloDone message
                upcomingStates.add(HS_SERVER_HELLO_DONE);

                // Not support SupplementalData extension yet.
                //
                // // Optional SupplementalData message
                // if (hasSupplementalDataExt) {
                //     upcomingStates.add(HS_CLIENT_SUPPLEMENTAL_DATA);
                // }

                // Optional client Certificate message
                upcomingStates.add(HS_CLIENT_CERTIFICATE);

                // Not support CertificateURL extension yet.
                //
                // // Alternative CertificateURL message, optional too.
                // //
                // // Please put CertificateURL rather than Certificate
                // // message in the alternatives list.  So that we can
                // // simplify the process of this alternative pair later.
                // if (hasCertificateUrlExt) {
                //     alternatives.add(HS_CERTIFICATE_URL);
                // }

                // Mandatory ClientKeyExchange message
                upcomingStates.add(HS_CLIENT_KEY_EXCHANGE);

                // Optional CertificateVerify message
                upcomingStates.add(HS_CERTIFICATE_VERIFY);

                // Mandatory client ChangeCipherSpec and Finished messages
                upcomingStates.add(HS_CLIENT_CHANGE_CIPHER_SPEC);
                upcomingStates.add(HS_CLEINT_FINISHED);

                // Not support SessionTicket extension yet.
                //
                // // Mandatory NewSessionTicket message
                // if (hasSessionTicketExt) {
                //     upcomingStates.add(HS_NEW_SESSION_TICKET);
                // }

                // Mandatory server ChangeCipherSpec and Finished messages
                upcomingStates.add(HS_SERVER_CHANGE_CIPHER_SPEC);
                upcomingStates.add(HS_SERVER_FINISHED);
            }

            break;

        case HandshakeMessage.ht_certificate:
            //
            // State machine:
            //     PRESENT: HS_CERTIFICATE_URL or
            //              HS_CLIENT_CERTIFICATE
            //        TO  : HS_CLIENT_KEY_EXCHANGE
            //
            //     Or
            //
            //     PRESENT: HS_SERVER_CERTIFICATE
            //        TO  : HS_CERTIFICATE_STATUS [optional]
            //              HS_SERVER_KEY_EXCHANGE [optional]
            //              HS_CERTIFICATE_REQUEST [optional]
            //              HS_SERVER_HELLO_DONE
            //
            // Note that this state may have an alternative option.

            // Check and update the present state.
            while (!upcomingStates.isEmpty()) {
                HandshakeState handshakeState = upcomingStates.pop();
                if (handshakeState.handshakeType == handshakeType) {
                    hasPresentState = true;

                    // The current state should be HS_CLIENT_CERTIFICATE or
                    // HS_SERVER_CERTIFICATE.
                    //
                    // Note that we won't put HS_CLIENT_CERTIFICATE into
                    // the alternative list.
                    if ((handshakeState != HS_CLIENT_CERTIFICATE) &&
                            (handshakeState != HS_SERVER_CERTIFICATE)) {
                        throw new SSLProtocolException(exceptionMsg);
                    }

                    // Is it an expected client Certificate message?
                    boolean isClientMessage = false;
                    if (!upcomingStates.isEmpty()) {
                        // If the next expected message is ClientKeyExchange,
                        // this one should be an expected client Certificate
                        // message.
                        HandshakeState nextState = upcomingStates.getFirst();
                        if (nextState == HS_CLIENT_KEY_EXCHANGE) {
                            isClientMessage = true;
                        }
                    }

                    if (isClientMessage) {
                        if (handshakeState != HS_CLIENT_CERTIFICATE) {
                            throw new SSLProtocolException(exceptionMsg);
                        }

                        // Not support CertificateURL extension yet.
                        /*******************************************
                        // clear up the alternatives list
                        if (!alternatives.isEmpty()) {
                            HandshakeState alternative = alternatives.pop();

                            if (alternative != HS_CERTIFICATE_URL) {
                                throw new SSLProtocolException(exceptionMsg);
                            }
                        }
                        ********************************************/
                    } else {
                        if ((handshakeState != HS_SERVER_CERTIFICATE)) {
                            throw new SSLProtocolException(exceptionMsg);
                        }
                    }

                    break;
                } else if (!handshakeState.isOptional) {
                    throw new SSLProtocolException(exceptionMsg);
                }   // Otherwise, looking for next state track.
            }

            // No present state.
            if (!hasPresentState) {
                throw new SSLProtocolException(exceptionMsg);
            }

            // no new upcoming states.

            break;

        // Not support CertificateURL extension yet.
        /*************************************************/
        case HandshakeMessage.ht_certificate_url:
            //
            // State machine:
            //     PRESENT: HS_CERTIFICATE_URL or
            //              HS_CLIENT_CERTIFICATE
            //        TO  : HS_CLIENT_KEY_EXCHANGE
            //
            // Note that this state may have an alternative option.

            // Check and update the present state.
            while (!upcomingStates.isEmpty()) {
                // The current state should be HS_CLIENT_CERTIFICATE.
                //
                // Note that we won't put HS_CLIENT_CERTIFICATE into
                // the alternative list.
                HandshakeState handshakeState = upcomingStates.pop();
                if (handshakeState.handshakeType ==
                        HS_CLIENT_CERTIFICATE.handshakeType) {
                    hasPresentState = true;

                    // Look for HS_CERTIFICATE_URL state track.
                    if (!alternatives.isEmpty()) {
                        HandshakeState alternative = alternatives.pop();

                        if (alternative != HS_CERTIFICATE_URL) {
                            throw new SSLProtocolException(exceptionMsg);
                        }
                    } else {
                        // No alternative CertificateUR state track.
                        throw new SSLProtocolException(exceptionMsg);
                    }

                    if ((handshakeState != HS_CLIENT_CERTIFICATE)) {
                        throw new SSLProtocolException(exceptionMsg);
                    }

                    break;
                } else if (!handshakeState.isOptional) {
                    throw new SSLProtocolException(exceptionMsg);
                }   // Otherwise, looking for next state track.

            }

            // No present state.
            if (!hasPresentState) {
                // No present state.
                throw new SSLProtocolException(exceptionMsg);
            }

            // no new upcoming states.

            break;
        /*************************************************/

        default:
            // Check and update the present state.
            while (!upcomingStates.isEmpty()) {
                HandshakeState handshakeState = upcomingStates.pop();
                if (handshakeState.handshakeType == handshakeType) {
                    hasPresentState = true;
                    break;
                } else if (!handshakeState.isOptional) {
                    throw new SSLProtocolException(exceptionMsg);
                }   // Otherwise, looking for next state track.
            }

            // No present state.
            if (!hasPresentState) {
                throw new SSLProtocolException(exceptionMsg);
            }

            // no new upcoming states.
        }

        if (debugIsOn) {
            for (HandshakeState handshakeState : upcomingStates) {
                System.out.println(
                    "upcoming handshake states: " + handshakeState);
            }
            for (HandshakeState handshakeState : alternatives) {
                System.out.println(
                    "upcoming handshake alternative state: " + handshakeState);
            }
        }
    }

    void changeCipherSpec(boolean isInput,
            boolean isClient) throws SSLProtocolException {

        if (debugIsOn) {
            System.out.println(
                    "update handshake state: change_cipher_spec");
        }

        String exceptionMsg = "ChangeCipherSpec message sequence violation";

        HandshakeState expectedState;
        if ((isClient && isInput) || (!isClient && !isInput)) {
            expectedState = HS_SERVER_CHANGE_CIPHER_SPEC;
        } else {
            expectedState = HS_CLIENT_CHANGE_CIPHER_SPEC;
        }

        boolean hasPresentState = false;

        // Check and update the present state.
        while (!upcomingStates.isEmpty()) {
            HandshakeState handshakeState = upcomingStates.pop();
            if (handshakeState == expectedState) {
                hasPresentState = true;
                break;
            } else if (!handshakeState.isOptional) {
                throw new SSLProtocolException(exceptionMsg);
            }   // Otherwise, looking for next state track.
        }

        // No present state.
        if (!hasPresentState) {
            throw new SSLProtocolException(exceptionMsg);
        }

        // no new upcoming states.

        if (debugIsOn) {
            for (HandshakeState handshakeState : upcomingStates) {
                System.out.println(
                    "upcoming handshake states: " + handshakeState);
            }
            for (HandshakeState handshakeState : alternatives) {
                System.out.println(
                    "upcoming handshake alternative state: " + handshakeState);
            }
        }
    }

    private static String toString(byte handshakeType) {
        String s = handshakeTypes.get(handshakeType);
        if (s == null) {
            s = "unknown";
        }
        return (s + "[" + handshakeType + "]");
    }
}


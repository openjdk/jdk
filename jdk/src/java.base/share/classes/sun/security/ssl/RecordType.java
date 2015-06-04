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

/*
 * enumation of record type
 */
enum RecordType {

    RECORD_CHANGE_CIPHER_SPEC   (Record.ct_change_cipher_spec,
                                    HandshakeMessage.ht_not_applicable),
    RECORD_ALERT                (Record.ct_alert,
                                    HandshakeMessage.ht_not_applicable),
    RECORD_HELLO_REQUEST        (Record.ct_handshake,
                                    HandshakeMessage.ht_hello_request),
    RECORD_CLIENT_HELLO         (Record.ct_handshake,
                                    HandshakeMessage.ht_client_hello),
    RECORD_SERVER_HELLO         (Record.ct_handshake,
                                    HandshakeMessage.ht_server_hello),
    RECORD_HELLO_VERIFY_REQUEST (Record.ct_handshake,
                                    HandshakeMessage.ht_hello_verify_request),
    RECORD_NEW_SESSION_TICKET   (Record.ct_handshake,
                                    HandshakeMessage.ht_new_session_ticket),
    RECORD_CERTIFICATE          (Record.ct_handshake,
                                    HandshakeMessage.ht_certificate),
    RECORD_SERVER_KEY_EXCHANGE  (Record.ct_handshake,
                                    HandshakeMessage.ht_server_key_exchange),
    RECORD_CERTIFICATE_REQUEST  (Record.ct_handshake,
                                    HandshakeMessage.ht_certificate_request),
    RECORD_SERVER_HELLO_DONE    (Record.ct_handshake,
                                    HandshakeMessage.ht_server_hello_done),
    RECORD_CERTIFICATE_VERIFY   (Record.ct_handshake,
                                    HandshakeMessage.ht_certificate_verify),
    RECORD_CLIENT_KEY_EXCHANGE  (Record.ct_handshake,
                                    HandshakeMessage.ht_client_key_exchange),
    RECORD_FINISHED             (Record.ct_handshake,
                                    HandshakeMessage.ht_finished),
    RECORD_CERTIFICATE_URL      (Record.ct_handshake,
                                    HandshakeMessage.ht_certificate_url),
    RECORD_CERTIFICATE_STATUS   (Record.ct_handshake,
                                    HandshakeMessage.ht_certificate_status),
    RECORD_SUPPLIEMENTAL_DATA   (Record.ct_handshake,
                                    HandshakeMessage.ht_supplemental_data),
    RECORD_APPLICATION_DATA     (Record.ct_application_data,
                                    HandshakeMessage.ht_not_applicable);

    byte            contentType;
    byte            handshakeType;

    private RecordType(byte contentType, byte handshakeType) {
        this.contentType = contentType;
        this.handshakeType = handshakeType;
    }

    static RecordType valueOf(byte contentType, byte handshakeType) {
        if (contentType == Record.ct_change_cipher_spec) {
            return RECORD_CHANGE_CIPHER_SPEC;
        } else if (contentType == Record.ct_alert) {
            return RECORD_ALERT;
        } else if (contentType == Record.ct_application_data) {
            return RECORD_APPLICATION_DATA;
        } else if (handshakeType == HandshakeMessage.ht_hello_request) {
            return RECORD_HELLO_REQUEST;
        } else if (handshakeType == HandshakeMessage.ht_client_hello) {
            return RECORD_CLIENT_HELLO;
        } else if (handshakeType == HandshakeMessage.ht_server_hello) {
            return RECORD_SERVER_HELLO;
        } else if (handshakeType == HandshakeMessage.ht_hello_verify_request) {
            return RECORD_HELLO_VERIFY_REQUEST;
        } else if (handshakeType == HandshakeMessage.ht_new_session_ticket) {
            return RECORD_NEW_SESSION_TICKET;
        } else if (handshakeType == HandshakeMessage.ht_certificate) {
            return RECORD_CERTIFICATE;
        } else if (handshakeType == HandshakeMessage.ht_server_key_exchange) {
            return RECORD_SERVER_KEY_EXCHANGE;
        } else if (handshakeType == HandshakeMessage.ht_certificate_request) {
            return RECORD_CERTIFICATE_REQUEST;
        } else if (handshakeType == HandshakeMessage.ht_server_hello_done) {
            return RECORD_SERVER_HELLO_DONE;
        } else if (handshakeType == HandshakeMessage.ht_certificate_verify) {
            return RECORD_CERTIFICATE_VERIFY;
        } else if (handshakeType == HandshakeMessage.ht_client_key_exchange) {
            return RECORD_CLIENT_KEY_EXCHANGE;
        } else if (handshakeType == HandshakeMessage.ht_finished) {
            return RECORD_FINISHED;
        } else if (handshakeType == HandshakeMessage.ht_certificate_url) {
            return RECORD_CERTIFICATE_URL;
        } else if (handshakeType == HandshakeMessage.ht_certificate_status) {
            return RECORD_CERTIFICATE_STATUS;
        } else if (handshakeType == HandshakeMessage.ht_supplemental_data) {
            return RECORD_SUPPLIEMENTAL_DATA;
        }

        // otherwise, invalid record type
        throw new IllegalArgumentException(
                "Invalid record type (ContentType:" + contentType +
                ", HandshakeType:" + handshakeType + ")");
    }
}

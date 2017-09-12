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

import java.io.IOException;
import java.nio.charset.*;
import java.util.*;

import javax.net.ssl.*;

/*
 * [RFC 7301]
 * This TLS extension facilitates the negotiation of application-layer protocols
 * within the TLS handshake. Clients MAY include an extension of type
 * "application_layer_protocol_negotiation" in the (extended) ClientHello
 * message. The "extension_data" field of this extension SHALL contain a
 * "ProtocolNameList" value:
 *
 *     enum {
 *         application_layer_protocol_negotiation(16), (65535)
 *     } ExtensionType;
 *
 *     opaque ProtocolName<1..2^8-1>;
 *
 *     struct {
 *         ProtocolName protocol_name_list<2..2^16-1>
 *     } ProtocolNameList;
 */
final class ALPNExtension extends HelloExtension {

    final static int ALPN_HEADER_LENGTH = 1;
    final static int MAX_APPLICATION_PROTOCOL_LENGTH = 255;
    final static int MAX_APPLICATION_PROTOCOL_LIST_LENGTH = 65535;
    private int listLength = 0;     // ProtocolNameList length
    private List<String> protocolNames = null;

    // constructor for ServerHello
    ALPNExtension(String protocolName) throws SSLException {
        this(new String[]{ protocolName });
    }

    // constructor for ClientHello
    ALPNExtension(String[] protocolNames) throws SSLException {
        super(ExtensionType.EXT_ALPN);
        if (protocolNames.length == 0) { // never null, never empty
            throw new IllegalArgumentException(
                "The list of application protocols cannot be empty");
        }
        this.protocolNames = Arrays.asList(protocolNames);
        for (String p : protocolNames) {
            int length = p.getBytes(StandardCharsets.UTF_8).length;
            if (length == 0) {
                throw new SSLProtocolException(
                    "Application protocol name is empty");
            }
            if (length <= MAX_APPLICATION_PROTOCOL_LENGTH) {
                listLength += length + ALPN_HEADER_LENGTH;
            } else {
                throw new SSLProtocolException(
                    "Application protocol name is too long: " + p);
            }
            if (listLength > MAX_APPLICATION_PROTOCOL_LIST_LENGTH) {
                throw new SSLProtocolException(
                    "Application protocol name list is too long");
            }
        }
    }

    // constructor for ServerHello for parsing ALPN extension
    ALPNExtension(HandshakeInStream s, int len) throws IOException {
        super(ExtensionType.EXT_ALPN);

        if (len >= 2) {
            listLength = s.getInt16(); // list length
            if (listLength < 2 || listLength + 2 != len) {
                throw new SSLProtocolException(
                    "Invalid " + type + " extension: incorrect list length " +
                    "(length=" + listLength + ")");
            }
        } else {
            throw new SSLProtocolException(
                "Invalid " + type + " extension: insufficient data " +
                "(length=" + len + ")");
        }

        int remaining = listLength;
        this.protocolNames = new ArrayList<>();
        while (remaining > 0) {
            // opaque ProtocolName<1..2^8-1>; // RFC 7301
            byte[] bytes = s.getBytes8();
            if (bytes.length == 0) {
                throw new SSLProtocolException("Invalid " + type +
                    " extension: empty application protocol name");
            }
            String p =
                new String(bytes, StandardCharsets.UTF_8); // app protocol
            protocolNames.add(p);
            remaining -= bytes.length + ALPN_HEADER_LENGTH;
        }

        if (remaining != 0) {
            throw new SSLProtocolException(
                "Invalid " + type + " extension: extra data " +
                "(length=" + remaining + ")");
        }
    }

    List<String> getPeerAPs() {
        return protocolNames;
    }

    /*
     * Return the length in bytes, including extension type and length fields.
     */
    @Override
    int length() {
        return 6 + listLength;
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        s.putInt16(listLength + 2); // length of extension_data
        s.putInt16(listLength);     // length of ProtocolNameList

        for (String p : protocolNames) {
            s.putBytes8(p.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (protocolNames == null || protocolNames.isEmpty()) {
            sb.append("<empty>");
        } else {
            for (String protocolName : protocolNames) {
                sb.append("[" + protocolName + "]");
            }
        }

        return "Extension " + type +
            ", protocol names: " + sb;
    }
}

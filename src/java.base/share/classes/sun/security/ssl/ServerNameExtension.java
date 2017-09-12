/*
 * Copyright (c) 2006, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIMatcher;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLProtocolException;
import javax.net.ssl.StandardConstants;

/*
 * [RFC 4366/6066] To facilitate secure connections to servers that host
 * multiple 'virtual' servers at a single underlying network address, clients
 * MAY include an extension of type "server_name" in the (extended) client
 * hello.  The "extension_data" field of this extension SHALL contain
 * "ServerNameList" where:
 *
 *     struct {
 *         NameType name_type;
 *         select (name_type) {
 *             case host_name: HostName;
 *         } name;
 *     } ServerName;
 *
 *     enum {
 *         host_name(0), (255)
 *     } NameType;
 *
 *     opaque HostName<1..2^16-1>;
 *
 *     struct {
 *         ServerName server_name_list<1..2^16-1>
 *     } ServerNameList;
 */
final class ServerNameExtension extends HelloExtension {

    // For backward compatibility, all future data structures associated with
    // new NameTypes MUST begin with a 16-bit length field.
    static final int NAME_HEADER_LENGTH = 3;    // NameType: 1 byte
                                                // Name length: 2 bytes
    private Map<Integer, SNIServerName> sniMap;
    private int listLength;     // ServerNameList length

    // constructor for ServerHello
    ServerNameExtension() throws IOException {
        super(ExtensionType.EXT_SERVER_NAME);

        listLength = 0;
        sniMap = Collections.<Integer, SNIServerName>emptyMap();
    }

    // constructor for ClientHello
    ServerNameExtension(List<SNIServerName> serverNames)
            throws IOException {
        super(ExtensionType.EXT_SERVER_NAME);

        listLength = 0;
        sniMap = new LinkedHashMap<>();
        for (SNIServerName serverName : serverNames) {
            // check for duplicated server name type
            if (sniMap.put(serverName.getType(), serverName) != null) {
                // unlikely to happen, but in case ...
                throw new RuntimeException(
                    "Duplicated server name of type " + serverName.getType());
            }

            listLength += serverName.getEncoded().length + NAME_HEADER_LENGTH;
        }

        // This constructor is used for ClientHello only.  Empty list is
        // not allowed in client mode.
        if (listLength == 0) {
            throw new RuntimeException("The ServerNameList cannot be empty");
        }
    }

    // constructor for ServerHello for parsing SNI extension
    ServerNameExtension(HandshakeInStream s, int len)
            throws IOException {
        super(ExtensionType.EXT_SERVER_NAME);

        int remains = len;
        if (len >= 2) {    // "server_name" extension in ClientHello
            listLength = s.getInt16();     // ServerNameList length
            if (listLength == 0 || listLength + 2 != len) {
                throw new SSLProtocolException(
                        "Invalid " + type + " extension");
            }

            remains -= 2;
            sniMap = new LinkedHashMap<>();
            while (remains > 0) {
                int code = s.getInt8();       // NameType

                // HostName (length read in getBytes16);
                byte[] encoded = s.getBytes16();
                SNIServerName serverName;
                switch (code) {
                    case StandardConstants.SNI_HOST_NAME:
                        if (encoded.length == 0) {
                            throw new SSLProtocolException(
                                "Empty HostName in server name indication");
                        }
                        try {
                            serverName = new SNIHostName(encoded);
                        } catch (IllegalArgumentException iae) {
                            SSLProtocolException spe = new SSLProtocolException(
                                "Illegal server name, type=host_name(" +
                                code + "), name=" +
                                (new String(encoded, StandardCharsets.UTF_8)) +
                                ", value=" + Debug.toString(encoded));
                            spe.initCause(iae);
                            throw spe;
                        }
                        break;
                    default:
                        try {
                            serverName = new UnknownServerName(code, encoded);
                        } catch (IllegalArgumentException iae) {
                            SSLProtocolException spe = new SSLProtocolException(
                                "Illegal server name, type=(" + code +
                                "), value=" + Debug.toString(encoded));
                            spe.initCause(iae);
                            throw spe;
                        }
                }
                // check for duplicated server name type
                if (sniMap.put(serverName.getType(), serverName) != null) {
                    throw new SSLProtocolException(
                            "Duplicated server name of type " +
                            serverName.getType());
                }

                remains -= encoded.length + NAME_HEADER_LENGTH;
            }
        } else if (len == 0) {     // "server_name" extension in ServerHello
            listLength = 0;
            sniMap = Collections.<Integer, SNIServerName>emptyMap();
        }

        if (remains != 0) {
            throw new SSLProtocolException("Invalid server_name extension");
        }
    }

    List<SNIServerName> getServerNames() {
        if (sniMap != null && !sniMap.isEmpty()) {
            return Collections.<SNIServerName>unmodifiableList(
                                        new ArrayList<>(sniMap.values()));
        }

        return Collections.<SNIServerName>emptyList();
    }

    /*
     * Is the extension recognized by the corresponding matcher?
     *
     * This method is used to check whether the server name indication can
     * be recognized by the server name matchers.
     *
     * Per RFC 6066, if the server understood the ClientHello extension but
     * does not recognize the server name, the server SHOULD take one of two
     * actions: either abort the handshake by sending a fatal-level
     * unrecognized_name(112) alert or continue the handshake.
     *
     * If there is an instance of SNIMatcher defined for a particular name
     * type, it must be used to perform match operations on the server name.
     */
    boolean isMatched(Collection<SNIMatcher> matchers) {
        if (sniMap != null && !sniMap.isEmpty()) {
            for (SNIMatcher matcher : matchers) {
                SNIServerName sniName = sniMap.get(matcher.getType());
                if (sniName != null && (!matcher.matches(sniName))) {
                    return false;
                }
            }
        }

        return true;
    }

    /*
     * Is the extension is identical to a server name list?
     *
     * This method is used to check the server name indication during session
     * resumption.
     *
     * Per RFC 6066, when the server is deciding whether or not to accept a
     * request to resume a session, the contents of a server_name extension
     * MAY be used in the lookup of the session in the session cache.  The
     * client SHOULD include the same server_name extension in the session
     * resumption request as it did in the full handshake that established
     * the session.  A server that implements this extension MUST NOT accept
     * the request to resume the session if the server_name extension contains
     * a different name.  Instead, it proceeds with a full handshake to
     * establish a new session.  When resuming a session, the server MUST NOT
     * include a server_name extension in the server hello.
     */
    boolean isIdentical(List<SNIServerName> other) {
        if (other.size() == sniMap.size()) {
            for(SNIServerName sniInOther : other) {
                SNIServerName sniName = sniMap.get(sniInOther.getType());
                if (sniName == null || !sniInOther.equals(sniName)) {
                    return false;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    int length() {
        return listLength == 0 ? 4 : 6 + listLength;
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        if (listLength == 0) {
            s.putInt16(listLength);     // in ServerHello, empty extension_data
        } else {
            s.putInt16(listLength + 2); // length of extension_data
            s.putInt16(listLength);     // length of ServerNameList

            for (SNIServerName sniName : sniMap.values()) {
                s.putInt8(sniName.getType());         // server name type
                s.putBytes16(sniName.getEncoded());   // server name value
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (SNIServerName sniName : sniMap.values()) {
            sb.append("[" + sniName + "]");
        }

        return "Extension " + type + ", server_name: " + sb;
    }

    private static class UnknownServerName extends SNIServerName {
        UnknownServerName(int code, byte[] encoded) {
            super(code, encoded);
        }
    }

}

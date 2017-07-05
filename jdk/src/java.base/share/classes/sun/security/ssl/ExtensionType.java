/*
 * Copyright (c) 2006, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

final class ExtensionType {

    final int id;
    final String name;

    private ExtensionType(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }

    static List<ExtensionType> knownExtensions = new ArrayList<>(15);

    static ExtensionType get(int id) {
        for (ExtensionType ext : knownExtensions) {
            if (ext.id == id) {
                return ext;
            }
        }
        return new ExtensionType(id, "type_" + id);
    }

    private static ExtensionType e(int id, String name) {
        ExtensionType ext = new ExtensionType(id, name);
        knownExtensions.add(ext);
        return ext;
    }

    // extensions defined in RFC 3546
    static final ExtensionType EXT_SERVER_NAME =
            e(0x0000, "server_name");            // IANA registry value: 0
    static final ExtensionType EXT_MAX_FRAGMENT_LENGTH =
            e(0x0001, "max_fragment_length");    // IANA registry value: 1
    static final ExtensionType EXT_CLIENT_CERTIFICATE_URL =
            e(0x0002, "client_certificate_url"); // IANA registry value: 2
    static final ExtensionType EXT_TRUSTED_CA_KEYS =
            e(0x0003, "trusted_ca_keys");        // IANA registry value: 3
    static final ExtensionType EXT_TRUNCATED_HMAC =
            e(0x0004, "truncated_hmac");         // IANA registry value: 4
    static final ExtensionType EXT_STATUS_REQUEST =
            e(0x0005, "status_request");         // IANA registry value: 5

    // extensions defined in RFC 4681
    static final ExtensionType EXT_USER_MAPPING =
            e(0x0006, "user_mapping");           // IANA registry value: 6

    // extensions defined in RFC 5081
    static final ExtensionType EXT_CERT_TYPE =
            e(0x0009, "cert_type");              // IANA registry value: 9

    // extensions defined in RFC 4492 (ECC)
    static final ExtensionType EXT_ELLIPTIC_CURVES =
            e(0x000A, "elliptic_curves");        // IANA registry value: 10
    static final ExtensionType EXT_EC_POINT_FORMATS =
            e(0x000B, "ec_point_formats");       // IANA registry value: 11

    // extensions defined in RFC 5054
    static final ExtensionType EXT_SRP =
            e(0x000C, "srp");                    // IANA registry value: 12

    // extensions defined in RFC 5246
    static final ExtensionType EXT_SIGNATURE_ALGORITHMS =
            e(0x000D, "signature_algorithms");   // IANA registry value: 13

    // extension defined in RFC 7301 (ALPN)
    static final ExtensionType EXT_ALPN =
            e(0x0010, "application_layer_protocol_negotiation");
                                                 // IANA registry value: 16

    // extensions defined in RFC 6961
    static final ExtensionType EXT_STATUS_REQUEST_V2 =
            e(0x0011, "status_request_v2");      // IANA registry value: 17

    // extensions defined in RFC 5746
    static final ExtensionType EXT_RENEGOTIATION_INFO =
            e(0xff01, "renegotiation_info");     // IANA registry value: 65281
}

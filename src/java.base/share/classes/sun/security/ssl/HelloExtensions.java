/*
 * Copyright (c) 2006, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.io.PrintStream;
import java.util.*;
import javax.net.ssl.*;

/**
 * This file contains all the classes relevant to TLS Extensions for the
 * ClientHello and ServerHello messages. The extension mechanism and
 * several extensions are defined in RFC 6066. Additional extensions are
 * defined in the ECC RFC 4492 and the ALPN extension is defined in RFC 7301.
 *
 * Currently, only the two ECC extensions are fully supported.
 *
 * The classes contained in this file are:
 *  . HelloExtensions: a List of extensions as used in the client hello
 *      and server hello messages.
 *  . ExtensionType: an enum style class for the extension type
 *  . HelloExtension: abstract base class for all extensions. All subclasses
 *      must be immutable.
 *
 *  . UnknownExtension: used to represent all parsed extensions that we do not
 *      explicitly support.
 *  . ServerNameExtension: the server_name extension.
 *  . SignatureAlgorithmsExtension: the signature_algorithms extension.
 *  . SupportedGroupsExtension: the supported groups extension.
 *  . EllipticPointFormatsExtension: the ECC supported point formats
 *      (compressed/uncompressed) extension.
 *  . ALPNExtension: the application_layer_protocol_negotiation extension.
 *
 * @since   1.6
 * @author  Andreas Sterbenz
 */
final class HelloExtensions {

    private List<HelloExtension> extensions;
    private int encodedLength;

    HelloExtensions() {
        extensions = Collections.emptyList();
    }

    HelloExtensions(HandshakeInStream s) throws IOException {
        int len = s.getInt16();
        extensions = new ArrayList<HelloExtension>();
        encodedLength = len + 2;
        while (len > 0) {
            int type = s.getInt16();
            int extlen = s.getInt16();
            ExtensionType extType = ExtensionType.get(type);
            HelloExtension extension;
            if (extType == ExtensionType.EXT_SERVER_NAME) {
                extension = new ServerNameExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_SIGNATURE_ALGORITHMS) {
                extension = new SignatureAlgorithmsExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_SUPPORTED_GROUPS) {
                extension = new SupportedGroupsExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_EC_POINT_FORMATS) {
                extension = new EllipticPointFormatsExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_RENEGOTIATION_INFO) {
                extension = new RenegotiationInfoExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_ALPN) {
                extension = new ALPNExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_MAX_FRAGMENT_LENGTH) {
                extension = new MaxFragmentLengthExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_STATUS_REQUEST) {
                extension = new CertStatusReqExtension(s, extlen);
            } else if (extType == ExtensionType.EXT_STATUS_REQUEST_V2) {
                extension = new CertStatusReqListV2Extension(s, extlen);
            } else if (extType == ExtensionType.EXT_EXTENDED_MASTER_SECRET) {
                extension = new ExtendedMasterSecretExtension(s, extlen);
            } else {
                extension = new UnknownExtension(s, extlen, extType);
            }
            extensions.add(extension);
            len -= extlen + 4;
        }
        if (len != 0) {
            throw new SSLProtocolException(
                        "Error parsing extensions: extra data");
        }
    }

    // Return the List of extensions. Must not be modified by the caller.
    List<HelloExtension> list() {
        return extensions;
    }

    void add(HelloExtension ext) {
        if (extensions.isEmpty()) {
            extensions = new ArrayList<HelloExtension>();
        }
        extensions.add(ext);
        encodedLength = -1;
    }

    HelloExtension get(ExtensionType type) {
        for (HelloExtension ext : extensions) {
            if (ext.type == type) {
                return ext;
            }
        }
        return null;
    }

    int length() {
        if (encodedLength >= 0) {
            return encodedLength;
        }
        if (extensions.isEmpty()) {
            encodedLength = 0;
        } else {
            encodedLength = 2;
            for (HelloExtension ext : extensions) {
                encodedLength += ext.length();
            }
        }
        return encodedLength;
    }

    void send(HandshakeOutStream s) throws IOException {
        int length = length();
        if (length == 0) {
            return;
        }
        s.putInt16(length - 2);
        for (HelloExtension ext : extensions) {
            ext.send(s);
        }
    }

    void print(PrintStream s) throws IOException {
        for (HelloExtension ext : extensions) {
            s.println(ext.toString());
        }
    }
}

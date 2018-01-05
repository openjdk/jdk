/*
 * Copyright (c) 2017, Red Hat, Inc. and/or its affiliates.
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
import javax.net.ssl.SSLProtocolException;

/**
 * Extended Master Secret TLS extension (TLS 1.0+). This extension
 * defines how to calculate the TLS connection master secret and
 * mitigates some types of man-in-the-middle attacks.
 *
 * See further information in
 * <a href="https://tools.ietf.org/html/rfc7627">RFC 7627</a>.
 *
 * @author Martin Balao (mbalao@redhat.com)
 */
final class ExtendedMasterSecretExtension extends HelloExtension {
    ExtendedMasterSecretExtension() {
        super(ExtensionType.EXT_EXTENDED_MASTER_SECRET);
    }

    ExtendedMasterSecretExtension(HandshakeInStream s,
            int len) throws IOException {
        super(ExtensionType.EXT_EXTENDED_MASTER_SECRET);

        if (len != 0) {
            throw new SSLProtocolException("Invalid " + type + " extension");
        }
    }

    @Override
    int length() {
        return 4;       // 4: extension type and length fields
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);    // ExtensionType extension_type;
        s.putInt16(0);          // extension_data length
    }

    @Override
    public String toString() {
        return "Extension " + type;
    }
}


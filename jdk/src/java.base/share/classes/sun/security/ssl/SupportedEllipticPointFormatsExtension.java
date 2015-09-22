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
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLProtocolException;

final class SupportedEllipticPointFormatsExtension extends HelloExtension {

    static final int FMT_UNCOMPRESSED = 0;
    static final int FMT_ANSIX962_COMPRESSED_PRIME = 1;
    static final int FMT_ANSIX962_COMPRESSED_CHAR2 = 2;

    static final HelloExtension DEFAULT =
        new SupportedEllipticPointFormatsExtension(
            new byte[] {FMT_UNCOMPRESSED});

    private final byte[] formats;

    private SupportedEllipticPointFormatsExtension(byte[] formats) {
        super(ExtensionType.EXT_EC_POINT_FORMATS);
        this.formats = formats;
    }

    SupportedEllipticPointFormatsExtension(HandshakeInStream s, int len)
            throws IOException {
        super(ExtensionType.EXT_EC_POINT_FORMATS);
        formats = s.getBytes8();
        // RFC 4492 says uncompressed points must always be supported.
        // Check just to make sure.
        boolean uncompressed = false;
        for (int format : formats) {
            if (format == FMT_UNCOMPRESSED) {
                uncompressed = true;
                break;
            }
        }
        if (uncompressed == false) {
            throw new SSLProtocolException
                ("Peer does not support uncompressed points");
        }
    }

    @Override
    int length() {
        return 5 + formats.length;
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        s.putInt16(formats.length + 1);
        s.putBytes8(formats);
    }

    private static String toString(byte format) {
        int f = format & 0xff;
        switch (f) {
        case FMT_UNCOMPRESSED:
            return "uncompressed";
        case FMT_ANSIX962_COMPRESSED_PRIME:
            return "ansiX962_compressed_prime";
        case FMT_ANSIX962_COMPRESSED_CHAR2:
            return "ansiX962_compressed_char2";
        default:
            return "unknown-" + f;
        }
    }

    @Override
    public String toString() {
        List<String> list = new ArrayList<String>();
        for (byte format : formats) {
            list.add(toString(format));
        }
        return "Extension " + type + ", formats: " + list;
    }
}

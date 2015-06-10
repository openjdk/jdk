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
import javax.net.ssl.SSLProtocolException;

/*
 * [RFC6066] TLS specifies a fixed maximum plaintext fragment length of
 * 2^14 bytes.  It may be desirable for constrained clients to negotiate
 * a smaller maximum fragment length due to memory limitations or bandwidth
 * limitations.
 *
 * In order to negotiate smaller maximum fragment lengths, clients MAY
 * include an extension of type "max_fragment_length" in the (extended)
 * client hello.  The "extension_data" field of this extension SHALL
 * contain:
 *
 *    enum{
 *        2^9(1), 2^10(2), 2^11(3), 2^12(4), (255)
 *    } MaxFragmentLength;
 *
 * whose value is the desired maximum fragment length.
 */
final class MaxFragmentLengthExtension extends HelloExtension {

    private static final int MAX_FRAGMENT_LENGTH_512  = 1;      // 2^9
    private static final int MAX_FRAGMENT_LENGTH_1024 = 2;      // 2^10
    private static final int MAX_FRAGMENT_LENGTH_2048 = 3;      // 2^11
    private static final int MAX_FRAGMENT_LENGTH_4096 = 4;      // 2^12

    final int maxFragmentLength;

    MaxFragmentLengthExtension(int fragmentSize) {
        super(ExtensionType.EXT_MAX_FRAGMENT_LENGTH);

        if (fragmentSize < 1024) {
            maxFragmentLength = MAX_FRAGMENT_LENGTH_512;
        } else if (fragmentSize < 2048) {
            maxFragmentLength = MAX_FRAGMENT_LENGTH_1024;
        } else if (fragmentSize < 4096) {
            maxFragmentLength = MAX_FRAGMENT_LENGTH_2048;
        } else {
            maxFragmentLength = MAX_FRAGMENT_LENGTH_4096;
        }
    }

    MaxFragmentLengthExtension(HandshakeInStream s, int len)
                throws IOException {
        super(ExtensionType.EXT_MAX_FRAGMENT_LENGTH);

        // check the extension length
        if (len != 1) {
            throw new SSLProtocolException("Invalid " + type + " extension");
        }

        maxFragmentLength = s.getInt8();
        if ((maxFragmentLength > 4) || (maxFragmentLength < 1)) {
            throw new SSLProtocolException("Invalid " + type + " extension");
        }
    }

    // Length of the encoded extension, including the type and length fields
    @Override
    int length() {
        return 5;               // 4: extension type and length fields
                                // 1: MaxFragmentLength field
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        s.putInt16(1);
        s.putInt8(maxFragmentLength);
    }

    int getMaxFragLen() {
        switch (maxFragmentLength) {
            case MAX_FRAGMENT_LENGTH_512:
                return 512;
            case MAX_FRAGMENT_LENGTH_1024:
                return 1024;
            case MAX_FRAGMENT_LENGTH_2048:
                return 2048;
            case MAX_FRAGMENT_LENGTH_4096:
                return 4096;
        }

        // unlikely to happen
        return -1;
    }

    static boolean needFragLenNego(int fragmentSize) {
        return (fragmentSize > 0) && (fragmentSize <= 4096);
    }

    static int getValidMaxFragLen(int fragmentSize) {
        if (fragmentSize < 1024) {
            return 512;
        } else if (fragmentSize < 2048) {
            return 1024;
        } else if (fragmentSize < 4096) {
            return 2048;
        } else if (fragmentSize == 4096) {
            return 4096;
        } else {
            return 16384;
        }
    }

    @Override
    public String toString() {
        return "Extension " + type + ", max_fragment_length: " +
                "(2^" + (maxFragmentLength + 8) + ")";
    }
}

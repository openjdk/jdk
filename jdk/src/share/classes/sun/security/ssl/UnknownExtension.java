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

final class UnknownExtension extends HelloExtension {

    private final byte[] data;

    UnknownExtension(HandshakeInStream s, int len, ExtensionType type)
            throws IOException {
        super(type);
        data = new byte[len];
        // s.read() does not handle 0-length arrays.
        if (len != 0) {
            s.read(data);
        }
    }

    @Override
    int length() {
        return 4 + data.length;
    }

    @Override
    void send(HandshakeOutStream s) throws IOException {
        s.putInt16(type.id);
        s.putBytes16(data);
    }

    @Override
    public String toString() {
        return "Unsupported extension " + type + ", data: " +
            Debug.toString(data);
    }
}

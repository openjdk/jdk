/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.net.http;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;

final class WSUtils {

    private WSUtils() { }

    static final System.Logger logger = System.getLogger("java.net.http.WebSocket");
    static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

    //
    // Helps to trim long names (packages, nested/inner types) in logs/toString
    //
    static String toStringSimple(Object o) {
        return o.getClass().getSimpleName() + "@" +
                Integer.toHexString(System.identityHashCode(o));
    }

    //
    // 1. It adds a number of remaining bytes;
    // 2. Standard Buffer-type toString for CharBuffer (since it adheres to the
    // contract of java.lang.CharSequence.toString() which is both not too
    // useful and not too private)
    //
    static String toString(Buffer b) {
        return toStringSimple(b)
                + "[pos=" + b.position()
                + " lim=" + b.limit()
                + " cap=" + b.capacity()
                + " rem=" + b.remaining() + "]";
    }

    static String toString(CharSequence s) {
        return s == null
                ? "null"
                : toStringSimple(s) + "[len=" + s.length() + "]";
    }

    static String dump(Object... objects) {
        return Arrays.toString(objects);
    }

    static String webSocketSpecViolation(String section, String detail) {
        return "RFC 6455 " + section + " " + detail;
    }
}

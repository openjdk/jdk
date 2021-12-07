/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * (No at-test tag because it's run by JImageStringsMatchTestRun)
 */

package jdk.internal.jimage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import jdk.internal.jimage.ImageStringsReader;

public class ImageStringsMatchTest {
    private static String[] testStrings = {
        "\u3042",
        "/test/\u3042",
        "\u3042/test",
        "/test/\u3042/test",
    };

    private static void matchTest(String str) {
        byte[] b = str.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(b.length + 1);
        buf.put(b);
        buf.put((byte)0); // Explicit null termination is required
        int match = ImageStringsReader.stringFromByteBufferMatches(buf, 0, str, 0);
        if (match != str.length()) {
            throw new RuntimeException("Unexpected mismatch for \"" + str + "\"");
        }
    }

    public static void main(String[] args) {
        for (String str: testStrings) {
            matchTest(str);
        }
    }
}

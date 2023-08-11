/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8211382 8301119
 * @summary Check GB18030
 * @modules jdk.charsets
 * @run main TestGB18030
 * @run main/othervm -Djdk.charset.GB18030=2000 TestGB18030
 */

import java.nio.*;
import java.nio.charset.*;
import java.util.Set;

public class TestGB18030 {
    private static final Charset cs = Charset.forName("GB18030");
    public static void gb18030_1(boolean useDirect) throws Exception {
        for(char ch : new char[]{'\uFFFE', '\uFFFF'}) {
            char[] ca = new char[]{ch};
            CharsetEncoder ce = cs.newEncoder();
            CharsetDecoder cd = cs.newDecoder();
            CharBuffer cb = CharBuffer.wrap(ca);
            ByteBuffer bb;
            if (useDirect) {
                bb = ByteBuffer.allocateDirect(
                    (int)Math.ceil(ce.maxBytesPerChar()));
            } else {
                bb = ByteBuffer.allocate(
                    (int)Math.ceil(ce.maxBytesPerChar()));
            }
            CoderResult cr = ce.encode(cb, bb, true);
            if (!cr.isUnderflow()) {
                throw new RuntimeException(
                    String.format("Encoder Error: \\u%04X: direct=%b: %s",
                        (int)ch,
                        useDirect,
                        cr.toString()));
            }
            bb.position(0);
            cb = CharBuffer.allocate((int)Math.ceil(
                cd.maxCharsPerByte()*bb.limit()));
            cr = cd.decode(bb, cb, true);
            if (!cr.isUnderflow()) {
                throw new RuntimeException(
                    String.format("Decoder Error: \\u%04X: direct=%b: %s",
                        (int)ch,
                        useDirect,
                        cr.toString()));
            }
            if (ca[0] != cb.get(0)) {
                throw new RuntimeException(
                    String.format("direct=%b: \\u%04X <> \\u%04X",
                        useDirect,
                        (int)ca[0],
                        (int)cb.get(0)));
            }
        }
    }

    static void checkAlias() {
        var IS_2000 = "2000".equals(System.getProperty("jdk.charset.GB18030"));
        var expected = IS_2000 ? Set.of("gb18030-2000") : Set.of("gb18030-2022");
        var found = cs.aliases();
        System.out.printf("checkAlias(): IS_2000: %s, expected: %s, found: %s\n", IS_2000, expected, found);
        if (!cs.aliases().equals(expected)) {
            throw new RuntimeException("Result mismatch");
        }
    }
    public static void main(String args[]) throws Exception {
        gb18030_1(false);
        gb18030_1(true);
        checkAlias();
    }
}

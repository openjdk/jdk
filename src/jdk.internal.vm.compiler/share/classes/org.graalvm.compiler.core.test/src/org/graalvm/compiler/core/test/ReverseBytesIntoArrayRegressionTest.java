/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import java.util.Formatter;

import org.junit.Test;

/**
 * @see "https://bugs.openjdk.java.net/browse/JDK-8204914"
 */
public class ReverseBytesIntoArrayRegressionTest extends GraalCompilerTest {

    private static String toHexBytes(byte[] arr) {
        Formatter buf = new Formatter();
        for (int b : arr) {
            buf.format("0x%x ", b & 0xff);
        }
        return buf.toString().trim();
    }

    @Override
    protected void assertDeepEquals(Object expected, Object actual) {
        if (expected instanceof byte[] && actual instanceof byte[]) {
            super.assertDeepEquals(toHexBytes((byte[]) expected), toHexBytes((byte[]) actual));
        }
        super.assertDeepEquals(expected, actual);
    }

    @Test
    public void test1() {
        test("serialize", 1);
    }

    protected static final short SERIAL_COOKIE = 12347;

    public static byte[] serialize(int size) {
        int v = Integer.reverseBytes(SERIAL_COOKIE | ((size - 1) << 16));
        byte[] ba = new byte[4];
        ba[0] = (byte) ((v >>> 24) & 0xFF);
        ba[1] = (byte) ((v >>> 16) & 0xFF);
        ba[2] = (byte) ((v >>> 8) & 0xFF);
        ba[3] = (byte) ((v >>> 0) & 0xFF);
        return ba;
    }
}

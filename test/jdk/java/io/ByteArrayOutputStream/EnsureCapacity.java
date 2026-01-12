/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8374610
 * @summary Verifies basic behavior of the ensureCapacity(int) method.
 */

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class EnsureCapacity {

    public static void main(String[] args) {

        TestOutputStream out = new TestOutputStream();
        int len = out.getBufferLength();
        assertEquals(32, len);

        out.ensureCapacity(48);
        len = out.getBufferLength();
        assertAtLeast(len, 48);

        out.ensureCapacity(0); // ignored
        assertEquals(len, out.getBufferLength());

        out.ensureCapacity(-9); // ignored
        assertEquals(len, out.getBufferLength());

        out.write(1);
        out.write(2);
        out.write(3);
        assertEquals(len, out.getBufferLength());

        out.ensureCapacity(65);
        len = out.getBufferLength();
        assertAtLeast(len, 65);

        out.ensureCapacity(0); // ignored
        assertEquals(len, out.getBufferLength());

        out.ensureCapacity(-32); // ignored
        assertEquals(len, out.getBufferLength());

        out.write(4);
        out.write(5);
        out.write(6);
        assertEquals(len, out.getBufferLength());

        byte[] actual = out.toByteArray();
        byte[] expected = new byte[] { 1, 2, 3, 4, 5, 6 };
        assertEquals(expected, actual);
    }

    private static void assertAtLeast(int actual, int min) {
        if (actual < min) {
            String msg = String.format("Expected %d >= %d", actual, min);
            throw new RuntimeException(msg);
        }
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            String msg = String.format("Expected %d, got %d", expected, actual);
            throw new RuntimeException(msg);
        }
    }

    private static void assertEquals(byte[] expected, byte[] actual) {
        if (!Arrays.equals(expected, actual)) {
            String msg = String.format("Expected %s, got %s",
                Arrays.toString(expected), Arrays.toString(actual));
            throw new RuntimeException(msg);
        }
    }

    private static final class TestOutputStream extends ByteArrayOutputStream {
        public void ensureCapacity(int minCapacity) {
            super.ensureCapacity(minCapacity);
        }
        public int getBufferLength() {
            return buf.length;
        }
    }
}

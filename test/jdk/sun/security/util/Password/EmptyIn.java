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

import jdk.test.lib.Asserts;
import sun.security.util.Password;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/*
 * @test
 * @bug 8374555
 * @summary only print warning when reading from System.in
 * @modules java.base/sun.security.util
 * @library /test/lib
 */
public class EmptyIn {
    public static void main(String[] args) throws Exception {
        testSystemIn();
        testNotSystemIn();
    }

    static void testSystemIn() throws Exception {
        var in = new ByteArrayInputStream(new byte[0]);
        var err = new ByteArrayOutputStream();
        var oldErr = System.err;
        var oldIn = System.in;
        try {
            System.setIn(in);
            System.setErr(new PrintStream(err));
            Password.readPassword(System.in);
        } finally {
            System.setIn(oldIn);
            System.setErr(oldErr);
        }
        // Read from System.in. Should warn.
        Asserts.assertNotEquals(0, err.size());
    }

    static void testNotSystemIn() throws Exception {
        var in = new ByteArrayInputStream(new byte[0]);
        var err = new ByteArrayOutputStream();
        var oldErr = System.err;
        try {
            System.setErr(new PrintStream(err));
            Password.readPassword(in);
        } finally {
            System.setErr(oldErr);
        }
        // Not read from System.in. Should not warn.
        Asserts.assertEQ(0, err.size());
    }
}

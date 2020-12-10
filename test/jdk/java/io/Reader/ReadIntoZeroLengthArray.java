/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.CharArrayReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;

/*
 * @test
 * @bug 8248383
 * @summary Ensure that zero is returned for read into zero length array
 */
public class ReadIntoZeroLengthArray {
    private static char[] cbuf0 = new char[0];
    private static char[] cbuf1 = new char[1];

    public static void main(String[] args) throws Exception {
        File file = File.createTempFile("foo", "bar", new File("."));
        file.deleteOnExit();

        Reader fileReader = new FileReader(file);
        Reader[] readers = new Reader[] {
            new LineNumberReader(fileReader),
            new CharArrayReader(new char[] {27}),
            new PushbackReader(fileReader),
            fileReader,
            new StringReader(new String(new byte[] {(byte)42}))
        };

        for (Reader reader : readers) {
            check(reader);
        }

        System.out.println("Test succeeded");
    }

    private static void check(Reader r) throws IOException {
        System.out.printf("Testing %s%n", r.getClass().getName());
        if (r.read(cbuf0) != 0)
            throw new RuntimeException();
        if (r.read(cbuf1, 0, 0) !=  0)
            throw new RuntimeException();
    }
}

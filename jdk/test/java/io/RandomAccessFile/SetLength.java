/*
 * Copyright (c) 1997, Oracle and/or its affiliates. All rights reserved.
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
   @summary General tests of the setLength method -- Should migrate to 1.2 JCK
 */

import java.io.*;


public class SetLength {

    static void fail(String s) {
        throw new RuntimeException(s);
    }

    static void go(File fn, int max) throws IOException {
        int chunk = max / 4;
        long i;
        RandomAccessFile f;

        f = new RandomAccessFile(fn, "rw");
        f.setLength(2 * chunk);
        if (f.length() != 2 * chunk) fail("Length not increased to " + (2 * chunk));
        if ((i = f.getFilePointer()) != 0) fail("File pointer shifted to " + i);
        byte[] buf = new byte[max];
        f.write(buf);
        if (f.length() != max) fail("Write didn't work");
        if (f.getFilePointer() != max) fail("File pointer inconsistent");
        f.setLength(3 * chunk);
        if (f.length() != 3 * chunk) fail("Length not reduced to " + 3 * chunk);
        if (f.getFilePointer() != 3 * chunk) fail("File pointer not shifted to " + (3 * chunk));
        f.seek(1 * chunk);
        if (f.getFilePointer() != 1 * chunk) fail("File pointer not shifted to " + (1 * chunk));
        f.setLength(2 * chunk);
        if (f.length() != 2 * chunk) fail("Length not reduced to " + (2 * chunk));
        if (f.getFilePointer() != 1 * chunk) fail("File pointer not shifted to " + (1 * chunk));
        f.close();
    }

    public static void main(String[] args) throws IOException {
        File fn = new File("x.SetLength");
        try {
            go(fn, 20);
            fn.delete();
            go(fn, 64 * 1024);
            RandomAccessFile f = new RandomAccessFile(fn, "r");
            boolean thrown = false;
            try {
                f.setLength(3);
            } catch (IOException x) {
                thrown = true;
            }
            if (!thrown) fail("setLength succeeded on a file opened read-only");
            f.close();
        }
        finally {
            fn.delete();
        }
    }

}

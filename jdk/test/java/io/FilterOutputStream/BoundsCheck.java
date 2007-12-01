/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
   @bug 4176379
   @summary Ensure that FilterOutputStream.write(byte[], int, int) with
            negative len, throws appropriate exception.
 */

import java.io.*;

public class BoundsCheck {
    static class DummyFilterStream extends FilterOutputStream {

        public DummyFilterStream(OutputStream o) {
            super(o);
        }

        public void write(int val) throws IOException {
            super.write(val + 1);
        }
    }

    public static void main(String[] args) throws Exception {
        byte data[] = {90, 91, 92, 93, 94, 95, 96, 97, 98, 99};
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DummyFilterStream dfs = new DummyFilterStream(bos);
        boolean caughtException = false;

        // -ve length
        try {
            dfs.write(data, 0, -5);
        } catch (IndexOutOfBoundsException ie) {
            caughtException = true;
        } finally {
            if (!caughtException)
                throw new RuntimeException("Test failed");
        }

        // -ve offset
        caughtException = false;
        try {
            dfs.write(data, -2, 5);
        } catch (IndexOutOfBoundsException ie) {
            caughtException = true;
        } finally {
            if (!caughtException)
                throw new RuntimeException("Test failed");
        }

        // off + len > data.length
        caughtException = false;
        try {
            dfs.write(data, 6, 5);
        } catch (IndexOutOfBoundsException ie) {
            caughtException = true;
        } finally {
            if (!caughtException)
                throw new RuntimeException("Test failed");
        }

        // null data
        caughtException = false;
        try {
            dfs.write(null, 0, 5);
        } catch (NullPointerException re) {
            caughtException = true;
        } finally {
            if (!caughtException)
                throw new RuntimeException("Test failed");
        }
    }
}

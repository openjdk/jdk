/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6864911
 * @summary ASN.1/DER input stream parser needs more work
 * @modules java.base/sun.security.util
 */

import java.io.*;
import sun.security.util.*;

public class BadValue {

    public static void main(String[] args) throws Exception {

        // Test IOUtils.readFully

        // We have 4 bytes
        InputStream in = new ByteArrayInputStream(new byte[10]);
        byte[] bs = IOUtils.readFully(in, 4, true);
        if (bs.length != 4 || in.available() != 6) {
            throw new Exception("First read error");
        }
        // But only 6 left
        bs = IOUtils.readFully(in, 10, false);
        if (bs.length != 6 || in.available() != 0) {
            throw new Exception("Second read error");
        }
        // MAX read as much as it can
        in = new ByteArrayInputStream(new byte[10]);
        bs = IOUtils.readFully(in, Integer.MAX_VALUE, true);
        if (bs.length != 10 || in.available() != 0) {
            throw new Exception("Second read error");
        }
        // MAX ignore readAll
        in = new ByteArrayInputStream(new byte[10]);
        bs = IOUtils.readFully(in, Integer.MAX_VALUE, false);
        if (bs.length != 10 || in.available() != 0) {
            throw new Exception("Second read error");
        }
        // 20>10, readAll means failure
        in = new ByteArrayInputStream(new byte[10]);
        try {
            bs = IOUtils.readFully(in, 20, true);
            throw new Exception("Third read error");
        } catch (EOFException e) {
            // OK
        }
        int bignum = 10 * 1024 * 1024;
        bs = IOUtils.readFully(new SuperSlowStream(bignum), -1, true);
        if (bs.length != bignum) {
            throw new Exception("Fourth read error");
        }

        // Test DerValue
        byte[] input = {0x04, (byte)0x84, 0x40, 0x00, 0x42, 0x46, 0x4b};
        try {
            new DerValue(new ByteArrayInputStream(input));
        } catch (IOException ioe) {
            // This is OK
        }
    }
}

/**
 * An InputStream contains a given number of bytes, but only returns one byte
 * per read.
 */
class SuperSlowStream extends InputStream {
    private int p;
    /**
     * @param Initial capacity
     */
    public SuperSlowStream(int capacity) {
        p = capacity;
    }
    @Override
    public int read() throws IOException {
        if (p > 0) {
            p--;
            return 0;
        } else {
            return -1;
        }
    }
    @Override
    public int read(byte b[], int off, int len) throws IOException {
        if (len == 0) return 0;
        if (p > 0) {
            p--;
            b[off] = 0;
            return 1;
        } else {
            return -1;
        }
    }
}

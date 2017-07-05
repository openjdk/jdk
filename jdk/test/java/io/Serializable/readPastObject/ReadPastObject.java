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
 * @bug 4253271
 * @summary Ensure that ObjectInputStream.readObject() is called, it doesn't
 *          read past the end of the object in the underlying stream.
 */

import java.io.*;

class LimitInputStream extends ByteArrayInputStream {
    int limit;

    LimitInputStream(byte[] b) {
        super(b);
        limit = b.length;
    }

    public int read() {
        if (limit < 1)
            throw new Error("limit exceeded");
        int c = super.read();
        if (c != -1)
            limit--;
        return c;
    }

    public int read(byte[] b) {
        return read(b, 0, b.length);
    }

    public int read(byte[] b, int off, int len) {
        if (limit < len)
            throw new Error("limit exceeded");
        int n = super.read(b, off, len);
        if (n != -1)
            limit -= n;
        return n;
    }
}

public class ReadPastObject {
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout);
        oout.writeObject("foo");
        LimitInputStream lin = new LimitInputStream(bout.toByteArray());
        ObjectInputStream oin = new ObjectInputStream(lin);
        System.out.println(oin.readObject());
    }
}

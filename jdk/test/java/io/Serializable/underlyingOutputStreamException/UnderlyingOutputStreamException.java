/*
 * Copyright 2001 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @summary Verify that if ObjectOutputStream's underlying OutputStream throws
 *          an IOException, the original IOException (not a
 *          StreamCorruptedException) will be thrown to the writing thread.
 */

import java.io.*;

class OriginalIOException extends IOException {
}

class BrokenOutputStream extends OutputStream {
    boolean broken = false;

    public void write(int b) throws IOException {
        if (broken) {
            throw new OriginalIOException();
        }
    }
}

public class UnderlyingOutputStreamException {
    public static void main(String[] args) throws Exception {
        BrokenOutputStream bout = new BrokenOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout);
        bout.broken = true;
        try {
            oout.writeObject("foo");
            throw new Error();
        } catch (OriginalIOException ex) {
        }
    }
}

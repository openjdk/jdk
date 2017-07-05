/*
 * Copyright 1996-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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


package sun.security.ssl;

import java.io.*;
import java.security.SecureRandom;

/*
 * RandomCookie ... SSL hands standard format random cookies (nonces)
 * around.  These know how to encode/decode themselves on SSL streams,
 * and can be created and printed.
 *
 * @author David Brownell
 */
final class RandomCookie {

    byte random_bytes[];  // exactly 32 bytes

    RandomCookie(SecureRandom generator) {
        long temp = System.currentTimeMillis() / 1000;
        int gmt_unix_time;
        if (temp < Integer.MAX_VALUE) {
            gmt_unix_time = (int) temp;
        } else {
            gmt_unix_time = Integer.MAX_VALUE;          // Whoops!
        }

        random_bytes = new byte[32];
        generator.nextBytes(random_bytes);

        random_bytes[0] = (byte)(gmt_unix_time >> 24);
        random_bytes[1] = (byte)(gmt_unix_time >> 16);
        random_bytes[2] = (byte)(gmt_unix_time >>  8);
        random_bytes[3] = (byte)gmt_unix_time;
    }

    RandomCookie(HandshakeInStream m) throws IOException {
        random_bytes = new byte[32];
        m.read(random_bytes, 0, 32);
    }

    void send(HandshakeOutStream out) throws IOException {
        out.write(random_bytes, 0, 32);
    }

    void print(PrintStream s) {
        int i, gmt_unix_time;

        gmt_unix_time = random_bytes[0] << 24;
        gmt_unix_time += random_bytes[1] << 16;
        gmt_unix_time += random_bytes[2] << 8;
        gmt_unix_time += random_bytes[3];

        s.print("GMT: " + gmt_unix_time + " ");
        s.print("bytes = { ");

        for (i = 4; i < 32; i++) {
            if (i != 4) {
                s.print(", ");
            }
            s.print(random_bytes[i] & 0x0ff);
        }
        s.println(" }");
    }
}

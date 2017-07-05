/*
 * Copyright (c) 1996, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

    byte[] random_bytes;  // exactly 32 bytes

    RandomCookie(SecureRandom generator) {
        random_bytes = new byte[32];
        generator.nextBytes(random_bytes);
    }

    RandomCookie(HandshakeInStream m) throws IOException {
        random_bytes = new byte[32];
        m.read(random_bytes, 0, 32);
    }

    void send(HandshakeOutStream out) throws IOException {
        out.write(random_bytes, 0, 32);
    }

    void print(PrintStream s) {
        s.print("random_bytes = {");
        for (int i = 0; i < 32; i++) {
            int k = random_bytes[i] & 0xFF;
            if (i != 0) {
                s.print(' ');
            }
            s.print(Utilities.hexDigits[k >>> 4]);
            s.print(Utilities.hexDigits[k & 0xf]);
        }
        s.println("}");
    }
}

/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4856966
 * @summary
 * @author Andreas Sterbenz
 * @library ..
 * @key randomness
 */

import java.util.*;

import java.security.*;

public class ReinitDigest extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new ReinitDigest());
    }

    public void main(Provider p) throws Exception {
        if (p.getService("MessageDigest", "MD5") == null) {
            System.out.println("Provider does not support MD5, skipping");
            return;
        }
        Random r = new Random();
        byte[] data1 = new byte[10 * 1024];
        byte[] data2 = new byte[10 * 1024];
        r.nextBytes(data1);
        r.nextBytes(data2);
        MessageDigest md;
        md = MessageDigest.getInstance("MD5", "SUN");
        byte[] d1 = md.digest(data1);
        md = MessageDigest.getInstance("MD5", p);
        byte[] d2 = md.digest(data1);
        check(d1, d2);
        byte[] d3 = md.digest(data1);
        check(d1, d3);
        md.update(data2);
        md.update((byte)0);
        md.reset();
        byte[] d4 = md.digest(data1);
        check(d1, d4);
        System.out.println("All tests passed");
    }

    private static void check(byte[] d1, byte[] d2) throws Exception {
        if (Arrays.equals(d1, d2) == false) {
            throw new RuntimeException("Digest mismatch");
        }
    }
}

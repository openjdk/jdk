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
 * @bug 4915392
 * @summary test that the getAlgorithm() method works correctly
 * @author Andreas Sterbenz
 */

import java.io.*;

import java.security.*;

public class GetAlgorithm {

    private final static String BASE = System.getProperty("test.src", ".");

    public static void main(String[] args) throws Exception {
        SecureRandom sr;

        sr = new SecureRandom();
        if (sr.getAlgorithm().equals("unknown")) {
            throw new Exception("Unknown: " + sr.getAlgorithm());
        }

        sr = SecureRandom.getInstance("SHA1PRNG");
        check("SHA1PRNG", sr);

//      OutputStream out = new FileOutputStream("sha1prng.bin");
//      ObjectOutputStream oout = new ObjectOutputStream(out);
//      sr.nextInt();
//      oout.writeObject(sr);
//      oout.flush();
//      oout.close();

        sr = new MySecureRandom();
        check("unknown", sr);

        InputStream in = new FileInputStream(new File(BASE, "sha1prng-old.bin"));
        ObjectInputStream oin = new ObjectInputStream(in);
        sr = (SecureRandom)oin.readObject();
        oin.close();
        check("unknown", sr);

        in = new FileInputStream(new File(BASE, "sha1prng-new.bin"));
        oin = new ObjectInputStream(in);
        sr = (SecureRandom)oin.readObject();
        oin.close();
        check("SHA1PRNG", sr);

        System.out.println("All tests passed");
    }

    private static void check(String s1, SecureRandom sr) throws Exception {
        String s2 = sr.getAlgorithm();
        if (s1.equals(s2) == false) {
            throw new Exception("Expected " + s1 + ", got " + s2);
        }
    }

    private static class MySecureRandom extends SecureRandom {

    }

}

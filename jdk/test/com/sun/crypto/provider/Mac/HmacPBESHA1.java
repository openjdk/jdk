/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4893959
 * @summary basic test for HmacPBESHA1
 * @author Valerie Peng
 */
import java.io.PrintStream;
import java.util.*;
import java.security.*;
import java.security.spec.*;

import javax.crypto.*;
import javax.crypto.spec.*;

public class HmacPBESHA1 {
    private static final String MAC_ALGO = "HmacPBESHA1";
    private static final String KEY_ALGO = "PBE";
    private static final String PROVIDER = "SunJCE";

    private SecretKey key = null;

    public static void main(String argv[]) throws Exception {
        HmacPBESHA1 test = new HmacPBESHA1();
        test.run();
        System.out.println("Test Passed");
    }

    public void run() throws Exception {
        if (key == null) {
            char[] password = { 't', 'e', 's', 't' };
            PBEKeySpec keySpec = new PBEKeySpec(password);
            SecretKeyFactory kf = SecretKeyFactory.getInstance(KEY_ALGO, PROVIDER);
            key = kf.generateSecret(keySpec);
        }
        Mac mac = Mac.getInstance(MAC_ALGO, PROVIDER);
        byte[] plainText = new byte[30];

        mac.init(key);
        mac.update(plainText);
        byte[] value1 = mac.doFinal();
        if (value1.length != 20) {
            throw new Exception("incorrect MAC output length, " +
                                "expected 20, got " + value1.length);
        }
        mac.update(plainText);
        byte[] value2 = mac.doFinal();
        if (!Arrays.equals(value1, value2)) {
            throw new Exception("generated different MAC outputs");
        }
    }
}

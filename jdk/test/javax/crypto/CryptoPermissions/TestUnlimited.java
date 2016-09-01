/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8061842
 * @summary Package jurisdiction policy files as something other than JAR
 * @run main/othervm TestUnlimited "" exception
 * @run main/othervm TestUnlimited limited fail
 * @run main/othervm TestUnlimited unlimited pass
 * @run main/othervm TestUnlimited unlimited/ pass
 * @run main/othervm TestUnlimited NosuchDir exception
 * @run main/othervm TestUnlimited . exception
 * @run main/othervm TestUnlimited /tmp/unlimited exception
 * @run main/othervm TestUnlimited ../policy/unlimited exception
 * @run main/othervm TestUnlimited ./unlimited exception
 * @run main/othervm TestUnlimited /unlimited exception
 */
import javax.crypto.*;
import java.security.Security;

public class TestUnlimited {

    public static void main(String[] args) throws Exception {
        /*
         * Override the Security property to allow for unlimited policy.
         * Would need appropriate permissions if Security Manager were
         * active.
         */
        if (args.length != 2) {
            throw new Exception("Two args required");
        }

        boolean expected = args[1].equals("pass");
        boolean exception = args[1].equals("exception");
        boolean result = false;

        System.out.println("Testing: " + args[0]);

        if (args[0].equals("\"\"")) {
            Security.setProperty("crypto.policy", "");
        } else {
            Security.setProperty("crypto.policy", args[0]);
        }

        /*
         * Use the AES as the test Cipher
         * If there is an error initializing, we will never get past here.
         */
        try {
            int maxKeyLen = Cipher.getMaxAllowedKeyLength("AES");
            System.out.println("max AES key len:" + maxKeyLen);
            if (maxKeyLen > 128) {
                System.out.println("Unlimited policy is active");
                result = true;
            } else {
                System.out.println("Unlimited policy is NOT active");
                result = false;
            }
        } catch (Throwable e) {
            if (!exception) {
                throw new Exception();
            }
        }

        System.out.println(
                "Expected:\t" + expected + "\nResult:\t\t" + result);
        if (expected != result) {
            throw new Exception();
        }

        System.out.println("DONE!");
    }
}

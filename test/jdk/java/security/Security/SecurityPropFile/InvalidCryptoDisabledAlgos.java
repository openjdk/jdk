/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8244336
 * @summary Check that invalid property values for
 *         "jdk.crypto.disabledAlgorithms" are rejected
 * @run main/othervm InvalidCryptoDisabledAlgos "*"
 * @run main/othervm InvalidCryptoDisabledAlgos "."
 * @run main/othervm InvalidCryptoDisabledAlgos ".AES"
 * @run main/othervm InvalidCryptoDisabledAlgos "Cipher."
 * @run main/othervm InvalidCryptoDisabledAlgos "A.B"
 * @run main/othervm InvalidCryptoDisabledAlgos "KeyStore.MY,."
 * @run main/othervm InvalidCryptoDisabledAlgos "KeyStore.MY,.AES"
 * @run main/othervm InvalidCryptoDisabledAlgos "KeyStore.MY,Cipher."
 * @run main/othervm InvalidCryptoDisabledAlgos "KeyStore.MY,A.B"
 */

import java.security.MessageDigest;
import java.security.Security;

public class InvalidCryptoDisabledAlgos {

    public static void main(String[] args) throws Exception {
        System.out.println("Invalid Property Value = " + args[0]);
        Security.setProperty("jdk.crypto.disabledAlgorithms", args[0]);
        // Trigger the check to parse and validate property value
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            throw new RuntimeException("Should Fail!");
        } catch (ExceptionInInitializerError e) {
            Throwable t = e.getException();
            if (t instanceof IllegalArgumentException) {
                System.out.println("Expected Ex thrown for " + t.getMessage());
            } else {
                // pass it up
                throw e;
            }
        }
    }
}

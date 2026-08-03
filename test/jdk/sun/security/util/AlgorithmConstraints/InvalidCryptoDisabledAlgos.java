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
 * @modules java.base/sun.security.util
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
import java.security.Security;
import sun.security.util.CryptoAlgorithmConstraints;

public class InvalidCryptoDisabledAlgos {

    public static void main(String[] args) throws Exception {
        System.out.println("Invalid Property Value = " + args[0]);
        Security.setProperty("jdk.crypto.disabledAlgorithms", args[0]);
        try {
            // Trigger the check to parse and validate property value
            CryptoAlgorithmConstraints.permits("x", "y");
            throw new AssertionError(
                    "CryptoAlgorithmConstraints.permits() did not generate expected exception");
        } catch (Throwable t) {
            if (!(t instanceof ExceptionInInitializerError)
                    || !(t.getCause() instanceof IllegalArgumentException)) {
                // unexpected exception, propagate it
                throw t;
            }
            // got expected
            System.out.println("Received expected exception: " + t);
        }
    }
}

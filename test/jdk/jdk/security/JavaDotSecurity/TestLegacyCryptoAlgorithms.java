/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.security.Security;

/**
 * @test
 * @bug 8388138
 * @summary Test the default setting of the jdk.crypto.legacyAlgorithms security property
 * @comment This property has a default value of "Cipher.RSA/ECB/PKCS1Padding"
 *          This test assures the default is not changed.
 * @run main TestLegacyCryptoAlgorithms
 */
public class TestLegacyCryptoAlgorithms {

    public static void main(String args[]) throws Exception {
        String value = Security.getProperty("jdk.crypto.legacyAlgorithms");
        if (value == null || !value.equals("Cipher.RSA/ECB/PKCS1Padding")) {
            throw new RuntimeException("Test failed: jdk.crypto.legacyAlgorithms " +
                "security property does not have default value of Cipher.RSA/ECB/PKCS1Padding");
        }
    }
}

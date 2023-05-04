/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8155191
 * @summary check PKCS#11 SecureRandom throws NPE for null output
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main/othervm NextBytesNull
 */

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;

public class NextBytesNull extends PKCS11Test {

    @Override
    public void main(Provider p) throws Exception {
        SecureRandom random;
        try {
            random = SecureRandom.getInstance("PKCS11", p);
        } catch (NoSuchAlgorithmException e) {
            System.out.println("Provider " + p +
                    " does not support PKCS11 SecureRandom, skipping");
            e.printStackTrace();
            return;
        }
        try {
            random.nextBytes(null);
            throw new RuntimeException("Fail: expected NPE not thrown");
        } catch (NullPointerException npe) {
            System.out.println("OK, expected NPE thrown");
        }
    }

    public static void main(String[] args) throws Exception {
        main(new NextBytesNull(), args);
    }
}

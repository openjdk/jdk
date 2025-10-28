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
 * @bug 8356087
 * @summary Ensure P11Mac using SHA message digests can be initialized with
 *     secret keys with unrecognized algorithms
 * @library /test/lib ..
 * @modules jdk.crypto.cryptoki
 * @run main/othervm InitMacWithAnyKey
 */

import java.security.Provider;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class InitMacWithAnyKey extends PKCS11Test {

    public static void main(String[] args) throws Exception {
        main(new InitMacWithAnyKey(), args);
    }

    @Override
    public void main(Provider p) throws Exception {
        SecretKey skey = new SecretKeySpec("whatever".getBytes(), "Any");
        // Test against Hmacs using SHA-1, SHA-2 message digests and skip
        // PBE-related Hmacs as they need PBEKey
        List<String> algorithms = getSupportedAlgorithms("Mac", "HmacSHA", p);
        for (String algo : algorithms) {
            System.out.println("Testing " + algo);
            Mac mac = Mac.getInstance(algo, p);
            try {
                mac.init(skey);
            } catch (Exception e) {
                throw new Exception("Unexpected exception", e);
            }
        }
        System.out.println("Passed");
    }
}

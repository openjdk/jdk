/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7196382
 * @summary Ensure that 2048-bit DH key pairs can be generated
 * @author Valerie Peng
 * @library ..
 * @run main/othervm TestDH2048
 * @run main/othervm TestDH2048 sm
 */

import java.security.InvalidParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;

public class TestDH2048 extends PKCS11Test {

    private static void checkUnsupportedKeySize(KeyPairGenerator kpg, int ks)
        throws Exception {
        try {
            kpg.initialize(ks);
            throw new Exception("Expected IPE not thrown for " + ks);
        } catch (InvalidParameterException ipe) {
        }
    }

    @Override
    public void main(Provider p) throws Exception {
        if (p.getService("KeyPairGenerator", "DH") == null) {
            System.out.println("KPG for DH not supported, skipping");
            return;
        }
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH", p);
        kpg.initialize(2048);
        KeyPair kp1 = kpg.generateKeyPair();
        checkUnsupportedKeySize(kpg, 1536);
        checkUnsupportedKeySize(kpg, 2176);
        checkUnsupportedKeySize(kpg, 3072);
    }

    public static void main(String[] args) throws Exception {
        main(new TestDH2048(), args);
    }
}

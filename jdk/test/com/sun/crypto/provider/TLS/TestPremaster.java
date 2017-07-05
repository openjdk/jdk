/*
 * Copyright (c) 2005, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6313661
 * @summary Basic tests for TlsRsaPremasterSecret generator
 * @author Andreas Sterbenz
 */

import java.security.Security;
import java.security.Provider;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import sun.security.internal.spec.TlsRsaPremasterSecretParameterSpec;

public class TestPremaster {

    public static void main(String[] args) throws Exception {
        Provider provider = Security.getProvider("SunJCE");

        KeyGenerator kg;

        kg = KeyGenerator.getInstance("SunTlsRsaPremasterSecret", provider);

        try {
            kg.generateKey();
            throw new Exception("no exception");
        } catch (IllegalStateException e) {
            System.out.println("OK: " + e);
        }

        test(kg, 3, 0);
        test(kg, 3, 1);
        test(kg, 3, 2);
        test(kg, 4, 0);

        System.out.println("Done.");
    }

    private static void test(KeyGenerator kg, int major, int minor) throws Exception {

        kg.init(new TlsRsaPremasterSecretParameterSpec(major, minor));
        SecretKey key = kg.generateKey();
        byte[] encoded = key.getEncoded();
        if (encoded.length != 48) {
            throw new Exception("length: " + encoded.length);
        }
        if ((encoded[0] != major) || (encoded[1] != minor)) {
            throw new Exception("version mismatch: "  + encoded[0] + "." + encoded[1]);
        }
        System.out.println("OK: " + major + "." + minor);
    }
}

/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8286024 8286422
 * @library /test/lib
 * @summary PKCS12 keystore should show correct SecretKey algorithm names
 */

import jdk.test.lib.Asserts;

import javax.crypto.KeyGenerator;
import java.security.Key;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Map;

public class SecretKeyAlgorithms {
    public static void main(String[] args) throws Exception {
        char[] pass = "changeit".toCharArray();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        var names = Map.of(
                "des", "DES",
                "desede", "DESede",
                "aes", "AES",
                "blowfish", "Blowfish",
                "rc2", "RC2",
                "arcfour", "ARCFOUR");
        for (var alg : names.entrySet()) {
            KeyGenerator g = KeyGenerator.getInstance(alg.getKey());
            Key k = g.generateKey();
            Asserts.assertEQ(k.getAlgorithm(), alg.getValue());
            ks.setKeyEntry(alg.getKey(), k, pass, null);
        }
        for (var alias : Collections.list(ks.aliases())) {
            var k = ks.getKey(alias, pass);
            Asserts.assertEQ(k.getAlgorithm(), names.get(alias));
        }
    }
}

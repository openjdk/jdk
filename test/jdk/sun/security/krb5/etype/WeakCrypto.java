/*
 * Copyright (c) 2010, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6844909 8012679 8139348 8273670
 * @modules java.security.jgss/sun.security.krb5
 *          java.security.jgss/sun.security.krb5.internal.crypto
 * @library /test/lib
 * @run main/othervm WeakCrypto
 */

import jdk.test.lib.Asserts;
import sun.security.krb5.Config;
import sun.security.krb5.EncryptionKey;
import sun.security.krb5.internal.crypto.EType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class WeakCrypto {
    public static void main(String[] args) throws Exception {

        System.setProperty("java.security.krb5.conf", "tmp.conf");

        test(null, null,
                18, 17, 20, 19);    // the defaults
        test(false, null,
                18, 17, 20, 19);    // the defaults
        test(true, null,
                18, 17, 20, 19);    // the defaults

        String strongAndWeak = "aes256-cts aes128-cts aes256-sha2 aes128-sha2" +
                " des3-hmac-sha1 arcfour-hmac des-cbc-crc des-cbc-md5";
        test(null, strongAndWeak, 18, 17, 20, 19);
        test(false, strongAndWeak, 18, 17, 20, 19);
        test(true, strongAndWeak, 18, 17, 20, 19, 16, 23, 1, 3);

        String anotherOrder = "aes256-cts aes256-sha2 aes128-cts aes128-sha2" +
                " des3-hmac-sha1 arcfour-hmac des-cbc-crc des-cbc-md5";
        test(null, anotherOrder, 18, 20, 17, 19);
        test(false, anotherOrder, 18, 20, 17, 19);
        test(true, anotherOrder, 18, 20, 17, 19, 16, 23, 1, 3);

        String two = "aes256-cts arcfour-hmac";
        test(null, two, 18);
        test(false, two, 18);
        test(true, two, 18, 23);
    }

    /**
     * Writes a krb5.conf and makes sure it's correctly parsed.
     *
     * @param allowWeak if "allow_weak_crypto = true" should be written
     * @param etypes redefined "default_tkt_enctypes"
     * @param expected expected etypes
     */
    static void test(Boolean allowWeak, String etypes, int... expected) throws Exception {

        String s = "[libdefaults]\n";
        if (allowWeak != null) {
            s += "allow_weak_crypto = " + allowWeak + "\n";
        }
        if (etypes != null) {
            s += "default_tkt_enctypes = " + etypes;
        }
        Files.write(Path.of("tmp.conf"), s.getBytes(StandardCharsets.UTF_8));
        Config.refresh();

        // Check internal config read
        int[] config = EType.getDefaults("default_tkt_enctypes");
        Asserts.assertTrue(Arrays.equals(config, expected),
                "config: " + Arrays.toString(config));

        // Check actual etypes used
        int[] generated = Arrays.stream(EncryptionKey.acquireSecretKeys(
                        "password".toCharArray(), "salt"))
                .mapToInt(EncryptionKey::getEType)
                .toArray();
        Asserts.assertTrue(Arrays.equals(generated, expected),
                "generated: " + Arrays.toString(generated));
    }
}

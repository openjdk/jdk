/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.SecurityTools;
import jdk.test.lib.security.CertUtils;

import java.security.KeyStore;
import java.security.SecureRandom;

/*
 * @test
 * @bug 6415696 6931562 8180570
 * @requires os.family == "windows"
 * @library /test/lib
 * @summary Test "keytool -changealias" using the Microsoft CryptoAPI provider.
 */
public class KeytoolChangeAlias {
    public static void main(String[] args) throws Exception {
        SecureRandom random = new SecureRandom();
        String alias = Integer.toString(random.nextInt(1000, 8192));
        String newAlias = alias + "1";
        KeyStore ks = KeyStore.getInstance("Windows-MY");
        ks.load(null, null);

        try {
            ks.setCertificateEntry(alias, CertUtils.getCertFromFile("246810.cer"));

            if (ks.containsAlias(newAlias)) {
                ks.deleteEntry(newAlias);
            }

            int before = ks.size();

            ks.store(null, null); // no-op, but let's do it before a keytool command

            SecurityTools.keytool("-changealias",
                    "-storetype", "Windows-My",
                    "-alias", alias,
                    "-destalias", newAlias).shouldHaveExitValue(0);

            ks.load(null, null);

            if (ks.size() != before) {
                throw new Exception("error: unexpected number of entries in the "
                        + "Windows-MY store. Before: " + before
                        + ". After: " + ks.size());
            }

            if (!ks.containsAlias(newAlias)) {
                throw new Exception("error: cannot find the new alias name"
                        + " in the Windows-MY store");
            }
        } finally {
            try {
                ks.deleteEntry(newAlias);
            } catch (Exception e) {
                System.err.println("Couldn't delete alias " + newAlias);
                e.printStackTrace(System.err);
            }
            try {
                ks.deleteEntry(alias);
            } catch (Exception e) {
                System.err.println("Couldn't delete alias " + alias);
                e.printStackTrace(System.err);
            }
            ks.store(null, null);
        }
    }
}

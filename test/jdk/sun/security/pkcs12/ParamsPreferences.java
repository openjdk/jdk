/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.util.ObjectIdentifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static jdk.test.lib.security.DerUtils.*;
import static sun.security.pkcs.ContentInfo.DATA_OID;
import static sun.security.pkcs.ContentInfo.ENCRYPTED_DATA_OID;
import static sun.security.x509.AlgorithmId.*;

/*
 * @test
 * @bug 8076190
 * @library /test/lib
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.x509
 *          java.base/sun.security.util
 * @summary Checks the preferences order of pkcs12 params
 */
public class ParamsPreferences {

    public static final void main(String[] args) throws Exception {
        int c = 0;

        // with storepass
        test(c++, "-", "-",
                pbeWithSHA1AndRC2_40_oid, 50000,
                pbeWithSHA1AndDESede_oid, 50000,
                SHA_oid, 100000);

        // password-less with system property
        test(c++, "keystore.pkcs12.certProtectionAlgorithm", "NONE",
                "keystore.pkcs12.macAlgorithm", "NONE",
                "-", "-",
                null, 0,
                pbeWithSHA1AndDESede_oid, 50000,
                null, 0);

        // password-less with security property
        test(c++, "-",
                "keystore.pkcs12.certProtectionAlgorithm", "NONE",
                "keystore.pkcs12.macAlgorithm", "NONE",
                "-",
                null, 0,
                pbeWithSHA1AndDESede_oid, 50000,
                null, 0);

        // back to with storepass by overriding security property with system property
        test(c++, "keystore.pkcs12.certProtectionAlgorithm", "PBEWithSHA1AndDESede",
                "keystore.pkcs12.macAlgorithm", "HmacPBESHA256",
                "-",
                "keystore.pkcs12.certProtectionAlgorithm", "NONE",
                "keystore.pkcs12.macAlgorithm", "NONE",
                "-",
                pbeWithSHA1AndDESede_oid, 50000,
                pbeWithSHA1AndDESede_oid, 50000,
                SHA256_oid, 100000);

        // back to with storepass by using "" to force hardcoded default
        test(c++, "keystore.pkcs12.certProtectionAlgorithm", "",
                "keystore.pkcs12.keyProtectionAlgorithm", "",
                "keystore.pkcs12.macAlgorithm", "",
                "-",
                "keystore.pkcs12.certProtectionAlgorithm", "NONE",
                "keystore.pkcs12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_40",
                "keystore.pkcs12.macAlgorithm", "NONE",
                "-",
                pbeWithSHA1AndRC2_40_oid, 50000,
                pbeWithSHA1AndDESede_oid, 50000,
                SHA_oid, 100000);

        // change everything with system property
        test(c++, "keystore.pkcs12.certProtectionAlgorithm", "PBEWithSHA1AndDESede",
                "keystore.pkcs12.certPbeIterationCount", 3000,
                "keystore.pkcs12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_40",
                "keystore.pkcs12.keyPbeIterationCount", 4000,
                "keystore.pkcs12.macAlgorithm", "HmacPBESHA256",
                "keystore.pkcs12.macIterationCount", 2000,
                "-", "-",
                pbeWithSHA1AndDESede_oid, 3000,
                pbeWithSHA1AndRC2_40_oid, 4000,
                SHA256_oid, 2000);

        // change everything with security property
        test(c++, "-",
                "keystore.pkcs12.certProtectionAlgorithm", "PBEWithSHA1AndDESede",
                "keystore.pkcs12.certPbeIterationCount", 3000,
                "keystore.pkcs12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_40",
                "keystore.pkcs12.keyPbeIterationCount", 4000,
                "keystore.pkcs12.macAlgorithm", "HmacPBESHA256",
                "keystore.pkcs12.macIterationCount", 2000,
                "-",
                pbeWithSHA1AndDESede_oid, 3000,
                pbeWithSHA1AndRC2_40_oid, 4000,
                SHA256_oid, 2000);

        // override security property with system property
        test(c++, "keystore.pkcs12.certProtectionAlgorithm", "PBEWithSHA1AndDESede",
                "keystore.pkcs12.certPbeIterationCount", 13000,
                "keystore.pkcs12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_40",
                "keystore.pkcs12.keyPbeIterationCount", 14000,
                "keystore.pkcs12.macAlgorithm", "HmacPBESHA256",
                "keystore.pkcs12.macIterationCount", 12000,
                "-",
                "keystore.pkcs12.certProtectionAlgorithm", "PBEWithSHA1AndRC2_40",
                "keystore.pkcs12.certPbeIterationCount", 3000,
                "keystore.pkcs12.keyProtectionAlgorithm", "PBEWithSHA1AndDESede",
                "keystore.pkcs12.keyPbeIterationCount", 4000,
                "keystore.pkcs12.macAlgorithm", "HmacPBESHA1",
                "keystore.pkcs12.macIterationCount", 2000,
                "-",
                pbeWithSHA1AndDESede_oid, 13000,
                pbeWithSHA1AndRC2_40_oid, 14000,
                SHA256_oid, 12000);

        // check keyProtectionAlgorithm old behavior. Preferences of
        // 4 different settings.

        test(c++, "-",
                "keystore.PKCS12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_128",
                "-",
                pbeWithSHA1AndRC2_40_oid, 50000,
                pbeWithSHA1AndRC2_128_oid, 50000,
                SHA_oid, 100000);
        test(c++, "-",
                "keystore.PKCS12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_128",
                "keystore.pkcs12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_40",
                "-",
                pbeWithSHA1AndRC2_40_oid, 50000,
                pbeWithSHA1AndRC2_40_oid, 50000,
                SHA_oid, 100000);
        test(c++,
                "keystore.PKCS12.keyProtectionAlgorithm", "PBEWithSHA1AndRC4_128",
                "-",
                "keystore.PKCS12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_128",
                "keystore.pkcs12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_40",
                "-",
                pbeWithSHA1AndRC2_40_oid, 50000,
                pbeWithSHA1AndRC4_128_oid, 50000,
                SHA_oid, 100000);
        test(c++,
                "keystore.PKCS12.keyProtectionAlgorithm", "PBEWithSHA1AndRC4_128",
                "keystore.pkcs12.keyProtectionAlgorithm", "PBEWithSHA1AndRC4_40",
                "-",
                "keystore.PKCS12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_128",
                "keystore.pkcs12.keyProtectionAlgorithm", "PBEWithSHA1AndRC2_40",
                "-",
                pbeWithSHA1AndRC2_40_oid, 50000,
                pbeWithSHA1AndRC4_40_oid, 50000,
                SHA_oid, 100000);
    }

    /**
     * Run once.
     *
     * @param args an array containing system properties and values, "-",
     *             security properties and values, "-", expected certPbeAlg,
     *             certPbeIC, keyPbeAlg, keyPbeIc, macAlg, macIC.
     */
    static void test(int n, Object... args) throws Exception {
        boolean isSysProp = true;
        String cmd = "-keystore ks" + n + " -genkeypair -keyalg EC "
                + "-alias a -dname CN=A -storepass changeit "
                + "-J-Djava.security.properties=" + n + ".conf";
        List<String> jsConf = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (isSysProp) {
                if (args[i].equals("-")) {
                    isSysProp = false;
                } else {
                    cmd += " -J-D" + args[i] + "=" + args[++i];
                }
            } else {
                if (args[i] == "-") {
                    Files.write(Path.of(n + ".conf"), jsConf);
                    System.out.println("--------- test starts ----------");
                    System.out.println(jsConf);
                    SecurityTools.keytool(cmd).shouldHaveExitValue(0);

                    byte[] data = Files.readAllBytes(Path.of("ks" + n));

                    // cert pbe alg + ic
                    if (args[i+1] == null) {
                        checkAlg(data, "110c10", DATA_OID);
                    } else {
                        checkAlg(data, "110c10", ENCRYPTED_DATA_OID);
                        checkAlg(data, "110c110110", (ObjectIdentifier)args[i+1]);
                        checkInt(data, "110c1101111", (int)args[i+2]);
                    }

                    // key pbe alg + ic
                    checkAlg(data, "110c010c01000", (ObjectIdentifier)args[i+3]);
                    checkInt(data, "110c010c010011", (int)args[i+4]);

                    // mac alg + ic
                    if (args[i+5] == null) {
                        shouldNotExist(data, "2");
                    } else {
                        checkAlg(data, "2000", (ObjectIdentifier)args[i+5]);
                        checkInt(data, "22", (int)args[i+6]);
                    }
                } else {
                    jsConf.add(args[i] + "=" + args[++i]);
                }
            }
        }
    }
}

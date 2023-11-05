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
 * @bug 8286069
 * @summary keytool prints out wrong key algorithm for -importpass command
 * @library /test/lib
 * @modules java.base/sun.security.util
 */

import jdk.test.lib.SecurityTools;
import jdk.test.lib.security.DerUtils;
import sun.security.util.KnownOIDs;

import java.nio.file.Files;
import java.nio.file.Path;

public class ImportPassKeyAlg {
    public static void main(String[] args) throws Exception {
        // Default or "PBE" uses default algorithms defined in java.security
        importpass("def", null, KnownOIDs.PBES2,
                KnownOIDs.HmacSHA256, KnownOIDs.AES_256$CBC$NoPadding);
        importpass("pbe", "PBE", KnownOIDs.PBES2,
                KnownOIDs.HmacSHA256, KnownOIDs.AES_256$CBC$NoPadding);
        // You can use other algorithms as well
        importpass("pbes2", "PBEWithHmacSHA1AndAES_128",
                KnownOIDs.PBES2, KnownOIDs.HmacSHA1, KnownOIDs.AES_128$CBC$NoPadding);
        importpass("des", "PBEwithMD5andDES", KnownOIDs.PBEWithMD5AndDES);
        importpass("3des", "PBEWithSHA1AndDESede", KnownOIDs.PBEWithSHA1AndDESede);
    }

    /**
     * Run `keytool -importpass`.
     *
     * @param name keystore name
     * @param algorithm -keyalg option value, null if not provided
     * @param oids expected OIDs inside keystore, if PBES2, plus prf and enc OIDs
     * @throws Exception
     */
    static void importpass(String name, String algorithm, KnownOIDs... oids) throws Exception {

        Files.deleteIfExists(Path.of(name));

        var cmd = "-keystore " + name + " -storepass changeit -importpass -v -alias a";
        if (algorithm != null) {
            cmd += " -keyalg " + algorithm;
        }

        SecurityTools.setResponse("changeit\nchangeit\n");
        SecurityTools.keytool(cmd)
                .shouldHaveExitValue(0)
                .shouldContain("Generated PBE secret key");

        // The algorithm id of a protected entry (at 110c010c01010c0 inside p12) is:
        //
        // 0000:002A  [] SEQUENCE
        // 0002:000C  [0]     OID 1.2.840.113549.1.12.1.3 (PBEWithSHA1AndDESede)
        // 000E:001C  [1]     SEQUENCE
        // 0010:0016  [10]         OCTET STRING
        // 0026:0004  [11]         INTEGER 10000
        //
        // or
        //
        // 0000:0068  [] SEQUENCE
        // 0002:000B  [0]     OID 1.2.840.113549.1.5.13 (PBES2)
        // 000D:005B  [1]     SEQUENCE
        // 000F:003A  [10]         SEQUENCE
        // 0011:000B  [100]             OID 1.2.840.113549.1.5.12 (PBKDF2WithHmacSHA1)
        // 001C:002D  [101]             SEQUENCE
        // 001E:0016  [1010]                 OCTET STRING
        // 0034:0004  [1011]                 INTEGER 10000
        // 0038:0003  [1012]                 INTEGER 16
        // 003B:000E  [1013]                 SEQUENCE
        // 003D:000A  [10130]                     OID 1.2.840.113549.2.7 (HmacSHA1)
        // 0047:0002  [10131]                     NULL
        // 0049:001F  [11]         SEQUENCE
        // 004B:000B  [110]             OID 2.16.840.1.101.3.4.1.2 (AES_128/CBC/NoPadding)
        // 0056:0012  [111]             OCTET STRING
        var data = Files.readAllBytes(Path.of(name));
        DerUtils.checkAlg(data, "110c010c01010c00", oids[0]);
        if (oids[0] == KnownOIDs.PBES2) {
            DerUtils.checkAlg(data, "110c010c01010c010130", oids[1]);
            DerUtils.checkAlg(data, "110c010c01010c0110", oids[2]);
        }
    }
}

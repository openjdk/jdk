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
 * @bug 8343232
 * @summary Verify correctness of the structure of PKCS12 PBMAC1
 *          keystores created with various property values.
 *          Verify that keystores load correctly from an input stream.
 * @modules java.base/sun.security.util
 * @library /test/lib
 */
import jdk.test.lib.Asserts;
import jdk.test.lib.security.DerUtils;
import sun.security.util.KnownOIDs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class PBMAC1Test {

    static final char[] PASSWORD = "1234".toCharArray();

    public static void main(String[] args) throws Exception {
        create();
        migrate();
        overflow();
    }

    // PBMAC1 inside PKCS12
    //0019:008B  [2]     SEQUENCE
    //001C:007B  [20]         SEQUENCE
    //001E:0057  [200]             SEQUENCE
    //0020:000B  [2000]                 OID 1.2.840.113549.1.5.14 (PBMAC1)
    //002B:004A  [2001]                 SEQUENCE
    //002D:003A  [20010]                     SEQUENCE
    //002F:000B  [200100]                         OID 1.2.840.113549.1.5.12 (PBKDF2)
    //003A:002D  [200101]                         SEQUENCE
    //003C:0016  [2001010]                             OCTET STRING (20 bytes of salt)
    //0052:0004  [2001011]                             INTEGER 10000
    //0056:0003  [2001012]                             INTEGER 32
    //0059:000E  [2001013]                             SEQUENCE
    //005B:000A  [20010130]                                 OID 1.2.840.113549.2.9 (HmacSHA256)
    //0065:0002  [20010131]                                 NULL
    //0067:000E  [20011]                     SEQUENCE
    //0069:000A  [200110]                         OID 1.2.840.113549.2.9 (HmacSHA256)
    //0073:0002  [200111]                         NULL
    //0075:0022  [201]             OCTET STRING (32 bytes of mac)
    //0097:000A  [21]         OCTET STRING (8 bytes of useless salt)
    //00A1:0003  [22]         INTEGER 1
    static void create() throws Exception {
        System.setProperty("keystore.pkcs12.macAlgorithm", "pbewithhmacsha256");
        var der = emptyP12();
        DerUtils.checkAlg(der, "2000", KnownOIDs.PBMAC1);
        DerUtils.checkAlg(der, "200100", KnownOIDs.PBKDF2);
        DerUtils.checkAlg(der, "20010130", KnownOIDs.HmacSHA256);
        DerUtils.checkAlg(der, "200110", KnownOIDs.HmacSHA256);
        DerUtils.checkInt(der, "2001011", 10000);
        DerUtils.checkInt(der, "2001012", 32);

        System.setProperty("keystore.pkcs12.macAlgorithm", "PBEWITHHMACSHA512");
        der = emptyP12();
        DerUtils.checkAlg(der, "2000", KnownOIDs.PBMAC1);
        DerUtils.checkAlg(der, "200100", KnownOIDs.PBKDF2);
        DerUtils.checkAlg(der, "20010130", KnownOIDs.HmacSHA512);
        DerUtils.checkAlg(der, "200110", KnownOIDs.HmacSHA512);
        DerUtils.checkInt(der, "2001011", 10000);
        DerUtils.checkInt(der, "2001012", 64);

        System.setProperty("keystore.pkcs12.macAlgorithm", "PBEWiThHmAcSHA512/224");
        der = emptyP12();
        DerUtils.checkAlg(der, "2000", KnownOIDs.PBMAC1);
        DerUtils.checkAlg(der, "200100", KnownOIDs.PBKDF2);
        DerUtils.checkAlg(der, "20010130", KnownOIDs.HmacSHA512$224);
        DerUtils.checkAlg(der, "200110", KnownOIDs.HmacSHA512$224);
        DerUtils.checkInt(der, "2001011", 10000);
        DerUtils.checkInt(der, "2001012", 28);

        // As strange as I can...
        System.setProperty("keystore.pkcs12.macAlgorithm",
                "PBEWithHmacSHA512/224AndHmacSHA3-384");
        der = emptyP12();
        DerUtils.checkAlg(der, "2000", KnownOIDs.PBMAC1);
        DerUtils.checkAlg(der, "200100", KnownOIDs.PBKDF2);
        DerUtils.checkAlg(der, "20010130", KnownOIDs.HmacSHA512$224);
        DerUtils.checkAlg(der, "200110", KnownOIDs.HmacSHA3_384);
        DerUtils.checkInt(der, "2001011", 10000);
        DerUtils.checkInt(der, "2001012", 48);

        // Bad alg names
        System.setProperty("keystore.pkcs12.macAlgorithm", "PBEWithHmacSHA456");
        var reason = Asserts.assertThrows(NoSuchAlgorithmException.class,
                () -> emptyP12()).getMessage();
        Asserts.assertTrue(reason.contains("Algorithm hmacsha456 not available"), reason);

        // Verify that DEFAULT HmacSHA1 prf does not get encoded.
        System.setProperty("keystore.pkcs12.macAlgorithm", "PBEWITHHMACSHA1");
        der = emptyP12();
        DerUtils.checkAlg(der, "2000", KnownOIDs.PBMAC1);
        DerUtils.checkAlg(der, "200100", KnownOIDs.PBKDF2);
        DerUtils.shouldNotExist(der, "20010130");
        DerUtils.checkAlg(der, "200110", KnownOIDs.HmacSHA1);
        DerUtils.checkInt(der, "2001011", 10000);
        DerUtils.checkInt(der, "2001012", 20);
    }

    static void migrate() throws Exception {
        // A pkcs12 file using PBEWithHmacSHA256 but key length is 8
        var sha2p12 = """
                MIGhAgEDMBEGCSqGSIb3DQEHAaAEBAIwADCBiDB5MFUGCSqGSIb3DQEFDjBIMDgGCSqGSIb3DQEF
                DDArBBSV6e5xI+9AYtGHQlDI0X4pmvWLBQICJxACAQgwDAYIKoZIhvcNAgkFADAMBggqhkiG9w0C
                CQUABCAaaSO6JgEh1lDo1pvAC0CF5HqgIFBvzt1+GZlgFy7xFQQITk9UIFVTRUQCAQE=
                """;
        var der = Base64.getMimeDecoder().decode(sha2p12);
        DerUtils.checkInt(der, "2001012", 8); // key length used to be 8

        der = loadAndStore(sha2p12);
        DerUtils.checkAlg(der, "20010130", KnownOIDs.HmacSHA256);
        DerUtils.checkAlg(der, "200110", KnownOIDs.HmacSHA256);
        DerUtils.checkInt(der, "2001012", 32); // key length changed to 32
    }

    static void overflow() throws Exception {

        // Cannot create new
        System.setProperty("keystore.pkcs12.macIterationCount", "5000001");
        System.setProperty("keystore.pkcs12.macAlgorithm", "pbewithhmacsha256");
        Asserts.assertThrows(IllegalArgumentException.class, PBMAC1Test::emptyP12);
        System.clearProperty("keystore.pkcs12.macAlgorithm");
        Asserts.assertThrows(IllegalArgumentException.class, PBMAC1Test::emptyP12);

        // IC=5000001 using old algorithm
        var bigICt = """
                MGYCAQMwEQYJKoZIhvcNAQcBoAQEAjAAME4wMTANBglghkgBZQMEAgEFAAQgyLBK5h9/E/2o7l2A
                eALbI1otiS8kT3C41Ef3T38OMjUEFIic7isrAJNr+3+8fUbnMtmB0qytAgNMS0E=
                """;

        // IC=5000000 using old algorithm
        var smallICt = """
                MGYCAQMwEQYJKoZIhvcNAQcBoAQEAjAAME4wMTANBglghkgBZQMEAgEFAAQgR61YZLW6H81rkGTk
                XfuU138mkIugdoQBhuNsnvWuBtQEFJ0wmMlpoUiji8PlvwCrmMbqWW4XAgNMS0A=
                """;

        // IC=5000001 using PBMAC1
        var bigICp = """
                MIGiAgEDMBEGCSqGSIb3DQEHAaAEBAIwADCBiTB6MFYGCSqGSIb3DQEFDjBJMDkGCSqGSIb3DQEF
                DDAsBBQFNf/gHCO5jNT429D6Q5gxTKHqVAIDTEtBAgEgMAwGCCqGSIb3DQIJBQAwDAYIKoZIhvcN
                AgkFAAQgwEVMcyMPQXJSXUIbWqNWjMArtnXDlNUGnKD+19B7QFkECE5PVCBVU0VEAgEB
                """;

        // IC=5000000 using PBMAC1
        var smallICp = """
                MIGiAgEDMBEGCSqGSIb3DQEHAaAEBAIwADCBiTB6MFYGCSqGSIb3DQEFDjBJMDkGCSqGSIb3DQEF
                DDAsBBS/ZFfC7swsDHvaCXwyQkuMrZ7dbgIDTEtAAgEgMAwGCCqGSIb3DQIJBQAwDAYIKoZIhvcN
                AgkFAAQgCRvE7LDbzkcYOVv/7iBv0KB3DoUkwnpTI0nsonVfv9UECE5PVCBVU0VEAgEB""";

        loadAndStore(smallICp);
        loadAndStore(smallICt);

        Asserts.assertTrue(Asserts.assertThrows(IOException.class, () -> loadAndStore(bigICp))
                .getMessage().contains("MAC iteration count too large: 5000001"));
        Asserts.assertTrue(Asserts.assertThrows(IOException.class, () -> loadAndStore(bigICt))
                .getMessage().contains("MAC iteration count too large: 5000001"));

        // Incorrect Salt
        var incorrectSalt = """
                MIGdAgEDMBEGCSqGSIb3DQEHAaAEBAIwADCBhDB1MFEGCSqGSIb3DQEFDjBEMDYGCSqGSIb3DQEF
                DDApBBSakVhBLltKvqUj6EAxvWqJi+gc7AICJxACASAwCgYIKoZIhvcNAgkwCgYIKoZIhvcNAgkE
                IG+euEHE8iN/2C7txbCjCJ9mU4TgEsHPsC9L3Rxa7malBAhOT1QgVVNFRAIBAQ==""";
        Asserts.assertTrue(Asserts.assertThrows(IOException.class, () -> loadAndStore(incorrectSalt))
                .getMessage().contains("Integrity check failed"));

        // Incorrect Iteration Count
        var incorrectIC = """
                MIGdAgEDMBEGCSqGSIb3DQEHAaAEBAIwADCBhDB1MFEGCSqGSIb3DQEFDjBEMDYGCSqGSIb3DQEF
                DDApBBSZkVhBLltKvqUj6EAxvWqJi+gc7AICKBACASAwCgYIKoZIhvcNAgkwCgYIKoZIhvcNAgkE
                IG+euEHE8iN/2C7txbCjCJ9mU4TgEsHPsC9L3Rxa7malBAhOT1QgVVNFRAIBAQ==""";
        Asserts.assertTrue(Asserts.assertThrows(IOException.class, () -> loadAndStore(incorrectIC))
                .getMessage().contains("Integrity check failed"));

        // Missing Key Length
        var missingKeyLength = """
                MIGaAgEDMBEGCSqGSIb3DQEHAaAEBAIwADCBgTByME4GCSqGSIb3DQEFDjBBMDMGCSqGSIb3DQEF
                DDAmBBSZkVhBLltKvqUj6EAxvWqJi+gc7AICJxAwCgYIKoZIhvcNAgkwCgYIKoZIhvcNAgkEIG+e
                uEHE8iN/2C7txbCjCJ9mU4TgEsHPsC9L3Rxa7malBAhOT1QgVVNFRAIBAQ==""";
        Asserts.assertTrue(Asserts.assertThrows(IOException.class, () -> loadAndStore(missingKeyLength))
                .getMessage().contains("missing keyLength field"));
    }

    static byte[] emptyP12() throws Exception {
        var ks = KeyStore.getInstance("pkcs12");
        ks.load(null, null);
        var os = new ByteArrayOutputStream();
        ks.store(os, PASSWORD);
        return os.toByteArray();
    }

    static byte[] loadAndStore(String data) throws Exception {
        var bytes = Base64.getMimeDecoder().decode(data);
        var ks = KeyStore.getInstance("PKCS12");
        ks.load(new ByteArrayInputStream(bytes), PASSWORD);
        var baos = new ByteArrayOutputStream();
        ks.store(baos, PASSWORD);
        var newBytes = baos.toByteArray();
        var bais = new ByteArrayInputStream(newBytes);
        ks.load(bais, PASSWORD);
        return newBytes;
    }
}

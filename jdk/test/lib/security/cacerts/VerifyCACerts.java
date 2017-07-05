/*
 * Copyright 2002-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug 4400624 6321453 6728890 6732157 6754779 6768559
 * @summary Make sure all self-signed root cert signatures are valid
 */
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.*;
import java.util.*;

public class VerifyCACerts {

    private final static String cacertsFileName =
        System.getProperty("java.home") +
        System.getProperty("file.separator") + "lib" +
        System.getProperty("file.separator") + "security" +
        System.getProperty("file.separator") + "cacerts";

    private static boolean atLeastOneFailed = false;

    private static MessageDigest md;

    // map of cert alias to SHA1 fingerprint
    private static Map<String, String> fpMap = new HashMap<String, String>();

    private static String[][] entries = {
        { "swisssignsilverg2ca", "9B:AA:E5:9F:56:EE:21:CB:43:5A:BE:25:93:DF:A7:F0:40:D1:1D:CB"},
        { "swisssigngoldg2ca", "D8:C5:38:8A:B7:30:1B:1B:6E:D4:7A:E6:45:25:3A:6F:9F:1A:27:61"},
        { "swisssignplatinumg2ca", "56:E0:FA:C0:3B:8F:18:23:55:18:E5:D3:11:CA:E8:C2:43:31:AB:66"},
        { "verisigntsaca", "BE:36:A4:56:2F:B2:EE:05:DB:B3:D3:23:23:AD:F4:45:08:4E:D6:56"},
        { "camerfirmachambersignca", "4A:BD:EE:EC:95:0D:35:9C:89:AE:C7:52:A1:2C:5B:29:F6:D6:AA:0C"},
        { "camerfirmachambersca", "78:6A:74:AC:76:AB:14:7F:9C:6A:30:50:BA:9E:A8:7E:FE:9A:CE:3C"},
        { "camerfirmachamberscommerceca", "6E:3A:55:A4:19:0C:19:5C:93:84:3C:C0:DB:72:2E:31:30:61:F0:B1"},
        { "deutschetelekomrootca2", "85:A4:08:C0:9C:19:3E:5D:51:58:7D:CD:D6:13:30:FD:8C:DE:37:BF"},
    };

    static {
        for (String[] entry : entries) {
            fpMap.put(entry[0], entry[1]);
        }
    };

    public static void main(String[] args) throws Exception {
        md = MessageDigest.getInstance("SHA1");
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(cacertsFileName), "changeit".toCharArray());

        // check that all entries in the map are in the keystore
        for (String alias : fpMap.keySet()) {
            if (!ks.isCertificateEntry(alias)) {
                atLeastOneFailed = true;
                System.err.println(alias + " is not in cacerts");
            }
        }
        // pull all the trusted self-signed CA certs out of the cacerts file
        // and verify their signatures
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            System.out.println("Verifying " + alias);
            if (!ks.isCertificateEntry(alias)) {
                atLeastOneFailed = true;
                System.err.println(alias + " is not a trusted cert entry");
            }
            Certificate cert = ks.getCertificate(alias);
            // remember the GTE CyberTrust CA cert for further tests
            if (alias.equals("gtecybertrustca")) {
                atLeastOneFailed = true;
                System.err.println
                    ("gtecybertrustca is expired and should be deleted");
            }
            cert.verify(cert.getPublicKey());
            if (!checkFingerprint(alias, cert)) {
                atLeastOneFailed = true;
                System.err.println
                    (alias + " SHA1 fingerprint is incorrect");
            }
        }

        if (atLeastOneFailed) {
            throw new Exception("At least one cacert test failed");
        }
    }

    private static boolean checkFingerprint(String alias, Certificate cert)
        throws Exception {
        String fingerprint = fpMap.get(alias);
        if (fingerprint == null) {
            // no entry for alias
            return true;
        }
        System.out.println("Checking fingerprint of " + alias);
        byte[] digest = md.digest(cert.getEncoded());
        return fingerprint.equals(toHexString(digest));
    }

    private static String toHexString(byte[] block) {
        StringBuffer buf = new StringBuffer();
        int len = block.length;
        for (int i = 0; i < len; i++) {
             byte2hex(block[i], buf);
             if (i < len-1) {
                 buf.append(":");
             }
        }
        return buf.toString();
    }

    private static void byte2hex(byte b, StringBuffer buf) {
        char[] hexChars = { '0', '1', '2', '3', '4', '5', '6', '7', '8',
                            '9', 'A', 'B', 'C', 'D', 'E', 'F' };
        int high = ((b & 0xf0) >> 4);
        int low = (b & 0x0f);
        buf.append(hexChars[high]);
        buf.append(hexChars[low]);
    }
}

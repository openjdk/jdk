/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary Testing PEM decoding
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;

public class PEMEncoderTest {

    static Map<String, DEREncodable> keymap;
    final static Pattern CRLF = Pattern.compile("\r\n");
    final static Pattern CR = Pattern.compile("\r");
    final static Pattern LF = Pattern.compile("\n");


    public static void main(String[] args) throws Exception {
        keymap = generateObjKeyMap(PEMCerts.entryList);
        PEMEncoder encoder = PEMEncoder.of();
        System.out.println("Same instance Encoder test:");
        keymap.keySet().stream().forEach(key -> test(key, encoder));
        System.out.println("New instance Encoder test:");
        keymap.keySet().stream().forEach(key -> test(key, PEMEncoder.of()));
        System.out.println("Same instance Encoder testToString:");
        keymap.keySet().stream().forEach(key -> testToString(key, encoder));
        System.out.println("New instance Encoder testToString:");
        keymap.keySet().stream().forEach(key -> testToString(key, PEMEncoder.of()));
//        System.out.println("All SecurityObjects Same instance Encoder new withEnc test:");
//        keymap.keySet().stream().forEach(key -> testEncrypted(key, encoder));

        keymap = generateObjKeyMap(PEMCerts.encryptedList);
        System.out.println("Same instance Encoder new withEnc test:");
        keymap.keySet().stream().forEach(key -> testEncrypted(key, encoder));
        System.out.println("New instance Encoder and withEnc test:");
        keymap.keySet().stream().forEach(key -> testEncrypted(key, PEMEncoder.of()));
        System.out.println("Same instance Encoder and withEnc test:");
        PEMEncoder encEncoder = encoder.withEncryption(PEMCerts.encryptedList.getFirst().password());
        keymap.keySet().stream().forEach(key -> test(key, encEncoder));
    }

    static Map generateObjKeyMap(List<PEMCerts.Entry> list) {
        Map<String, DEREncodable> keymap = new HashMap<>();
        PEMDecoder pemd = PEMDecoder.of();
        for (PEMCerts.Entry entry : list) {
            try {
                if (entry.password() != null) {
                    keymap.put(entry.name(), pemd.withDecryption(entry.password()).decode(entry.pem()));
                } else {
                    keymap.put(entry.name(), pemd.decode(entry.pem()));
                }
            } catch (Exception e) {
                System.err.println("Verify PEMDecoderTest passes before debugging this test.");
                throw new AssertionError("Failed to initialize map on" +
                    " entry \"" + entry.name() + "\"", e);
            }
        }
        return keymap;
    }

    static void test(String key, PEMEncoder encoder) {
        byte[] result;
        PEMCerts.Entry entry = PEMCerts.getEntry(key);
        try {
            result = encoder.encode(keymap.get(key));
        } catch (IOException e) {
            throw new AssertionError("Encoder use failure with " + entry.name(), e);
        }

        checkResults(entry, new String(result, StandardCharsets.UTF_8));
        System.out.println("PASS: " + entry.name());
    }

    static void testToString(String key, PEMEncoder encoder) {
        String result;
        PEMCerts.Entry entry = PEMCerts.getEntry(key);
        try {
            result = encoder.encodeToString(keymap.get(key));
        } catch (IOException e) {
            throw new AssertionError("Encoder use failure with " + entry.name(), e);
        }

        checkResults(entry, result);
        System.out.println("PASS: " + entry.name());
    }

    static void testEncrypted(String key, PEMEncoder encoder) {
        byte[] result;
        PEMCerts.Entry entry = PEMCerts.getEntry(key);
        try {
            result = encoder.withEncryption(
                (entry.password() != null ? entry.password() : "fish".toCharArray()))
                .encode(keymap.get(key));
        } catch (IOException e) {
            throw new AssertionError("Encrypted encoder use failure with " + entry.name(), e);
        }

        checkResults(entry, new String(result, StandardCharsets.UTF_8));
        System.out.println("PASS: " + entry.name());
    }

    static void checkResults(PEMCerts.Entry entry, String result) {
        String pem = entry.pem();
        // The below matches the \r\n generated PEM with the PEM passed
        // into the test.
        if (pem.indexOf("\r\n") > 0) {
            // Do nothing, generated and passed in PEM must be the same
        } else if (pem.indexOf('\r') > 0) {
            result = CR.matcher(pem).replaceAll("");
        } else if (pem.indexOf('\n') > 0) {
            // This one is strange.  Apparently Pattern("\n") removes both
            // "\r\n".  To compensate, replacing it with "\n" just removes
            // the "\r"
            result = LF.matcher(pem).replaceAll("\n");
        } else {
            // Remove all \r\n to match the passed in pem
            result = CRLF.matcher(pem).replaceAll("");
        }

        try {
            if (pem.compareTo(result) != 0) {
                System.out.println("expected:\n" + pem);
                System.out.println("generated:\n" + result);
                indexDiff(pem, result);
            }
        } catch (AssertionError e) {
            throw new AssertionError("Encoder PEM mismatch " + entry.name(), e);
        }
    }

    static void indexDiff(String a, String b) {
        String lenerr = "";
        int len = a.length();
        int lenb = b.length();
        if (len != lenb) {
            lenerr = ":  Length mismatch: " + len + " vs " + lenb;
            len = Math.min(len, lenb);
        }
        for (int i = 0; i < len; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                throw new AssertionError("Char mistmatch, index #" + i + "  (" + a.charAt(i) + " vs " + b.charAt(i) + ")" + lenerr);
            }
        }
    }
}

/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Asserts;
import jdk.test.lib.json.JSONValue;

import java.security.*;
import java.util.Arrays;

import static jdk.test.lib.Utils.toByteArray;

// JSON spec at https://pages.nist.gov/ACVP/draft-celi-acvp-sha.html
// and https://pages.nist.gov/ACVP/draft-celi-acvp-sha3.html
public class SHA_Test {

    public static void run(JSONValue kat, Provider provider) throws Exception {
        var alg = kat.get("algorithm").asString();
        if (alg.startsWith("SHA2-")) alg = "SHA-" + alg.substring(5);
        var md = provider == null ? MessageDigest.getInstance(alg)
                : MessageDigest.getInstance(alg, provider);
        for (var t : kat.get("testGroups").asArray()) {
            var testType = t.get("testType").asString();
            switch (testType) {
                case "AFT" -> {
                    for (var c : t.get("tests").asArray()) {
                        System.out.print(c.get("tcId").asString() + " ");
                        var msg = toByteArray(c.get("msg").asString());
                        var len = Integer.parseInt(c.get("len").asString());
                        if (msg.length * 8 == len) {
                            Asserts.assertEqualsByteArray(md.digest(msg),
                                    toByteArray(c.get("md").asString()));
                        } else {
                            System.out.print("bits ");
                        }
                    }
                }
                case "MCT" -> {
                    var mctVersion = t.get("mctVersion").asString();
                    var trunc = mctVersion.equals("alternate");
                    for (var c : t.get("tests").asArray()) {
                        System.out.print(c.get("tcId").asString() + " ");
                        var SEED = toByteArray(c.get("msg").asString());
                        var INITIAL_SEED_LENGTH = Integer.parseInt(c.get("len").asString());
                        if (SEED.length * 8 == INITIAL_SEED_LENGTH) {
                            for (var r : c.get("resultsArray").asArray()) {
                                if (alg.startsWith("SHA3-")) {
                                    var MD = SEED;
                                    for (var i = 0; i < 1000; i++) {
                                        if (trunc) {
                                            MD = Arrays.copyOf(MD, INITIAL_SEED_LENGTH / 8);
                                        }
                                        MD = md.digest(MD);
                                    }
                                    Asserts.assertEqualsByteArray(MD,
                                            toByteArray(r.get("md").asString()));
                                    SEED = MD;
                                } else {
                                    var A = SEED;
                                    var B = SEED;
                                    var C = SEED;
                                    byte[] MD = null;
                                    for (var i = 0; i < 1000; i++) {
                                        var MSG = concat(A, B, C);
                                        if (trunc) {
                                            MSG = Arrays.copyOf(MSG, INITIAL_SEED_LENGTH / 8);
                                        }
                                        MD = md.digest(MSG);
                                        A = B;
                                        B = C;
                                        C = MD;
                                    }
                                    Asserts.assertEqualsByteArray(MD,
                                            toByteArray(r.get("md").asString()));
                                    SEED = MD;
                                }
                            }
                        } else {
                            System.out.print("bits ");
                        }
                    }
                }
                case "LDT" -> {
                    for (var c : t.get("tests").asArray()) {
                        System.out.print(c.get("tcId").asString() + " ");
                        var lm = c.get("largeMsg");
                        var ct = toByteArray(lm.get("content").asString());
                        var flen = Long.parseLong(lm.get("fullLength").asString());
                        var clen = Long.parseLong(lm.get("contentLength").asString());
                        var cc = 0L;
                        while (cc < flen) {
                            md.update(ct);
                            cc += clen;
                        }
                        Asserts.assertEqualsByteArray(md.digest(),
                                toByteArray(c.get("md").asString()));
                    }
                }
                default -> throw new UnsupportedOperationException(
                        "Unknown testType: " + testType);
            }
            System.out.println();
        }
    }

    /////////////

    static byte[] concat(byte[]... input) {
        var sum = 0;
        for (var i : input) {
            sum += i.length;
        }
        var out = new byte[sum];
        sum = 0;
        for (var i : input) {
            System.arraycopy(i, 0, out, sum, i.length);
            sum += i.length;
        }
        return out;
    }
}

/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.util.RawKeySpec;

import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;

import static jdk.test.lib.Utils.toByteArray;

// JSON spec at https://pages.nist.gov/ACVP/draft-celi-acvp-lms.html
public class LMS_Test {

    public static void run(JSONValue kat, Provider provider) throws Exception {

        var mode = kat.get("mode").asString();
        if (mode.equals("sigVer")) {
            sigVerTest(kat, provider);
        } else {
            throw new UnsupportedOperationException("Unknown mode: " + mode);
        }
    }

    static void sigVerTest(JSONValue kat, Provider p) throws Exception {
        var s = p == null
                ? Signature.getInstance("HSS/LMS")
                : Signature.getInstance("HSS/LMS", p);
        for (JSONValue t : kat.get("testGroups").elements()) {

            System.out.println(">> " + t.get("lmsMode").asString()
                    + " " + t.get("lmOtsMode").asString() + " verify");

            for (JSONValue c : t.get("tests").elements()) {
                System.out.print(c.get("tcId").asInt() + " ");
                var expected = c.get("testPassed").asBoolean();
                var actual = true;

                // Convert to HSS key by prepending height of tree (1)
                // to the LMS public key.
                RawKeySpec rks = new RawKeySpec(toByteArray(
                        "00000001" + t.get("publicKey").asString()));
                KeyFactory kf = p == null ? KeyFactory.getInstance("HSS/LMS") :
                        KeyFactory.getInstance("HSS/LMS", p);
                PublicKey pk1 = kf.generatePublic(rks);

                try {
                    s.initVerify(pk1);
                    s.update(toByteArray(c.get("message").asString()));
                    // Convert to HSS signature by prepending
                    // Nspk value of 0.
                    actual = s.verify(toByteArray(
                            "00000000" + c.get("signature").asString()));
                } catch (InvalidKeyException | SignatureException e) {
                    actual = false;
                }
                Asserts.assertEQ(expected, actual);
            }
            System.out.println();
        }
    }
}

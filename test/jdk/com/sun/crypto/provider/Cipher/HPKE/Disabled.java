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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.Proc;

import javax.crypto.Cipher;
import javax.crypto.spec.HPKEParameterSpec;
import java.security.PrivateKey;

/*
 * @test
 * @bug 8325448
 * @library /test/lib
 * @summary disabling HPKE algorithm identifiers
 */
public class Disabled {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            // Try if an HPKE cipher can be initialized with HPKEParameterSpec.of(16, 1, 1).
            // When "Cannot encrypt with private key" is seen, alg id check already passed
            test(null).stderrShouldContain("Cannot encrypt with private key");
            test("kem_id=17").stderrShouldContain("Cannot encrypt with private key");
            test("kem_id=17-19").stderrShouldContain("Cannot encrypt with private key");
            test("kem_id=1-15").stderrShouldContain("Cannot encrypt with private key");

            test("kem_id=16").stderrShouldContain("Disabled kem_id: 16");
            test("kem_id=16-19").stderrShouldContain("Disabled kem_id: 16");
            test("kem_id=11-16").stderrShouldContain("Disabled kem_id: 16");
            test("kem_id=0x10").stderrShouldContain("Disabled kem_id: 16");
            test("kem_id=#10").stderrShouldContain("Disabled kem_id: 16");

            test("kem_id=17,kdf_id=2,aead_id=2").stderrShouldContain("Cannot encrypt with private key");
            test("kem_id=16,kdf_id=2,aead_id=2").stderrShouldContain("Disabled kem_id: 16");
            test("kem_id=17,kdf_id=1,aead_id=2").stderrShouldContain("Disabled kdf_id: 1");
            test("kem_id=17,kdf_id=2,aead_id=1").stderrShouldContain("Disabled aead_id: 1");
        } else {
            var c = Cipher.getInstance("HPKE");
            var ak = new PrivateKey() {
                public String getAlgorithm() { return "EC"; }
                public String getFormat() { return null; }
                public byte[] getEncoded() { return null; }
            };
            c.init(Cipher.ENCRYPT_MODE, ak, HPKEParameterSpec.of(16, 1, 1));
        }
    }

    static OutputAnalyzer test(String v) throws Exception {
        var proc = Proc.create("Disabled");
        if (v != null) proc.secprop("jdk.hpke.disabledAlgorithms", v);
        return proc.args("test").start().output();
    }
}

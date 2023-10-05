/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.SecurityTools;
import jdk.test.lib.Utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import static jdk.test.lib.security.XMLUtils.*;
/**
 * @test
 * @bug 8305972
 * @summary Basic tests using XMLUtils
 * @library /test/lib
 * @modules java.xml.crypto
 */
public class Basic {

    public static void main(String[] args) throws Exception {
        var x = "<a><b>c</b>x</a>";
        var p = Files.write(Path.of("x.xml"), List.of(x));
        var b = Path.of("").toUri().toString();
        var d = string2doc(x);
        var pass = "changeit".toCharArray();
        for (String alg: List.of("DSA", "RSA", "RSASSA-PSS", "EC", "EdDSA", "Ed25519", "Ed448")) {
            SecurityTools.keytool(String.format(
                    "-keystore ks -keyalg %s -storepass changeit -genkeypair -alias %s -dname CN=%s",
                    alg, alg, alg)).shouldHaveExitValue(0);
            var ks = KeyStore.getInstance(new File("ks"), pass);
            var c = (X509Certificate) ks.getCertificate(alg);
            var pr = (PrivateKey) ks.getKey(alg, pass);
            var pu = c.getPublicKey();

            var s0 = signer(pr); // No KeyInfo
            var s1 = signer(pr, c); // KeyInfo is X509Data
            var s2 = signer(ks, alg, pass); // KeyInfo is KeyName
            var v1 = validator(); // knows nothing
            var v2 = validator(ks); // knows KeyName

            Utils.runAndCheckException(() -> v1.validate(s0.sign(d)), IllegalArgumentException.class); // need PublicKey
            s0.sign(string2doc(x));
            Asserts.assertTrue(v1.validate(s0.sign(d), pu)); // need PublicKey
            Asserts.assertTrue(v1.validate(s1.sign(d))); // can read KeyInfo
            Asserts.assertTrue(v2.validate(s2.sign(d))); // can read KeyInfo
            Asserts.assertTrue(v2.secureValidation(false).validate(s2.sign(p.toUri()))); // can read KeyInfo
            Asserts.assertTrue(v2.secureValidation(false).baseURI(b).validate(
                    s2.sign(p.toAbsolutePath().getParent().toUri(), p.getFileName().toUri()))); // can read KeyInfo

            Asserts.assertTrue(v1.validate(s0.sign("text"), pu)); // plain text
            Asserts.assertTrue(v1.validate(s0.sign("binary".getBytes()), pu)); // raw data
            Asserts.assertTrue(v1.validate(s0.signEnveloping(d, "x", "#x"), pu));
            Asserts.assertTrue(v1.validate(s0.signEnveloping(d, "x", "#xpointer(id('x'))"), pu));

            // No KeyValue defined for RSASSA-PSS or EdDSA yet
            if (!alg.startsWith("Ed") && !alg.equals("RSASSA-PSS")) {
                var ss = signer(pr, pu); // KeyInfo is PublicKey
                Asserts.assertTrue(v1.validate(ss.sign(d))); // can read KeyInfo
                Asserts.assertTrue(v1.validate(ss.sign("text"))); // plain text
                Asserts.assertTrue(v1.validate(ss.sign("binary".getBytes()))); // raw data
                Asserts.assertTrue(v1.validate(ss.signEnveloping(d, "x", "#x")));
                Asserts.assertTrue(v1.validate(ss.signEnveloping(d, "x", "#xpointer(id('x'))")));
            }
        }
    }

}

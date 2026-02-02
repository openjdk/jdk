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

import jdk.test.lib.Asserts;
import jdk.test.lib.security.SeededSecureRandom;
import jdk.test.lib.security.XMLUtils;
import org.w3c.dom.Document;

import javax.xml.crypto.dsig.XMLSignatureException;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Base64;

/**
 * @test
 * @bug 8359395
 * @summary ensure properties are used
 * @library /test/lib
 */
public class Properties {

    private static final String DOM_SIGNATURE_PROVIDER
            = "org.jcp.xml.dsig.internal.dom.SignatureProvider";
    private static final String DOM_SIGNATURE_RANDOM
            = "jdk.xmldsig.SecureRandom";

    public static void main(String[] args) throws Exception {
        // Do not test on RSA. It's always deterministic.
        test("EC");
        test("RSASSA-PSS");
    }

    static void test(String alg) throws Exception {
        var kp = KeyPairGenerator.getInstance(alg).generateKeyPair();
        var signer = XMLUtils.signer(kp.getPrivate(), kp.getPublic());

        var n1 = getSignature(signer.sign("hello")); // random one
        var n2 = getSignature(signer.sign("hello")); // another random one

        signer.prop(DOM_SIGNATURE_RANDOM, new SeededSecureRandom(1L));
        var s1 = getSignature(signer.sign("hello")); // deterministic one

        signer.prop(DOM_SIGNATURE_RANDOM, new SeededSecureRandom(1L));
        var s1again = getSignature(signer.sign("hello")); // deterministic one repeated

        signer.prop(DOM_SIGNATURE_RANDOM, new SeededSecureRandom(2L));
        var s2 = getSignature(signer.sign("hello")); // deterministic two

        Asserts.assertEqualsByteArray(s1, s1again);
        assertsAllDifferent(n1, n2, s1, s2);

        signer.prop(DOM_SIGNATURE_PROVIDER, Security.getProvider("SunJCE"));
        // Asserts throwing XMLSignatureException with cause NoSuchAlgorithmException
        Asserts.assertEquals(
                Asserts.assertThrows(XMLSignatureException.class,
                        () -> signer.sign("hello")).getCause().getClass(),
                NoSuchAlgorithmException.class);
    }

    private static void assertsAllDifferent(byte[]... inputs) {
        for (var a : inputs) {
            for (var b : inputs) {
                if (a != b) {
                    Asserts.assertNotEqualsByteArray(a, b);
                }
            }
        }
    }

    static byte[] getSignature(Document doc) {
        for (var n = doc.getDocumentElement().getFirstChild();
                n != null; n = n.getNextSibling()) {
            if ("SignatureValue".equals(n.getLocalName())) {
                return Base64.getMimeDecoder().decode(n.getTextContent());
            }
        }
        throw new IllegalArgumentException("Not found");
    }
}

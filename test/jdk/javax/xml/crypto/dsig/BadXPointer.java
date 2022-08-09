/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Utils;
import jdk.test.lib.security.XMLUtils;

import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.dsig.XMLSignatureException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

/**
 * @test
 * @bug 8278186
 * @summary reject malformed xpointer(id('a')) gracefully
 * @library /test/lib
 * @modules java.xml.crypto
 */
public class BadXPointer {

    public static void main(String[] args) throws Exception {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = kpg.generateKeyPair();

        var signer = XMLUtils.signer(kp.getPrivate(), kp.getPublic());
        var doc = XMLUtils.string2doc("<root/>");

        // No enclosing ' for id
        Utils.runAndCheckException(
                () -> signer.signEnveloping(doc, "a", "#xpointer(id('a))"),
                ex -> Asserts.assertTrue(ex instanceof XMLSignatureException
                        && ex.getCause() instanceof URIReferenceException
                        && ex.getMessage().contains("Could not find a resolver"),
                    ex.toString()));
    }
}

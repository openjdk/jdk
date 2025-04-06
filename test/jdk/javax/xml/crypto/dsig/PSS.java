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
import jdk.test.lib.security.XMLUtils;

import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.spec.RSAPSSParameterSpec;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;

/**
 * @test
 * @bug 8344137
 * @summary check RSASSA-PSS key
 * @library /test/lib
 * @modules java.xml.crypto
 */
public class PSS {

    public static void main(String[] args) throws Exception {

        var doc = XMLUtils.string2doc("<a><b>Text</b>Raw</a>");
        var kpg = KeyPairGenerator.getInstance("RSASSA-PSS");
        kpg.initialize(2048);
        var keyPair = kpg.generateKeyPair();

        var pspec = new PSSParameterSpec("SHA-384", "MGF1",
                MGF1ParameterSpec.SHA512, 48,
                PSSParameterSpec.TRAILER_FIELD_BC);

        var signed = XMLUtils.signer(keyPair.getPrivate(), keyPair.getPublic())
                .dm(DigestMethod.SHA384)
                .sm(SignatureMethod.RSA_PSS, new RSAPSSParameterSpec(pspec))
                .sign(doc);

        Asserts.assertTrue(XMLUtils.validator().validate(signed));
    }
}

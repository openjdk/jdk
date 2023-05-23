/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import sun.security.x509.CRLExtensions;
import sun.security.x509.CRLNumberExtension;
import sun.security.x509.X500Name;
import sun.security.x509.X509CRLImpl;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.cert.X509CRLSelector;
import java.util.Date;

/**
 * @test
 * @bug 8296399
 * @summary crlNumExtVal might be null inside X509CRLSelector::match
 * @library /test/lib
 * @modules java.base/sun.security.x509
 */

public class CRLNumberMissing {

    public static void main(String[] args) throws Exception {

        var pk = KeyPairGenerator.getInstance("Ed25519")
                .generateKeyPair().getPrivate();

        var crlWithoutNum = X509CRLImpl.newSigned(
                new X509CRLImpl.TBSCertList(
                        new X500Name("CN=CRL"), new Date(), new Date()),
                pk, "Ed25519");

        var exts = new CRLExtensions();
        exts.setExtension("CRLNumber", new CRLNumberExtension(1));
        var crlWithNum = X509CRLImpl.newSigned(
                new X509CRLImpl.TBSCertList(
                        new X500Name("CN=CRL"), new Date(), new Date(),
                        null, exts),
                pk, "Ed25519");

        var sel = new X509CRLSelector();
        Asserts.assertTrue(sel.match(crlWithNum));
        Asserts.assertTrue(sel.match(crlWithoutNum));

        sel = new X509CRLSelector();
        sel.setMinCRLNumber(BigInteger.ZERO);
        Asserts.assertTrue(sel.match(crlWithNum));
        Asserts.assertFalse(sel.match(crlWithoutNum));

        sel = new X509CRLSelector();
        sel.setMinCRLNumber(BigInteger.TWO);
        Asserts.assertFalse(sel.match(crlWithNum));
        Asserts.assertFalse(sel.match(crlWithoutNum));
    }
}

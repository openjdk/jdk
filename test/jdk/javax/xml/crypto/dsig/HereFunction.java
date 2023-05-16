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

/**
 * @test
 * @bug 8305972
 * @summary Demonstrate here() support for validating XML Signatures
 * @modules java.base/sun.security.util
 *          java.base/sun.security.x509
 *          java.xml.crypto/org.jcp.xml.dsig.internal.dom
 * @library /test/lib
 * @compile -XDignore.symbol.file KeySelectors.java SignatureValidator.java
 *     X509KeySelector.java ValidationTests.java
 * @run main/othervm HereFunction default true
 * @run main/othervm HereFunction true true
 * @run main/othervm HereFunction false false
 */
import java.io.File;
import java.io.FileInputStream;
import java.security.*;
import javax.xml.crypto.Data;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.OctetStreamData;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.URIReference;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.XMLSignatureFactory;

import jdk.test.lib.security.SecurityUtils;

public class HereFunction {

    private static SignatureValidator validator;
    private final static String DIR = System.getProperty("test.src", ".");
    private final static String DATA_DIR =
            DIR + System.getProperty("file.separator") + "data";
    private final static String KEYSTORE =
            DATA_DIR + System.getProperty("file.separator") + "certs" +
                    System.getProperty("file.separator") + "xmldsig.jks";
    private final static String STYLESHEET =
            "http://www.w3.org/TR/xml-stylesheet";
    private final static String STYLESHEET_B64 =
            "http://www.w3.org/Signature/2002/04/xml-stylesheet.b64";

    public static void main(String args[]) throws Exception {

        if (!args[0].equals("default")) {
            Security.setProperty("jdk.xml.dsig.hereFunctionSupported", args[0]);
        }
        boolean expected = Boolean.parseBoolean(args[1]);

        // Re-enable sha1 algs
        SecurityUtils.removeAlgsFromDSigPolicy("sha1");

        validator = new SignatureValidator(new File(DATA_DIR));

        KeyStore keystore = KeyStore.getInstance("JKS");
        KeySelector ks;
        try (FileInputStream fis = new FileInputStream(KEYSTORE)) {
            keystore.load(fis, "changeit".toCharArray());
            ks = new X509KeySelector(keystore, false);
        }

        boolean actual = validator.validate(
                "signature.xml", ks, new HttpURIDereferencer(), false);

        if (actual != expected) {
            throw new Exception("Expected: " + expected + ", actual: " + actual);
        }
    }

    /**
     * This URIDereferencer returns locally cached copies of http content to
     * avoid test failures due to network glitches, etc.
     */
    private static class HttpURIDereferencer implements URIDereferencer {
        private URIDereferencer defaultUd;

        HttpURIDereferencer() {
            defaultUd = XMLSignatureFactory.getInstance().getURIDereferencer();
        }

        public Data dereference(final URIReference ref, XMLCryptoContext ctx)
                throws URIReferenceException {
            String uri = ref.getURI();
            if (uri.equals(STYLESHEET) || uri.equals(STYLESHEET_B64)) {
                try {
                    FileInputStream fis = new FileInputStream(new File
                            (DATA_DIR, uri.substring(uri.lastIndexOf('/'))));
                    return new OctetStreamData(fis,ref.getURI(),ref.getType());
                } catch (Exception e) { throw new URIReferenceException(e); }
            }

            // fallback on builtin deref
            return defaultUd.dereference(ref, ctx);
        }
    }
}

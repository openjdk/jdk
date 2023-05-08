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
 * @bug 4635230 6365103 6366054 6824440 7131084 8046724 8079693
 * @summary Basic unit tests for validating XML Signatures with JSR 105
 * @modules java.base/sun.security.util
 *          java.base/sun.security.x509
 *          java.xml.crypto/org.jcp.xml.dsig.internal.dom
 *          java.xml.crypto/com.sun.org.apache.xml.internal.security.utils
 *          java.xml.crypto/com.sun.org.apache.xml.internal.security.transforms.implementations
 *          java.xml.crypto/com.sun.org.slf4j.internal
 *          java.xml/com.sun.org.apache.xml.internal.dtm
 *          java.xml/com.sun.org.apache.xml.internal.utils
 *          java.xml/com.sun.org.apache.xpath.internal.functions
 *          java.xml/com.sun.org.apache.xpath.internal.objects
 *          java.xml/com.sun.org.apache.xpath.internal.res
 *          java.xml/com.sun.org.apache.xpath.internal
 *          java.xml/com.sun.org.apache.xpath.internal.compiler
 *
 *
 * @library /test/lib
 * @compile -XDignore.symbol.file KeySelectors.java SignatureValidator.java
 *     X509KeySelector.java ValidationTests.java
 * @compile xalan/TransformXPath.java xalan/TransformXPath2Filter.java
 * @run main/othervm XalanTest
 * @author Sean Mullan
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

/**
 * This test used to be part of ValidationTests but was moved into its own
 * test because it tests a signature that contains the here() function which
 * depends on Xalan internals. The Xalan dependency has been removed from
 * the DSig implementation but can be optionally configured by using a
 * customized TransformXPath instance. Use this test as an example.
 */
public class XalanTest {

    private static SignatureValidator validator;
    private final static String DIR = System.getProperty("test.src", ".");
    private final static String DATA_DIR =
        DIR + System.getProperty("file.separator") + "data";
    private final static String XALAN_DIR =
            DIR + System.getProperty("file.separator") + "xalan";
    private final static String KEYSTORE =
        DATA_DIR + System.getProperty("file.separator") + "certs" +
        System.getProperty("file.separator") + "xmldsig.jks";
    private final static String STYLESHEET =
        "http://www.w3.org/TR/xml-stylesheet";
    private final static String STYLESHEET_B64 =
        "http://www.w3.org/Signature/2002/04/xml-stylesheet.b64";

    public static void main(String args[]) throws Exception {
        // Re-enable sha1 algs
        SecurityUtils.removeAlgsFromDSigPolicy("sha1");
        // Use custom XPath implementation
        System.setProperty(
                "com.sun.org.apache.xml.internal.security.resource.config",
                "xalan/config-xalan.xml");

        validator = new SignatureValidator(new File(XALAN_DIR));

        KeyStore keystore = KeyStore.getInstance("JKS");
        KeySelector ks;
        try (FileInputStream fis = new FileInputStream(KEYSTORE)) {
            keystore.load(fis, "changeit".toCharArray());
            ks = new X509KeySelector(keystore, false);
        }
        if (!validator.validate("signature.xml", ks, new HttpURIDereferencer(), false)) {
            throw new Exception
                    ("At least one signature did not validate as expected");
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

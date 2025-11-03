/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.xml.crypto.Data;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.OctetStreamData;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.URIReference;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.security.SecurityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class HereFunction {

    private final static String DIR = System.getProperty("test.src", ".");
    private final static String DATA_DIR =
            DIR + System.getProperty("file.separator") + "data";
    private final static String KEYSTORE_VERIFY =
            DATA_DIR + System.getProperty("file.separator") + "certs" +
                    System.getProperty("file.separator") + "xmldsig.jks";
    private final static String KEYSTORE_SIGN =
            DATA_DIR + System.getProperty("file.separator") + "certs" +
                    System.getProperty("file.separator") + "test.jks";
    private final static String STYLESHEET =
            "http://www.w3.org/TR/xml-stylesheet";
    private final static String STYLESHEET_B64 =
            "http://www.w3.org/Signature/2002/04/xml-stylesheet.b64";
    private final static char[] PASS = "changeit".toCharArray();

    public static void main(String args[]) throws Throwable {
        if (!args[0].equals("default")) {
            Security.setProperty("jdk.xml.dsig.hereFunctionSupported", args[0]);
        }
        // Re-enable sha1 and xpath algs
        SecurityUtils.removeAlgsFromDSigPolicy("sha1", "xpath");

        boolean expected = Boolean.parseBoolean(args[1]);

        sign(expected);

        // Validating an old signature signed by JDK < 21
        validate(expected);
    }

    static void validate(boolean expected) throws Exception {
        SignatureValidator validator = new SignatureValidator(new File(DATA_DIR));

        KeyStore keystore = KeyStore.getInstance(new File(KEYSTORE_VERIFY), PASS);
        KeySelector ks = new X509KeySelector(keystore, false);

        if (expected) {
            Asserts.assertTrue(validator.validate(
                    "signature.xml", ks, new HttpURIDereferencer(), false));
        } else {
            Utils.runAndCheckException(() -> validator.validate(
                    "signature.xml", ks, new HttpURIDereferencer(), false),
                    XMLSignatureException.class);
        }
    }

    static void sign(boolean expected) throws Exception {
        XMLSignatureFactory fac = XMLSignatureFactory.getInstance();
        DigestMethod sha1 = fac.newDigestMethod(DigestMethod.SHA1, null);
        CanonicalizationMethod withoutComments = fac.newCanonicalizationMethod
                (CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec)null);
        SignatureMethod dsaSha1 = fac.newSignatureMethod(SignatureMethod.DSA_SHA1, null);
        KeyInfoFactory kifac = fac.getKeyInfoFactory();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        String ENVELOPE =
                DATA_DIR + System.getProperty("file.separator") + "envelope.xml";

        var ks = KeyStore.getInstance(new File(KEYSTORE_SIGN), PASS);
        var signingKey = ks.getKey("user", PASS);
        var signingCert = ks.getCertificate("user");

        // create references
        List<Reference> refs = new ArrayList<>();

        // Reference 1
        refs.add(fac.newReference(STYLESHEET, sha1));

        // Reference 2
        String expr = "\n"
                + " ancestor-or-self::dsig:SignedInfo                  " + "\n"
                + "  and                                               " + "\n"
                + " count(ancestor-or-self::dsig:Reference |           " + "\n"
                + "      here()/ancestor::dsig:Reference[1]) >         " + "\n"
                + " count(ancestor-or-self::dsig:Reference)            " + "\n"
                + "  or                                                " + "\n"
                + " count(ancestor-or-self::node() |                   " + "\n"
                + "      id('notaries')) =                             " + "\n"
                + " count(ancestor-or-self::node())                    " + "\n";

        XPathFilterParameterSpec xfp = new XPathFilterParameterSpec(expr,
                Collections.singletonMap("dsig", XMLSignature.XMLNS));
        refs.add(fac.newReference("", sha1, Collections.singletonList
                        (fac.newTransform(Transform.XPATH, xfp)),
                XMLObject.TYPE, null));

        // create SignedInfo
        SignedInfo si = fac.newSignedInfo(withoutComments, dsaSha1, refs);

        // create keyinfo
        KeyInfo ki = kifac.newKeyInfo(List.of(
                kifac.newX509Data(List.of(signingCert))), null);

        // create XMLSignature
        XMLSignature sig = fac.newXMLSignature(si, ki, null, "signature", null);

        dbf.setValidating(false);
        Document envDoc = dbf.newDocumentBuilder()
                .parse(new FileInputStream(ENVELOPE));
        Element ys = (Element)
                envDoc.getElementsByTagName("YoursSincerely").item(0);

        DOMSignContext dsc = new DOMSignContext(signingKey, ys);
        dsc.setURIDereferencer(new HttpURIDereferencer());

        if (expected) {
            sig.sign(dsc);
        } else {
            Utils.runAndCheckException(
                    () -> sig.sign(dsc), XMLSignatureException.class);
            return; // Signing fails, no need to validate
        }

//      StringWriter sw = new StringWriter();
//        dumpDocument(envDoc, sw);

        NodeList nl =
                envDoc.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
        if (nl.getLength() == 0) {
            throw new Exception("Couldn't find signature Element");
        }
        Element sigElement = (Element) nl.item(0);

        DOMValidateContext dvc = new DOMValidateContext
                (new X509KeySelector(ks), sigElement);
        dvc.setURIDereferencer(new HttpURIDereferencer());
        File f = new File(
                System.getProperty("dir.test.vector.baltimore") +
                        System.getProperty("file.separator") +
                        "merlin-xmldsig-twenty-three" +
                        System.getProperty("file.separator"));
        dvc.setBaseURI(f.toURI().toString());

        XMLSignature sig2 = fac.unmarshalXMLSignature(dvc);

        if (sig.equals(sig2) == false) {
            throw new Exception
                    ("Unmarshalled signature is not equal to generated signature");
        }
        if (sig2.validate(dvc) == false) {
            throw new Exception("Validation of generated signature failed");
        }
    }

    /**
     * This URIDereferencer returns locally cached copies of http content to
     * avoid test failures due to network glitches, etc.
     */
    private static class HttpURIDereferencer implements URIDereferencer {
        private final URIDereferencer defaultUd;

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

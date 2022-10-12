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

/**
 * @test
 * @bug 8272908
 * @summary Verify signature KeyInfo
 * @library /test/lib
 * @modules java.xml.crypto/com.sun.org.apache.xml.internal.security
 *          java.xml.crypto/com.sun.org.apache.xml.internal.security.c14n
 *          java.xml.crypto/com.sun.org.apache.xml.internal.security.signature
 *          java.xml.crypto/com.sun.org.apache.xml.internal.security.utils
 *          java.xml.crypto/com.sun.org.apache.xml.internal.security.keys
 *          java.xml.crypto/com.sun.org.apache.xml.internal.security.keys.content.keyvalues
 *          java.xml.crypto/com.sun.org.apache.xml.internal.security.keys.content
 *          java.xml.crypto/com.sun.org.apache.xml.internal.security.exceptions
 * @run main/othervm SignatureKeyInfo
 */

import com.sun.org.apache.xml.internal.security.Init;
import com.sun.org.apache.xml.internal.security.exceptions.XMLSecurityException;
import com.sun.org.apache.xml.internal.security.keys.KeyInfo;
import com.sun.org.apache.xml.internal.security.keys.content.PGPData;
import com.sun.org.apache.xml.internal.security.keys.content.RetrievalMethod;
import com.sun.org.apache.xml.internal.security.keys.content.SPKIData;
import com.sun.org.apache.xml.internal.security.signature.XMLSignature;
import com.sun.org.apache.xml.internal.security.utils.Constants;
import com.sun.org.apache.xml.internal.security.utils.XMLUtils;
import com.sun.org.apache.xml.internal.security.utils.ElementProxy;
import com.sun.org.apache.xml.internal.security.keys.content.keyvalues.RSAKeyValue;
import com.sun.org.apache.xml.internal.security.keys.content.keyvalues.DSAKeyValue;

import jdk.test.lib.Asserts;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.math.BigInteger;
import java.security.*;

import static jdk.test.lib.Asserts.assertEquals;

public class SignatureKeyInfo {

    private final static String DIR = System.getProperty("test.src", ".");
    private static DocumentBuilderFactory dbf = null;
    private static Document doc;

    private static final String NAME = "testName";
    private static final String TEXT = "testText";
    private static final String NS = Constants.SignatureSpecNS;
    private static final String RSA = "RSA";
    private static final String DSA = "DSA";
    private static final String FILE_TO_SIGN = "signature-enveloping-hmac-sha1.xml";
    private static final String FILE_TO_VERIFY = "signature-enveloping-hmac-sha1-keyinfo.xml";
    private static final int FIRST_EL = 0;

    public static void main(String[] args) throws Exception {

        Init.init();
        dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setValidating(false);
        verifyXmlKeyInfo();
        sign(RSA);
        sign(DSA);
    }

    private static void sign(String algorithm) throws Exception {
        File file = new File(DIR, FILE_TO_SIGN);

        doc = dbf.newDocumentBuilder().parse(file);

        KeyPair kp = getKeyPair(algorithm);

        String signMethod = RSA.equals(algorithm) ? SignatureMethod.RSA_SHA256
                : SignatureMethod.DSA_SHA256;

        XMLSignature signature = new XMLSignature(doc, null,
                signMethod, CanonicalizationMethod.INCLUSIVE);

        signature.addKeyInfo(kp.getPublic());
        KeyInfo keyInfo = signature.getKeyInfo();
        addKeyInfoData(keyInfo, algorithm);
        signature.sign(kp.getPrivate());
    }

    private static Element getSignElement() {
        NodeList nl =
                doc.getElementsByTagNameNS(NS, "Signature");
        if (nl.getLength() == 0) {
            throw new RuntimeException("Could not find signature Element");
        }

        return (Element) nl.item(FIRST_EL);
    }

    private static void addKeyInfoData(KeyInfo keyInfo, String algorithm) throws Exception {
        KeyPair keyPair = getKeyPair(algorithm);

        if (algorithm.equals(RSA)) {
            RSAKeyValue rsaKeyValue = new RSAKeyValue(doc, keyPair.getPublic());
            keyInfo.add(rsaKeyValue);
        } else {
            DSAKeyValue dsaKeyValue = new DSAKeyValue(doc, keyPair.getPublic());
            keyInfo.add(dsaKeyValue);
        }

        Element elpgp= doc.createElementNS(NS, Constants._TAG_PGPDATA);
        Element elrm= doc.createElementNS(NS, Constants._TAG_RETRIEVALMETHOD);
        Element elspki= doc.createElementNS(NS, Constants._TAG_SPKIDATA);
        keyInfo.add(new PGPData(elpgp, NS));
        keyInfo.add(new RetrievalMethod(elrm, NS));
        keyInfo.add(new SPKIData(elspki, NS));

        keyInfo.setId(TEXT);
        keyInfo.addKeyName(TEXT);
        keyInfo.add(keyPair.getPublic());
        keyInfo.addKeyValue(keyPair.getPublic());
        keyInfo.addDEREncodedKeyValue(keyPair.getPublic());
        keyInfo.addKeyInfoReference(NS);
        keyInfo.addMgmtData(TEXT);

        Element e = XMLUtils.createElementInSignatureSpace(doc, NAME);
        keyInfo.addKeyValue(e);
        keyInfo.addUnknownElement(e);
        keyInfo.addText(TEXT);
        keyInfo.addTextElement(TEXT, NAME);
        keyInfo.addBigIntegerElement(BigInteger.valueOf(12345), NAME);
        keyInfo.addBase64Text(TEXT.getBytes());
        keyInfo.addBase64Element(TEXT.getBytes(), NAME);

        verifyKeyInfoData(keyInfo, algorithm);
    }

    private static KeyPair getKeyPair(String algorithm) throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm);
        keyGen.initialize(2048);

        return keyGen.genKeyPair();
    }

    private static void verifyKeyInfoData(KeyInfo keyInfo, String algorithm)
            throws XMLSecurityException {
        Asserts.assertTrue(keyInfo.containsKeyName());
        verifyElementText(keyInfo.itemKeyName(FIRST_EL));
        Asserts.assertTrue(keyInfo.containsKeyValue());
        verifyElementNS(keyInfo.itemKeyValue(FIRST_EL).getBaseNamespace());

        Asserts.assertTrue(keyInfo.containsKeyInfoReference());
        verifyElementNS(keyInfo.itemKeyInfoReference(FIRST_EL).getURI());
        Asserts.assertTrue(keyInfo.containsDEREncodedKeyValue());
        Asserts.assertTrue(keyInfo.containsMgmtData());
        verifyElementText(keyInfo.itemMgmtData(FIRST_EL));
        Asserts.assertEquals(TEXT, keyInfo.getId());

        Asserts.assertTrue(keyInfo.containsPGPData());
        verifyElementNS(keyInfo.itemPGPData(FIRST_EL).getBaseNamespace());

        Asserts.assertTrue(keyInfo.containsRetrievalMethod());
        verifyElementNS(keyInfo.itemRetrievalMethod(FIRST_EL).getBaseNamespace());
        Asserts.assertTrue(keyInfo.containsSPKIData());
        verifyElementNS(keyInfo.itemSPKIData(FIRST_EL).getBaseNamespace());

        Asserts.assertTrue(keyInfo.containsUnknownElement());
        Asserts.assertEquals(NAME, keyInfo.itemUnknownElement(13).getLocalName());

        Asserts.assertFalse(keyInfo.isEmpty());
        Asserts.assertEquals(algorithm, keyInfo.getPublicKey().getAlgorithm());
    }

    private static void verifyXmlKeyInfo() throws Exception {
        File file = new File(DIR, FILE_TO_VERIFY);

        doc = dbf.newDocumentBuilder().parse(file);
        Element sigElement = getSignElement();
        XMLSignature signature = new XMLSignature
                (sigElement, file.toURI().toString());

        KeyInfo keyInfo = signature.getKeyInfo();
        assertEquals(TEXT, keyInfo.itemMgmtData(FIRST_EL).getMgmtData());
    }

    private static void verifyElementText(ElementProxy elementProxy) {
        Asserts.assertEquals(TEXT, elementProxy.getTextFromTextChild());
    }

    private static void verifyElementNS(String actualNs) {
        Asserts.assertEquals(NS, actualNs);
    }
}
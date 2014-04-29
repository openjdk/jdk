/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4635230 6283345 6303830 6824440 6867348 7094155 8038184
 * @summary Basic unit tests for generating XML Signatures with JSR 105
 * @compile -XDignore.symbol.file KeySelectors.java SignatureValidator.java
 *     X509KeySelector.java GenerationTests.java
 * @run main/othervm GenerationTests
 * @author Sean Mullan
 */

import java.io.*;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.X509CRL;
import java.security.spec.KeySpec;
import java.security.spec.DSAPrivateKeySpec;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;
import javax.crypto.SecretKey;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import javax.xml.crypto.Data;
import javax.xml.crypto.KeySelector;
import javax.xml.crypto.OctetStreamData;
import javax.xml.crypto.URIDereferencer;
import javax.xml.crypto.URIReference;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.XMLStructure;
import javax.xml.crypto.dsig.*;
import javax.xml.crypto.dom.*;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.crypto.dsig.keyinfo.*;
import javax.xml.crypto.dsig.spec.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * Test that recreates merlin-xmldsig-twenty-three test vectors but with
 * different keys and X.509 data.
 */
public class GenerationTests {

    private static XMLSignatureFactory fac;
    private static KeyInfoFactory kifac;
    private static DocumentBuilder db;
    private static CanonicalizationMethod withoutComments;
    private static SignatureMethod dsaSha1, rsaSha1, rsaSha256, rsaSha384, rsaSha512;
    private static DigestMethod sha1, sha256, sha384, sha512;
    private static KeyInfo dsa, rsa, rsa1024;
    private static KeySelector kvks = new KeySelectors.KeyValueKeySelector();
    private static KeySelector sks;
    private static Key signingKey;
    private static PublicKey validatingKey;
    private static Certificate signingCert;
    private static KeyStore ks;
    private final static String DIR = System.getProperty("test.src", ".");
//    private final static String DIR = ".";
    private final static String DATA_DIR =
        DIR + System.getProperty("file.separator") + "data";
    private final static String KEYSTORE =
        DATA_DIR + System.getProperty("file.separator") + "certs" +
        System.getProperty("file.separator") + "test.jks";
    private final static String CRL =
        DATA_DIR + System.getProperty("file.separator") + "certs" +
        System.getProperty("file.separator") + "crl";
    private final static String ENVELOPE =
        DATA_DIR + System.getProperty("file.separator") + "envelope.xml";
    private static URIDereferencer httpUd = null;
    private final static String STYLESHEET =
        "http://www.w3.org/TR/xml-stylesheet";
    private final static String STYLESHEET_B64 =
        "http://www.w3.org/Signature/2002/04/xml-stylesheet.b64";

    public static void main(String args[]) throws Exception {
        setup();
        test_create_signature_enveloped_dsa();
        test_create_signature_enveloping_b64_dsa();
        test_create_signature_enveloping_dsa();
        test_create_signature_enveloping_hmac_sha1_40();
        test_create_signature_enveloping_hmac_sha256();
        test_create_signature_enveloping_hmac_sha384();
        test_create_signature_enveloping_hmac_sha512();
        test_create_signature_enveloping_rsa();
        test_create_signature_external_b64_dsa();
        test_create_signature_external_dsa();
        test_create_signature_keyname();
        test_create_signature_retrievalmethod_rawx509crt();
        test_create_signature_x509_crt_crl();
        test_create_signature_x509_crt();
        test_create_signature_x509_is();
        test_create_signature_x509_ski();
        test_create_signature_x509_sn();
        test_create_signature();
        test_create_exc_signature();
        test_create_sign_spec();
        test_create_signature_enveloping_sha256_dsa();
        test_create_signature_enveloping_sha384_rsa_sha256();
        test_create_signature_enveloping_sha512_rsa_sha384();
        test_create_signature_enveloping_sha512_rsa_sha512();
        test_create_signature_reference_dependency();
        test_create_signature_with_attr_in_no_namespace();
        test_create_signature_with_empty_id();
    }

    private static void setup() throws Exception {
        fac = XMLSignatureFactory.getInstance();
        kifac = fac.getKeyInfoFactory();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        db = dbf.newDocumentBuilder();

        // get key & self-signed certificate from keystore
        FileInputStream fis = new FileInputStream(KEYSTORE);
        ks = KeyStore.getInstance("JKS");
        ks.load(fis, "changeit".toCharArray());
        signingKey = ks.getKey("user", "changeit".toCharArray());
        signingCert = ks.getCertificate("user");
        validatingKey = signingCert.getPublicKey();

        // create common objects
        withoutComments = fac.newCanonicalizationMethod
            (CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec)null);
        dsaSha1 = fac.newSignatureMethod(SignatureMethod.DSA_SHA1, null);
        sha1 = fac.newDigestMethod(DigestMethod.SHA1, null);
        sha256 = fac.newDigestMethod(DigestMethod.SHA256, null);
        sha384 = fac.newDigestMethod
            ("http://www.w3.org/2001/04/xmldsig-more#sha384", null);
        sha512 = fac.newDigestMethod(DigestMethod.SHA512, null);
        dsa = kifac.newKeyInfo(Collections.singletonList
            (kifac.newKeyValue(validatingKey)));
        rsa = kifac.newKeyInfo(Collections.singletonList
            (kifac.newKeyValue(getPublicKey("RSA"))));
        rsa1024 = kifac.newKeyInfo(Collections.singletonList
            (kifac.newKeyValue(getPublicKey("RSA", 1024))));
        rsaSha1 = fac.newSignatureMethod(SignatureMethod.RSA_SHA1, null);
        rsaSha256 = fac.newSignatureMethod
            ("http://www.w3.org/2001/04/xmldsig-more#rsa-sha256", null);
        rsaSha384 = fac.newSignatureMethod
            ("http://www.w3.org/2001/04/xmldsig-more#rsa-sha384", null);
        rsaSha512 = fac.newSignatureMethod
            ("http://www.w3.org/2001/04/xmldsig-more#rsa-sha512", null);
        sks = new KeySelectors.SecretKeySelector("secret".getBytes("ASCII"));

        httpUd = new HttpURIDereferencer();
    }

    static void test_create_signature_enveloped_dsa() throws Exception {
        System.out.println("* Generating signature-enveloped-dsa.xml");
        // create SignedInfo
        SignedInfo si = fac.newSignedInfo
            (withoutComments, dsaSha1, Collections.singletonList
                (fac.newReference
                    ("", sha1, Collections.singletonList
                        (fac.newTransform(Transform.ENVELOPED,
                            (TransformParameterSpec) null)),
                 null, null)));

        // create XMLSignature
        XMLSignature sig = fac.newXMLSignature(si, dsa);

        Document doc = db.newDocument();
        Element envelope = doc.createElementNS
            ("http://example.org/envelope", "Envelope");
        envelope.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI,
            "xmlns", "http://example.org/envelope");
        doc.appendChild(envelope);

        DOMSignContext dsc = new DOMSignContext(signingKey, envelope);

        sig.sign(dsc);
//      StringWriter sw = new StringWriter();
//      dumpDocument(doc, sw);
//      System.out.println(sw.toString());

        DOMValidateContext dvc = new DOMValidateContext
            (kvks, envelope.getFirstChild());
        XMLSignature sig2 = fac.unmarshalXMLSignature(dvc);

        if (sig.equals(sig2) == false) {
            throw new Exception
                ("Unmarshalled signature is not equal to generated signature");
        }

        if (sig2.validate(dvc) == false) {
            throw new Exception("Validation of generated signature failed");
        }
        System.out.println();
    }

    static void test_create_signature_enveloping_b64_dsa() throws Exception {
        System.out.println("* Generating signature-enveloping-b64-dsa.xml");
        test_create_signature_enveloping
            (sha1, dsaSha1, dsa, signingKey, kvks, true);
        System.out.println();
    }

    static void test_create_signature_enveloping_dsa() throws Exception {
        System.out.println("* Generating signature-enveloping-dsa.xml");
        test_create_signature_enveloping
            (sha1, dsaSha1, dsa, signingKey, kvks, false);
        System.out.println();
    }

    static void test_create_signature_enveloping_sha256_dsa() throws Exception {
        System.out.println("* Generating signature-enveloping-sha256-dsa.xml");
        test_create_signature_enveloping
            (sha256, dsaSha1, dsa, signingKey, kvks, false);
        System.out.println();
    }

    static void test_create_signature_enveloping_hmac_sha1_40()
        throws Exception {
        System.out.println("* Generating signature-enveloping-hmac-sha1-40.xml");
        SignatureMethod hmacSha1 = fac.newSignatureMethod
            (SignatureMethod.HMAC_SHA1, new HMACParameterSpec(40));
        try {
            test_create_signature_enveloping(sha1, hmacSha1, null,
                getSecretKey("secret".getBytes("ASCII")), sks, false);
        } catch (Exception e) {
            if (!(e instanceof XMLSignatureException)) {
                throw e;
            }
        }
        System.out.println();
    }

    static void test_create_signature_enveloping_hmac_sha256()
        throws Exception {
        System.out.println("* Generating signature-enveloping-hmac-sha256.xml");
        SignatureMethod hmacSha256 = fac.newSignatureMethod
            ("http://www.w3.org/2001/04/xmldsig-more#hmac-sha256", null);
        test_create_signature_enveloping(sha1, hmacSha256, null,
            getSecretKey("secret".getBytes("ASCII")), sks, false);
        System.out.println();
    }

    static void test_create_signature_enveloping_hmac_sha384()
        throws Exception {
        System.out.println("* Generating signature-enveloping-hmac-sha384.xml");
        SignatureMethod hmacSha384 = fac.newSignatureMethod
            ("http://www.w3.org/2001/04/xmldsig-more#hmac-sha384", null);
        test_create_signature_enveloping(sha1, hmacSha384, null,
            getSecretKey("secret".getBytes("ASCII")), sks, false);
        System.out.println();
    }

    static void test_create_signature_enveloping_hmac_sha512()
        throws Exception {
        System.out.println("* Generating signature-enveloping-hmac-sha512.xml");
        SignatureMethod hmacSha512 = fac.newSignatureMethod
            ("http://www.w3.org/2001/04/xmldsig-more#hmac-sha512", null);
        test_create_signature_enveloping(sha1, hmacSha512, null,
            getSecretKey("secret".getBytes("ASCII")), sks, false);
        System.out.println();
    }

    static void test_create_signature_enveloping_rsa() throws Exception {
        System.out.println("* Generating signature-enveloping-rsa.xml");
        test_create_signature_enveloping(sha1, rsaSha1, rsa,
            getPrivateKey("RSA"), kvks, false);
        System.out.println();
    }

    static void test_create_signature_enveloping_sha384_rsa_sha256()
        throws Exception {
        System.out.println("* Generating signature-enveloping-sha384-rsa_sha256.xml");
        test_create_signature_enveloping(sha384, rsaSha256, rsa,
            getPrivateKey("RSA"), kvks, false);
        System.out.println();
    }

    static void test_create_signature_enveloping_sha512_rsa_sha384()
        throws Exception {
        System.out.println("* Generating signature-enveloping-sha512-rsa_sha384.xml");
        test_create_signature_enveloping(sha512, rsaSha384, rsa1024,
            getPrivateKey("RSA", 1024), kvks, false);
        System.out.println();
    }

    static void test_create_signature_enveloping_sha512_rsa_sha512()
        throws Exception {
        System.out.println("* Generating signature-enveloping-sha512-rsa_sha512.xml");
        test_create_signature_enveloping(sha512, rsaSha512, rsa1024,
            getPrivateKey("RSA", 1024), kvks, false);
        System.out.println();
    }

    static void test_create_signature_external_b64_dsa() throws Exception {
        System.out.println("* Generating signature-external-b64-dsa.xml");
        test_create_signature_external(dsaSha1, dsa, signingKey, kvks, true);
        System.out.println();
    }

    static void test_create_signature_external_dsa() throws Exception {
        System.out.println("* Generating signature-external-dsa.xml");
        test_create_signature_external(dsaSha1, dsa, signingKey, kvks, false);
        System.out.println();
    }

    static void test_create_signature_keyname() throws Exception {
        System.out.println("* Generating signature-keyname.xml");
        KeyInfo kn = kifac.newKeyInfo(Collections.singletonList
            (kifac.newKeyName("user")));
        test_create_signature_external(dsaSha1, kn, signingKey,
            new X509KeySelector(ks), false);
        System.out.println();
    }

    static void test_create_signature_retrievalmethod_rawx509crt()
        throws Exception {
        System.out.println(
            "* Generating signature-retrievalmethod-rawx509crt.xml");
        KeyInfo rm = kifac.newKeyInfo(Collections.singletonList
            (kifac.newRetrievalMethod
            ("certs/user.crt", X509Data.RAW_X509_CERTIFICATE_TYPE, null)));
        test_create_signature_external(dsaSha1, rm, signingKey,
            new X509KeySelector(ks), false);
        System.out.println();
    }

    static void test_create_signature_x509_crt_crl() throws Exception {
        System.out.println("* Generating signature-x509-crt-crl.xml");
        List<Object> xds = new ArrayList<Object>();
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        xds.add(signingCert);
        FileInputStream fis = new FileInputStream(CRL);
        X509CRL crl = (X509CRL) cf.generateCRL(fis);
        fis.close();
        xds.add(crl);
        KeyInfo crt_crl = kifac.newKeyInfo(Collections.singletonList
            (kifac.newX509Data(xds)));

        test_create_signature_external(dsaSha1, crt_crl, signingKey,
            new X509KeySelector(ks), false);
        System.out.println();
    }

    static void test_create_signature_x509_crt() throws Exception {
        System.out.println("* Generating signature-x509-crt.xml");
        KeyInfo crt = kifac.newKeyInfo(Collections.singletonList
            (kifac.newX509Data(Collections.singletonList(signingCert))));

        test_create_signature_external(dsaSha1, crt, signingKey,
            new X509KeySelector(ks), false);
        System.out.println();
    }

    static void test_create_signature_x509_is() throws Exception {
        System.out.println("* Generating signature-x509-is.xml");
        KeyInfo is = kifac.newKeyInfo(Collections.singletonList
            (kifac.newX509Data(Collections.singletonList
            (kifac.newX509IssuerSerial
            ("CN=User", new BigInteger("45ef2729", 16))))));
        test_create_signature_external(dsaSha1, is, signingKey,
            new X509KeySelector(ks), false);
        System.out.println();
    }

    static void test_create_signature_x509_ski() throws Exception {
        System.out.println("* Generating signature-x509-ski.xml");
        KeyInfo ski = kifac.newKeyInfo(Collections.singletonList
            (kifac.newX509Data(Collections.singletonList
            ("keyid".getBytes("ASCII")))));

        test_create_signature_external(dsaSha1, ski, signingKey,
            KeySelector.singletonKeySelector(validatingKey), false);
        System.out.println();
    }

    static void test_create_signature_x509_sn() throws Exception {
        System.out.println("* Generating signature-x509-sn.xml");
        KeyInfo sn = kifac.newKeyInfo(Collections.singletonList
            (kifac.newX509Data(Collections.singletonList("CN=User"))));

        test_create_signature_external(dsaSha1, sn, signingKey,
            new X509KeySelector(ks), false);
        System.out.println();
    }

    static void test_create_signature_reference_dependency() throws Exception {
        System.out.println("* Generating signature-reference-dependency.xml");
        // create references
        List<Reference> refs = Collections.singletonList
            (fac.newReference("#object-1", sha1));

        // create SignedInfo
        SignedInfo si = fac.newSignedInfo(withoutComments, rsaSha1, refs);

        // create objects
        List<XMLStructure> objs = new ArrayList<XMLStructure>();

        // Object 1
        List<Reference> manRefs = Collections.singletonList
            (fac.newReference("#object-2", sha1));
        objs.add(fac.newXMLObject(Collections.singletonList
            (fac.newManifest(manRefs, "manifest-1")), "object-1", null, null));

        // Object 2
        Document doc = db.newDocument();
        Element nc = doc.createElementNS(null, "NonCommentandus");
        nc.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", "");
        nc.appendChild(doc.createComment(" Commentandum "));
        objs.add(fac.newXMLObject(Collections.singletonList
            (new DOMStructure(nc)), "object-2", null, null));

        // create XMLSignature
        XMLSignature sig = fac.newXMLSignature(si, rsa, objs, "signature", null);
        DOMSignContext dsc = new DOMSignContext(getPrivateKey("RSA"), doc);

        sig.sign(dsc);

//      dumpDocument(doc, new PrintWriter(System.out));

        DOMValidateContext dvc = new DOMValidateContext
            (kvks, doc.getDocumentElement());
        XMLSignature sig2 = fac.unmarshalXMLSignature(dvc);

        if (sig.equals(sig2) == false) {
            throw new Exception
                ("Unmarshalled signature is not equal to generated signature");
        }
        if (sig2.validate(dvc) == false) {
            throw new Exception("Validation of generated signature failed");
        }

        System.out.println();
    }

    static void test_create_signature_with_attr_in_no_namespace()
        throws Exception
    {
        System.out.println
            ("* Generating signature-with-attr-in-no-namespace.xml");

        // create references
        List<Reference> refs = Collections.singletonList
            (fac.newReference("#unknown", sha1));

        // create SignedInfo
        SignedInfo si = fac.newSignedInfo(withoutComments, rsaSha1, refs);

        // create object-1
        Document doc = db.newDocument();
        Element nc = doc.createElementNS(null, "NonCommentandus");
        // add attribute with no namespace
        nc.setAttribute("Id", "unknown");
        XMLObject obj = fac.newXMLObject(Collections.singletonList
            (new DOMStructure(nc)), "object-1", null, null);

        // create XMLSignature
        XMLSignature sig = fac.newXMLSignature(si, rsa,
                                               Collections.singletonList(obj),
                                               "signature", null);
        DOMSignContext dsc = new DOMSignContext(getPrivateKey("RSA"), doc);
        dsc.setIdAttributeNS(nc, null, "Id");

        sig.sign(dsc);

//      dumpDocument(doc, new PrintWriter(System.out));

        DOMValidateContext dvc = new DOMValidateContext
            (kvks, doc.getDocumentElement());
        dvc.setIdAttributeNS(nc, null, "Id");
        XMLSignature sig2 = fac.unmarshalXMLSignature(dvc);

        if (sig.equals(sig2) == false) {
            throw new Exception
                ("Unmarshalled signature is not equal to generated signature");
        }
        if (sig2.validate(dvc) == false) {
            throw new Exception("Validation of generated signature failed");
        }

        System.out.println();
    }

    static void test_create_signature_with_empty_id() throws Exception {
        System.out.println("* Generating signature-with-empty-id.xml");

        // create references
        List<Reference> refs = Collections.singletonList
            (fac.newReference("#", sha1));

        // create SignedInfo
        SignedInfo si = fac.newSignedInfo(withoutComments, rsaSha1, refs);

        // create object with empty id
        Document doc = db.newDocument();
        XMLObject obj = fac.newXMLObject(Collections.singletonList
            (new DOMStructure(doc.createTextNode("I am the text."))),
            "", "text/plain", null);

        // create XMLSignature
        XMLSignature sig = fac.newXMLSignature(si, rsa,
                                               Collections.singletonList(obj),
                                               "signature", null);
        DOMSignContext dsc = new DOMSignContext(getPrivateKey("RSA"), doc);
        sig.sign(dsc);
    }

    static void test_create_signature() throws Exception {
        System.out.println("* Generating signature.xml");

        // create references
        List<Reference> refs = new ArrayList<Reference>();

        // Reference 1
        refs.add(fac.newReference(STYLESHEET, sha1));

        // Reference 2
        refs.add(fac.newReference
            (STYLESHEET_B64,
            sha1, Collections.singletonList
            (fac.newTransform(Transform.BASE64,
                (TransformParameterSpec) null)), null, null));

        // Reference 3
        refs.add(fac.newReference("#object-1", sha1, Collections.singletonList
            (fac.newTransform(Transform.XPATH,
            new XPathFilterParameterSpec("self::text()"))),
            XMLObject.TYPE, null));

        // Reference 4
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

        // Reference 5
        refs.add(fac.newReference("#object-2", sha1, Collections.singletonList
            (fac.newTransform
                (Transform.BASE64, (TransformParameterSpec) null)),
            XMLObject.TYPE, null));

        // Reference 6
        refs.add(fac.newReference
            ("#manifest-1", sha1, null, Manifest.TYPE, null));

        // Reference 7
        refs.add(fac.newReference("#signature-properties-1", sha1, null,
            SignatureProperties.TYPE, null));

        // Reference 8
        List<Transform> transforms = new ArrayList<Transform>();
        transforms.add(fac.newTransform
            (Transform.ENVELOPED, (TransformParameterSpec) null));
        refs.add(fac.newReference("", sha1, transforms, null, null));

        // Reference 9
        transforms.add(fac.newTransform
            (CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS,
                (TransformParameterSpec) null));
        refs.add(fac.newReference("", sha1, transforms, null, null));

        // Reference 10
        Transform env = fac.newTransform
            (Transform.ENVELOPED, (TransformParameterSpec) null);
        refs.add(fac.newReference("#xpointer(/)",
            sha1, Collections.singletonList(env), null, null));

        // Reference 11
        transforms.clear();
        transforms.add(fac.newTransform
            (Transform.ENVELOPED, (TransformParameterSpec) null));
        transforms.add(fac.newTransform
            (CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS,
             (TransformParameterSpec) null));
        refs.add(fac.newReference("#xpointer(/)", sha1, transforms,
            null, null));

        // Reference 12
        refs.add
            (fac.newReference("#object-3", sha1, null, XMLObject.TYPE, null));

        // Reference 13
        Transform withComments = fac.newTransform
            (CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS,
             (TransformParameterSpec) null);
        refs.add(fac.newReference("#object-3", sha1,
            Collections.singletonList(withComments), XMLObject.TYPE, null));

        // Reference 14
        refs.add(fac.newReference("#xpointer(id('object-3'))", sha1, null,
            XMLObject.TYPE, null));

        // Reference 15
        withComments = fac.newTransform
            (CanonicalizationMethod.INCLUSIVE_WITH_COMMENTS,
             (TransformParameterSpec) null);
        refs.add(fac.newReference("#xpointer(id('object-3'))", sha1,
            Collections.singletonList(withComments), XMLObject.TYPE, null));

        // Reference 16
        refs.add(fac.newReference("#reference-2", sha1));

        // Reference 17
        refs.add(fac.newReference("#manifest-reference-1", sha1, null,
            null, "reference-1"));

        // Reference 18
        refs.add(fac.newReference("#reference-1", sha1, null, null,
            "reference-2"));

        // create SignedInfo
        SignedInfo si = fac.newSignedInfo(withoutComments, dsaSha1, refs);

        // create keyinfo
        XPathFilterParameterSpec xpf = new XPathFilterParameterSpec(
            "ancestor-or-self::dsig:X509Data",
            Collections.singletonMap("dsig", XMLSignature.XMLNS));
        RetrievalMethod rm = kifac.newRetrievalMethod("#object-4",
            X509Data.TYPE, Collections.singletonList(fac.newTransform
            (Transform.XPATH, xpf)));
        KeyInfo ki = kifac.newKeyInfo(Collections.singletonList(rm), null);

        Document doc = db.newDocument();

        // create objects
        List<XMLStructure> objs = new ArrayList<XMLStructure>();

        // Object 1
        objs.add(fac.newXMLObject(Collections.singletonList
            (new DOMStructure(doc.createTextNode("I am the text."))),
            "object-1", "text/plain", null));

        // Object 2
        objs.add(fac.newXMLObject(Collections.singletonList
            (new DOMStructure(doc.createTextNode("SSBhbSB0aGUgdGV4dC4="))),
            "object-2", "text/plain", Transform.BASE64));

        // Object 3
        Element nc = doc.createElementNS(null, "NonCommentandus");
        nc.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", "");
        nc.appendChild(doc.createComment(" Commentandum "));
        objs.add(fac.newXMLObject(Collections.singletonList
            (new DOMStructure(nc)), "object-3", null, null));

        // Manifest
        List<Reference> manRefs = new ArrayList<Reference>();

        // Manifest Reference 1
        manRefs.add(fac.newReference(STYLESHEET,
            sha1, null, null, "manifest-reference-1"));

        // Manifest Reference 2
        manRefs.add(fac.newReference("#reference-1", sha1));

        // Manifest Reference 3
        List<Transform> manTrans = new ArrayList<Transform>();
        String xslt = ""
          + "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform'\n"
          + "            xmlns='http://www.w3.org/TR/xhtml1/strict' \n"
          + "            exclude-result-prefixes='foo' \n"
          + "            version='1.0'>\n"
          + "  <xsl:output encoding='UTF-8' \n"
          + "           indent='no' \n"
          + "           method='xml' />\n"
          + "  <xsl:template match='/'>\n"
          + "    <html>\n"
          + "   <head>\n"
          + "    <title>Notaries</title>\n"
          + "   </head>\n"
          + "   <body>\n"
          + "    <table>\n"
          + "      <xsl:for-each select='Notaries/Notary'>\n"
          + "           <tr>\n"
          + "           <th>\n"
          + "            <xsl:value-of select='@name' />\n"
          + "           </th>\n"
          + "           </tr>\n"
          + "      </xsl:for-each>\n"
          + "    </table>\n"
          + "   </body>\n"
          + "    </html>\n"
          + "  </xsl:template>\n"
          + "</xsl:stylesheet>\n";
        Document docxslt = db.parse(new ByteArrayInputStream(xslt.getBytes()));
        Node xslElem = docxslt.getDocumentElement();

        manTrans.add(fac.newTransform(Transform.XSLT,
            new XSLTTransformParameterSpec(new DOMStructure(xslElem))));
        manTrans.add(fac.newTransform(CanonicalizationMethod.INCLUSIVE,
            (TransformParameterSpec) null));
        manRefs.add(fac.newReference("#notaries", sha1, manTrans, null, null));

        objs.add(fac.newXMLObject(Collections.singletonList
            (fac.newManifest(manRefs, "manifest-1")), null, null, null));

        // SignatureProperties
        Element sa = doc.createElementNS("urn:demo", "SignerAddress");
        sa.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", "urn:demo");
        Element ip = doc.createElementNS("urn:demo", "IP");
        ip.appendChild(doc.createTextNode("192.168.21.138"));
        sa.appendChild(ip);
        SignatureProperty sp = fac.newSignatureProperty
            (Collections.singletonList(new DOMStructure(sa)),
            "#signature", null);
        SignatureProperties sps = fac.newSignatureProperties
            (Collections.singletonList(sp), "signature-properties-1");
        objs.add(fac.newXMLObject(Collections.singletonList(sps), null,
            null, null));

        // Object 4
        List<Object> xds = new ArrayList<Object>();
        xds.add("CN=User");
        xds.add(kifac.newX509IssuerSerial
            ("CN=User", new BigInteger("45ef2729", 16)));
        xds.add(signingCert);
        objs.add(fac.newXMLObject(Collections.singletonList
            (kifac.newX509Data(xds)), "object-4", null, null));

        // create XMLSignature
        XMLSignature sig = fac.newXMLSignature(si, ki, objs, "signature", null);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        dbf.setValidating(false);
        Document envDoc = dbf.newDocumentBuilder().parse
            (new FileInputStream(ENVELOPE));
        Element ys = (Element)
            envDoc.getElementsByTagName("YoursSincerely").item(0);

        DOMSignContext dsc = new DOMSignContext(signingKey, ys);
        dsc.setURIDereferencer(httpUd);

        sig.sign(dsc);

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
        dvc.setURIDereferencer(httpUd);
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
        System.out.println();
    }

    private static void dumpDocument(Document doc, Writer w) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer trans = tf.newTransformer();
//      trans.setOutputProperty(OutputKeys.INDENT, "yes");
        trans.transform(new DOMSource(doc), new StreamResult(w));
    }

    private static void test_create_signature_external
        (SignatureMethod sm, KeyInfo ki, Key signingKey, KeySelector ks,
        boolean b64) throws Exception {

        // create reference
        Reference ref;
        if (b64) {
            ref = fac.newReference
                (STYLESHEET_B64,
                sha1, Collections.singletonList
                (fac.newTransform(Transform.BASE64,
                 (TransformParameterSpec) null)), null, null);
        } else {
            ref = fac.newReference(STYLESHEET, sha1);
        }

        // create SignedInfo
        SignedInfo si = fac.newSignedInfo(withoutComments, sm,
            Collections.singletonList(ref));

        Document doc = db.newDocument();

        // create XMLSignature
        XMLSignature sig = fac.newXMLSignature(si, ki);

        DOMSignContext dsc = new DOMSignContext(signingKey, doc);
        dsc.setURIDereferencer(httpUd);

        sig.sign(dsc);

        DOMValidateContext dvc = new DOMValidateContext
            (ks, doc.getDocumentElement());
        File f = new File(DATA_DIR);
        dvc.setBaseURI(f.toURI().toString());
        dvc.setURIDereferencer(httpUd);

        XMLSignature sig2 = fac.unmarshalXMLSignature(dvc);

        if (sig.equals(sig2) == false) {
            throw new Exception
                ("Unmarshalled signature is not equal to generated signature");
        }
        if (sig2.validate(dvc) == false) {
            throw new Exception("Validation of generated signature failed");
        }
    }

    private static void test_create_signature_enveloping
        (DigestMethod dm, SignatureMethod sm, KeyInfo ki, Key signingKey,
         KeySelector ks, boolean b64) throws Exception {

        // create reference
        Reference ref;
        if (b64) {
            ref = fac.newReference("#object", dm, Collections.singletonList
                (fac.newTransform(Transform.BASE64,
                 (TransformParameterSpec) null)), null, null);
        } else {
            ref = fac.newReference("#object", dm);
        }

        // create SignedInfo
        SignedInfo si = fac.newSignedInfo(withoutComments, sm,
            Collections.singletonList(ref));

        Document doc = db.newDocument();
        // create Objects
        String text = b64 ? "c29tZSB0ZXh0" : "some text";
        XMLObject obj = fac.newXMLObject(Collections.singletonList
            (new DOMStructure(doc.createTextNode(text))),
            "object", null, null);

        // create XMLSignature
        XMLSignature sig = fac.newXMLSignature
            (si, ki, Collections.singletonList(obj), null, null);

        DOMSignContext dsc = new DOMSignContext(signingKey, doc);

        sig.sign(dsc);

//        dumpDocument(doc, new FileWriter("/tmp/foo.xml"));

        DOMValidateContext dvc = new DOMValidateContext
            (ks, doc.getDocumentElement());
        XMLSignature sig2 = fac.unmarshalXMLSignature(dvc);

        if (sig.equals(sig2) == false) {
            throw new Exception
                ("Unmarshalled signature is not equal to generated signature");
        }
        if (sig2.validate(dvc) == false) {
            throw new Exception("Validation of generated signature failed");
        }
    }

    static void test_create_exc_signature() throws Exception {
        System.out.println("* Generating exc_signature.xml");
        List<Reference> refs = new ArrayList<Reference>(4);

        // create reference 1
        refs.add(fac.newReference
            ("#xpointer(id('to-be-signed'))",
             fac.newDigestMethod(DigestMethod.SHA1, null),
             Collections.singletonList
                (fac.newTransform(CanonicalizationMethod.EXCLUSIVE,
                 (TransformParameterSpec) null)),
             null, null));

        // create reference 2
        List<String> prefixList = new ArrayList<String>(2);
        prefixList.add("bar");
        prefixList.add("#default");
        ExcC14NParameterSpec params = new ExcC14NParameterSpec(prefixList);
        refs.add(fac.newReference
            ("#xpointer(id('to-be-signed'))",
             fac.newDigestMethod(DigestMethod.SHA1, null),
             Collections.singletonList
                (fac.newTransform(CanonicalizationMethod.EXCLUSIVE, params)),
             null, null));

        // create reference 3
        refs.add(fac.newReference
            ("#xpointer(id('to-be-signed'))",
             fac.newDigestMethod(DigestMethod.SHA1, null),
             Collections.singletonList(fac.newTransform
                (CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS,
                 (TransformParameterSpec) null)),
             null, null));

        // create reference 4
        prefixList = new ArrayList<String>(2);
        prefixList.add("bar");
        prefixList.add("#default");
        params = new ExcC14NParameterSpec(prefixList);
        refs.add(fac.newReference
            ("#xpointer(id('to-be-signed'))",
             fac.newDigestMethod(DigestMethod.SHA1, null),
             Collections.singletonList(fac.newTransform
                (CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS, params)),
             null, null));

        // create SignedInfo
        SignedInfo si = fac.newSignedInfo(
            fac.newCanonicalizationMethod
                (CanonicalizationMethod.EXCLUSIVE,
                 (C14NMethodParameterSpec) null),
            fac.newSignatureMethod(SignatureMethod.DSA_SHA1, null), refs);

        // create KeyInfo
        List<XMLStructure> kits = new ArrayList<XMLStructure>(2);
        kits.add(kifac.newKeyValue(validatingKey));
        KeyInfo ki = kifac.newKeyInfo(kits);

        // create Objects
        Document doc = db.newDocument();
        Element baz = doc.createElementNS("urn:bar", "bar:Baz");
        Comment com = doc.createComment(" comment ");
        baz.appendChild(com);
        XMLObject obj = fac.newXMLObject(Collections.singletonList
            (new DOMStructure(baz)), "to-be-signed", null, null);

        // create XMLSignature
        XMLSignature sig = fac.newXMLSignature
            (si, ki, Collections.singletonList(obj), null, null);

        Element foo = doc.createElementNS("urn:foo", "Foo");
        foo.setAttributeNS("http://www.w3.org/2000/xmlns/", "xmlns", "urn:foo");
        foo.setAttributeNS
            ("http://www.w3.org/2000/xmlns/", "xmlns:bar", "urn:bar");
        doc.appendChild(foo);

        DOMSignContext dsc = new DOMSignContext(signingKey, foo);
        dsc.putNamespacePrefix(XMLSignature.XMLNS, "dsig");

        sig.sign(dsc);

//      dumpDocument(doc, new FileWriter("/tmp/foo.xml"));

        DOMValidateContext dvc = new DOMValidateContext
            (new KeySelectors.KeyValueKeySelector(), foo.getLastChild());
        XMLSignature sig2 = fac.unmarshalXMLSignature(dvc);

        if (sig.equals(sig2) == false) {
            throw new Exception
                ("Unmarshalled signature is not equal to generated signature");
        }
        if (sig2.validate(dvc) == false) {
            throw new Exception("Validation of generated signature failed");
        }
        System.out.println();
    }

    static void test_create_sign_spec() throws Exception {
        System.out.println("* Generating sign-spec.xml");
        List<Reference> refs = new ArrayList<Reference>(2);

        // create reference 1
        List<XPathType> types = new ArrayList<XPathType>(3);
        types.add(new XPathType(" //ToBeSigned ", XPathType.Filter.INTERSECT));
        types.add(new XPathType(" //NotToBeSigned ",
            XPathType.Filter.SUBTRACT));
        types.add(new XPathType(" //ReallyToBeSigned ",
            XPathType.Filter.UNION));
        XPathFilter2ParameterSpec xp1 = new XPathFilter2ParameterSpec(types);
        refs.add(fac.newReference
            ("", fac.newDigestMethod(DigestMethod.SHA1, null),
             Collections.singletonList(fac.newTransform(Transform.XPATH2, xp1)),
             null, null));

        // create reference 2
        List<Transform> trans2 = new ArrayList<Transform>(2);
        trans2.add(fac.newTransform(Transform.ENVELOPED,
            (TransformParameterSpec) null));
        XPathFilter2ParameterSpec xp2 = new XPathFilter2ParameterSpec
            (Collections.singletonList
                (new XPathType(" / ", XPathType.Filter.UNION)));
        trans2.add(fac.newTransform(Transform.XPATH2, xp2));
        refs.add(fac.newReference("#signature-value",
            fac.newDigestMethod(DigestMethod.SHA1, null), trans2, null, null));

        // create SignedInfo
        SignedInfo si = fac.newSignedInfo(
            fac.newCanonicalizationMethod
                (CanonicalizationMethod.INCLUSIVE,
                 (C14NMethodParameterSpec) null),
            fac.newSignatureMethod(SignatureMethod.DSA_SHA1, null), refs);

        // create KeyInfo
        List<XMLStructure> kits = new ArrayList<XMLStructure>(2);
        kits.add(kifac.newKeyValue(validatingKey));
        List<Object> xds = new ArrayList<Object>(2);
        xds.add("CN=User");
        xds.add(signingCert);
        kits.add(kifac.newX509Data(xds));
        KeyInfo ki = kifac.newKeyInfo(kits);

        // create XMLSignature
        XMLSignature sig = fac.newXMLSignature
            (si, ki, null, null, "signature-value");

        Document doc = db.newDocument();
        Element tbs1 = doc.createElementNS(null, "ToBeSigned");
        Comment tbs1Com = doc.createComment(" comment ");
        Element tbs1Data = doc.createElementNS(null, "Data");
        Element tbs1ntbs = doc.createElementNS(null, "NotToBeSigned");
        Element tbs1rtbs = doc.createElementNS(null, "ReallyToBeSigned");
        Comment tbs1rtbsCom = doc.createComment(" comment ");
        Element tbs1rtbsData = doc.createElementNS(null, "Data");
        tbs1rtbs.appendChild(tbs1rtbsCom);
        tbs1rtbs.appendChild(tbs1rtbsData);
        tbs1ntbs.appendChild(tbs1rtbs);
        tbs1.appendChild(tbs1Com);
        tbs1.appendChild(tbs1Data);
        tbs1.appendChild(tbs1ntbs);

        Element tbs2 = doc.createElementNS(null, "ToBeSigned");
        Element tbs2Data = doc.createElementNS(null, "Data");
        Element tbs2ntbs = doc.createElementNS(null, "NotToBeSigned");
        Element tbs2ntbsData = doc.createElementNS(null, "Data");
        tbs2ntbs.appendChild(tbs2ntbsData);
        tbs2.appendChild(tbs2Data);
        tbs2.appendChild(tbs2ntbs);

        Element document = doc.createElementNS(null, "Document");
        document.appendChild(tbs1);
        document.appendChild(tbs2);
        doc.appendChild(document);

        DOMSignContext dsc = new DOMSignContext(signingKey, document);

        sig.sign(dsc);

//      dumpDocument(doc, new FileWriter("/tmp/foo.xml"));

        DOMValidateContext dvc = new DOMValidateContext
            (new KeySelectors.KeyValueKeySelector(), document.getLastChild());
        XMLSignature sig2 = fac.unmarshalXMLSignature(dvc);

        if (sig.equals(sig2) == false) {
            throw new Exception
                ("Unmarshalled signature is not equal to generated signature");
        }
        if (sig2.validate(dvc) == false) {
            throw new Exception("Validation of generated signature failed");
        }
        System.out.println();
    }

    private static final String DSA_Y =
        "070662842167565771936588335128634396171789331656318483584455493822" +
        "400811200853331373030669235424928346190274044631949560438023934623" +
        "71310375123430985057160";
    private static final String DSA_P =
        "013232376895198612407547930718267435757728527029623408872245156039" +
        "757713029036368719146452186041204237350521785240337048752071462798" +
        "273003935646236777459223";
    private static final String DSA_Q =
        "0857393771208094202104259627990318636601332086981";
    private static final String DSA_G =
        "054216440574364751416096484883257051280474283943804743768346673007" +
        "661082626139005426812890807137245973106730741193551360857959820973" +
        "90670890367185141189796";
    private static final String DSA_X =
        "0527140396812450214498055937934275626078768840117";
    private static final String RSA_MOD =
        "010800185049102889923150759252557522305032794699952150943573164381" +
        "936603255999071981574575044810461362008102247767482738822150129277" +
        "490998033971789476107463";
    private static final String RSA_PRIV =
        "016116973584421969795445996229612671947635798429212816611707210835" +
        "915586591340598683996088487065438751488342251960069575392056288063" +
        "6800379454345804879553";
    private static final String RSA_PUB = "065537";
    private static final String RSA_1024_MOD = "098871307553789439961130765" +
        "909423744508062468450669519128736624058048856940468016843888594585" +
        "322862378444314635412341974900625010364163960238734457710620107530" +
        "573945081856371709138380902553309075505688814637544923038853658690" +
        "857672483016239697038853418682988686871489963827000080098971762923" +
        "833614557257607521";
    private static final String RSA_1024_PRIV = "03682574144968491431483287" +
        "297021581096848810374110568017963075809477047466189822987258068867" +
        "704855380407747867998863645890602646601140183818953428006646987710" +
        "237008997971129772408397621801631622129297063463868593083106979716" +
        "204903524890556839550490384015324575598723478554854070823335021842" +
        "210112348400928769";

    private static PublicKey getPublicKey(String algo) throws Exception {
        return getPublicKey(algo, 512);
    }

    private static PublicKey getPublicKey(String algo, int keysize)
        throws Exception {
        KeyFactory kf = KeyFactory.getInstance(algo);
        KeySpec kspec;
        if (algo.equalsIgnoreCase("DSA")) {
            kspec = new DSAPublicKeySpec(new BigInteger(DSA_Y),
                                         new BigInteger(DSA_P),
                                         new BigInteger(DSA_Q),
                                         new BigInteger(DSA_G));
        } else if (algo.equalsIgnoreCase("RSA")) {
            if (keysize == 512) {
                kspec = new RSAPublicKeySpec(new BigInteger(RSA_MOD),
                                             new BigInteger(RSA_PUB));
            } else {
                kspec = new RSAPublicKeySpec(new BigInteger(RSA_1024_MOD),
                                             new BigInteger(RSA_PUB));
            }
        } else throw new RuntimeException("Unsupported key algorithm " + algo);
        return kf.generatePublic(kspec);
    }

    private static PrivateKey getPrivateKey(String algo) throws Exception {
        return getPrivateKey(algo, 512);
    }

    private static PrivateKey getPrivateKey(String algo, int keysize)
        throws Exception {
        KeyFactory kf = KeyFactory.getInstance(algo);
        KeySpec kspec;
        if (algo.equalsIgnoreCase("DSA")) {
            kspec = new DSAPrivateKeySpec
                (new BigInteger(DSA_X), new BigInteger(DSA_P),
                 new BigInteger(DSA_Q), new BigInteger(DSA_G));
        } else if (algo.equalsIgnoreCase("RSA")) {
            if (keysize == 512) {
                kspec = new RSAPrivateKeySpec
                    (new BigInteger(RSA_MOD), new BigInteger(RSA_PRIV));
            } else {
                kspec = new RSAPrivateKeySpec(new BigInteger(RSA_1024_MOD),
                                              new BigInteger(RSA_1024_PRIV));
            }
        } else throw new RuntimeException("Unsupported key algorithm " + algo);
        return kf.generatePrivate(kspec);
    }

    private static SecretKey getSecretKey(final byte[] secret) {
        return new SecretKey() {
            public String getFormat()   { return "RAW"; }
            public byte[] getEncoded()  { return secret; }
            public String getAlgorithm(){ return "SECRET"; }
        };
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

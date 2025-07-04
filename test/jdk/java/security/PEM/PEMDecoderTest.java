/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

/*
 * @test
 * @bug 8298420
 * @library /test/lib
 * @modules java.base/sun.security.pkcs
 *          java.base/sun.security.util
 * @summary Testing basic PEM API decoding
 * @enablePreview
 */

import javax.crypto.EncryptedPrivateKeyInfo;
import java.io.*;
import java.lang.Class;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;
import java.util.Arrays;

import jdk.test.lib.Asserts;
import sun.security.pkcs.PKCS8Key;
import sun.security.util.Pem;

public class PEMDecoderTest {

    static HexFormat hex = HexFormat.of();

    public static void main(String[] args) throws Exception {
        System.out.println("Decoder test:");
        PEMData.entryList.forEach(entry -> test(entry, false));
        System.out.println("Decoder test withFactory:");
        PEMData.entryList.forEach(entry -> test(entry, true));
        System.out.println("Decoder test returning DEREncodable class:");
        PEMData.entryList.forEach(entry -> test(entry, DEREncodable.class));
        System.out.println("Decoder test with encrypted PEM:");
        PEMData.encryptedList.forEach(PEMDecoderTest::testEncrypted);
        System.out.println("Decoder test with OAS:");
        testTwoKeys();
        System.out.println("Decoder test RSA PEM setting RSAKey.class returned:");
        test(PEMData.rsapriv, RSAKey.class);
        System.out.println("Decoder test failures:");
        PEMData.failureEntryList.forEach(PEMDecoderTest::testFailure);
        System.out.println("Decoder test ecsecp256 PEM asking for ECPublicKey.class returned:");
        testFailure(PEMData.ecsecp256, ECPublicKey.class);
        System.out.println("Decoder test rsapriv PEM setting P8EKS.class returned:");
        testClass(PEMData.rsapriv, RSAPrivateKey.class);
        System.out.println("Decoder test rsaOpenSSL P1 PEM asking for RSAPublicKey.class returned:");
        testFailure(PEMData.rsaOpenSSL, RSAPublicKey.class);
        System.out.println("Decoder test rsapub PEM asking X509EKS.class returned:");
        testClass(PEMData.rsapub, X509EncodedKeySpec.class, true);
        System.out.println("Decoder test rsapriv PEM asking X509EKS.class returned:");
        testClass(PEMData.rsapriv, X509EncodedKeySpec.class, false);
        System.out.println("Decoder test RSAcert PEM asking X509EKS.class returned:");
        testClass(PEMData.rsaCert, X509EncodedKeySpec.class, false);
        System.out.println("Decoder test OAS RFC PEM asking PrivateKey.class returned:");
        testClass(PEMData.oasrfc8410, PrivateKey.class, true);
        testClass(PEMData.oasrfc8410, PublicKey.class, true);
        System.out.println("Decoder test ecsecp256:");
        testFailure(PEMData.ecsecp256pub.makeNoCRLF("pubecpem-no"));
        System.out.println("Decoder test RSAcert with decryption Decoder:");
        PEMDecoder d = PEMDecoder.of().withDecryption("123".toCharArray());
        d.decode(PEMData.rsaCert.pem());
        System.out.println("Decoder test ecsecp256 private key with decryption Decoder:");
        ((KeyPair) d.decode(PEMData.ecsecp256.pem())).getPrivate();
        System.out.println("Decoder test ecsecp256 to P8EKS:");
        d.decode(PEMData.ecsecp256.pem(), PKCS8EncodedKeySpec.class);

        System.out.println("Checking if decode() returns the same encoding:");
        PEMData.privList.forEach(PEMDecoderTest::testDERCheck);
        PEMData.oasList.forEach(PEMDecoderTest::testDERCheck);

        System.out.println("Check a Signature/Verify op is successful:");
        PEMData.privList.forEach(PEMDecoderTest::testSignature);
        PEMData.oasList.stream().filter(e -> !e.name().endsWith("xdh"))
                .forEach(PEMDecoderTest::testSignature);

        System.out.println("Checking if decode() returns a PKCS8Key and can generate a pub");
        PEMData.oasList.forEach(PEMDecoderTest::testPKCS8Key);

        System.out.println("Checking if ecCSR:");
        test(PEMData.ecCSR);
        System.out.println("Checking if ecCSR with preData:");
        DEREncodable result = PEMDecoder.of().decode(PEMData.ecCSRWithData.pem(), PEMRecord.class);
        if (result instanceof PEMRecord rec) {
            if (PEMData.preData.compareTo(new String(rec.leadingData())) != 0) {
                System.err.println("expected: " + PEMData.preData);
                System.err.println("received: " + new String(rec.leadingData()));
                throw new AssertionError("ecCSRWithData preData wrong");
            }
            if (rec.content().lastIndexOf("F") > rec.content().length() - 5) {
                System.err.println("received: " + rec.content());
                throw new AssertionError("ecCSRWithData: " +
                    "End of PEM data has an unexpected character");
            }
        } else {
            throw new AssertionError("ecCSRWithData didn't return a PEMRecord");
        }

        System.out.println("Decoding RSA pub using class PEMRecord:");
        result = PEMDecoder.of().decode(PEMData.rsapub.pem(), PEMRecord.class);
        if (!(result instanceof PEMRecord)) {
            throw new AssertionError("pubecpem didn't return a PEMRecord");
        }
        if (((PEMRecord) result).type().compareTo(Pem.PUBLIC_KEY) != 0) {
            throw new AssertionError("pubecpem PEMRecord didn't decode as a Public Key");
        }

        testInputStream();
        testPEMRecord(PEMData.rsapub);
        testPEMRecord(PEMData.ecCert);
        testPEMRecord(PEMData.ec25519priv);
        testPEMRecord(PEMData.ecCSR);
        testPEMRecord(PEMData.ecCSRWithData);
        testPEMRecordDecode(PEMData.rsapub);
        testPEMRecordDecode(PEMData.ecCert);
        testPEMRecordDecode(PEMData.ec25519priv);
        testPEMRecordDecode(PEMData.ecCSR);
        testPEMRecordDecode(PEMData.ecCSRWithData);

        d = PEMDecoder.of();
        System.out.println("Check leadingData is null with back-to-back PEMs: ");
        String s = new PEMRecord("ONE", "1212").toString()
            + new PEMRecord("TWO", "3434").toString();
        var ins = new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
        if (d.decode(ins, PEMRecord.class).leadingData() != null) {
            throw new AssertionError("leading data not null on first pem");
        }
        if (d.decode(ins, PEMRecord.class).leadingData() != null) {
            throw new AssertionError("leading data not null on second pem");
        }
        System.out.println("PASS");

        System.out.println("Decode to EncryptedPrivateKeyInfo: ");
        EncryptedPrivateKeyInfo ekpi =
            d.decode(PEMData.ed25519ep8.pem(), EncryptedPrivateKeyInfo.class);
        PrivateKey privateKey;
        try {
            privateKey = ekpi.getKey(PEMData.ed25519ep8.password());
            System.out.println("PASS");
        } catch (GeneralSecurityException e) {
            throw new AssertionError("ed25519ep8 error", e);
        }

        // PBE
        System.out.println("EncryptedPrivateKeyInfo.encryptKey with PBE: ");
        ekpi = EncryptedPrivateKeyInfo.encryptKey(privateKey,
            "password".toCharArray(),"PBEWithMD5AndDES", null, null);
        try {
            ekpi.getKey("password".toCharArray());
            System.out.println("PASS");
        } catch (Exception e) {
            throw new AssertionError("error getting key", e);
        }

        // PBES2
        System.out.println("EncryptedPrivateKeyInfo.encryptKey with default: ");
        ekpi = EncryptedPrivateKeyInfo.encryptKey(privateKey
            , "password".toCharArray());
        try {
            ekpi.getKey("password".toCharArray());
            System.out.println("PASS");
        } catch (Exception e) {
            throw new AssertionError("error getting key", e);
        }
        testCertTypeConverter(PEMData.ecCert);

        System.out.println("Decoder test testCoefZero:");
        testCoefZero(PEMData.rsaCrtCoefZeroPriv);
    }

    static void testInputStream() throws IOException {
        ByteArrayOutputStream ba = new ByteArrayOutputStream(2048);
        OutputStreamWriter os = new OutputStreamWriter(ba);
        os.write(PEMData.preData);
        os.write(PEMData.rsapub.pem());
        os.write(PEMData.preData);
        os.write(PEMData.rsapub.pem());
        os.write(PEMData.postData);
        os.flush();
        ByteArrayInputStream is = new ByteArrayInputStream(ba.toByteArray());

        System.out.println("Decoding 2 RSA pub with pre & post data:");
        PEMRecord obj;
        int keys = 0;
        while (keys++ < 2) {
            obj = PEMDecoder.of().decode(is, PEMRecord.class);
            if (!PEMData.preData.equalsIgnoreCase(
                new String(obj.leadingData()))) {
                System.out.println("expected: \"" + PEMData.preData + "\"");
                System.out.println("returned: \"" +
                    new String(obj.leadingData()) + "\"");
                throw new AssertionError("Leading data incorrect");
            }
            System.out.println("  Read public key.");
        }
        try {
            PEMDecoder.of().decode(is, PEMRecord.class);
            throw new AssertionError("3rd entry returned a PEMRecord");
        } catch (EOFException e) {
            System.out.println("Success: No 3rd entry found.  EOFE thrown.");
        }

        // End of stream
        try {
            System.out.println("Failed: There should be no PEMRecord: " +
                PEMDecoder.of().decode(is, PEMRecord.class));
        } catch (EOFException e) {
            System.out.println("Success");
            return;
        } catch (Exception e) {
            throw new AssertionError("Caught unexpected exception " +
                "should have been IOE EOF.");
        }

        throw new AssertionError("Failed");
    }

    // test that X509 CERTIFICATE is converted to CERTIFICATE in PEM
    static void testCertTypeConverter(PEMData.Entry entry) throws CertificateEncodingException {
        String certPem = entry.pem().replace("CERTIFICATE", "X509 CERTIFICATE");
        Asserts.assertEqualsByteArray(entry.der(),
                PEMDecoder.of().decode(certPem, X509Certificate.class).getEncoded());

        certPem = entry.pem().replace("CERTIFICATE", "X.509 CERTIFICATE");
        Asserts.assertEqualsByteArray(entry.der(),
                PEMDecoder.of().decode(certPem, X509Certificate.class).getEncoded());
    }

    // test that when the crtCoeff is zero, the key is decoded but only the modulus and private
    // exponent are used resulting in a different der
    static void testCoefZero(PEMData.Entry entry) {
        RSAPrivateKey decoded = PEMDecoder.of().decode(entry.pem(), RSAPrivateKey.class);
        Asserts.assertNotEqualsByteArray(decoded.getEncoded(), entry.der());
    }

    static void testPEMRecord(PEMData.Entry entry) {
        PEMRecord r = PEMDecoder.of().decode(entry.pem(), PEMRecord.class);
        String expected = entry.pem().split("-----")[2].replace(System.lineSeparator(), "");
        try {
            PEMData.checkResults(expected, r.content());
        } catch (AssertionError e) {
            System.err.println("expected:\n" + expected);
            System.err.println("received:\n" + r.content());
            throw e;
        }

        boolean result = switch(r.type()) {
            case Pem.PRIVATE_KEY ->
                PrivateKey.class.isAssignableFrom(entry.clazz());
            case Pem.PUBLIC_KEY ->
                PublicKey.class.isAssignableFrom(entry.clazz());
            case Pem.CERTIFICATE, Pem.X509_CERTIFICATE ->
                entry.clazz().isAssignableFrom(X509Certificate.class);
            case Pem.X509_CRL ->
                entry.clazz().isAssignableFrom(X509CRL.class);
            case "CERTIFICATE REQUEST" ->
                entry.clazz().isAssignableFrom(PEMRecord.class);
            default -> false;
        };

        if (!result) {
            System.err.println("PEMRecord type is a " + r.type());
            System.err.println("Entry is a " + entry.clazz().getName());
            throw new AssertionError("PEMRecord class didn't match:" +
                entry.name());
        }
        System.out.println("Success (" + entry.name() + ")");
    }


    static void testPEMRecordDecode(PEMData.Entry entry) {
        PEMRecord r = PEMDecoder.of().decode(entry.pem(), PEMRecord.class);
        DEREncodable de = PEMDecoder.of().decode(r.toString());

        boolean result = switch(r.type()) {
            case Pem.PRIVATE_KEY ->
                PrivateKey.class.isAssignableFrom(de.getClass());
            case Pem.PUBLIC_KEY ->
                PublicKey.class.isAssignableFrom(de.getClass());
            case Pem.CERTIFICATE, Pem.X509_CERTIFICATE ->
                (de instanceof X509Certificate);
            case Pem.X509_CRL -> (de instanceof X509CRL);
            case "CERTIFICATE REQUEST" -> (de instanceof PEMRecord);
            default -> false;
        };

        if (!result) {
            System.err.println("Entry is a " + entry.clazz().getName());
            System.err.println("PEMRecord type is a " + r.type());
            System.err.println("Returned was a " + entry.clazz().getName());
            throw new AssertionError("PEMRecord class didn't match:" +
                entry.name());
        }
        System.out.println("Success (" + entry.name() + ")");
    }


    static void testFailure(PEMData.Entry entry) {
        testFailure(entry, entry.clazz());
    }

    static void testFailure(PEMData.Entry entry, Class c) {
        try {
            test(entry.pem(), c, PEMDecoder.of());
            if (entry.pem().indexOf('\r') != -1) {
                System.out.println("Found a CR.");
            }
            if (entry.pem().indexOf('\n') != -1) {
                System.out.println("Found a LF");
            }
            throw new AssertionError("Failure with " +
                entry.name() + ":  Not supposed to succeed.");
        } catch (NullPointerException e) {
            System.out.println("PASS (" + entry.name() + "):  " + e.getClass() +
                ": " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            System.out.println("PASS (" + entry.name() + "):  " + e.getClass() +
                ": " + e.getMessage());
        }
    }

    static DEREncodable testEncrypted(PEMData.Entry entry) {
        PEMDecoder decoder = PEMDecoder.of();
        if (!Objects.equals(entry.clazz(), EncryptedPrivateKeyInfo.class)) {
            decoder = decoder.withDecryption(entry.password());
        }

        try {
            return test(entry.pem(), entry.clazz(), decoder);
        } catch (Exception | AssertionError e) {
            throw new RuntimeException("Error with PEM (" + entry.name() +
                "):  " + e.getMessage(), e);
        }
    }

    // Change the Entry to use the given class as the expected class returned
    static DEREncodable test(PEMData.Entry entry, Class c) {
        return test(entry.newClass(c), false);
    }

    // Run test with a given Entry
    static DEREncodable test(PEMData.Entry entry) {
        return test(entry, false);
    }

    // Run test with a given Entry
    static DEREncodable test(PEMData.Entry entry, boolean withFactory) {
        System.out.printf("Testing %s %s%n", entry.name(), entry.provider());
        try {
            PEMDecoder pemDecoder;
            if (withFactory) {
                Provider provider = Security.getProvider(entry.provider());
                pemDecoder = PEMDecoder.of().withFactory(provider);
            } else {
                pemDecoder = PEMDecoder.of();
            }
            DEREncodable r = test(entry.pem(), entry.clazz(), pemDecoder);
            System.out.println("PASS (" + entry.name() + ")");
            return r;
        } catch (Exception | AssertionError e) {
            throw new RuntimeException("Error with PEM (" + entry.name() +
                "):  " + e.getMessage(), e);
        }
    }

    static List getInterfaceList(Class ccc) {
        Class<?>[] interfaces = ccc.getInterfaces();
        List<Class> list = new ArrayList<>(Arrays.asList(interfaces));
        var x = ccc.getSuperclass();
        if (x != null) {
            list.add(x);
        }
        List<Class> results = new ArrayList<>(list);
        if (list.size() > 0) {
            for (Class cname : list) {
                try {
                    if (cname != null &&
                        cname.getName().startsWith("java.security.")) {
                        results.addAll(getInterfaceList(cname));
                    }
                } catch (Exception e) {
                    System.err.println("Exception with " + cname);
                }
            }
        }
        return results;
    }

    /**
     * Perform the decoding test with the given decoder, on the given pem, and
     * expect the clazz to be returned.
     */
    static DEREncodable test(String pem, Class clazz, PEMDecoder decoder)
        throws IOException {
        DEREncodable pk = decoder.decode(pem);

        // Check that clazz matches what pk returned.
        if (pk.getClass().equals(clazz)) {
            return pk;
        }

        // Search interfaces and inheritance to find a match with clazz
        List<Class> list = getInterfaceList(pk.getClass());
        for (Class cc : list) {
            if (cc != null && cc.equals(clazz)) {
                return pk;
            }
        }

        throw new RuntimeException("Entry did not contain expected: " +
            clazz.getName());
    }

    // Run the same key twice through the same decoder and make sure the
    // result is the same
    static void testTwoKeys() throws IOException {
        PublicKey p1, p2;
        PEMDecoder pd = PEMDecoder.of();
        p1 = pd.decode(PEMData.rsapub.pem(), RSAPublicKey.class);
        p2 = pd.decode(PEMData.rsapub.pem(), RSAPublicKey.class);
        if (!Arrays.equals(p1.getEncoded(), p2.getEncoded())) {
            System.err.println("These two should have matched:");
            System.err.println(hex.parseHex(new String(p1.getEncoded())));
            System.err.println(hex.parseHex(new String(p2.getEncoded())));
            throw new AssertionError("Two decoding of the same" +
                " key failed to match: ");
        }
    }

    private static void testPKCS8Key(PEMData.Entry entry) {
        try {
            PKCS8Key key = PEMDecoder.of().decode(entry.pem(), PKCS8Key.class);
            PKCS8EncodedKeySpec spec =
                    new PKCS8EncodedKeySpec(key.getEncoded());

            KeyFactory kf = KeyFactory.getInstance(key.getAlgorithm());
            kf.generatePublic(spec);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    static void testClass(PEMData.Entry entry, Class clazz) throws IOException {
        var pk = PEMDecoder.of().decode(entry.pem(), clazz);
    }

    static void testClass(PEMData.Entry entry, Class clazz, boolean pass)
        throws RuntimeException {
        try {
            testClass(entry, clazz);
        } catch (Exception e) {
            if (pass) {
                throw new RuntimeException(e);
            }
        }
    }

    // Run test with a given Entry
    static void testDERCheck(PEMData.Entry entry) {
        if (entry.name().equals("rsaOpenSSL") ||  // PKCS1 data
            entry.name().equals("ed25519ekpi")) {
            return;
        }

        PKCS8EncodedKeySpec p8 = PEMDecoder.of().decode(entry.pem(),
                PKCS8EncodedKeySpec.class);
        int result = Arrays.compare(entry.der(), p8.getEncoded());
        if (result != 0) {
            System.err.println("Compare error with " + entry.name() + "(" +
                result + ")");
            System.err.println("Expected DER: " + HexFormat.of().
                formatHex(entry.der()));
            System.err.println("Returned DER: " + HexFormat.of().
                formatHex(p8.getEncoded()));
                throw new AssertionError("Failed to match " +
                "expected DER");
        }
        System.out.println("PASS (" + entry.name() + ")");
        System.out.flush();
    }

    /**
     * Run decoded keys through Signature to make sure they are valid keys
     */
    static void testSignature(PEMData.Entry entry) {
        Signature s;
        byte[] data = "12345678".getBytes();
        PrivateKey privateKey;

        DEREncodable d = PEMDecoder.of().decode(entry.pem());
        switch (d) {
            case PrivateKey p -> privateKey = p;
            case KeyPair kp -> privateKey = kp.getPrivate();
            case EncryptedPrivateKeyInfo e -> {
                System.out.println("SKIP: EncryptedPrivateKeyInfo " +
                    entry.name());
                return;
            }
            default -> throw new AssertionError("Private key " +
                "should not be null");
        }

        AlgorithmParameterSpec spec = null;
        String algorithm = switch(privateKey.getAlgorithm()) {
            case "EC" -> "SHA256withECDSA";
            case "EdDSA" -> "EdDSA";
            case "RSASSA-PSS" -> {
                spec = new PSSParameterSpec(
                        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);
                yield "RSASSA-PSS";
            }
            case null -> {
                System.out.println("Algorithm is null " +
                    entry.name());
                throw new AssertionError("PrivateKey algorithm" +
                    "should not be null");
            }
            default -> "SHA256with" + privateKey.getAlgorithm();
        };

        try {
            if (d instanceof PrivateKey) {
                s = Signature.getInstance(algorithm);
                if (spec != null) {
                    s.setParameter(spec);
                }
                s.initSign(privateKey);
                s.update(data);
                s.sign();
                System.out.println("PASS (Sign): " + entry.name());
            } else if (d instanceof KeyPair) {
                s = Signature.getInstance(algorithm);
                s.initSign(privateKey);
                s.update(data);
                byte[] sig = s.sign();
                s.initVerify(((KeyPair)d).getPublic());
                s.verify(sig);
                System.out.println("PASS (Sign/Verify): " + entry.name());
            } else {
                System.out.println("SKIP: " + entry.name());
            }
        } catch (Exception e) {
            System.out.println("FAIL: " + entry.name());
            throw new AssertionError(e);
        }
    }
}
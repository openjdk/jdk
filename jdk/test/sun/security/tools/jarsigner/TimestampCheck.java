/*
 * Copyright (c) 2003, 2010, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.net.httpserver.*;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import sun.security.pkcs.ContentInfo;
import sun.security.pkcs.PKCS7;
import sun.security.pkcs.SignerInfo;
import sun.security.util.DerOutputStream;
import sun.security.util.DerValue;
import sun.security.util.ObjectIdentifier;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X500Name;

public class TimestampCheck {
    static final String TSKS = "tsks";
    static final String JAR = "old.jar";

    static class Handler implements HttpHandler {
        public void handle(HttpExchange t) throws IOException {
            int len = 0;
            for (String h: t.getRequestHeaders().keySet()) {
                if (h.equalsIgnoreCase("Content-length")) {
                    len = Integer.valueOf(t.getRequestHeaders().get(h).get(0));
                }
            }
            byte[] input = new byte[len];
            t.getRequestBody().read(input);

            try {
                int path = 0;
                if (t.getRequestURI().getPath().length() > 1) {
                    path = Integer.parseInt(
                            t.getRequestURI().getPath().substring(1));
                }
                byte[] output = sign(input, path);
                Headers out = t.getResponseHeaders();
                out.set("Content-Type", "application/timestamp-reply");

                t.sendResponseHeaders(200, output.length);
                OutputStream os = t.getResponseBody();
                os.write(output);
            } catch (Exception e) {
                e.printStackTrace();
                t.sendResponseHeaders(500, 0);
            }
            t.close();
        }

        /**
         * @param input The data to sign
         * @param path different cases to simulate, impl on URL path
         * 0: normal
         * 1: Missing nonce
         * 2: Different nonce
         * 3: Bad digets octets in messageImprint
         * 4: Different algorithmId in messageImprint
         * 5: whole chain in cert set
         * 6: extension is missing
         * 7: extension is non-critical
         * 8: extension does not have timestamping
         * @returns the signed
         */
        byte[] sign(byte[] input, int path) throws Exception {
            // Read TSRequest
            DerValue value = new DerValue(input);
            System.err.println("\nIncoming Request\n===================");
            System.err.println("Version: " + value.data.getInteger());
            DerValue messageImprint = value.data.getDerValue();
            AlgorithmId aid = AlgorithmId.parse(
                    messageImprint.data.getDerValue());
            System.err.println("AlgorithmId: " + aid);

            BigInteger nonce = null;
            while (value.data.available() > 0) {
                DerValue v = value.data.getDerValue();
                if (v.tag == DerValue.tag_Integer) {
                    nonce = v.getBigInteger();
                    System.err.println("nonce: " + nonce);
                } else if (v.tag == DerValue.tag_Boolean) {
                    System.err.println("certReq: " + v.getBoolean());
                }
            }

            // Write TSResponse
            System.err.println("\nResponse\n===================");
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(new FileInputStream(TSKS), "changeit".toCharArray());

            String alias = "ts";
            if (path == 6) alias = "tsbad1";
            if (path == 7) alias = "tsbad2";
            if (path == 8) alias = "tsbad3";

            DerOutputStream statusInfo = new DerOutputStream();
            statusInfo.putInteger(0);

            DerOutputStream token = new DerOutputStream();
            AlgorithmId[] algorithms = {aid};
            Certificate[] chain = ks.getCertificateChain(alias);
            X509Certificate[] signerCertificateChain = null;
            X509Certificate signer = (X509Certificate)chain[0];
            if (path == 5) {   // Only case 5 uses full chain
                signerCertificateChain = new X509Certificate[chain.length];
                for (int i=0; i<chain.length; i++) {
                    signerCertificateChain[i] = (X509Certificate)chain[i];
                }
            } else if (path == 9) {
                signerCertificateChain = new X509Certificate[0];
            } else {
                signerCertificateChain = new X509Certificate[1];
                signerCertificateChain[0] = (X509Certificate)chain[0];
            }

            DerOutputStream tst = new DerOutputStream();

            tst.putInteger(1);
            tst.putOID(new ObjectIdentifier("1.2.3.4"));    // policy

            if (path != 3 && path != 4) {
                tst.putDerValue(messageImprint);
            } else {
                byte[] data = messageImprint.toByteArray();
                if (path == 4) {
                    data[6] = (byte)0x01;
                } else {
                    data[data.length-1] = (byte)0x01;
                    data[data.length-2] = (byte)0x02;
                    data[data.length-3] = (byte)0x03;
                }
                tst.write(data);
            }

            tst.putInteger(1);

            Calendar cal = Calendar.getInstance();
            tst.putGeneralizedTime(cal.getTime());

            if (path == 2) {
                tst.putInteger(1234);
            } else if (path == 1) {
                // do nothing
            } else {
                tst.putInteger(nonce);
            }

            DerOutputStream tstInfo = new DerOutputStream();
            tstInfo.write(DerValue.tag_Sequence, tst);

            DerOutputStream tstInfo2 = new DerOutputStream();
            tstInfo2.putOctetString(tstInfo.toByteArray());

            Signature sig = Signature.getInstance("SHA1withDSA");
            sig.initSign((PrivateKey)(ks.getKey(
                    alias, "changeit".toCharArray())));
            sig.update(tstInfo.toByteArray());

            ContentInfo contentInfo = new ContentInfo(new ObjectIdentifier(
                    "1.2.840.113549.1.9.16.1.4"),
                    new DerValue(tstInfo2.toByteArray()));

            System.err.println("Signing...");
            System.err.println(new X500Name(signer
                    .getIssuerX500Principal().getName()));
            System.err.println(signer.getSerialNumber());

            SignerInfo signerInfo = new SignerInfo(
                    new X500Name(signer.getIssuerX500Principal().getName()),
                    signer.getSerialNumber(),
                    aid, AlgorithmId.get("DSA"), sig.sign());

            SignerInfo[] signerInfos = {signerInfo};
            PKCS7 p7 =
                    new PKCS7(algorithms, contentInfo, signerCertificateChain,
                    signerInfos);
            ByteArrayOutputStream p7out = new ByteArrayOutputStream();
            p7.encodeSignedData(p7out);

            DerOutputStream response = new DerOutputStream();
            response.write(DerValue.tag_Sequence, statusInfo);
            response.putDerValue(new DerValue(p7out.toByteArray()));

            DerOutputStream out = new DerOutputStream();
            out.write(DerValue.tag_Sequence, response);

            return out.toByteArray();
        }
    }

    public static void main(String[] args) throws Exception {

        Handler h = new Handler();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        HttpContext ctx = server.createContext("/", h);
        server.start();

        String cmd = null;
        // Use -J-Djava.security.egd=file:/dev/./urandom to speed up
        // nonce generation in timestamping request. Not avaibale on
        // Windows and defaults to thread seed generator, not too bad.
        if (System.getProperty("java.home").endsWith("jre")) {
            cmd = System.getProperty("java.home") + "/../bin/jarsigner" +
                " -J-Djava.security.egd=file:/dev/./urandom" +
                " -debug -keystore " + TSKS + " -storepass changeit" +
                " -tsa http://localhost:" + port + "/%d" +
                " -signedjar new.jar " + JAR + " old";
        } else {
            cmd = System.getProperty("java.home") + "/bin/jarsigner" +
                " -J-Djava.security.egd=file:/dev/./urandom" +
                " -debug -keystore " + TSKS + " -storepass changeit" +
                " -tsa http://localhost:" + port + "/%d" +
                " -signedjar new.jar " + JAR + " old";
        }

        try {
            if (args.length == 0) {         // Run this test
                jarsigner(cmd, 0, true);    // Success, normal call
                jarsigner(cmd, 1, false);   // These 4 should fail
                jarsigner(cmd, 2, false);
                jarsigner(cmd, 3, false);
                jarsigner(cmd, 4, false);
                jarsigner(cmd, 5, true);    // Success, 6543440 solved.
                jarsigner(cmd, 6, false);   // tsbad1
                jarsigner(cmd, 7, false);   // tsbad2
                jarsigner(cmd, 8, false);   // tsbad3
                jarsigner(cmd, 9, false);   // no cert in timestamp
            } else {                        // Run as a standalone server
                System.err.println("Press Enter to quit server");
                System.in.read();
            }
        } finally {
            server.stop(0);
            new File("x.jar").delete();
        }
    }

    /**
     * @param cmd the command line (with a hole to plug in)
     * @param path the path in the URL, i.e, http://localhost/path
     * @param expected if this command should succeed
     */
    static void jarsigner(String cmd, int path, boolean expected)
            throws Exception {
        System.err.println("Test " + path);
        Process p = Runtime.getRuntime().exec(String.format(cmd, path));
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getErrorStream()));
        while (true) {
            String s = reader.readLine();
            if (s == null) break;
            System.err.println(s);
        }
        int result = p.waitFor();
        if (expected && result != 0 || !expected && result == 0) {
            throw new Exception("Failed");
        }
    }
}

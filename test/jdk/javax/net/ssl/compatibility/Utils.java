/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;

/*
 * Utilities for testing.
 */
public class Utils {

    /* ***** Properties ***** */
    public static final String PROP_PORT = "test.port";
    public static final String PROP_CERTS = "test.certs";
    public static final String PROP_PROTOCOL = "test.protocol";
    public static final String PROP_CIPHER_SUITE = "test.cipher.suite";
    public static final String PROP_CLIENT_AUTH = "test.client.auth";
    public static final String PROP_SERVER_JDK = "test.server.jdk";
    public static final String PROP_CLIENT_JDK = "test.client.jdk";
    public static final String PROP_SERVER_NAME = "test.server.name";
    public static final String PROP_APP_PROTOCOLS
            = "test.app.protocols";
    public static final String PROP_NEGO_APP_PROTOCOL
            = "test.negotiated.app.protocol";
    public static final String PROP_SUPPORTS_SNI_ON_SERVER
            = "test.supports.sni.on.server";
    public static final String PROP_SUPPORTS_SNI_ON_CLIENT
            = "test.supports.sni.on.client";
    public static final String PROP_SUPPORTS_ALPN_ON_SERVER
            = "test.supports.alpn.on.server";
    public static final String PROP_SUPPORTS_ALPN_ON_CLIENT
            = "test.supports.alpn.on.client";
    public static final String PROP_NEGATIVE_CASE_ON_SERVER
            = "test.negative.case.on.server";
    public static final String PROP_NEGATIVE_CASE_ON_CLIENT
            = "test.negative.case.on.client";

    public static final boolean DEBUG = Boolean.getBoolean("debug");
    public static final String SECURITY_PROPERTIES_FILE = System.getProperty(
            "test.security.properties",
            System.getProperty("test.src") + "/java.security");

    public static final int TIMEOUT = 10000;
    public static final char[] PASSWORD = "testpass".toCharArray();

    public static final String TEST_LOG = "test.html";
    public static final String PORT_LOG = "port";

    public static final String HTTP_2 = "h2";
    public static final String HTTP_1_1 = "http/1.1";

    public static final String PARAM_DELIMITER = ";";
    public static final String VALUE_DELIMITER = ",";

    /*
     * Creates SSL context with the specified certificate.
     */
    public static SSLContext createSSLContext(Cert... certs) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        trustStore.load(null, null);
        for (int i = 0; i < certs.length; i++) {
            trustStore.setCertificateEntry("trust-" + certs[i].name(),
                    createCert(certs[i]));
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
        tmf.init(trustStore);

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        for (int i = 0; i < certs.length; i++) {
            PrivateKey privKey = createKey(certs[i]);
            keyStore.setKeyEntry("cert-" + certs[i].name(), privKey, PASSWORD,
                    new Certificate[] { createCert(certs[i]) });
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("NewSunX509");
        kmf.init(keyStore, PASSWORD);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return context;
    }

    private static Certificate createCert(Cert cert) throws IOException {
        try {
            CertificateFactory certFactory
                    = CertificateFactory.getInstance("X.509");
            return certFactory.generateCertificate(
                    new ByteArrayInputStream(cert.certMaterials.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Create key failed: " + cert, e);
        }
    }

    private static PrivateKey createKey(Cert cert)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        PKCS8EncodedKeySpec privKeySpec = new PKCS8EncodedKeySpec(
                hexToBytes(cert.privKeyMaterials));
        KeyFactory keyFactory = KeyFactory.getInstance(
                cert.keyAlgorithm.name);
        PrivateKey privKey = keyFactory.generatePrivate(privKeySpec);
        return privKey;
    }

    public static byte[] hexToBytes(String hex) {
        if (hex == null) {
            return null;
        }

        int length = hex.length();
        if (length % 2 != 0) {
            throw new IllegalArgumentException("Hex format is wrong.");
        }

        byte[] bytes = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    @SuppressWarnings("unchecked")
    public static <T> String join(String delimiter, T... values) {
        StringBuilder result = new StringBuilder();
        if (values != null && values.length > 0) {
            for (int i = 0; i < values.length - 1; i++) {
                if (values[i] != null && !values[i].toString().isEmpty()) {
                    result.append(values[i]).append(delimiter);
                }
            }

            if (values[values.length - 1] != null
                    && !values[values.length - 1].toString().isEmpty()) {
                result.append(values[values.length - 1]);
            }
        }
        return result.toString();
    }

    public static String[] split(String str, String delimiter) {
        return str == null ? new String[0] : str.split(delimiter);
    }

    public static String boolToStr(boolean bool) {
        return bool ? "Y" : "N";
    }

    public static Status handleException(Exception exception,
            boolean negativeCase) {
        Status status;
        if ((exception instanceof SSLHandshakeException
                || exception instanceof IllegalArgumentException)
                && negativeCase) {
            System.out.println("Expected exception: " + exception);
            status = Status.EXPECTED_FAIL;
        } else if (exception instanceof SocketTimeoutException) {
            status = Status.TIMEOUT;
        } else {
            exception.printStackTrace(System.out);
            status = Status.FAIL;
        }
        return status;
    }

    /* The HTML-related constants and methods. */

    private static final String STYLE
            = "style=\"font-family: Courier New; "
            + "font-size: 12px; "
            + "white-space: pre-wrap\"";

    private static final String TABLE_STYLE
            = "#test { font-family: \"Courier New\"; font-size: 12px; border-collapse: collapse; }\n"
            + "#test td { border: 1px solid #ddd; padding: 4px; }\n"
            + "#test tr:nth-child(odd) { background-color: #f2f2f2; }";

    public static String row(Object... values) {
        StringBuilder row = new StringBuilder();
        row.append(startTr());
        for (Object value : values) {
            row.append(startTd());
            row.append(value);
            row.append(endTd());
        }
        row.append(endTr());
        return row.toString();
    }

    public static String startHtml() {
        return startTag("html");
    }

    public static String endHtml() {
        return endTag("html");
    }

    public static String startPre() {
        return startTag("pre " + STYLE);
    }

    public static String endPre() {
        return endTag("pre");
    }

    public static String anchorName(String name, String text) {
        return "<a name=" + name + ">" + text + "</a>";
    }

    public static String anchorLink(String file, String anchorName,
            String text) {
        return "<a href=" + file + "#" + anchorName + ">" + text + "</a>";
    }

    public static String tableStyle() {
        return startTag("style") + TABLE_STYLE  +endTag("style");
    }

    public static String startTable() {
        return startTag("table id=\"test\"");
    }

    public static String endTable() {
        return endTag("table");
    }

    private static String startTr() {
        return startTag("tr");
    }

    private static String endTr() {
        return endTag("tr");
    }

    private static String startTd() {
        return startTag("td");
    }

    private static String endTd() {
        return endTag("td");
    }

    private static String startTag(String tag) {
        return "<" + tag + ">";
    }

    private static String endTag(String tag) {
        return "</" + tag + ">";
    }
}

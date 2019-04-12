/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8056174
 * @summary Make sure the jarsigner tool still works after it's modified to
 *          be based on JarSigner API
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 *          jdk.jartool/sun.security.tools.jarsigner
 *          java.base/sun.security.pkcs
 *          java.base/sun.security.x509
 * @build jdk.test.lib.util.JarUtils
 * @run main Options
 */

import com.sun.jarsigner.ContentSigner;
import com.sun.jarsigner.ContentSignerParameters;
import jdk.test.lib.util.JarUtils;
import sun.security.pkcs.PKCS7;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class Options {

    public static void main(String[] args) throws Exception {

        // Prepares raw file
        Files.write(Paths.get("a"), List.of("a"));

        // Pack
        JarUtils.createJar("a.jar", "a");

        // Prepare a keystore
        sun.security.tools.keytool.Main.main(
                ("-keystore jks -storepass changeit -keypass changeit -dname" +
                        " CN=A -alias a -genkeypair -keyalg rsa").split(" "));

        // -altsign
        sun.security.tools.jarsigner.Main.main(
                ("-debug -signedjar altsign.jar -keystore jks -storepass changeit" +
                        " -altsigner Options$X a.jar a").split(" "));

        try (JarFile jf = new JarFile("altsign.jar")) {
            JarEntry je = jf.getJarEntry("META-INF/A.RSA");
            try (InputStream is = jf.getInputStream(je)) {
                if (!Arrays.equals(is.readAllBytes(), "1234".getBytes())) {
                    throw new Exception("altsign go wrong");
                }
            }
        }

        // -sigfile, -digestalg, -sigalg, -internalsf, -sectionsonly
        sun.security.tools.jarsigner.Main.main(
                ("-debug -signedjar new.jar -keystore jks -storepass changeit" +
                " -sigfile olala -digestalg SHA1 -sigalg SHA224withRSA" +
                " -internalsf -sectionsonly a.jar a").split(" "));

        try (JarFile jf = new JarFile("new.jar")) {
            JarEntry je = jf.getJarEntry("META-INF/OLALA.SF");
            Objects.requireNonNull(je);     // check -sigfile
            byte[] sf = null;               // content of .SF
            try (InputStream is = jf.getInputStream(je)) {
                sf = is.readAllBytes();     // save for later comparison
                Attributes attrs = new Manifest(new ByteArrayInputStream(sf))
                        .getMainAttributes();
                // check -digestalg
                if (!attrs.containsKey(new Attributes.Name(
                        "SHA1-Digest-Manifest-Main-Attributes"))) {
                    throw new Exception("digestalg incorrect");
                }
                // check -sectionsonly
                if (attrs.containsKey(new Attributes.Name(
                        "SHA1-Digest-Manifest"))) {
                    throw new Exception("SF should not have file digest");
                }
            }

            je = jf.getJarEntry("META-INF/OLALA.RSA");
            try (InputStream is = jf.getInputStream(je)) {
                PKCS7 p7 = new PKCS7(is.readAllBytes());
                String alg = p7.getSignerInfos()[0]
                        .getDigestAlgorithmId().getName();
                if (!alg.equals("SHA-224")) {   // check -sigalg
                    throw new Exception("PKCS7 signing is using " + alg);
                }
                // check -internalsf
                if (!Arrays.equals(sf, p7.getContentInfo().getData())) {
                    throw new Exception("SF not in RSA");
                }
            }

        }

        // TSA-related ones are checked in ts.sh
    }

    public static class X extends ContentSigner {
        @Override
        public byte[] generateSignedData(ContentSignerParameters parameters,
                boolean omitContent, boolean applyTimestamp)
                throws NoSuchAlgorithmException, CertificateException,
                        IOException {
            return "1234".getBytes();
        }
    }
}

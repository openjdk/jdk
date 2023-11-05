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

/*
 * @test
 * @bug 8281234
 * @summary The -protected option is not always checked in keytool and jarsigner
 * @library /test/lib
 * @modules java.base/sun.security.tools.keytool
 *          java.base/sun.security.x509
 */

import jdk.test.lib.Asserts;
import jdk.test.lib.SecurityTools;
import jdk.test.lib.util.JarUtils;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarFile;

public class AutoKeyStore {

    public static void main(String[] args) throws Exception {

        JarUtils.createJarFile(Path.of("unsigned.jar"), Path.of("."),
                Files.writeString(Path.of("file"), "hello"));

        SecurityTools.keytool("""
                -J--add-exports -Jjava.base/sun.security.tools.keytool=ALL-UNNAMED
                -J--add-exports -Jjava.base/sun.security.x509=ALL-UNNAMED
                -providerClass AutoKeyStore$AutoProvider
                -providerPath $test.classes
                -storetype AUTO -keystore NONE -protected
                -list
                """).shouldHaveExitValue(0)
                .shouldContain("Keystore type: AUTO")
                .shouldContain("Keystore provider: AUTO")
                .shouldContain("PrivateKeyEntry");

        SecurityTools.jarsigner("""
                -J--add-exports -Jjava.base/sun.security.tools.keytool=ALL-UNNAMED
                -J--add-exports -Jjava.base/sun.security.x509=ALL-UNNAMED
                -providerClass AutoKeyStore$AutoProvider
                -providerPath $test.classes
                -storetype AUTO -keystore NONE -protected
                -signedJar signed.jar
                unsigned.jar
                one
                """).shouldHaveExitValue(0)
                .shouldContain("jar signed.");

        Asserts.assertTrue(new JarFile("signed.jar")
                .getEntry("META-INF/ONE.EC") != null);
    }

    public static class AutoProvider extends Provider {
        public AutoProvider() {
            super("AUTO", "1.1.1", "auto");
            put("KeyStore.AUTO", "AutoKeyStore$KeyStoreImpl");
        }
    }

    // This keystore is not based on file. Whenever it's loaded
    // a self-sign certificate is generated inside
    public static class KeyStoreImpl extends KeyStoreSpi {

        private PrivateKey pri;
        private PublicKey pub;
        private X509Certificate cert;

        @Override
        public Key engineGetKey(String alias, char[] password) {
            return pri;
        }

        @Override
        public Certificate[] engineGetCertificateChain(String alias) {
            return new Certificate[] { cert };
        }

        @Override
        public Certificate engineGetCertificate(String alias) {
            return cert;
        }

        @Override
        public Date engineGetCreationDate(String alias) {
            return new Date();
        }

        @Override
        public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
            throw new KeyStoreException("Not supported");
        }

        @Override
        public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
            throw new KeyStoreException("Not supported");
        }

        @Override
        public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
            throw new KeyStoreException("Not supported");
        }

        @Override
        public void engineDeleteEntry(String alias) throws KeyStoreException {
            throw new KeyStoreException("Not supported");
        }

        @Override
        public Enumeration<String> engineAliases() {
            return Collections.enumeration(List.of("one"));
        }

        @Override
        public boolean engineContainsAlias(String alias) {
            return alias.equalsIgnoreCase("one");
        }

        @Override
        public int engineSize() {
            return 1;
        }

        @Override
        public boolean engineIsKeyEntry(String alias) {
            return true;
        }

        @Override
        public boolean engineIsCertificateEntry(String alias) {
            return false;
        }

        @Override
        public String engineGetCertificateAlias(Certificate cert) {
            return "one";
        }

        @Override
        public void engineStore(OutputStream stream, char[] password) {
        }

        @Override
        public void engineLoad(InputStream stream, char[] password) throws IOException {
            try {
                CertAndKeyGen cag = new CertAndKeyGen("EC", "SHA256withECDSA");
                cag.generate("secp256r1");
                pri = cag.getPrivateKey();
                pub = cag.getPublicKey();
                cert = cag.getSelfCertificate(new X500Name("CN=one"), 3600);
            } catch (Exception e) {
                throw new IOException("Not loaded");
            }
        }
    }
}

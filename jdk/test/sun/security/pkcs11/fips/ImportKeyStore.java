/*
 * Copyright (c) 2005, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.util.*;

import java.security.*;
import java.security.KeyStore.*;
import java.security.cert.*;

/**

This is an approximation of the process used to create the *.db files
in this directory.

setenv LD_LIBRARY_PATH $WS/test/sun/security/pkcs11/nss/lib/solaris-sparc
modutil -create -dbdir .
modutil -changepw "NSS Internal PKCS #11 Module" -dbdir .

$JHOME/bin/keytool -list -storetype PKCS11 -providerclass sun.security.pkcs11.SunPKCS11 -providerarg "--name=NSS\nnssSecmodDirectory=." -v -storepass test12

modutil -fips true -dbdir .

*/

public class ImportKeyStore {

    public static void main(String[] args) throws Exception {
        String nssCfg = "--name=NSS\nnssSecmodDirectory=.\n ";
//          "attributes(*,CKO_PRIVATE_KEY,CKK_DSA) = { CKA_NETSCAPE_DB = 0h00 }";
        Provider p = Security.getProvider("SunPKCS11");
        p.configure(nssCfg);

        KeyStore ks = KeyStore.getInstance("PKCS11", p);
        ks.load(null, "test12".toCharArray());
        System.out.println("Aliases: " + Collections.list(ks.aliases()));
        System.out.println();

        char[] srcpw = "passphrase".toCharArray();
//      importKeyStore("truststore", srcpw, ks);
        importKeyStore("keystore", srcpw, ks);

        System.out.println("OK.");
    }

    private static void importKeyStore(String filename, char[] passwd, KeyStore dstks) throws Exception {
        System.out.println("Importing JKS KeyStore " + filename);
        InputStream in = new FileInputStream(filename);
        KeyStore srcks = KeyStore.getInstance("JKS");
        srcks.load(in, passwd);
        in.close();
        List<String> aliases = Collections.list(srcks.aliases());
        for (String alias : aliases) {
            System.out.println("Alias: " + alias);
            if (srcks.isCertificateEntry(alias)) {
                X509Certificate cert = (X509Certificate)srcks.getCertificate(alias);
                System.out.println("  Certificate: " + cert.getSubjectX500Principal());
                dstks.setCertificateEntry(alias + "-cert", cert);
            } else if (srcks.isKeyEntry(alias)) {
                PrivateKeyEntry entry = (PrivateKeyEntry)srcks.getEntry(alias, new PasswordProtection(passwd));
                System.out.println("  Key: " + entry.getPrivateKey().toString().split("\n")[0]);
                dstks.setEntry(alias, entry, null);
            } else {
                System.out.println("  Unknown entry: " + alias);
            }
        }
        System.out.println();
    }

}

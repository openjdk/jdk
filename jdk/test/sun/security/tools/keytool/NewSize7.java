/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6561126
 * @summary keytool should use larger default keysize for keypairs
 */

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import sun.security.tools.KeyTool;

public class NewSize7 {
    public static void main(String[] args) throws Exception {
        String FILE = "newsize7-ks";
        new File(FILE).delete();
        KeyTool.main(("-debug -genkeypair -keystore " + FILE +
                " -alias a -dname cn=c -storepass changeit" +
                " -keypass changeit -keyalg rsa").split(" "));
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(FILE), null);
        new File(FILE).delete();
        RSAPublicKey r = (RSAPublicKey)ks.getCertificate("a").getPublicKey();
        if (r.getModulus().bitLength() != 2048) {
            throw new Exception("Bad keysize");
        }
        X509Certificate x = (X509Certificate)ks.getCertificate("a");
        if (!x.getSigAlgName().equals("SHA256withRSA")) {
            throw new Exception("Bad sigalg");
        }
    }
}

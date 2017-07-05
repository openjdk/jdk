/*
 * Copyright 2010 Sun Microsystems, Inc.  All Rights Reserved.
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
 * This test is called by certreplace.sh
 */

import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import sun.security.validator.Validator;

public class CertReplace {

    private final static String cacerts = "certreplace.jks";
    private final static String certs = "certreplace.certs";

    public static void main(String[] args) throws Exception {

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(cacerts), "changeit".toCharArray());
        Validator v = Validator.getInstance
            (Validator.TYPE_PKIX, Validator.VAR_GENERIC, ks);
        X509Certificate[] chain = createPath();
        System.out.println(Arrays.toString(v.validate(chain)));

    }

    public static X509Certificate[] createPath() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        List list = new ArrayList();
        for (Certificate c: cf.generateCertificates(
                new FileInputStream(certs))) {
            list.add((X509Certificate)c);
        }
        return (X509Certificate[]) list.toArray(new X509Certificate[0]);
    }
}

/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

/**
 * @test
 * @bug 4400624 6321453
 * @summary Make sure all self-signed root cert signatures are valid
 */
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.cert.*;
import java.util.*;

public class VerifyCACerts {

    private final static String cacertsFileName =
        System.getProperty("java.home") +
        System.getProperty("file.separator") + "lib" +
        System.getProperty("file.separator") + "security" +
        System.getProperty("file.separator") + "cacerts";

    public static void main(String[] args) throws Exception {

        // pull all the trusted self-signed CA certs out of the cacerts file
        // and verify their signatures
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(cacertsFileName), "changeit".toCharArray());
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            System.out.println("Verifying " + alias);
            if (!ks.isCertificateEntry(alias))
                throw new Exception(alias + " is not a trusted cert entry");
            Certificate cert = ks.getCertificate(alias);
            // remember the GTE CyberTrust CA cert for further tests
            if (alias.equals("gtecybertrustca")) {
                throw new Exception
                    ("gtecybertrustca is expired and should be deleted");
            }
            cert.verify(cert.getPublicKey());
        }
    }
}

/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package jdk.testlibrary;

import com.sun.net.httpserver.*;

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

/**
 * Creates a simple usable SSLContext for SSLSocketFactory
 * or a HttpsServer using either a given keystore or a default
 * one in the test tree.
 *
 * Using this class with a security manager requires the following
 * permissions to be granted:
 *
 * permission "java.util.PropertyPermission" "test.src.path", "read";
 * permission java.io.FilePermission
 *    "${test.src}/../../../lib/testlibrary/jdk/testlibrary/testkeys", "read";
 * The exact path above depends on the location of the test.
 */
public class SimpleSSLContext {

    SSLContext ssl;

    /**
     * loads default keystore from SimpleSSLContext
     * source directory
     */
    public SimpleSSLContext () throws IOException {
        String paths = System.getProperty("test.src.path");
        StringTokenizer st = new StringTokenizer(paths,":");
        boolean securityExceptions = false;
        while (st.hasMoreTokens()) {
            String path = st.nextToken();
            try {
                File f = new File(path, "jdk/testlibrary/testkeys");
                if (f.exists()) {
                    init (new FileInputStream(f));
                    return;
                }
            } catch (SecurityException e) {
                // catch and ignore because permission only required
                // for one entry on path (at most)
                securityExceptions = true;
            }
        }
        if (securityExceptions) {
            System.err.println("SecurityExceptions thrown on loading testkeys");
        }
    }

    /**
     * loads default keystore from given directory
     */
    public SimpleSSLContext (String dir) throws IOException {
        String file = dir+"/testkeys";
        FileInputStream fis = new FileInputStream(file);
        init(fis);
    }

    private void init (InputStream i) throws IOException {
        try {
            char[] passphrase = "passphrase".toCharArray();
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(i, passphrase);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(ks);

            ssl = SSLContext.getInstance ("TLS");
            ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        } catch (KeyManagementException e) {
            throw new RuntimeException (e.getMessage());
        } catch (KeyStoreException e) {
            throw new RuntimeException (e.getMessage());
        } catch (UnrecoverableKeyException e) {
            throw new RuntimeException (e.getMessage());
        } catch (CertificateException e) {
            throw new RuntimeException (e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException (e.getMessage());
        }
    }

    public SSLContext get () {
        return ssl;
    }
}

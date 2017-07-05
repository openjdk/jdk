/*
 * Copyright (c) 2002, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4667976 8130181
 * @modules java.base/com.sun.net.ssl
 * @compile JavaxSSLContextImpl.java ComSSLContextImpl.java
 *      JavaxTrustManagerFactoryImpl.java ComTrustManagerFactoryImpl.java
 *      JavaxKeyManagerFactoryImpl.java ComKeyManagerFactoryImpl.java
 * @run main/othervm ProviderTest
 * @summary brokenness in the com.sun.net.ssl.SSLSecurity wrappers
 */

import java.security.*;
import com.sun.net.ssl.*;

public class ProviderTest {

    public static void main(String args[]) throws Exception {
        SSLContext sslc;
        TrustManagerFactory tmf;
        KeyManagerFactory kmf;

        Provider extraProvider = new MyProvider();
        Security.addProvider(extraProvider);
        try {
            System.out.println("getting a javax SSLContext");
            sslc = SSLContext.getInstance("javax");
            sslc.init(null, null, null);
            System.out.println("\ngetting a com SSLContext");
            sslc = SSLContext.getInstance("com");
            sslc.init(null, null, null);

            System.out.println("\ngetting a javax TrustManagerFactory");
            tmf = TrustManagerFactory.getInstance("javax");
            tmf.init((KeyStore) null);
            System.out.println("\ngetting a com TrustManagerFactory");
            tmf = TrustManagerFactory.getInstance("com");
            tmf.init((KeyStore) null);

            System.out.println("\ngetting a javax KeyManagerFactory");
            kmf = KeyManagerFactory.getInstance("javax");
            kmf.init((KeyStore) null, null);
            System.out.println("\ngetting a com KeyManagerFactory");
            kmf = KeyManagerFactory.getInstance("com");
            kmf.init((KeyStore) null, null);
        } finally {
            Security.removeProvider(extraProvider.getName());
        }
    }
}

class MyProvider extends Provider {

    private static String info = "Brad's provider";

    /**
     * Installs the JSSE provider.
     */
    public static synchronized void install()
    {
        /* nop. Remove this method in the future. */
    }

    public MyProvider()
    {
        super("BRAD", "1.0", info);

        AccessController.doPrivileged(new java.security.PrivilegedAction() {
            public Object run() {

                put("SSLContext.javax", "JavaxSSLContextImpl");
                put("SSLContext.com",   "ComSSLContextImpl");
                put("TrustManagerFactory.javax",
                                        "JavaxTrustManagerFactoryImpl");
                put("TrustManagerFactory.com",
                                        "ComTrustManagerFactoryImpl");
                put("KeyManagerFactory.javax",
                                        "JavaxKeyManagerFactoryImpl");
                put("KeyManagerFactory.com",
                                        "ComKeyManagerFactoryImpl");

                return null;
            }
        });

    }
}

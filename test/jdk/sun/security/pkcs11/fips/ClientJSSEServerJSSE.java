/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6313675 6323647 8028192
 * @summary Verify that all ciphersuites work in FIPS mode
 * @library /test/lib ..
 * @author Andreas Sterbenz
 * @modules java.base/com.sun.net.ssl.internal.ssl
 * @run main/manual ClientJSSEServerJSSE
 */

/*
 * JSSE supported cipher suites are changed with CR 6916074,
 * need to update this test case in JDK 7 soon
 */

import java.security.*;

// This test belongs more in JSSE than here, but the JSSE workspace does not
// have the NSS test infrastructure. It will live here for the time being.

public class ClientJSSEServerJSSE extends SecmodTest {

    public static void main(String[] args) throws Exception {
        if (initSecmod() == false) {
            return;
        }

        String arch = System.getProperty("os.arch");
        if (!("sparc".equals(arch) || "sparcv9".equals(arch))) {
            // we have not updated other platforms with the proper NSS
            // libraries yet
            System.out.println(
                    "Test currently works only on solaris-sparc " +
                    "and solaris-sparcv9. Skipping on " + arch);
            return;
        }

        String configName = BASE + SEP + "fips.cfg";
        Provider p = getSunPKCS11(configName);

        System.out.println(p);
        Security.addProvider(p);

        Security.removeProvider("SunJSSE");
        Provider jsse = new com.sun.net.ssl.internal.ssl.Provider(p);
        Security.addProvider(jsse);
        System.out.println(jsse.getInfo());

        KeyStore ks = KeyStore.getInstance("PKCS11", p);
        ks.load(null, "test12".toCharArray());

        CipherTest.main(new JSSEFactory(), ks, args);
    }

    private static class JSSEFactory extends CipherTest.PeerFactory {

        String getName() {
            return "Client JSSE - Server JSSE";
        }

        CipherTest.Client newClient(CipherTest cipherTest) throws Exception {
            return new JSSEClient(cipherTest);
        }

        CipherTest.Server newServer(CipherTest cipherTest) throws Exception {
            return new JSSEServer(cipherTest);
        }
    }
}

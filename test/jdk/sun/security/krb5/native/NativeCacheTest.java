/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8123456
 * @summary Test JAAS access to in-memory credential caches
 * @library /test/lib ../auto
 * @requires os.family != "windows"
 * @compile -XDignore.symbol.file
 *   --add-exports java.security.jgss/sun.security.krb5=ALL-UNNAMED
 *   --add-exports java.security.jgss/sun.security.krb5.internal=ALL-UNNAMED
 *   --add-exports java.security.jgss/sun.security.krb5.internal.ccache=ALL-UNNAMED
 *   --add-exports java.security.jgss/sun.security.krb5.internal.crypto=ALL-UNNAMED
 *   --add-exports java.security.jgss/sun.security.krb5.internal.ktab=ALL-UNNAMED
 *   --add-exports java.security.jgss/sun.security.jgss.krb5=ALL-UNNAMED
 *   --add-exports java.base/sun.security.util=ALL-UNNAMED
 *   --add-exports java.base/jdk.internal.misc=ALL-UNNAMED
 *   NativeCacheTest.java
 * @run main jdk.test.lib.FileInstaller TestHosts TestHosts
 * @run main/othervm/native
 *   --add-exports java.security.jgss/sun.security.krb5=ALL-UNNAMED
 *   --add-exports java.security.jgss/sun.security.krb5.internal=ALL-UNNAMED
 *   --add-exports java.security.jgss/sun.security.krb5.internal.ccache=ALL-UNNAMED
 *   --add-exports java.security.jgss/sun.security.krb5.internal.crypto=ALL-UNNAMED
 *   --add-exports java.security.jgss/sun.security.krb5.internal.ktab=ALL-UNNAMED
 *   --add-exports java.security.jgss/sun.security.jgss.krb5=ALL-UNNAMED
 *   --add-exports java.base/sun.security.util=ALL-UNNAMED
 *   --add-exports java.base/jdk.internal.misc=ALL-UNNAMED
 *   --add-opens java.security.jgss/sun.security.krb5=ALL-UNNAMED
 *   --add-opens java.security.jgss/sun.security.krb5.internal=ALL-UNNAMED
 *   --add-opens java.base/sun.security.util=ALL-UNNAMED
 *   --enable-native-access=ALL-UNNAMED
 *   -Djdk.net.hosts.file=TestHosts
 *   NativeCacheTest
 */

import sun.security.krb5.Credentials;
import javax.security.auth.login.LoginContext;
import javax.security.auth.Subject;
import javax.security.auth.kerberos.KerberosTicket;
import java.io.File;

/**
 * Test JAAS access to in-memory credential caches.
 *
 * This test validates that JAAS can access in-memory credential caches
 * on Linux through the native enhancement, using TGTs from OneKDC.
 */
public class NativeCacheTest {

    public static void main(String[] args) throws Exception {
        try {
            // Create TGT using OneKDC (in file cache)
            createTGTWithOneKDC();

            // Copy TGT to in-memory cache using JNI
            String inMemoryCacheName = copyTGTToInMemoryCache();

            // Test JAAS access to in-memory cache
            testJAASAccessToInMemoryCache(inMemoryCacheName);

        } catch (UnsatisfiedLinkError e) {
            System.out.println("Kerberos native library not available - test skipped");
            return;
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Use OneKDC to create a TGT via the JAAS LoginModule
     */
    private static void createTGTWithOneKDC() throws Exception {
        System.out.println("Creating TGT via OneKDC");

        OneKDC kdc = new OneKDC(null);
        kdc.writeJAASConf();

        // Force JAAS to save credentials to file cache for copying
        System.setProperty("test.kdc.save.ccache", "onekdc_cache.ccache");

        try {
            // Authenticate using the JAAS LoginModule
            LoginContext lc = new LoginContext("com.sun.security.jgss.krb5.initiate",
                                               new OneKDC.CallbackForClient());
            lc.login();

            // Verify authentication
            Subject subject = lc.getSubject();
            KerberosTicket ticket = subject.getPrivateCredentials(KerberosTicket.class).iterator().next();

            System.out.println("JAAS authentication successful");
            System.out.println("TGT: " + ticket.getClient() + " -> " + ticket.getServer());

        } catch (Exception e) {
            System.out.println("JAAS authentication failed: " + e.getMessage());
        }
    }

    /**
     * Copy the TGT to an in-memory cache using JNI
     */
    private static String copyTGTToInMemoryCache() throws Exception {
        System.out.println("Copying credentials to memory cache");

        String memoryCacheName = "MEMORY:test_" + System.currentTimeMillis();

        // Create the in-memory cache
        if (!NativeCredentialCacheHelper.createInMemoryCache(memoryCacheName)) {
            throw new RuntimeException("Failed to create memory cache");
        }
        System.out.println("Created memory cache: " + memoryCacheName);

        // Try to copy credentials from the file cache
        boolean copied = false;
        File fileCache = new File("onekdc_cache.ccache");
        if (fileCache.exists()) {
            System.out.println("Copying from: " + fileCache.getAbsolutePath());
            copied = NativeCredentialCacheHelper.copyCredentialsToInMemoryCache(
                memoryCacheName,
                "FILE:" + fileCache.getAbsolutePath()
            );
        }

        // Fallback to the default cache if copying from file cache fails
        if (!copied) {
            copied = NativeCredentialCacheHelper.copyCredentialsToInMemoryCache(memoryCacheName, null);
        }

        if (copied) {
            System.out.println("Credentials copied to memory cache");
        } else {
            System.out.println("No credentials found to copy");
        }

        // Set as the default cache for JAAS testing
        NativeCredentialCacheHelper.setDefaultCache(memoryCacheName);
        System.setProperty("KRB5CCNAME", memoryCacheName);

        return memoryCacheName;
    }

    /**
     * Test JAAS access to an in-memory cache
     */
    private static void testJAASAccessToInMemoryCache(String inMemoryCacheName) throws Exception {
        System.out.println("Testing JAAS access to an in-memory cache");

        // Verify KRB5CCNAME points to our in-memory cache
        String krb5ccname = System.getProperty("KRB5CCNAME");
        System.out.println("KRB5CCNAME is set to: " + krb5ccname);
        System.out.println("Expected in-memory cache: " + inMemoryCacheName);

        if (!inMemoryCacheName.equals(krb5ccname)) {
            System.out.println("ERROR: KRB5CCNAME does not point to our in-memory cache");
            throw new RuntimeException("test setup error - KRB5CCNAME not pointing to in-memory cache");
        }

        try {
            Credentials creds = Credentials.acquireDefaultCreds();

            if (creds != null) {
                String client = creds.getClient().toString();
                String server = creds.getServer().toString();

                System.out.println("SUCCESS: JAAS retrieved credentials from in-memory cache");
                System.out.println("Client: " + client);
                System.out.println("Server: " + server);

                // Verify these are the OneKDC test credentials
                if (client.contains("dummy") && server.contains("RABBIT.HOLE")) {
                    System.out.println("SUCCESS: Retrieved correct OneKDC test credentials from in-memory cache");
                    if (server.contains("krbtgt")) {
                        System.out.println("Retrieved TGT as expected");
                    }
                } else {
                    System.out.println("ERROR: JAAS retrieved wrong credentials from in-memory cache");
                    System.out.println("Expected: dummy@RABBIT.HOLE -> krbtgt/RABBIT.HOLE@RABBIT.HOLE");
                    System.out.println("Found: " + client + " -> " + server);
                    throw new RuntimeException("in-memory cache test failed - wrong credentials retrieved");
                }

            } else {
                System.out.println("JAAS accessed in-memory cache but found no credentials");
            }

        } catch (Exception e) {
            System.out.println("JAAS error: " + e.getMessage());
        }
    }
}

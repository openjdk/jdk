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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Locale;

/*
 * @test
 * @bug 8353749
 * @summary Check warnings for JKS and JCEKS KeyStore
 * @run main/othervm -Djava.security.debug=keystore TestOutdatedKeyStore
 */

public class TestOutdatedKeyStore {

    private static final String[] KS_TYPES = {
            "jks", "jceks"
    };

    private static final String KS_WARNING1 =
            "uses outdated cryptographic algorithm and will be removed " +
            "in a future release. Migrate to PKCS12 using:";

    private static final String KS_WARNING2=
            "keytool -importkeystore -srckeystore <keystore> " +
            "-destkeystore <keystore> -deststoretype pkcs12";

    public static void main(String[] args) throws Exception {
        for (String type : KS_TYPES) {
            testGetInstance1(type);
            testGetInstance2(type);
            testGetInstance3(type);
            testGetInstance4(type);
            testGetInstance5(type);
        }
        System.out.println("All tests completed.");
    }

    // Test getInstance(String type)
    private static void testGetInstance1(String type) throws Exception {
        System.out.println("Test getInstance(String type) with type: "
                + type);
        checkWarnings(type, () -> {
            KeyStore ks = KeyStore.getInstance(type);
        });
    }

    // Test getInstance(String type, String provider)
    private static void testGetInstance2(String type) throws Exception {
        System.out.println("Test getInstance(String type, String provider) with type: "
                + type);
        String provider = Security.getProviders("KeyStore." + type)[0].getName();
        checkWarnings(type, () -> {
            KeyStore ks = KeyStore.getInstance(type, provider);
        });
    }

    // Test getInstance(String type, Provider provider)
    private static void testGetInstance3(String type) throws Exception {
        System.out.println("Test getInstance(String type, Provider provider) with type: "
                + type);
        Provider provider = Security.getProviders("KeyStore." + type)[0];
        checkWarnings(type, () -> {
            KeyStore ks = KeyStore.getInstance(type, provider);
        });
    }

    // Test getInstance(File file, char[] password)
    private static void testGetInstance4(String type) throws Exception {
        System.out.println("Test getInstance(File file, char[] password) with type: "
                + type);
        File ksFile = createKeystore(type);
        checkWarnings(type, () -> {
            KeyStore ks = KeyStore.getInstance(ksFile, "changeit".toCharArray());
        });
    }

    // Test getInstance(File file, LoadStoreParameter param)
    private static void testGetInstance5(String type) throws Exception {
        System.out.println("Test getInstance(File file, LoadStoreParameter param) with type: "
                + type);
        File ksFile = createKeystore(type);
        KeyStore.LoadStoreParameter param = new KeyStore.LoadStoreParameter() {
            @Override
            public KeyStore.ProtectionParameter getProtectionParameter() {
                return new KeyStore.PasswordProtection("changeit".toCharArray());
            }
        };
        checkWarnings(type, () -> {
            KeyStore ks = KeyStore.getInstance(ksFile, param);
        });
    }

    private static File createKeystore(String type) throws Exception {
        File ksFile = File.createTempFile("kstore", ".tmp");
        ksFile.deleteOnExit();
        KeyStore ks = KeyStore.getInstance(type);
        ks.load(null, null);
        try (FileOutputStream fos = new FileOutputStream(ksFile)) {
            ks.store(fos, "changeit".toCharArray());
        }
        return ksFile;
    }

    private static void checkWarnings(String type, RunnableWithException r) throws Exception {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        PrintStream origErr = System.err;
        try {
            System.setErr(new PrintStream(bOut));
            r.run();
        } finally {
            System.setErr(origErr);
        }

        String msg = bOut.toString();
        if (!msg.contains("WARNING: " + type.toUpperCase(Locale.ROOT)) ||
                !msg.contains(KS_WARNING1) ||
                !msg.contains(KS_WARNING2)) {
            throw new RuntimeException("Expected warning not found for " + type + ":\n" + msg);
        }
    }

    interface RunnableWithException {
        void run() throws Exception;
    }
}

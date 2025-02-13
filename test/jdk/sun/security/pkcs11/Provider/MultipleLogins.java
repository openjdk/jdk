/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8240256 8269034
 * @summary
 * @library /test/lib/ /sun/security/pkcs11/
 * @modules jdk.crypto.cryptoki/sun.security.pkcs11
 * @run main/othervm
 *        -DCUSTOM_P11_CONFIG=${test.src}/MultipleLogins-nss.txt
 *        -DCUSTOM_DB_DIR=./nss/db
 *        MultipleLogins
 */

import sun.security.pkcs11.SunPKCS11;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import jdk.test.lib.Utils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.*;
import java.util.List;

import jdk.test.lib.util.ForceGC;
import jtreg.SkippedException;

public class MultipleLogins {
    private static final String KS_TYPE = "PKCS11";
    private static final int NUM_PROVIDERS = 20;
    private static final SunPKCS11[] providers = new SunPKCS11[NUM_PROVIDERS];

    private static void copyDbFiles() throws IOException {
        final var testFolder = System.getProperty("test.src", ".");
        final var srcDbFolder = Paths.get(testFolder).getParent().resolve("nss", "db");

        // Getting path & creating the temporary scratch directory ./nss/db
        final var nssFolder = Path.of(".").resolve("nss");
        Files.createDirectory(nssFolder);
        final var destination = nssFolder.resolve("db");

        final var sourceFiles = List.of(
                srcDbFolder.resolve("cert9.db"),
                srcDbFolder.resolve("key4.db"),
                srcDbFolder.resolve("cert8.db"),
                srcDbFolder.resolve("key3.db")
        );

        final var copiedFiles = Utils.copyFiles(sourceFiles, destination, StandardCopyOption.REPLACE_EXISTING);
        copiedFiles.forEach(path -> path.toFile().setWritable(true));

        System.out.println("NSS db files copied to: ");
        copiedFiles.forEach(System.out::println);
    }

    public static void main(String[] args) throws Exception {
        copyDbFiles();

        String nssConfig = null;
        try {
            nssConfig = PKCS11Test.getNssConfig();
        } catch (SkippedException exc) {
            System.out.println("Skipping test: " + exc.getMessage());
        }

        if (nssConfig == null) {
            // No test framework support yet. Ignore
            System.out.println("No NSS config found. Skipping.");
            return;
        }

        for (int i =0; i < NUM_PROVIDERS; i++) {
            // loop to set up test without security manger
            providers[i] = (SunPKCS11)PKCS11Test.newPKCS11Provider();
        }

        for (int i =0; i < NUM_PROVIDERS; i++) {
            providers[i] = (SunPKCS11)providers[i].configure(nssConfig);
            Security.addProvider(providers[i]);
            test(providers[i]);
        }

        WeakReference<SunPKCS11>[] weakRef = new WeakReference[NUM_PROVIDERS];
        for (int i =0; i < NUM_PROVIDERS; i++) {
            weakRef[i] = new WeakReference<>(providers[i]);
            providers[i].logout();

            if (i == 0) {
                // one provider stays for use with clean up thread
                continue;
            }

            try {
                providers[i].login(new Subject(), new PasswordCallbackHandler());
                throw new RuntimeException("Expected LoginException");
            } catch (LoginException le) {
                // expected
            }

            Security.removeProvider(providers[i].getName());
            providers[i] = null;

            int finalI = i;
            if (!ForceGC.wait(() -> weakRef[finalI].refersTo(null))) {
                throw new RuntimeException("Expected SunPKCS11 Provider to be GC'ed..");
            }
        }
    }

    private static void test(SunPKCS11 p) throws Exception {
        KeyStore ks = KeyStore.getInstance(KS_TYPE, p);
        p.setCallbackHandler(new PasswordCallbackHandler());
        try {
            ks.load(null, (char[]) null);
        } catch (IOException e) {
            if (!e.getMessage().contains("load failed")) {
                // we expect the keystore load to fail
                throw new RuntimeException("unexpected exception", e);
            }
        }

        p.logout();

        try {
            ks.load(null, (char[]) null);
        } catch (IOException e) {
            if (e.getCause() instanceof LoginException &&
                    e.getCause().getMessage().contains("No token present")) {
                // expected
            } else {
                throw new RuntimeException("Token was present", e);
            }
        }
    }

    public static class PasswordCallbackHandler implements CallbackHandler {
        public void handle(Callback[] callbacks)
                throws IOException, UnsupportedCallbackException {
            if (!(callbacks[0] instanceof PasswordCallback)) {
                throw new UnsupportedCallbackException(callbacks[0]);
            }
            PasswordCallback pc = (PasswordCallback)callbacks[0];
            pc.setPassword(null);
        }
    }
}

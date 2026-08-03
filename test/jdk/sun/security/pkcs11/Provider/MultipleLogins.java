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

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.*;

import jdk.test.lib.util.ForceGC;
import jtreg.SkippedException;

public class MultipleLogins extends PKCS11Test {
    private static final int NUM_PROVIDERS = 20;
    private static final SunPKCS11[] providers = new SunPKCS11[NUM_PROVIDERS];

    public static void main(String[] args) throws Exception {
        // This bypasses the PKCS11Test settings and run the mandatory
        // main method directly. This is needed to keep the custom logic of the test
        new MultipleLogins().main((Provider)null);
    }

    @Override
    public void main(Provider p) throws Exception {
        copyNssCertKeyToClassesDir();

        String nssConfig = getNssConfig();

        if (nssConfig == null) {
            // No test framework support yet. Ignore
            throw new SkippedException("No NSS config found. Skipping.");
        }

        for (int i = 0; i < NUM_PROVIDERS; i++) {
            // loop to set up test without security manger
            providers[i] = (SunPKCS11)newPKCS11Provider();
        }

        for (int i =0; i < NUM_PROVIDERS; i++) {
            providers[i] = (SunPKCS11)providers[i].configure(nssConfig);
            Security.addProvider(providers[i]);
            test(providers[i]);
        }

        WeakReference<SunPKCS11>[] weakRef = new WeakReference[NUM_PROVIDERS];
        for (int i = 0; i < NUM_PROVIDERS; i++) {
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
        KeyStore ks = KeyStore.getInstance(PKCS11, p);
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
            if (!(e.getCause() instanceof LoginException) ||
                !(e.getCause().getMessage().contains("No token present"))) {

                throw new RuntimeException("Token was present", e);
            } // else expected
        }
    }

    public static class PasswordCallbackHandler implements CallbackHandler {
        public void handle(Callback[] callbacks)
                throws IOException, UnsupportedCallbackException {
            if (callbacks[0] instanceof PasswordCallback pc) {
                pc.setPassword(null);
            } else {
                throw new UnsupportedCallbackException(callbacks[0]);
            }

        }
    }
}

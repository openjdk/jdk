/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.lang.ref.WeakReference;
import java.security.*;
import javax.security.auth.callback.*;

import javax.security.auth.Subject;
import javax.security.auth.login.FailedLoginException;
import javax.security.auth.login.LoginException;

import sun.security.pkcs11.SunPKCS11;

public class Login extends PKCS11Test {

    private static final String KS_TYPE = "PKCS11";
    private static char[] password;
    private static SunPKCS11 pkcs11Provider = null;

    public static void main(String[] args) throws Exception {
        main(new Login(), args);

        pkcs11Provider.logout();
        WeakReference<SunPKCS11> weakRef = new WeakReference<>(pkcs11Provider);
        pkcs11Provider = null;
        for (int i = 0; i > 100; i++) {
            System.gc();
            Thread.sleep(100);
        }
        System.out.println("Finish: "+ weakRef.refersTo(null));
    }

    public void main(Provider p) throws Exception {

        int testnum = 1;

        KeyStore ks = KeyStore.getInstance(KS_TYPE, p);

        // check instance
        if (ks.getProvider() instanceof java.security.AuthProvider) {
            System.out.println("keystore provider instance of AuthProvider");
            System.out.println("test " + testnum++ + " passed");
        } else {
            throw new SecurityException("did not get AuthProvider KeyStore");
        }

        pkcs11Provider = (SunPKCS11)ks.getProvider();
        try {

            // test app-provided callback
            System.out.println("*** enter [foo] as the password ***");
            password = new char[] { 'f', 'o', 'o' };

            pkcs11Provider.login(new Subject(), new PasswordCallbackHandler());
            pkcs11Provider.logout();
            throw new SecurityException("test failed, expected LoginException");
        } catch (FailedLoginException fle) {
            System.out.println("test " + testnum++ + " passed");
        }

        try {

            // test default callback
            System.out.println("*** enter [foo] as the password ***");
            password = new char[] { 'f', 'o', 'o' };

            Security.setProperty("auth.login.defaultCallbackHandler",
                "Login$PasswordCallbackHandler");
            pkcs11Provider.login(new Subject(), null);
            pkcs11Provider.logout();
            throw new SecurityException("test failed, expected LoginException");
        } catch (FailedLoginException fle) {
            System.out.println("test " + testnum++ + " passed");
        }

        // test provider-set callback
        System.out.println("*** enter test12 (correct) password ***");
        password = new char[] { 't', 'e', 's', 't', '1', '2' };

        Security.setProperty("auth.login.defaultCallbackHandler", "");
        pkcs11Provider.setCallbackHandler(new PasswordCallbackHandler());
        pkcs11Provider.login(new Subject(), null);
        System.out.println("test " + testnum++ + " passed");

        // test user already logged in
        pkcs11Provider.setCallbackHandler(null);
        pkcs11Provider.login(new Subject(), null);
        System.out.println("test " + testnum++ + " passed");

        // logout
        pkcs11Provider.logout();

        // call KeyStore.load with a NULL password, and get prompted for PIN
        pkcs11Provider.setCallbackHandler(new PasswordCallbackHandler());
        try {
            ks.load(null, (char[]) null);
        } catch (IOException e) {
            if (e.getCause() instanceof LoginException &&
                    e.getCause().getMessage().contains("No token present")) {
                //ignore
            } else {
                throw new RuntimeException("Unexpected result", e);
            }
        }
        System.out.println("test " + testnum++ + " passed");
    }

    public static class PasswordCallbackHandler implements CallbackHandler {
        public void handle(Callback[] callbacks)
                throws IOException, UnsupportedCallbackException {
            if (!(callbacks[0] instanceof PasswordCallback)) {
                throw new UnsupportedCallbackException(callbacks[0]);
            }
            PasswordCallback pc = (PasswordCallback)callbacks[0];
            pc.setPassword(Login.password);
        }
    }
}

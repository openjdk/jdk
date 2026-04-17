/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8382403
 * @summary Kerberos SRV KDC discovery failure should include sanitized DNS failure cause
 * @modules java.security.jgss/sun.security.krb5
 * @modules jdk.security.auth/com.sun.security.auth.module
 * @run main/othervm
 *      -Djava.security.krb5.conf=${test.src}/krb5.conf
 *      -Djava.security.auth.login.config=${test.src}/jaas.conf
 *      SRVLookupFailureTest
 */

import javax.security.auth.callback.*;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

public class SRVLookupFailureTest {

    static class Handler implements CallbackHandler {
        @Override
        public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
            for (Callback cb : callbacks) {
                if (cb instanceof NameCallback ncb) {
                    ncb.setName("user@IDONTEXIST");
                } else if (cb instanceof PasswordCallback pcb) {
                    pcb.setPassword("password".toCharArray());
                } else {
                    throw new UnsupportedCallbackException(cb);
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        try {
            // trigger login failure to verify the exception is sanitized
            LoginContext lc = new LoginContext("KrbLogin", new Handler());
            lc.login();
            throw new RuntimeException("Login succeeded unexpectedly");
        } catch (LoginException e) {
            if (!containsSanitizedCause(e)) {
                e.printStackTrace(System.out);
                throw new RuntimeException("Expected DNS SRV failure cause not found");
            }
        }
    }

    static boolean containsSanitizedCause(Throwable t) {
        while (t != null) {
            String msg = t.getMessage();
            // check for the DNS SRV failure cause
            if (msg != null && msg.contains("DNS SRV lookup failed:")) {
                if (msg.contains("NXDOMAIN") || msg.contains("SERVFAIL") || msg.contains("COMMUNICATION_ERROR")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }
}
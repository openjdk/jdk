/*
 * Copyright 2005-2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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

package sun.net.www.protocol.http;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Arrays;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

/**
 * @since 1.6
 * Special callback handler used in JGSS for the HttpCaller.
 */
public class NegotiateCallbackHandler implements CallbackHandler {

    private String username;
    private char[] password;

    private final HttpCallerInfo hci;

    public NegotiateCallbackHandler(HttpCallerInfo hci) {
        this.hci = hci;
    }

    public void handle(Callback[] callbacks) throws
            UnsupportedCallbackException, IOException {
        for (int i=0; i<callbacks.length; i++) {
            Callback callBack = callbacks[i];

            if (callBack instanceof NameCallback) {
                if (username == null) {
                    PasswordAuthentication passAuth =
                            Authenticator.requestPasswordAuthentication(
                            hci.host, hci.addr, hci.port, hci.protocol,
                            hci.prompt, hci.scheme, hci.url, hci.authType);
                    username = passAuth.getUserName();
                    password = passAuth.getPassword();
                }
                NameCallback nameCallback =
                        (NameCallback)callBack;
                nameCallback.setName(username);

            } else if (callBack instanceof PasswordCallback) {
                PasswordCallback passwordCallback =
                        (PasswordCallback)callBack;
                if (password == null) {
                    PasswordAuthentication passAuth =
                            Authenticator.requestPasswordAuthentication(
                            hci.host, hci.addr, hci.port, hci.protocol,
                            hci.prompt, hci.scheme, hci.url, hci.authType);
                    username = passAuth.getUserName();
                    password = passAuth.getPassword();
                }
                passwordCallback.setPassword(password);
                Arrays.fill(password, ' ');
            } else {
                throw new UnsupportedCallbackException(callBack,
                        "Call back not supported");
            }
        }
    }
}

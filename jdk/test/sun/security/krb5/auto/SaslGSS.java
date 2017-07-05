/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8012082 8019267
 * @summary SASL: auth-conf negotiated, but unencrypted data is accepted,
  *         reset to unencrypt
 * @compile -XDignore.symbol.file SaslGSS.java
 * @run main/othervm SaslGSS
 */

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.RealmCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ietf.jgss.*;
import sun.security.jgss.GSSUtil;

public class SaslGSS {

    public static void main(String[] args) throws Exception {

        String name = "host." + OneKDC.REALM.toLowerCase(Locale.US);

        new OneKDC(null).writeJAASConf();
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");

        // Client in JGSS so that it can control wrap privacy mode
        GSSManager m = GSSManager.getInstance();
        GSSContext sc = m.createContext(
                        m.createName(OneKDC.SERVER, GSSUtil.NT_GSS_KRB5_PRINCIPAL),
                        GSSUtil.GSS_KRB5_MECH_OID,
                        null,
                        GSSContext.DEFAULT_LIFETIME);
        sc.requestMutualAuth(false);

        // Server in SASL
        final HashMap props = new HashMap();
        props.put(Sasl.QOP, "auth-conf");
        SaslServer ss = Sasl.createSaslServer("GSSAPI", "server",
                name, props,
                new CallbackHandler() {
                    public void handle(Callback[] callbacks)
                            throws IOException, UnsupportedCallbackException {
                        for (Callback cb : callbacks) {
                            if (cb instanceof RealmCallback) {
                                ((RealmCallback) cb).setText(OneKDC.REALM);
                            } else if (cb instanceof AuthorizeCallback) {
                                ((AuthorizeCallback) cb).setAuthorized(true);
                            }
                        }
                    }
                });

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(bout));

        Logger.getLogger("javax.security.sasl").setLevel(Level.ALL);
        Handler h = new ConsoleHandler();
        h.setLevel(Level.ALL);
        Logger.getLogger("javax.security.sasl").addHandler(h);

        byte[] token = new byte[0];

        try {
            // Handshake
            token = sc.initSecContext(token, 0, token.length);
            token = ss.evaluateResponse(token);
            token = sc.unwrap(token, 0, token.length, new MessageProp(0, false));
            token[0] = (byte)(((token[0] & 4) != 0) ? 4 : 2);
            token = sc.wrap(token, 0, token.length, new MessageProp(0, false));
            ss.evaluateResponse(token);
        } finally {
            System.setErr(oldErr);
        }

        // Talk
        // 1. Client sends a auth-int message
        byte[] hello = "hello".getBytes();
        MessageProp qop = new MessageProp(0, false);
        token = sc.wrap(hello, 0, hello.length, qop);
        // 2. Server accepts it anyway
        ss.unwrap(token, 0, token.length);
        // 3. Server sends a message
        token = ss.wrap(hello, 0, hello.length);
        // 4. Client accepts, should be auth-conf
        sc.unwrap(token, 0, token.length, qop);
        if (!qop.getPrivacy()) {
            throw new Exception();
        }

        for (String s: bout.toString().split("\\n")) {
            if (s.contains("KRB5SRV04") && s.contains("NULL")) {
                return;
            }
        }
        System.out.println("=======================");
        System.out.println(bout.toString());
        System.out.println("=======================");
        throw new Exception("Haven't seen KRB5SRV04 with NULL");
    }
}

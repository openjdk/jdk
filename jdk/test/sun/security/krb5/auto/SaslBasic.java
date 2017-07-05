/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7110803
 * @summary SASL service for multiple hostnames
 * @compile -XDignore.symbol.file SaslBasic.java
 * @run main/othervm SaslBasic bound
 * @run main/othervm SaslBasic unbound
 */
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.*;

// The basic krb5 test skeleton you can copy from
public class SaslBasic {

    public static void main(String[] args) throws Exception {

        boolean bound = args[0].equals("bound");
        String name = "host." + OneKDC.REALM.toLowerCase(Locale.US);

        new OneKDC(null).writeJAASConf();
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");

        HashMap clntprops = new HashMap();
        clntprops.put(Sasl.QOP, "auth-conf");
        SaslClient sc = Sasl.createSaslClient(
                new String[]{"GSSAPI"}, null, "server",
                name, clntprops, null);

        final HashMap srvprops = new HashMap();
        srvprops.put(Sasl.QOP, "auth,auth-int,auth-conf");
        SaslServer ss = Sasl.createSaslServer("GSSAPI", "server",
                bound? name: null, srvprops,
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

        byte[] token = new byte[0];
        while (!sc.isComplete() || !ss.isComplete()) {
            if (!sc.isComplete()) {
                token = sc.evaluateChallenge(token);
            }
            if (!ss.isComplete()) {
                token = ss.evaluateResponse(token);
            }
        }
        if (!bound) {
            String boundName = (String)ss.getNegotiatedProperty(Sasl.BOUND_SERVER_NAME);
            if (!boundName.equals(name)) {
                throw new Exception("Wrong bound server name");
            }
        }
        byte[] hello = "hello".getBytes();
        token = sc.wrap(hello, 0, hello.length);
        token = ss.unwrap(token, 0, token.length);
        if (!Arrays.equals(hello, token)) {
            throw new Exception("Message altered");
        }
    }
}

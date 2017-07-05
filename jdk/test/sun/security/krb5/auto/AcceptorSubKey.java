/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7077646
 * @summary gssapi wrap for CFX per-message tokens always set FLAG_ACCEPTOR_SUBKEY
 * @compile -XDignore.symbol.file AcceptorSubKey.java
 * @run main/othervm AcceptorSubKey
 */

import java.util.Arrays;
import sun.security.jgss.GSSUtil;

// The basic krb5 test skeleton you can copy from
public class AcceptorSubKey {

    public static void main(String[] args) throws Exception {

        new OneKDC(null).writeJAASConf();

        Context c, s;
        c = Context.fromJAAS("client");
        s = Context.fromJAAS("server");

        c.startAsClient(OneKDC.SERVER, GSSUtil.GSS_SPNEGO_MECH_OID);
        s.startAsServer(GSSUtil.GSS_SPNEGO_MECH_OID);

        Context.handshake(c, s);

        byte[] msg = "i say high --".getBytes();
        byte[] wrapped = s.wrap(msg, false);

        // FLAG_ACCEPTOR_SUBKEY is 4
        int flagOn = wrapped[2] & 4;
        if (flagOn != 0) {
            throw new Exception("Java GSS should not have set acceptor subkey");
        }

        s.dispose();
        c.dispose();
    }
}

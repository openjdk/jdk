/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * @test
 * @bug 6893158
 * @summary AP_REQ check should use key version number
 */

import sun.security.jgss.GSSUtil;
import sun.security.krb5.PrincipalName;
import sun.security.krb5.internal.ktab.KeyTab;

public class MoreKvno {

    public static void main(String[] args)
            throws Exception {

        OneKDC kdc = new OneKDC(null);
        kdc.writeJAASConf();

        // Rewrite keytab, 3 set of keys with different kvno
        KeyTab ktab = KeyTab.create(OneKDC.KTAB);
        PrincipalName p = new PrincipalName(OneKDC.SERVER+"@"+OneKDC.REALM, PrincipalName.KRB_NT_SRV_HST);
        ktab.addEntry(p, "pass0".toCharArray(), 0);
        ktab.addEntry(p, "pass2".toCharArray(), 2);
        ktab.addEntry(p, "pass1".toCharArray(), 1);
        ktab.save();

        kdc.addPrincipal(OneKDC.SERVER, "pass1".toCharArray());
        go(OneKDC.SERVER, "com.sun.security.jgss.krb5.accept");
        kdc.addPrincipal(OneKDC.SERVER, "pass2".toCharArray());
        // "server" initiate also, check pass2 is used at authentication
        go(OneKDC.SERVER, "server");
    }

    static void go(String server, String entry) throws Exception {
        Context c, s;
        c = Context.fromUserPass("dummy", "bogus".toCharArray(), false);
        s = Context.fromJAAS(entry);

        c.startAsClient(server, GSSUtil.GSS_KRB5_MECH_OID);
        s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);

        Context.handshake(c, s);

        s.dispose();
        c.dispose();
    }
}

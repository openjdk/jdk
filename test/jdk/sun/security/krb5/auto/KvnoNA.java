/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7197159 8194486 8367344
 * @summary accept different kvno if there no match
 * @library /test/lib
 * @compile -XDignore.symbol.file KvnoNA.java
 * @run main jdk.test.lib.FileInstaller TestHosts TestHosts
 * @run main/othervm -Djdk.net.hosts.file=TestHosts KvnoNA
 */

import jdk.test.lib.Asserts;
import org.ietf.jgss.GSSException;
import sun.security.jgss.GSSUtil;
import sun.security.krb5.KrbException;
import sun.security.krb5.PrincipalName;
import sun.security.krb5.internal.ktab.KeyTab;
import sun.security.krb5.internal.Krb5;

public class KvnoNA {

    public static void main(String[] args)
            throws Exception {

        OneKDC kdc = new OneKDC(null);
        kdc.writeJAASConf();

        // Use backend as server because its isInitiator is false,
        // therefore no login failure. In KDC, kvno is 2.
        kdc.addPrincipal(OneKDC.BACKEND, "pass2".toCharArray());

        // Rewrite a keytab which might contain different keys from KDC
        KeyTab ktab = KeyTab.create(OneKDC.KTAB);
        PrincipalName p = new PrincipalName(
                OneKDC.BACKEND + "@" + OneKDC.REALM, PrincipalName.KRB_NT_SRV_HST);
        ktab.addEntry(p, "pass1".toCharArray(), 1, true);

        // Usual case, kvno 2 has the same password
        ktab.addEntry(p, "pass2".toCharArray(), 2, true);
        ktab.save();
        check();

        // Usual case, kvno 2 has a different password
        ktab.deleteEntries(p, -1, 2);
        ktab.addEntry(p, "pass3".toCharArray(), 2, true);
        ktab.save();
        Asserts.assertTrue(
                Asserts.assertThrows(GSSException.class, KvnoNA::check)
                                .getMessage().contains("Checksum failed"));

        // 7197159: No kvno 2, kvno 3 has the same password
        ktab.deleteEntries(p, -1, 2);
        ktab.addEntry(p, "pass2".toCharArray(), 3, true);
        ktab.save();
        check();

        // 8367344: No kvno 2, kvno 3 has a different password
        ktab.deleteEntries(p, -1, 3);
        ktab.addEntry(p, "pass3".toCharArray(), 3, true);
        ktab.save();
        Asserts.assertTrue(Asserts.assertThrows(GSSException.class, KvnoNA::check)
                .getCause() instanceof KrbException ke
                && ke.returnCode() == Krb5.KRB_AP_ERR_BADKEYVER);
    }

    static void check() throws Exception {
        Context c, s;

        c = Context.fromUserPass("dummy", "bogus".toCharArray(), false);
        s = Context.fromJAAS("backend");

        c.startAsClient(OneKDC.BACKEND, GSSUtil.GSS_KRB5_MECH_OID);
        s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);

        Context.handshake(c, s);

        s.dispose();
        c.dispose();
    }
}

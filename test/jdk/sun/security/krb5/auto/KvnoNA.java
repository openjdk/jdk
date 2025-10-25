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
 * @run main/othervm -Djdk.net.hosts.file=TestHosts KvnoNA des-cbc-md5
 * @run main/othervm -Djdk.net.hosts.file=TestHosts KvnoNA des-cbc-crc
 * @run main/othervm -Djdk.net.hosts.file=TestHosts KvnoNA des3-cbc-sha1
 * @run main/othervm -Djdk.net.hosts.file=TestHosts KvnoNA rc4-hmac
 */

import jdk.test.lib.Asserts;
import org.ietf.jgss.GSSException;
import sun.security.jgss.GSSUtil;
import sun.security.krb5.Config;
import sun.security.krb5.KrbException;
import sun.security.krb5.PrincipalName;
import sun.security.krb5.internal.ktab.KeyTab;
import sun.security.krb5.internal.Krb5;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class KvnoNA {

    static void prepareKtabs(String etype) throws Exception {

        // Set up a temporary krb5.conf so we can generate ktab files
        // using the preferred etype.
        if (etype != null && etype.startsWith("des-cbc-")) {
            // When DES is used, we always write des-cbc-crc keys.
            // They should also be used by des-cbc-md5.
            Files.writeString(Path.of("temp.conf"), """
                    [libdefaults]
                    permitted_enctypes=des-cbc-crc
                    allow_weak_crypto = true
                    """);
        } else if (etype != null) {
            Files.writeString(Path.of("temp.conf"), String.format("""
                    [libdefaults]
                    permitted_enctypes=%s
                    allow_weak_crypto = true
                    """, etype));
        } else {
            Files.writeString(Path.of("temp.conf"), """
                    [libdefaults]
                    """);
        }

        System.setProperty("java.security.krb5.conf", "temp.conf");
        Config.refresh();

        PrincipalName p = new PrincipalName(
                OneKDC.SERVER + "@" + OneKDC.REALM, PrincipalName.KRB_NT_SRV_HST);

        // Case 1, kvno 2 has the same password
        KeyTab ktab = KeyTab.create("good2");
        ktab.addEntry(p, "pass2".toCharArray(), 2, true);
        ktab.save();

        // Case 2, kvno 2 has a different password
        ktab = KeyTab.create("bad2");
        ktab.addEntry(p, "pass3".toCharArray(), 2, true);
        ktab.save();

        // Case 3 (7197159), No kvno 2, kvno 3 has the same password
        ktab = KeyTab.create("good3");
        ktab.addEntry(p, "pass2".toCharArray(), 3, true);
        ktab.save();

        // Case 4 (8367344), No kvno 2, kvno 3 has a different password
        ktab = KeyTab.create("bad3");
        ktab.addEntry(p, "pass3".toCharArray(), 3, true);
        ktab.save();
    }

    public static void main(String[] args) throws Exception {

        String etype = args.length > 0 ? args[0] : null;

        prepareKtabs(etype);
        OneKDC kdc = new OneKDC(etype);

        // In KDC, kvno is 2.
        kdc.addPrincipal(OneKDC.SERVER, "pass2".toCharArray());

        // Case1, succeed
        check("good2");

        // Case 2, fails but without KRB_AP_ERR_BADKEYVER
        var e = Asserts.assertThrows(GSSException.class, () -> check("bad2"));
        Asserts.assertTrue(!(e.getCause() instanceof KrbException ke)
                || ke.returnCode() != Krb5.KRB_AP_ERR_BADKEYVER, e.toString());

        // Case 3, succeed
        check("good3");

        // Case 4, fails with KRB_AP_ERR_BADKEYVER
        e = Asserts.assertThrows(GSSException.class, () -> check("bad3"));
        Asserts.assertTrue(e.getCause() instanceof KrbException ke
                && ke.returnCode() == Krb5.KRB_AP_ERR_BADKEYVER, e.toString());
    }

    static void check(String ktab) throws Exception {

        Context c = Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        Context s = Context.fromUserKtab(OneKDC.SERVER, ktab, true);

        try {
            c.startAsClient(OneKDC.SERVER, GSSUtil.GSS_KRB5_MECH_OID);
            s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);

            Context.handshake(c, s);
        } finally {
            s.dispose();
            c.dispose();
        }
    }
}

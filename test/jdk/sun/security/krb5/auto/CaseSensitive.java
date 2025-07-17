/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8331975
 * @summary ensure correct name comparison when a system property is set
 * @library /test/lib
 * @compile -XDignore.symbol.file CaseSensitive.java
 * @run main jdk.test.lib.FileInstaller TestHosts TestHosts
 * @run main/othervm -Djdk.net.hosts.file=TestHosts CaseSensitive no
 * @run main/othervm -Djdk.net.hosts.file=TestHosts
 *      -Djdk.security.krb5.name.case.sensitive=true CaseSensitive yes
 */

import jdk.test.lib.Asserts;
import org.ietf.jgss.GSSException;
import sun.security.jgss.GSSUtil;

public class CaseSensitive {

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "yes" -> testSensitive();
            case "no" -> testInsensitive();
        }
    }

    static void testSensitive() throws Exception {
        var kdc = new OneKDC(null).writeJAASConf();
        kdc.addPrincipal("hello", "password".toCharArray());
        kdc.writeKtab(OneKDC.KTAB);

        Context c = Context.fromJAAS("client");
        Context s = Context.fromJAAS("com.sun.security.jgss.krb5.accept");

        // There is only "hello". Cannot talk to "HELLO"
        c.startAsClient("HELLO", GSSUtil.GSS_KRB5_MECH_OID);
        s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);
        try {
            Context.handshake(c, s);
            throw new RuntimeException("Should not succeed");
        } catch(GSSException ge) {
            System.out.println(ge.getMessage());
            System.out.println("No HELLO in db. Expected");
        }

        // Add "HELLO". Can talk to "HELLO" now.
        kdc.addPrincipal("HELLO", "different".toCharArray());
        kdc.writeKtab(OneKDC.KTAB);

        c.startAsClient("HELLO", GSSUtil.GSS_KRB5_MECH_OID);
        s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);
        Context.handshake(c, s);
        // Name could be partial without realm, so only compare the beginning
        Asserts.assertTrue(c.x().getTargName().toString().startsWith("HELLO"),
                c.x().getTargName().toString());
        Asserts.assertTrue(s.x().getTargName().toString().startsWith("HELLO"),
                s.x().getTargName().toString());

        // Can also talk to "hello", which has a different password.
        c.startAsClient("hello", GSSUtil.GSS_KRB5_MECH_OID);
        s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);
        Context.handshake(c, s);
        Asserts.assertTrue(c.x().getTargName().toString().startsWith("hello"),
                c.x().getTargName().toString());
        Asserts.assertTrue(s.x().getTargName().toString().startsWith("hello"),
                s.x().getTargName().toString());
    }

    static void testInsensitive() throws Exception {
        var kdc = new OneKDC(null).writeJAASConf();
        kdc.addPrincipal("hello", "password".toCharArray());
        kdc.writeKtab(OneKDC.KTAB);

        Context c = Context.fromJAAS("client");
        Context s = Context.fromJAAS("com.sun.security.jgss.krb5.accept");

        // There is only "hello" but we can talk to "HELLO".
        c.startAsClient("HELLO", GSSUtil.GSS_KRB5_MECH_OID);
        s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);
        Context.handshake(c, s);
        Asserts.assertTrue(c.x().getTargName().toString().startsWith("HELLO"),
                c.x().getTargName().toString());
        Asserts.assertTrue(s.x().getTargName().toString().startsWith("HELLO"),
                s.x().getTargName().toString());
    }
}

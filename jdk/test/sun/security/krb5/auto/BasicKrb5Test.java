/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6706974
 * @summary Add krb5 test infrastructure
 * @run main/othervm BasicKrb5Test
 * @run main/othervm BasicKrb5Test des-cbc-crc
 * @run main/othervm BasicKrb5Test des-cbc-md5
 * @run main/othervm BasicKrb5Test des3-cbc-sha1
 * @run main/othervm BasicKrb5Test aes128-cts
 * @run main/othervm BasicKrb5Test aes256-cts
 * @run main/othervm BasicKrb5Test rc4-hmac
 * @run main/othervm BasicKrb5Test -s
 * @run main/othervm BasicKrb5Test des-cbc-crc -s
 * @run main/othervm BasicKrb5Test des-cbc-md5 -s
 * @run main/othervm BasicKrb5Test des3-cbc-sha1 -s
 * @run main/othervm BasicKrb5Test aes128-cts -s
 * @run main/othervm BasicKrb5Test aes256-cts -s
 * @run main/othervm BasicKrb5Test rc4-hmac -s
 * @run main/othervm BasicKrb5Test -C
 * @run main/othervm BasicKrb5Test des-cbc-crc -C
 * @run main/othervm BasicKrb5Test des-cbc-md5 -C
 * @run main/othervm BasicKrb5Test des3-cbc-sha1 -C
 * @run main/othervm BasicKrb5Test aes128-cts -C
 * @run main/othervm BasicKrb5Test aes256-cts -C
 * @run main/othervm BasicKrb5Test rc4-hmac -C
 * @run main/othervm BasicKrb5Test -s -C
 * @run main/othervm BasicKrb5Test des-cbc-crc -s -C
 * @run main/othervm BasicKrb5Test des-cbc-md5 -s -C
 * @run main/othervm BasicKrb5Test des3-cbc-sha1 -s -C
 * @run main/othervm BasicKrb5Test aes128-cts -s -C
 * @run main/othervm BasicKrb5Test aes256-cts -s -C
 * @run main/othervm BasicKrb5Test rc4-hmac -s -C
 */

import org.ietf.jgss.GSSName;
import sun.security.jgss.GSSUtil;
import sun.security.krb5.Config;
import sun.security.krb5.internal.crypto.EType;

/**
 * Basic JGSS/krb5 test with 3 parties: client, server, backend server. Each
 * party uses JAAS login to get subjects and executes JGSS calls using
 * Subject.doAs.
 */
public class BasicKrb5Test {

    private static boolean conf = true;
    /**
     * @param args empty or etype
     */
    public static void main(String[] args)
            throws Exception {

        String etype = null;
        for (String arg: args) {
            if (arg.equals("-s")) Context.usingStream = true;
            else if(arg.equals("-C")) conf = false;
            else etype = arg;
        }

        // Creates and starts the KDC. This line must be put ahead of etype check
        // since the check needs a krb5.conf.
        new OneKDC(etype).writeJAASConf();

        System.out.println("Testing etype " + etype);
        if (etype != null && !EType.isSupported(Config.getInstance().getType(etype))) {
            // aes256 is not enabled on all systems
            System.out.println("Not supported.");
            return;
        }

        new BasicKrb5Test().go(OneKDC.SERVER, OneKDC.BACKEND);
    }

    void go(final String server, final String backend) throws Exception {
        Context c, s, s2, b;
        c = Context.fromJAAS("client");
        s = Context.fromJAAS("server");
        b = Context.fromJAAS("backend");

        c.startAsClient(server, GSSUtil.GSS_KRB5_MECH_OID);
        c.x().requestCredDeleg(true);
        c.x().requestConf(conf);
        s.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);

        c.status();
        s.status();

        Context.handshake(c, s);
        GSSName client = c.x().getSrcName();

        c.status();
        s.status();

        Context.transmit("i say high --", c, s);
        Context.transmit("   you say low", s, c);

        s2 = s.delegated();
        s.dispose();
        s = null;

        s2.startAsClient(backend, GSSUtil.GSS_KRB5_MECH_OID);
        s2.x().requestConf(conf);
        b.startAsServer(GSSUtil.GSS_KRB5_MECH_OID);

        s2.status();
        b.status();

        Context.handshake(s2, b);
        GSSName client2 = b.x().getSrcName();

        if (!client.equals(client2)) {
            throw new Exception("Delegation failed");
        }

        s2.status();
        b.status();

        Context.transmit("you say hello --", s2, b);
        Context.transmit("   i say goodbye", b, s2);

        s2.dispose();
        b.dispose();
    }
}

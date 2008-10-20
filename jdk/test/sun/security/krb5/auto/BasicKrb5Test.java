/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 6706974
 * @summary Add krb5 test infrastructure
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

    /**
     * @param args empty or etype
     */
    public static void main(String[] args)
            throws Exception {

        String etype = null;
        if (args.length > 0) {
            etype = args[0];
        }

        // Creates and starts the KDC. This line must be put ahead of etype check
        // since the check needs a krb5.conf.
        new OneKDC(etype).writeJAASConf();

        System.out.println("Testing etype " + etype);
        if (etype != null && !EType.isSupported(Config.getInstance().getType(etype))) {
            System.out.println("Not supported.");
            System.exit(0);
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

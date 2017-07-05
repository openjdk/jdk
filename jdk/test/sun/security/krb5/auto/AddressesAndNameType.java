/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4501327 4868379 8039132
 * @run main/othervm AddressesAndNameType 1
 * @run main/othervm AddressesAndNameType 2
 * @run main/othervm AddressesAndNameType 3
 * @summary noaddresses settings and server name type
 */

import java.net.InetAddress;
import java.util.Set;
import sun.security.krb5.Config;

import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;

public class AddressesAndNameType {

    public static void main(String[] args)
            throws Exception {

        OneKDC kdc = new OneKDC(null);
        kdc.writeJAASConf();

        String extraLine;
        switch (args[0]) {
            case "1": extraLine = "noaddresses = false"; break;
            case "2": extraLine = "noaddresses = true"; break;
            default: extraLine = ""; break;
        }

        KDC.saveConfig(OneKDC.KRB5_CONF, kdc,
                extraLine);
        Config.refresh();

        Context c = Context.fromUserPass(OneKDC.USER, OneKDC.PASS, false);
        Set<KerberosTicket> tickets =
                c.s().getPrivateCredentials(KerberosTicket.class);

        if (tickets.isEmpty()) throw new Exception();
        KerberosTicket ticket = tickets.iterator().next();
        InetAddress[] addresses = ticket.getClientAddresses();

        switch (args[0]) {
            case "1":
                if (addresses == null || addresses.length == 0) {
                    throw new Exception("No addresses");
                }
                if (ticket.getServer().getNameType()
                        != KerberosPrincipal.KRB_NT_SRV_INST) {
                    throw new Exception(
                            "Wrong type: " + ticket.getServer().getNameType());
                }
                break;
            default:
                if (addresses != null && addresses.length != 0) {
                    throw new Exception("See addresses");
                }
                break;
        }
    }
}

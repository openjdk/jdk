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
 * @bug 6682516
 * @summary SPNEGO_HTTP_AUTH/WWW_KRB and SPNEGO_HTTP_AUTH/WWW_SPNEGO failed on all non-windows platforms
 * @run main/othervm -Dsun.net.spi.nameservice.provider.1=ns,mock -Djava.security.krb5.conf=krb5.conf Test
 */

import java.net.InetAddress;
import java.net.UnknownHostException;
import sun.net.spi.nameservice.NameService;
import sun.net.spi.nameservice.NameServiceDescriptor;
import sun.security.krb5.PrincipalName;

public class Test implements NameServiceDescriptor {
    public static void main(String[] args) throws Exception {
        // This config file is generated using Kerberos.app on a Mac
        System.setProperty("java.security.krb5.realm", "THIS.REALM");
        System.setProperty("java.security.krb5.kdc", "localhost");

        // add using canonicalized name
        check("c1", "c1.this.domain");
        check("c1.this", "c1.this.domain");
        check("c1.this.domain", "c1.this.domain");

        // canonicalized name goes IP, reject
        check("c2", "c2");

        // canonicalized name goes strange, reject
        check("c3", "c3");

        // unsupported
        check("c4", "c4");
    }

    static void check(String input, String output) throws Exception {
        System.out.println(input + " -> " + output);
        PrincipalName pn = new PrincipalName("host/"+input,
                PrincipalName.KRB_NT_SRV_HST);
        if (!pn.getNameStrings()[1].equals(output)) {
            throw new Exception("Output is " + pn);
        }
    }

    @Override
    public NameService createNameService() throws Exception {
        NameService ns = new NameService() {
            @Override
            public InetAddress[] lookupAllHostAddr(String host)
                    throws UnknownHostException {
                // All c<n>.* goes to 127.0.0.n
                int i = Integer.valueOf(host.split("\\.")[0].substring(1));
                return new InetAddress[]{
                    InetAddress.getByAddress(host, new byte[]{127,0,0,(byte)i})
                };
            }
            @Override
            public String getHostByAddr(byte[] addr)
                    throws UnknownHostException {
                int i = addr[3];
                switch (i) {
                    case 1: return "c1.this.domain";        // Good
                    case 2: return "127.0.0.2";             // Only IP
                    case 3: return "d3.this.domain";        // name change
                    default:
                        throw new UnknownHostException();
                }
            }
        };
        return ns;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public String getType() {
        return "ns";
    }
}

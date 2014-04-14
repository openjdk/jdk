/*
 * Copyright (c) 2005, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4641821
 * @summary hashCode() and equals() for KerberosKey and KerberosTicket
 */

/*
 * Must setup KDC and Kerberos configuration file
 */

import java.net.InetAddress;
import java.util.Date;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;

public class KerberosHashEqualsTest {
    public static void main(String[] args) throws Exception {
        new KerberosHashEqualsTest().check();
    }

    void checkSame(Object o1, Object o2) {
        if(!o1.equals(o2)) {
            throw new RuntimeException("equals() fails");
        }
        if(o1.hashCode() != o2.hashCode()) {
            throw new RuntimeException("hashCode() not same");
        }
    }

    void checkNotSame(Object o1, Object o2) {
        if(o1.equals(o2)) {
            throw new RuntimeException("equals() succeeds");
        }
    }

    void check() throws Exception {
        KerberosKey k1, k2;
        k1 = new KerberosKey(newKP("A"), "pass".getBytes(), 1, 1);
        k2 = new KerberosKey(newKP("A"), "pass".getBytes(), 1, 1);
        checkSame(k1, k1);  // me to me
        checkSame(k1, k2);  // same

        k2.destroy();
        checkNotSame(k1, k2);

        // destroyed keys doesn't equal to each other
        checkNotSame(k2, k1);
        checkSame(k2, k2);

        // a little different
        k2 = new KerberosKey(newKP("B"), "pass".getBytes(), 1, 1);
        checkNotSame(k1, k2);
        k2 = new KerberosKey(newKP("A"), "ssap".getBytes(), 1, 1);
        checkNotSame(k1, k2);
        k2 = new KerberosKey(newKP("A"), "pass".getBytes(), 2, 1);
        checkNotSame(k1, k2);
        k2 = new KerberosKey(newKP("A"), "pass".getBytes(), 1, 2);
        checkNotSame(k1, k2);

        k1 = new KerberosKey(null, "pass".getBytes(), 1, 2);
        checkNotSame(k1, k2); // null to non-null
        k2 = new KerberosKey(null, "pass".getBytes(), 1, 2);
        checkSame(k1, k2);    // null to null

        checkNotSame(k1, "Another Object");

        KerberosTicket t1, t2;
        t1 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(0), new Date(0), new Date(0), null);
        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(0), new Date(0), new Date(0), null);
        checkSame(t1, t1);
        checkSame(t1, t2);
        t2 = new KerberosTicket("asn11".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(0), new Date(0), new Date(0), null);
        checkNotSame(t1, t2);
        t2 = new KerberosTicket("asn1".getBytes(), newKP("client1"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(0), new Date(0), new Date(0), null);
        checkNotSame(t1, t2);
        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server1"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(0), new Date(0), new Date(0), null);
        checkNotSame(t1, t2);
        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass1".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(0), new Date(0), new Date(0), null);
        checkNotSame(t1, t2);
        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 2, new boolean[] {true, true}, new Date(0), new Date(0), new Date(0), new Date(0), null);
        checkNotSame(t1, t2);
        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {false, true}, new Date(0), new Date(0), new Date(0), new Date(0), null);
        checkNotSame(t1, t2);
        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(1), new Date(0), new Date(0), new Date(0), null);
        checkNotSame(t1, t2);
        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(1), new Date(0), new Date(0), null);
        checkNotSame(t1, t2);
        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(0), new Date(1), new Date(0), null);
        checkNotSame(t1, t2);
        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(0), new Date(0), new Date(0), new InetAddress[2]);
        checkNotSame(t1, t2);

        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(0), new Date(0), new Date(1), null);
        t1 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true}, new Date(0), new Date(0), new Date(0), new Date(2), null);
        checkSame(t1, t2);  // renewtill is useless

        t2.destroy();
        checkNotSame(t1, t2);

        // destroyed tickets doesn't equal to each other
        checkNotSame(t2, t1);
        checkSame(t2, t2);

        t2 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true, true, true, true, true, true, true, true, true}, new Date(0), new Date(0), new Date(0), new Date(1), null);
        t1 = new KerberosTicket("asn1".getBytes(), newKP("client"), newKP("server"), "pass".getBytes(), 1, new boolean[] {true, true, true, true, true, true, true, true, true, true}, new Date(0), new Date(0), new Date(0), new Date(2), null);
        checkNotSame(t1, t2);  // renewtill is useful

        checkNotSame(t1, "Another Object");
        System.out.println("Good!");
    }

    KerberosPrincipal newKP(String s) {
        return new KerberosPrincipal(s + "@JLABS.SFBAY.SUN.COM");
    }
}

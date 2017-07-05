/*
 * Copyright 2005-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * @bug 4641821
 * @summary hashCode() and equals() for KerberosKey and KerberosTicket
 */

import java.net.InetAddress;
import java.util.Date;
import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KerberosTicket;

public class KerberosHashEqualsTest {
    public static void main(String[] args) throws Exception {
        new OneKDC(null);
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

        // The key part:
        // new KerberosKey(principal, bytes, keyType, version)

        KerberosKey k1, k2;
        KerberosPrincipal CLIENT = new KerberosPrincipal("client");
        KerberosPrincipal SERVER = new KerberosPrincipal("server");
        byte[] PASS = "pass".getBytes();

        k1 = new KerberosKey(CLIENT, PASS, 1, 1);
        k2 = new KerberosKey(CLIENT, PASS, 1, 1);
        checkSame(k1, k1);  // me is me
        checkSame(k1, k2);  // same

        // A destroyed key doesn't equal to any key
        k2.destroy();
        checkNotSame(k1, k2);
        checkNotSame(k2, k1);
        k1.destroy();
        checkNotSame(k1, k2);   // even if they are both destroyed
        checkNotSame(k2, k1);
        checkSame(k2, k2);

        // a little difference means not equal
        k1 = new KerberosKey(CLIENT, PASS, 1, 1);
        k2 = new KerberosKey(SERVER, PASS, 1, 1);
        checkNotSame(k1, k2);   // Different principal name

        k2 = new KerberosKey(CLIENT, "ssap".getBytes(), 1, 1);
        checkNotSame(k1, k2);   // Different password

        k2 = new KerberosKey(CLIENT, PASS, 2, 1);
        checkNotSame(k1, k2);   // Different keytype

        k2 = new KerberosKey(CLIENT, PASS, 1, 2);
        checkNotSame(k1, k2);   // Different version

        k2 = new KerberosKey(null, PASS, 1, 2);
        checkNotSame(k1, k2);   // null is not non-null

        k1 = new KerberosKey(null, PASS, 1, 2);
        checkSame(k1, k2);      // null is null

        checkNotSame(k1, "Another Object");

        // The ticket part:
        // new KerberosTicket(asn1 bytes, client, server, session key, type, flags,
        //      auth, start, end, renewUntil times, address)

        KerberosTicket t1, t2;

        byte[] ASN1 = "asn1".getBytes();
        boolean[] FORWARDABLE = new boolean[] {true, true};
        boolean[] ALLTRUE = new boolean[] {true, true, true, true, true, true, true, true, true, true};
        Date D0 = new Date(0);

        t1 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, FORWARDABLE, D0, D0, D0, D0, null);
        t2 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, FORWARDABLE, D0, D0, D0, D0, null);
        checkSame(t1, t1);
        checkSame(t1, t2);

        // destroyed tickets doesn't equal to each other
        t1.destroy();
        checkNotSame(t1, t2);
        checkNotSame(t2, t1);

        t2.destroy();
        checkNotSame(t1, t2);   // even if they are both destroyed
        checkNotSame(t2, t1);

        checkSame(t2, t2);  // unless they are the same object

        // a little difference means not equal
        t1 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, FORWARDABLE, D0, D0, D0, D0, null);
        t2 = new KerberosTicket("asn11".getBytes(), CLIENT, SERVER, PASS, 1, FORWARDABLE, D0, D0, D0, D0, null);
        checkNotSame(t1, t2);   // Different ASN1 encoding

        t2 = new KerberosTicket(ASN1, new KerberosPrincipal("client1"), SERVER, PASS, 1, FORWARDABLE, D0, D0, D0, D0, null);
        checkNotSame(t1, t2);   // Different client

        t2 = new KerberosTicket(ASN1, CLIENT, new KerberosPrincipal("server1"), PASS, 1, FORWARDABLE, D0, D0, D0, D0, null);
        checkNotSame(t1, t2);   // Different server

        t2 = new KerberosTicket(ASN1, CLIENT, SERVER, "pass1".getBytes(), 1, FORWARDABLE, D0, D0, D0, D0, null);
        checkNotSame(t1, t2);   // Different session key

        t2 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 2, FORWARDABLE, D0, D0, D0, D0, null);
        checkNotSame(t1, t2);   // Different key type

        t2 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, new boolean[] {true, false}, D0, D0, D0, D0, null);
        checkNotSame(t1, t2);   // Different flags, not FORWARDABLE

        t2 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, FORWARDABLE, new Date(1), D0, D0, D0, null);
        checkNotSame(t1, t2);   // Different authtime

        t2 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, FORWARDABLE, D0, new Date(1), D0, D0, null);
        checkNotSame(t1, t2);   // Different starttime

        t2 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, FORWARDABLE, D0, D0, new Date(1), D0, null);
        checkNotSame(t1, t2);   // Different endtime

        t2 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, FORWARDABLE, D0, D0, D0, D0, new InetAddress[2]);
        checkNotSame(t1, t2);   // Different client addresses

        t2 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, FORWARDABLE, D0, D0, D0, new Date(1), null);
        t1 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, FORWARDABLE, D0, D0, D0, new Date(2), null);
        checkSame(t1, t2);      // renewtill is ignored when RENEWABLE ticket flag is not set.

        t2 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, ALLTRUE, D0, D0, D0, new Date(1), null);
        t1 = new KerberosTicket(ASN1, CLIENT, SERVER, PASS, 1, ALLTRUE, D0, D0, D0, new Date(2), null);
        checkNotSame(t1, t2);   // renewtill is used when RENEWABLE is set.

        checkNotSame(t1, "Another Object");
        System.out.println("Good!");
    }
}

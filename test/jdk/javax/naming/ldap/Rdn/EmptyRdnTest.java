/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     8391649
 * @summary Rdn(String) and, transitively, LdapName(String) / LdapName.add(String)
 *          must reject an RDN string that contains no attributeTypeAndValue
 *          (an "empty RDN") by throwing InvalidNameException, per the RFC 2253
 *          grammar and the documented contract of Rdn.getType()/getValue()
 *          ("returns the ... type" / "returns the ... value", never an
 *          exception other than InvalidNameException at construction time).
 * @run main EmptyRdnTest
 */

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.util.List;

public class EmptyRdnTest {

    private static int failures = 0;

    public static void main(String[] args) throws Exception {

        expectInvalidNameException("new Rdn(\"\")", () -> new Rdn(""));

        expectInvalidNameException("new Rdn(\"   \")", () -> new Rdn("   "));

        expectInvalidNameException("new LdapName(\"cn=x,\")", () -> new LdapName("cn=x,"));
        expectInvalidNameException("new LdapName(\"cn=x;\")", () -> new LdapName("cn=x;"));
        expectInvalidNameException("new LdapName(\"cn=a,ou=b,\")", () -> new LdapName("cn=a,ou=b,"));

        expectInvalidNameException("LdapName.add(\"\")", () -> {
            LdapName dn = new LdapName("dc=example,dc=com");
            dn.add("");
        });

        expectInvalidNameException("new LdapName(\"cn=a,,cn=b\")", () -> new LdapName("cn=a,,cn=b"));

        checkValidRdn("cn=x");
        checkValidRdn("cn=x+ou=y");
        checkValidRdn("1.2.840.113549.1.9.1=someone@example.com");
        checkValidRdn("cn=");           // empty *value* is legal: one entry, value ""

        LdapName dn = new LdapName("cn=a,ou=b,dc=c");
        if (dn.size() != 3) {
            fail("new LdapName(\"cn=a,ou=b,dc=c\") -> expected size 3, got " + dn.size());
        }
        List<Rdn> rdns = dn.getRdns();
        for (Rdn r : rdns) {
            if (r.size() == 0) {
                fail("valid multi-RDN name produced an empty Rdn: " + dn);
            }
            r.getType();
            r.getValue();
        }

        if (failures > 0) {
            throw new RuntimeException(failures + " check(s) failed, see output above");
        }
        System.out.println("All checks passed.");
    }

    private static void checkValidRdn(String s) throws InvalidNameException {
        Rdn r = new Rdn(s);
        if (r.size() == 0) {
            fail("new Rdn(\"" + s + "\") produced an empty Rdn (should be valid, size >= 1)");
            return;
        }
        r.getType();
        r.getValue();
    }

    private interface Thrower {
        void run() throws Exception;
    }

    /**
     * Runs {@code t}. Passes only if it throws exactly
     * InvalidNameException. Fails (but does not abort the whole test
     * run) if it throws nothing, or throws anything else -- most
     * notably the IndexOutOfBoundsException this test guards against.
     */
    private static void expectInvalidNameException(String label, Thrower t) {
        try {
            t.run();
            fail(label + " -> did not throw; expected InvalidNameException");
        } catch (InvalidNameException e) {
            System.out.println("OK: " + label + " -> InvalidNameException(\"" + e.getMessage() + "\")");
        } catch (Throwable e) {
            fail(label + " -> threw " + e.getClass().getName()
                    + " (\"" + e.getMessage() + "\"), expected InvalidNameException");
        }
    }

    private static void fail(String message) {
        failures++;
        System.out.println("FAIL: " + message);
    }
}

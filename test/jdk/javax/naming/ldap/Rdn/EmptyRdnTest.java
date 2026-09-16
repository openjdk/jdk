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
 * @bug 8391649
 * @summary LdapName and Rdn must reject an empty RDN component
 * @run junit ${test.main.class}
 */

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmptyRdnTest {

    @Test
    void rejectsEmptyRdn() {
        assertThrows(InvalidNameException.class, () -> new Rdn(""));
    }

    @Test
    void rejectsWhitespaceRdn() {
        assertThrows(InvalidNameException.class, () -> new Rdn("   "));
    }

    @Test
    void rejectsTrailingComma() {
        assertThrows(InvalidNameException.class,
                () -> new LdapName("cn=x,"));
    }

    @Test
    void rejectsTrailingSemicolon() {
        assertThrows(InvalidNameException.class,
                () -> new LdapName("cn=x;"));
    }

    @Test
    void rejectsTrailingCommaInMultiRdnName() {
        assertThrows(InvalidNameException.class,
                () -> new LdapName("cn=a,ou=b,"));
    }

    @Test
    void rejectsAddingEmptyRdn() throws InvalidNameException {
        LdapName dn = new LdapName("dc=example,dc=com");

        assertThrows(InvalidNameException.class, () -> dn.add(""));
        assertEquals(new LdapName("dc=example,dc=com"), dn);
    }

    @Test
    void rejectsEmptyIntermediateRdn() {
        assertThrows(InvalidNameException.class,
                () -> new LdapName("cn=a,,cn=b"));
    }

    @Test
    void acceptsSingleValuedRdn() throws InvalidNameException {
        Rdn rdn = new Rdn("cn=x");

        assertEquals(1, rdn.size());
        assertEquals("cn", rdn.getType());
        assertEquals("x", rdn.getValue());
    }

    @Test
    void acceptsMultiValuedRdn() throws InvalidNameException {
        Rdn rdn = new Rdn("cn=x+ou=y");

        assertEquals(2, rdn.size());
        assertNotNull(rdn.getType());
        assertNotNull(rdn.getValue());
    }

    @Test
    void acceptsOidAttributeType() throws InvalidNameException {
        Rdn rdn = new Rdn("1.2.840.113549.1.9.1=someone@example.com");

        assertEquals(1, rdn.size());
        assertEquals("1.2.840.113549.1.9.1", rdn.getType());
        assertEquals("someone@example.com", rdn.getValue());
    }

    @Test
    void acceptsEmptyAttributeValue() throws InvalidNameException {
        Rdn rdn = new Rdn("cn=");

        assertEquals(1, rdn.size());
        assertEquals("cn", rdn.getType());
        assertEquals("", rdn.getValue());
    }

    @Test
    void acceptsMultiRdnName() throws InvalidNameException {
        LdapName dn = new LdapName("cn=a,ou=b,dc=c");

        assertEquals(3, dn.size());
        for (Rdn rdn : dn.getRdns()) {
            assertEquals(1, rdn.size());
            assertNotNull(rdn.getType());
            assertNotNull(rdn.getValue());
        }
    }

    @Test
    void acceptsEmptyLdapName() throws InvalidNameException {
        LdapName dn = new LdapName("");

        assertTrue(dn.isEmpty());
        assertEquals(0, dn.size());
    }
}

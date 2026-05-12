/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sax;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.NamespaceSupport;

import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit/othervm sax.NSSupportTest
 * @summary Test NamespaceSupport.
 */
public class NSSupportTest {

    @Test
    public void testProcessName() {
        NamespaceSupport nssupport = new NamespaceSupport();

        nssupport.pushContext();
        nssupport.declarePrefix("", "http://www.java.com");
        nssupport.declarePrefix("dc", "http://www.purl.org/dc");

        String[] parts = new String[3];
        nssupport.processName("dc:name1", parts, false);
        assertEquals("http://www.purl.org/dc", parts[0]);
        assertEquals("name1", parts[1]);
        assertEquals("dc:name1", parts[2]);

        nssupport.processName("name2", parts, false);
        assertEquals("http://www.java.com", parts[0]);
        assertEquals("name2", parts[1]);
        assertEquals("name2", parts[2]);
    }

    @Test
    public void testNamespaceDeclUris() {
        String[] parts = new String[3];
        NamespaceSupport nssupport = new NamespaceSupport();

        nssupport.pushContext();
        assertFalse(nssupport.isNamespaceDeclUris());
        nssupport.declarePrefix("xmlns", "");
        nssupport.processName("xmlns:name", parts, true);
        assertNull(parts[0]);
        assertNull(parts[1]);
        assertNull(parts[2]);

        nssupport.reset();

        nssupport.setNamespaceDeclUris(true);
        nssupport.declarePrefix("xmlns", "");
        nssupport.processName("xmlns:name", parts, true);
        assertEquals(NamespaceSupport.NSDECL, parts[0]);
        assertEquals("name", parts[1]);
        assertEquals("xmlns:name", parts[2]);

        nssupport.reset();

        nssupport.setNamespaceDeclUris(true);
        nssupport.declarePrefix("xml", "");
        nssupport.processName("xml:name", parts, true);
        assertEquals(NamespaceSupport.XMLNS, parts[0]);
        assertEquals("name", parts[1]);
        assertEquals("xml:name", parts[2]);

    }

    @Test
    public void testPopContext() {
        String[] parts = new String[3];
        NamespaceSupport nssupport = new NamespaceSupport();

        nssupport.pushContext();
        nssupport.declarePrefix("dc", "http://www.purl.org/dc");
        assertEquals("dc", nssupport.getPrefix("http://www.purl.org/dc"));

        nssupport.popContext();
        assertNull(nssupport.getPrefix("http://www.purl.org/dc"));
        nssupport.processName("dc:name1", parts, false);
        assertNull(parts[0]);
        assertNull(parts[1]);
        assertNull(parts[2]);
    }

    @Test
    public void testPrefixAndUri1() {
        boolean hasdc = false;
        boolean hasdc1 = false;
        boolean hasdc2 = false;
        boolean hasdcnew = false;
        NamespaceSupport nssupport = new NamespaceSupport();

        nssupport.pushContext();
        nssupport.declarePrefix("dc", "http://www.purl.org/dc");

        nssupport.pushContext();
        nssupport.declarePrefix("dc1", "http://www.purl.org/dc");
        nssupport.declarePrefix("dc2", "http://www.purl.org/dc2");
        nssupport.declarePrefix("dcnew", "http://www.purl.org/dcnew");

        Enumeration<String> enu1 = nssupport.getDeclaredPrefixes();
        while (enu1.hasMoreElements()) {
            String str = enu1.nextElement();
            if (str.equals("dc")) {
                hasdc = true;
            } else if (str.equals("dc1")) {
                hasdc1 = true;
            } else if (str.equals("dc2")) {
                hasdc2 = true;
            } else if (str.equals("dcnew")) {
                hasdcnew = true;
            }
        }
        assertTrue(hasdcnew && hasdc1 && hasdc2);
        assertFalse(hasdc);
    }

    @Test
    public void testPrefixAndUri2() {
        boolean hasdc = false;
        boolean hasdc1 = false;
        boolean hasdc2 = false;
        boolean hasdcnew = false;
        NamespaceSupport nssupport = new NamespaceSupport();

        nssupport.pushContext();
        nssupport.declarePrefix("dc", "http://www.purl.org/dc");

        nssupport.pushContext();
        nssupport.declarePrefix("dc1", "http://www.purl.org/dc");
        nssupport.declarePrefix("dc2", "http://www.purl.org/dc2");
        nssupport.declarePrefix("dcnew", "http://www.purl.org/dcnew");

        Enumeration<String> enu1 = nssupport.getPrefixes();
        while (enu1.hasMoreElements()) {
            String str = enu1.nextElement();
            if (str.equals("dc")) {
                hasdc = true;
            } else if (str.equals("dc1")) {
                hasdc1 = true;
            } else if (str.equals("dc2")) {
                hasdc2 = true;
            } else if (str.equals("dcnew")) {
                hasdcnew = true;
            }
        }
        assertTrue(hasdcnew && hasdc1 && hasdc2 && hasdc);
    }

    @Test
    public void testPrefixAndUri3() {
        boolean hasdc = false;
        boolean hasdc1 = false;
        boolean hasdc2 = false;
        boolean hasdcnew = false;
        NamespaceSupport nssupport = new NamespaceSupport();

        nssupport.pushContext();
        nssupport.declarePrefix("dc", "http://www.purl.org/dc");

        nssupport.pushContext();
        nssupport.declarePrefix("dc1", "http://www.purl.org/dc");
        nssupport.declarePrefix("dc2", "http://www.purl.org/dc2");
        nssupport.declarePrefix("dcnew", "http://www.purl.org/dcnew");

        Enumeration<String> enu1 = nssupport.getPrefixes("http://www.purl.org/dc");
        while (enu1.hasMoreElements()) {
            String str = enu1.nextElement();
            if (str.equals("dc")) {
                hasdc = true;
            } else if (str.equals("dc1")) {
                hasdc1 = true;
            } else if (str.equals("dc2")) {
                hasdc2 = true;
            } else if (str.equals("dcnew")) {
                hasdcnew = true;
            }
        }
        assertTrue(hasdc1 && hasdc);
        assertFalse(hasdc2);
        assertFalse(hasdcnew);
    }

    @Test
    public void testPrefixAndUri4() {
        NamespaceSupport nssupport = new NamespaceSupport();

        nssupport.pushContext();
        nssupport.declarePrefix("dc", "http://www.purl.org/dc");

        nssupport.pushContext();
        nssupport.declarePrefix("dc1", "http://www.purl.org/dc");
        nssupport.declarePrefix("dc2", "http://www.purl.org/dc2");
        nssupport.declarePrefix("dcnew", "http://www.purl.org/dcnew");

        assertEquals("http://www.purl.org/dc", nssupport.getURI("dc"));
        assertEquals("http://www.purl.org/dc", nssupport.getURI("dc1"));
        assertEquals("http://www.purl.org/dc2", nssupport.getURI("dc2"));
        assertEquals("http://www.purl.org/dcnew", nssupport.getURI("dcnew"));

        // Negative test
        assertNull(nssupport.getURI("wrong_prefix"));
        assertNull(nssupport.getURI(""));
    }
}

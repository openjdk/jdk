/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.xml.sax.ptests;

import org.junit.jupiter.api.Test;
import org.xml.sax.helpers.AttributesImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Class containing the test cases for AttributesImpl API.
 */
/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm org.xml.sax.ptests.AttrImplTest
 */
public class AttrImplTest {
    private static final String CAR_URI = "http://www.cars.com/xml";

    private static final String CAR_LOCALNAME = "part";

    private static final String CAR_QNAME = "p";

    private static final String CAR_TYPE = "abc";

    private static final String CAR_VALUE = "Merc";

    private static final String JEEP_URI = "http://www.jeeps.com/xml";

    private static final String JEEP_LOCALNAME = "wheel";

    private static final String JEEP_QNAME = "w";

    private static final String JEEP_TYPE = "xyz";

    private static final String JEEP_VALUE = "Mit";

    /**
     * Basic test for getIndex(String).
     */
    @Test
    public void testcase01() {
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(CAR_URI, CAR_LOCALNAME, CAR_QNAME, CAR_TYPE, CAR_VALUE);
        attr.addAttribute(JEEP_URI, JEEP_LOCALNAME, JEEP_QNAME, JEEP_TYPE,
                JEEP_VALUE);
        assertEquals(0, attr.getIndex(CAR_QNAME));
        assertEquals(1, attr.getIndex(JEEP_QNAME));
    }

    /**
     * Basic test for getIndex(String, String).
     */
    @Test
    public void testcase02() {
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(CAR_URI, CAR_LOCALNAME, CAR_QNAME, CAR_TYPE, CAR_VALUE);
        attr.addAttribute(JEEP_URI, JEEP_LOCALNAME, JEEP_QNAME, JEEP_TYPE,
                JEEP_VALUE);
        assertEquals(1, attr.getIndex(JEEP_URI, JEEP_LOCALNAME));
    }

    /**
     * getIndex(String, String) returns -1 if none matches.
     */
    @Test
    public void testcase03() {
        AttributesImpl attr = new AttributesImpl();
        assertEquals(-1, attr.getIndex(JEEP_URI, "whl"));
    }

    /**
     * Basic test for getType(int) and getType(String).
     */
    @Test
    public void testcase04() {
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(CAR_URI, CAR_LOCALNAME, CAR_QNAME, CAR_TYPE, CAR_VALUE);
        attr.addAttribute(JEEP_URI, JEEP_LOCALNAME, JEEP_QNAME, JEEP_TYPE,
                JEEP_VALUE);
        assertEquals(JEEP_TYPE, attr.getType(1));
        assertEquals(JEEP_TYPE, attr.getType(JEEP_QNAME));
    }

    /**
     * Basic test for getValue(int), getValue(String) and getValue(String, String).
     */
    @Test
    public void testcase05() {
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(CAR_URI, CAR_LOCALNAME, CAR_QNAME, CAR_TYPE, CAR_VALUE);
        attr.addAttribute(JEEP_URI, JEEP_LOCALNAME, JEEP_QNAME, JEEP_TYPE,
                JEEP_VALUE);
        assertEquals(JEEP_VALUE, attr.getValue(1));
        assertEquals(JEEP_VALUE, attr.getValue(attr.getQName(1)));
        assertEquals(JEEP_VALUE, attr.getValue(attr.getURI(1), attr.getLocalName(1)));
    }

    /**
     * Basic test for getLocalName(int), getQName(int), getType(int),
     * getType(String) and getURI(int).
     */
    @Test
    public void testcase06() {
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(CAR_URI, CAR_LOCALNAME, CAR_QNAME, CAR_TYPE, CAR_VALUE);
        attr.addAttribute(JEEP_URI, JEEP_LOCALNAME, JEEP_QNAME, JEEP_TYPE,
                JEEP_VALUE);
        attr.setAttribute(1, "www.megginson.com", "author", "meg", "s", "SAX2");
        assertEquals("author", attr.getLocalName(1));
        assertEquals("meg", attr.getQName(1));
        assertEquals("s", attr.getType(1));
        assertEquals("s", attr.getType("meg"));
        assertEquals("www.megginson.com", attr.getURI(1));
    }

    /**
     * Basic test for setLocalName(int, String), setQName(int, String),
     * setType(int, String), setValue(int, String) and setURI(int, String).
     */
    @Test
    public void testcase07() {
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(CAR_URI, CAR_LOCALNAME, CAR_QNAME, CAR_TYPE, CAR_VALUE);
        attr.addAttribute(JEEP_URI, JEEP_LOCALNAME, JEEP_QNAME, JEEP_TYPE,
                JEEP_VALUE);
        attr.setLocalName(1, "speclead");
        attr.setQName(1, "megi");
        attr.setType(1, "sax");
        attr.setValue(1, "SAX01");
        attr.setURI(1, "www.megginson.com/sax/sax01");

        assertEquals("speclead", attr.getLocalName(1));
        assertEquals("megi", attr.getQName(1));
        assertEquals("sax", attr.getType(1));
        assertEquals("sax", attr.getType("megi"));
        assertEquals("www.megginson.com/sax/sax01", attr.getURI(1));
    }

    /**
     * Basic test for getLength().
     */
    @Test
    public void testcase08() {
        AttributesImpl attr = new AttributesImpl();
        assertEquals(0, attr.getLength());
        attr.addAttribute(CAR_URI, CAR_LOCALNAME, CAR_QNAME, CAR_TYPE, CAR_VALUE);
        attr.addAttribute(JEEP_URI, JEEP_LOCALNAME, JEEP_QNAME, JEEP_TYPE,
                JEEP_VALUE);
        assertEquals(2, attr.getLength());
    }

    /**
     * Javadoc says getLocalName returns null if the index if out of range.
     */
    @Test
    public void testcase09() {
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(CAR_URI, CAR_LOCALNAME, CAR_QNAME, CAR_TYPE, CAR_VALUE);
        attr.addAttribute(JEEP_URI, JEEP_LOCALNAME, JEEP_QNAME, JEEP_TYPE,
                JEEP_VALUE);
        attr.removeAttribute(1);
        assertNull(attr.getLocalName(1));
    }

    /**
     * Javadoc says java.lang.ArrayIndexOutOfBoundsException is thrown When the
     * supplied index does not point to an attribute in the list.
     */
    @Test
    public void testcase10() {
        AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(CAR_URI, CAR_LOCALNAME, CAR_QNAME, CAR_TYPE, CAR_VALUE);
        attr.addAttribute(JEEP_URI, JEEP_LOCALNAME, JEEP_QNAME, JEEP_TYPE,
                JEEP_VALUE);
        attr.removeAttribute(1);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> attr.removeAttribute(1));
    }
}

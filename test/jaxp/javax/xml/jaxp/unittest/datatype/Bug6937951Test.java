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

package datatype;

import org.junit.jupiter.api.Test;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 6937951
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit/othervm datatype.Bug6937951Test
 * @summary Test midnight is same as the start of the next day in XMLGregorianCalendar.
 */
public class Bug6937951Test {

    @Test
    public void test() throws DatatypeConfigurationException {
        DatatypeFactory dtf = DatatypeFactory.newInstance();
        XMLGregorianCalendar c1 = dtf.newXMLGregorianCalendar("1999-12-31T24:00:00");
        XMLGregorianCalendar c2 = dtf.newXMLGregorianCalendar("2000-01-01T00:00:00");

        assertEquals(2000, c1.getYear());
        assertEquals(0, c1.getHour());
        assertEquals(c1, c2, "hour 24 should treated as equal to hour 0 of the next day");
    }

}

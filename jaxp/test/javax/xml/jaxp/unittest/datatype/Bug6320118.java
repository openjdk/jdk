/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 6320118
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng/othervm -DrunSecMngr=true datatype.Bug6320118
 * @run testng/othervm datatype.Bug6320118
 * @summary Test xml datatype XMLGregorianCalendar.
 */
@Listeners({jaxp.library.BasePolicy.class})
public class Bug6320118 {

    DatatypeFactory df;

    @Test
    public void test1() {
        try {
            df = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            Assert.fail(e.getMessage());
        }

        try {
            XMLGregorianCalendar calendar = df.newXMLGregorianCalendar(1970, 1, 1, 24, 0, 0, 0, 0);
        } catch (IllegalArgumentException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void test2() {
        try {
            df = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            Assert.fail(e.getMessage());
        }

        try {
            XMLGregorianCalendar calendar = df.newXMLGregorianCalendarTime(24, 0, 0, 0);
        } catch (IllegalArgumentException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void test3() {
        try {
            df = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            Assert.fail(e.getMessage());
        }
        try {
            XMLGregorianCalendar calendar = df.newXMLGregorianCalendar();
            // Must fail as other params are not 0 but undefined
            calendar.setHour(24);
            Assert.fail("test3() - Expected IllegalArgumentException not thrown");
        } catch (IllegalArgumentException e) {
            // falls through
        }
    }

    @Test
    public void test4() {
        try {
            df = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            Assert.fail(e.getMessage());
        }

        try {
            XMLGregorianCalendar calendar = df.newXMLGregorianCalendar();
            calendar.setTime(24, 0, 0, 0);
        } catch (IllegalArgumentException e) {
            Assert.fail(e.getMessage());
        }
    }

}

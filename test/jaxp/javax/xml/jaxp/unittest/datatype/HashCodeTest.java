/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/*
 * @test
 * @bug 8246816
 * @run junit datatype.HashCodeTest
 * @summary Test hashCode generation.
 */
public class HashCodeTest {
    /*
       DataProvider: for testHashCode
       Data: datetime1, datetime2, flag indicating if their hashCodes are equal
     */
    public static Object[][] getData() {

        return new Object[][] {
                // the reported case: identical hash codes before the patch
                { "2020-04-24T12:53:00+02:00", "2020-06-04T06:58:17.727Z", false },
                // a case mentioned in the dev note of hashCode() implementation
                { "2000-01-15T12:00:00-05:00", "2000-01-15T13:00:00-04:00", true },
                /*
                 * Comparing with a datetime that needs to be normalized.
                 * Before the patch, XMLGregorianCalendarImpl called the normalizeToTimezone
                 * method that will set UNDEFINED fractional second to zero.
                 */
                { "2000-01-01T03:19:04Z", "1999-12-31T23:49:04-03:30", true },
                // another case mentioned in the javadoc of XMLGregorianCalendar::normalize()
                { "2000-03-04T23:00:00+03:00", "2000-03-04T20:00:00Z", true },
        };
    }

    @ParameterizedTest
    @MethodSource("getData")
    public final void testHashCode(String dt1, String dt2, boolean equal) throws Exception {
        DatatypeFactory dataTypeFactory = DatatypeFactory.newInstance();
        XMLGregorianCalendar cal1 = dataTypeFactory.newXMLGregorianCalendar(dt1);
        XMLGregorianCalendar cal2 = dataTypeFactory.newXMLGregorianCalendar(dt2);

        // identical hash codes before the patch
        int hashCode1 = cal1.hashCode();
        int hashCode2 = cal2.hashCode();

        if (equal) {
            assertEquals(cal1, cal2);
            assertEquals(hashCode1, hashCode2);
        } else {
            assertNotEquals(cal1, cal2);
            assertNotEquals(hashCode1, hashCode2);
        }
    }
}

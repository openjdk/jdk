/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8271732
 * @summary Basic test that charAt throws IIOBE as expected for out of bounds indexes.
 * @run testng CharAt
 */

import java.util.List;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static org.testng.Assert.*;

public class CharAt {

    /**
     * StringBuilder/-Buffer.charAt throws:
     * IndexOutOfBoundsException - if index is negative or greater than or equal to length().
     * the test inputs, expected to throw IndexOutOfBoundsException.
     */
    @Test
    public void charAtIIOBE() {
        StringBuilder sb = new StringBuilder("test");
        StringBuffer sbuf = new StringBuffer("test");

        StringBuilder sbUtf16 = new StringBuilder("\uFF34est"); // Fullwidth Latin Capital Letter T
        StringBuffer sbufUtf16 = new StringBuffer("\uFF34est");

        List<Integer> outOfBoundsIndices = List.of(Integer.MIN_VALUE, -2, -1, 4, 5, Integer.MAX_VALUE);

        for (int index : outOfBoundsIndices) {
            try {
                sb.charAt(index);
                fail("StringBuilder.charAt index: " + index + " does not throw IOOBE as expected");
            } catch (IndexOutOfBoundsException ex) {
                // OK
            }

            try {
                sbUtf16.charAt(index);
                fail("StringBuilder.charAt index: " + index + " does not throw IOOBE as expected (UTF-16)");
            } catch (IndexOutOfBoundsException ex) {
                // OK
            }

            try {
                sbuf.charAt(index);
                fail("StringBuffer.charAt index: " + index + " does not throw IOOBE as expected");
            } catch (IndexOutOfBoundsException ex) {
                // OK
            }

            try {
                sbufUtf16.charAt(index);
                fail("StringBuffer.charAt index: " + index + " does not throw IOOBE as expected (UTF-16)");
            } catch (IndexOutOfBoundsException ex) {
                // OK
            }
        }
    }
}

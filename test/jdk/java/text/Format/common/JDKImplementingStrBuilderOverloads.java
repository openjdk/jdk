/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8313205
 * @summary Ensure correctness of StringBuilder overloads at the JDK implementing
 *          class level. See AbstractStrBuilderOverloads.java for behavior of
 *          default and non JDK implementing classes.
 * @run junit JDKImplementingStrBuilderOverloads
 */

import org.junit.jupiter.api.Test;

import java.text.ChoiceFormat;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ListFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Basic sanity to check that resultant toStrings from buffer/builder are equivalent
// and that resultant type is of StringBuilder
public class JDKImplementingStrBuilderOverloads {

    // Generic params to satisfy the StringBuilder/Buffer method calls
    private static final double db = 1.1;
    private static final long l = 1;
    private static final Date date = new Date(1);
    private static final Object num_o = 1;
    private static final Object date_o = new Date(1);
    private static final Object arr_o = new String[] {"foo", "bar", "baz"};
    private static final Object[] obj_arr = (Object[]) arr_o;
    private static final FieldPosition fp = new FieldPosition(1);

    @Test
    public void decimalFormatTest() {
        // decimalFormat w/ some formatting style
        var fmt = NumberFormat.getCurrencyInstance();
        StringBuilder bldr = fmt.format(db, new StringBuilder(), fp);
        assertEquals(fmt.format(db, new StringBuffer(), fp).toString(),
                bldr.toString()); // double
        bldr = fmt.format(l, new StringBuilder(), fp);
        assertEquals(fmt.format(l, new StringBuffer(), fp).toString(),
                bldr.toString()); // long
        bldr = fmt.format(num_o, new StringBuilder(), fp);
        assertEquals(fmt.format(num_o, new StringBuffer(), fp).toString(),
                bldr.toString()); // Object
    }

    @Test
    public void compactNumberFormatTest() {
        // compactNumberFormat w/ some formatting style
        var fmt = NumberFormat.getCompactNumberInstance();
        StringBuilder bldr = fmt.format(db, new StringBuilder(), fp);
        assertEquals(fmt.format(db, new StringBuffer(), fp).toString(),
                bldr.toString()); // double
        bldr = fmt.format(l, new StringBuilder(), fp);
        assertEquals(fmt.format(l, new StringBuffer(), fp).toString(),
                bldr.toString()); // long
        bldr = fmt.format(num_o, new StringBuilder(), fp);
        assertEquals(fmt.format(num_o, new StringBuffer(), fp).toString(),
                bldr.toString()); // Object
    }

    @Test
    public void listFormatTest() {
        var fmt = ListFormat.getInstance();
        StringBuilder bldr = fmt.format(arr_o, new StringBuilder(), fp);
        assertEquals(fmt.format(arr_o, new StringBuffer(), fp).toString(),
                bldr.toString()); // Object
    }

    @Test
    public void choiceFormatTest() {
        var fmt = new ChoiceFormat("0#foo1#bar");
        StringBuilder bldr = fmt.format(db, new StringBuilder(), fp);
        assertEquals(fmt.format(db, new StringBuffer(), fp).toString(),
                bldr.toString()); // double
        bldr = fmt.format(l, new StringBuilder(), fp);
        assertEquals(fmt.format(l, new StringBuffer(), fp).toString(),
                bldr.toString()); // long
        bldr = fmt.format(num_o, new StringBuilder(), fp);
        assertEquals(fmt.format(num_o, new StringBuffer(), fp).toString(),
                bldr.toString()); // Object
    }

    @Test
    public void simpleDateFormatTest() {
        // SimpleDateFormat w/ some formatting style
        var fmt = DateFormat.getDateInstance();
        StringBuilder bldr = fmt.format(date, new StringBuilder(), fp);
        assertEquals(fmt.format(date, new StringBuffer(), fp).toString(),
                bldr.toString()); // Date
        bldr = fmt.format(date_o, new StringBuilder(), fp);
        assertEquals(fmt.format(date_o, new StringBuffer(), fp).toString(),
                bldr.toString()); // Object
    }

    @Test
    public void messageFormatTest() {
        var fmt = new MessageFormat("First {0}, then {1}, lastly {2}.");
        StringBuilder bldr = fmt.format(arr_o, new StringBuilder(), fp);
        assertEquals(fmt.format(arr_o, new StringBuffer(), fp).toString(),
                bldr.toString()); // Object
        bldr = fmt.format(obj_arr, new StringBuilder(), fp);
        assertEquals(fmt.format(obj_arr, new StringBuffer(), fp).toString(),
                bldr.toString()); // Object[]
    }
}

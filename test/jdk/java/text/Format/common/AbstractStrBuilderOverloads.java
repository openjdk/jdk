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
 * @summary Ensure expected behavior of Format, NumberFormat, and DateFormat
 *          default and subclass behavior for the StringBuilder overloads.
 *          This test is not concerned with the correctness of the JDK implementing
 *          class behavior, see JDKImplementingStrBuilderOverloads.java.
 * @run junit AbstractStrBuilderOverloads
 */

import org.junit.jupiter.api.Test;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class AbstractStrBuilderOverloads {

    // Generic params to satisfy the StringBuilder method calls
    private static final double db = 1.1;
    private static final long l = 1;
    private static final Date date = new Date(1);
    private static final Object num_o = 1;
    private static final Object date_o = new Date(1);
    private static final StringBuilder bldr = new StringBuilder("foo");
    private static final FieldPosition fp = new FieldPosition(1);

    // Implementing subclass should support if overloading
    @Test
    public void implementingFmtClassTest() {
        var f = new implementingFormat();
        assertEquals(bldr, f.format(num_o, bldr, fp)); // Object
    }

    // Default implementation should throw for all StringBuilder overloads
    @Test
    public void defaultFmtClassTest() {
        var f = new defaultFormat();
        assertThrows(UnsupportedOperationException.class,
                () -> f.format(num_o, bldr, fp)); // Object
    }

    // Implementing subclass should support if overloading
    @Test
    public void implementingNFmtClassTest() {
        var f = new implementingNumberFormat();
        assertEquals(bldr, f.format(db, bldr, fp)); // double
        assertEquals(bldr, f.format(l, bldr, fp)); // long
        assertEquals(bldr, f.format(num_o, bldr, fp)); // Object
    }

    // Default implementation should throw for all StringBuilder overloads
    @Test
    public void defaultNFmtClassTest() {
        var f = new defaultNumberFormat();
        assertThrows(UnsupportedOperationException.class,
                () -> f.format(db, bldr, fp)); // double
        assertThrows(UnsupportedOperationException.class,
                () -> f.format(l, bldr, fp)); // long
        assertThrows(UnsupportedOperationException.class,
                () -> f.format(num_o, bldr, fp)); // Object
    }

    // Implementing subclass should support if overloading
    @Test
    public void implementingDFmtClassTest() {
        var f = new implementingDateFormat();
        assertEquals(bldr, f.format(date, bldr, fp)); // Date
        assertEquals(bldr, f.format(date_o, bldr, fp)); // Object
    }

    // Default implementation should throw for all StringBuilder overloads
    @Test
    public void defaultDFmtClassTest() {
        var f = new defaultDateFormat();
        assertThrows(UnsupportedOperationException.class,
                () -> f.format(date, bldr, fp)); // Date
        assertThrows(UnsupportedOperationException.class,
                () -> f.format(date_o, bldr, fp)); // Object
    }

    // Each defaultXxx extends the abstract base class and overrides the abstract methods
    // Each ImplementingXxx extends the defaultXxx and overrides the required StringBuilder overloads
    // We are not concerned with the actual implementation, but simply to check
    // that UOE is not thrown, hence the immediate return of the passed StringBuilder.
    static class implementingFormat extends defaultFormat {

        @Override
        public StringBuilder format(Object obj, StringBuilder toAppendTo, FieldPosition pos) {
            return toAppendTo;
        }
    }

    static class defaultFormat extends Format {

        @Override
        public StringBuffer format(Object obj, StringBuffer toAppendTo, FieldPosition pos) {
            return null;
        }

        @Override
        public Object parseObject(String source, ParsePosition pos) {
            return null;
        }
    }

    static class implementingNumberFormat extends defaultNumberFormat {

        @Override
        public StringBuilder format(double number, StringBuilder toAppendTo, FieldPosition pos) {
            return toAppendTo;
        }

        @Override
        public StringBuilder format(long number, StringBuilder toAppendTo, FieldPosition pos) {
            return toAppendTo;
        }
    }

    static class defaultNumberFormat extends NumberFormat {

        @Override
        public StringBuffer format(double number, StringBuffer toAppendTo, FieldPosition pos) {
            return null;
        }

        @Override
        public StringBuffer format(long number, StringBuffer toAppendTo, FieldPosition pos) {
            return null;
        }

        @Override
        public Number parse(String source, ParsePosition parsePosition) {
            return null;
        }
    }

    static class implementingDateFormat extends defaultDateFormat {

        @Override
        public StringBuilder format(Date date, StringBuilder toAppendTo,
                                    FieldPosition pos) {
            return toAppendTo;
        }
    }

    static class defaultDateFormat extends DateFormat {

        @Override
        public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition fieldPosition) {
            return null;
        }

        @Override
        public Date parse(String source, ParsePosition pos) {
            return null;
        }
    }
}

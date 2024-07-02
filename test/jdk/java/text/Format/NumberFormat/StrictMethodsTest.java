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
 * @bug 8327640
 * @summary Unit test for the isStrict() and setStrict() parsing related methods
 * @run junit StrictMethodsTest
 */

import org.junit.jupiter.api.Test;

import java.text.CompactNumberFormat;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StrictMethodsTest {

    // Check that DecimalFormat implements isStrict()/setStrict()
    // Ensure that the default value is false, and can be set to true via API
    @Test
    public void decimalFormatTest() {
        DecimalFormat dFmt = (DecimalFormat) NumberFormat.getInstance();
        assertFalse(dFmt.isStrict());
        dFmt.setStrict(true);
        assertTrue(dFmt.isStrict());
    }

    // Check that CompactNumberFormat implements isStrict()/setStrict()
    // Ensure that the default value is false, and can be set to true via API
    @Test
    public void compactFormatTest() {
        CompactNumberFormat cFmt = (CompactNumberFormat) NumberFormat.getCompactNumberInstance();
        assertFalse(cFmt.isStrict());
        cFmt.setStrict(true);
        assertTrue(cFmt.isStrict());
    }

    // Check that NumberFormat throws exception for isStrict()/setStrict()
    // when subclass does not implement said methods
    @Test
    public void numberFormatTest() {
        FooFormat fmt = new FooFormat();
        assertThrows(UnsupportedOperationException.class, fmt::isStrict);
        assertThrows(UnsupportedOperationException.class, () -> fmt.setStrict(false));
    }

    // Dummy NumberFormat class to check that isStrict() and setStrict()
    // are not implemented by default
    private static class FooFormat extends NumberFormat {

        // Provide overrides for abstract methods
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
}

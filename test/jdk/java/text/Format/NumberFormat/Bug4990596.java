/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4990596
 * @summary Make sure that any subclass of Number can be formatted using
 *          DecimalFormat.format() without throwing an exception.
 * @run junit Bug4990596
 */

import java.text.DecimalFormat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class Bug4990596 {

    // Test that a custom subclass of Number can be formatted by
    // DecimalFormat without throwing an IllegalArgumentException
    @Test
    public void formatSubclassedNumberTest() {
        assertDoesNotThrow(() -> new DecimalFormat().format(new MutableInteger(0)),
                "DecimalFormat.format() should support subclasses of Number");
    }

    // A custom subclass of Number. Prior to this fix, if an instance of this
    // class was formatted by DecimalFormat, an exception would be thrown.
    @SuppressWarnings("serial")
    public static class MutableInteger extends Number {
        public int value;

        public MutableInteger() {
        }
        public MutableInteger(int value) {
            this.value = value;
        }
        public double doubleValue() {
            return this.value;
        }
        public float floatValue() {
            return this.value;
        }
        public int intValue() {
            return this.value;
        }
        public long longValue() {
            return this.value;
        }
    }
}

/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8253459
 */

import java.util.*;

public class ArgumentIndexException {

    public static void main(String[] args) {

        testZeroIndex();
        nonRepresentableIntTest();
        testZeroWidthTest();
        nonRepresentableWidthTest();
        nonRepresentablePrecisionTest();


    }

    private static void testZeroIndex() {
        try {
            String r = String.format("%0$s", "A", "B");
        } catch (IllegalFormatException e) {
            if(e.getMessage().equals("Illegal format argument index = 0")){
                return;
            }
        }
        throw new RuntimeException("Expected IllegalFormatException for zero argument index.");
    }

    private static void nonRepresentableIntTest() {
        try {
            String r = String.format("%2147483648$s", "A", "B");
        } catch (IllegalFormatException e) {
            if(e.getMessage().equals("Illegal format argument index = " + Integer.MIN_VALUE)){
                return;
            }
        }
        throw new RuntimeException("Expected IllegalFormatException for non-representable integral index.");
    }

    private static void testZeroWidthTest() {
        try {
            String r = String.format("%0s", "A", "B");
        } catch (IllegalFormatException e) {
            //Captures the existing functionality. This is not a parsable format string.
            return;
        }
        throw new RuntimeException("Expected IllegalFormatException for zero width.");
    }

    private static void nonRepresentableWidthTest() {
        try {
            String r = String.format("%2147483648s", "A", "B");
        } catch (IllegalFormatWidthException e) {
            if (e.getMessage().equals(Integer.toString(Integer.MIN_VALUE))) {
                return;
            }
        }
        throw new RuntimeException("Expected IllegalFormatException for non-representable width.");
    }

    private static void nonRepresentablePrecisionTest() {
        try {
            String r = String.format("%.2147483648s", "A", "B");
        } catch (IllegalFormatException e) {
            if(e.getMessage().equals(Integer.toString(Integer.MIN_VALUE))){
                return;
            }
        }
        throw new RuntimeException("Expected IllegalFormatException for non-representable precision.");
    }

}
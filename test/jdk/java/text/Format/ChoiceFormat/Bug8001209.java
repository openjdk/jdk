/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8001209
 * @summary Confirm that the values set by setChoices() are not mutable.
 * @run junit Bug8001209
 */

import java.text.ChoiceFormat;
import java.text.ParsePosition;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Bug8001209 {

    // Represents the expected output of formatting the ChoiceFormat
    private static String expectedFormattedOutput;
    private static ChoiceFormat cFmt;
    private static ParsePosition status;
    private static String[] originalSetterArray;

    // Build the original ChoiceFormat to test if it can be mutated
    @BeforeAll
    static void setUpChoiceFormatAndOutput() {
        double[] limits = {1, 2, 3, 4, 5, 6, 7};
        originalSetterArray = new String[]{"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
        // Constructor calls setChoices
        cFmt = new ChoiceFormat(limits, originalSetterArray);
        status = new ParsePosition(0);

        // Build the expected results of formatting with the original ChoiceFormat
        StringBuilder before = new StringBuilder();
        for (double i = 1.0; i <= 7.0; ++i) {
            status.setIndex(0);
            String s = cFmt.format(i);
            before.append(" ");
            before.append(s);
            before.append(cFmt.parse(cFmt.format(i), status));
        }
        expectedFormattedOutput = before.toString();
    }

    /*
     * Ensure that mutating the arrays returned by getChoices and getLimits does
     * not affect the internal representation of the ChoiceFormat.
     */
    @Test
    public void immutableArraysFromGetters() {
        // Modify the array returned by getFormats() -> newFormats
        String[] newFormats = (String[]) cFmt.getFormats();
        newFormats[6] = "Doyoubi";
        StringBuilder after = new StringBuilder();
        for (double i = 1.0; i <= 7.0; ++i) {
            status.setIndex(0);
            String s = cFmt.format(i);
            after.append(" ");
            after.append(s);
            after.append(cFmt.parse(cFmt.format(i), status));
        }
        // Compare the expected results with the new formatted results
        assertEquals(after.toString(), expectedFormattedOutput,
                "Mutating array returned from getter changed internals of ChoiceFormat");
    }

    /*
     * Ensure that mutating the arrays passed to setChoices/constructor does
     * not affect the internal representation of the ChoiceFormat.
     */
    @Test
    public void immutableArraysFromSetter() {
        // Modify the array passed to setFormats() -> dayOfWeekNames
        originalSetterArray[6] = "Saturday";
        StringBuilder after = new StringBuilder();
        for (double i = 1.0; i <= 7.0; ++i) {
            status.setIndex(0);
            String s = cFmt.format(i);
            after.append(" ");
            after.append(s);
            after.append(cFmt.parse(cFmt.format(i), status));
        }
        // Compare the expected results with the new formatted results
        assertEquals(after.toString(), expectedFormattedOutput,
                "Mutating array passed to setter changed internals of ChoiceFormat");
    }
}

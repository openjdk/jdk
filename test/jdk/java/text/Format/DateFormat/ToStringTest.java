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
 * @bug 8321545
 * @library /java/text/testlib
 * @summary Ensure value returned by overridden toString method is as expected
 * @run junit ToStringTest
 */

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ToStringTest {

    // Check a normal expected value
    @Test
    public void normalValueTest() {
        String expectedStr =
                "SimpleDateFormat [locale: \"English (Canada)\", pattern: \"MMM d, y\"]\n";
        var s = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.CANADA);
        assertEquals(expectedStr, s.toString());
    }

    // Check an odd value. SimpleDateFormat constructor that takes DFS, will use
    // the default locale, not the locale from the DFS.
    @Test
    public void oddValueTest() {
        String expectedStr =
                "SimpleDateFormat [locale: \"" + Locale.getDefault().getDisplayName() + "\", pattern: \"MMM d, y\"]\n";
        var s = new SimpleDateFormat("MMM d, y", new DateFormatSymbols(Locale.JAPAN));
        assertEquals(expectedStr, s.toString());
    }


    // Check the expected value when the locale is null. This is only possible
    // via an older SimpleDateFormat that was deserialized. The current constructor
    // will throw NPE if locale is null.
    @Test
    public void nullLocaleTest() {
        String expectedStr =
                "SimpleDateFormat [locale: null, pattern: \"yyyy.MM.dd E hh.mm.ss zzz\"]\n";
        // Borrowed from DateFormatSymbolsSerializationTest
        SimpleDateFormat s;
        try (InputStream is = HexDumpReader.getStreamFromHexDump("SDFserialized.ser.txt");
             ObjectInputStream iStream = new ObjectInputStream(is)) {
            s = (SimpleDateFormat)iStream.readObject();
            assertEquals(expectedStr, s.toString());
        } catch (Exception e) {
            System.out.println("Error building stream from deserialized simple date format");
        }
    }
}

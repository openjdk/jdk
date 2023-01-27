/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify the implementation
 * of Locale.streamAvailableLocales()
 * @bug 8282319
 * @run junit StreamAvailableLocales
 */

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.Test;

public class StreamAvailableLocales {

    /**
     * Test to validate that the methods: Locale.getAvailableLocales()
     * and Locale.streamAvailableLocales() contain the same elements
     */
    @Test
    public void testStreamEqualsArray() {
        Locale[] arrayLocales = Locale.getAvailableLocales();
        Stream<Locale> streamedLocales = Locale.streamAvailableLocales();
        Locale[] convertedLocales = streamedLocales.toArray(Locale[]::new);
        if (Arrays.equals(arrayLocales, convertedLocales)) {
            System.out.println("$$$ Passed: Stream is equal to array elements");
        } else {
            throw new RuntimeException("$$$ Error: The underlying collections" +
                    " of getAvailableLocales() and streamAvailableLocales()" +
                    " are not the same");
        }
    }

    /**
     * Test to validate that the stream has the required Locale.ROOT.
     */
    @Test
    public void testStreamHasUS() {
        if (Locale.streamAvailableLocales().anyMatch(loc -> (loc.equals(Locale.US)))) {
            System.out.println("$$$ Passed: Stream has Locale.US!");
        } else {
            throw new RuntimeException("$$$ Error: Stream is missing Locale.US");
        }
    }

    /**
     * Test to validate that the stream has the required Locale.US.
     */
    @Test
    public void testStreamHasROOT() {
        if (Locale.streamAvailableLocales().anyMatch(loc -> (loc.equals(Locale.ROOT)))) {
            System.out.println("$$$ Passed: Stream has Locale.ROOT!");
        } else {
            throw new RuntimeException("$$$ Error: Stream is missing Locale.ROOT");
        }
    }
}

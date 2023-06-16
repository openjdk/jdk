/*
 * Copyright (c) 2007, 2023 Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4122700
 * @summary Verify that list of available locales is non-empty, and print the list
 * @run junit bug4122700
 */

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class bug4122700 {

    /**
     * Test that Locale.getAvailableLocales() is non-empty.
     * Print out the locales.
     */
    @Test
    public void nonEmptyLocalesTest() {
        Locale[] systemLocales = Locale.getAvailableLocales();
        assertNotEquals(systemLocales.length, 0, "Available locale list is empty!");
        System.out.println("Found " + systemLocales.length + " locales:");
        printLocales(systemLocales);
    }

    // Helper method to print out all the system locales
    private void printLocales(Locale[] systemLocales) {
        Locale[] locales = new Locale[systemLocales.length];
        for (int i = 0; i < locales.length; i++) {
            Locale lowest = null;
            for (Locale systemLocale : systemLocales) {
                if (i > 0 && locales[i - 1].toString().compareTo(systemLocale.toString()) >= 0)
                    continue;
                if (lowest == null || systemLocale.toString().compareTo(lowest.toString()) < 0)
                    lowest = systemLocale;
            }
            locales[i] = lowest;
        }
        for (Locale locale : locales) {
            if (locale.getCountry().length() == 0)
                System.out.println("    " + locale.getDisplayLanguage() + ":");
            else {
                if (locale.getVariant().length() == 0)
                    System.out.println("        " + locale.getDisplayCountry());
                else
                    System.out.println("        " + locale.getDisplayCountry() + ", "
                            + locale.getDisplayVariant());
            }
        }
    }
}

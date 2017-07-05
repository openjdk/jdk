/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
/*
    @test
    @summary test that getBundle handles change in default locale correctly
    @bug 4318520
*/

/*
 *
 */

import java.util.ResourceBundle;
import java.util.Locale;

public class Test4318520 {

    public static void main(String[] args) {
        test(Locale.GERMAN);
        test(Locale.ENGLISH);
    }

    private static void test(Locale locale) {
        Locale.setDefault(locale);
        ResourceBundle myResources =
                ResourceBundle.getBundle("Test4318520RB", Locale.FRENCH);
        String actualLocale = myResources.getString("name");
        if (!actualLocale.equals(locale.toString())) {
            System.out.println("expected: " + locale + ", got: " + actualLocale);
            throw new RuntimeException();
        }
    }
}

/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.text.*;
import java.util.*;
import static java.util.Calendar.*;
import sun.util.locale.provider.*;
import sun.util.resources.*;
import com.bar.CalendarDataProviderImpl;

/**
 * Test case for CalendarDataProvider.
 *
 * Test strategy:
 * com.bar.CalendarDataProviderImpl supports only ja_JP_kids locale. It returns
 * unusual week parameter values, WEDNESDAY - first day of week, 7 - minimal
 * days in the first week.
 *
 * A Calendar instance created with ja_JP_kids should use the week parameters
 * provided by com.bar.CalendarDataProviderImpl.
 */
public class CalendarDataProviderTest {

    public static void main(String[] s) {
        new CalendarDataProviderTest().test();
    }

    void test() {
        Locale kids = new Locale("ja", "JP", "kids"); // test provider's supported locale
        Calendar kcal = Calendar.getInstance(kids);
        Calendar jcal = Calendar.getInstance(Locale.JAPAN);

        // check the week parameters
        checkResult("firstDayOfWeek", kcal.getFirstDayOfWeek(), WEDNESDAY);
        checkResult("minimalDaysInFirstWeek", kcal.getMinimalDaysInFirstWeek(), 7);
    }

    private <T> void checkResult(String msg, T got, T expected) {
        if (!expected.equals(got)) {
            String s = String.format("%s: got='%s', expected='%s'", msg, got, expected);
            throw new RuntimeException(s);
        }
    }
}

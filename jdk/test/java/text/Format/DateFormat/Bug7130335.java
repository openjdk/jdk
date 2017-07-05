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

/**
 * @test
 * @bug 7130335
 * @summary Make sure that round-trip conversion (format/parse) works
 *          with old timestamps in Europe/Moscow.
 */
import java.text.*;
import java.util.*;

public class Bug7130335 {
    private static final TimeZone MOSCOW = TimeZone.getTimeZone("Europe/Moscow");

    public static void main(String[] args) throws Exception {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.US);
        sdf.setTimeZone(MOSCOW);
        Calendar cal = new GregorianCalendar(MOSCOW);
        cal.clear();
        // Try both +03:00 and +02:00
        cal.set(1922, Calendar.SEPTEMBER, 30);
        test(sdf, cal);
        cal.add(Calendar.DAY_OF_YEAR, 1);
        test(sdf, cal);
        cal.set(1991, Calendar.MARCH, 31);
        // in daylight saving time
        test(sdf, cal);
        cal.add(Calendar.DAY_OF_YEAR, 1);
        test(sdf, cal);
        // Try the current timestamp
        cal.setTimeInMillis(System.currentTimeMillis());
        test(sdf, cal);
    }

    private static void test(SimpleDateFormat sdf, Calendar cal) throws Exception {
        Date d = cal.getTime();
        String f = sdf.format(d);
        System.out.println(f);
        Date pd = sdf.parse(f);
        String p = sdf.format(pd);
        if (!d.equals(pd) || !f.equals(p)) {
            throw new RuntimeException("format: " + f + ", parse: " + p);
        }
    }
}

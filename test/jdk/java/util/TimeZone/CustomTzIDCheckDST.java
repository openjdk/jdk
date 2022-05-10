/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * Specifically called by runCustomTzIDCheckDST.sh to check if Daylight savings is
 * properly followed with a custom TZ code set through environment variables.
 * */

import java.util.Calendar;
import java.util.Date;
import java.time.Month;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class CustomTzIDCheckDST {
    public static void main(String args[]) {
        Calendar calendar = Calendar.getInstance();
        Date time = calendar.getTime();
        int month = time.getMonth();
        ZonedDateTime date = ZonedDateTime.ofInstant(time.toInstant(), ZoneId.systemDefault());

        /* TZ code will always be set to "MEZ-1MESZ,M3.5.0,M10.5.0" via invoking shell script.
         * This ensures the transition periods for Daylights Savings should be at March's last
         * Sunday and October's last Sunday.
         */
        if ((month > Month.MARCH.getValue() && month < Month.OCTOBER.getValue()) ||
                (month == Month.MARCH.getValue() && date.isAfter(getLastSundayOfMonth(date))) ||
                (month == Month.OCTOBER.getValue() && date.isBefore(getLastSundayOfMonth(date)))) {
            // We are in Daylight savings period.
            if (time.toString().endsWith("GMT+02:00 " + Integer.toString(calendar.getTime().getYear() + 1900)))
                return;
        } else {
            if (time.toString().endsWith("GMT+01:00 " + Integer.toString(calendar.getTime().getYear() + 1900)))
                return;
        }

        // Reaching here means time zone did not match up as expected.
        throw new RuntimeException("Got unexpected timezone information: " + time);
    }

    private static ZonedDateTime getLastSundayOfMonth(ZonedDateTime date) {
        return date.with(TemporalAdjusters.lastInMonth(DayOfWeek.SUNDAY));
    }
}

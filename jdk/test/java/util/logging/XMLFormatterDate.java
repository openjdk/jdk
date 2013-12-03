
/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.XMLFormatter;

/**
 * @test
 * @bug 8028185
 * @summary XMLFormatter.format emits incorrect year (year + 1900)
 * @author dfuchs
 */
public class XMLFormatterDate {

    /**
     * Before the fix, JDK8 prints: {@code
     * <record>
     *   <date>3913-11-18T17:35:40</date>
     *   <millis>1384792540403</millis>
     *   <sequence>0</sequence>
     *   <level>INFO</level>
     *   <thread>1</thread>
     *   <message>test</message>
     * </record>
     * }
     * After the fix, it should print: {@code
     * <record>
     *   <date>2013-11-18T17:35:40</date>
     *   <millis>1384792696519</millis>
     *   <sequence>0</sequence>
     *   <level>INFO</level>
     *   <thread>1</thread>
     *   <message>test</message>
     * </record>
     * }
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Locale locale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);

            final GregorianCalendar cal1 = new GregorianCalendar();
            final int year1 = cal1.get(Calendar.YEAR);

            LogRecord record = new LogRecord(Level.INFO, "test");
            XMLFormatter formatter = new XMLFormatter();
            final String formatted = formatter.format(record);
            System.out.println(formatted);

            final GregorianCalendar cal2 = new GregorianCalendar();
            final int year2 = cal2.get(Calendar.YEAR);
            if (year2 < 1900) {
                throw new Error("Invalid system year: " + year2);
            }

            StringBuilder buf2 = new StringBuilder()
                    .append("<date>").append(year2).append("-");
            if (!formatted.contains(buf2.toString())) {
                StringBuilder buf1 = new StringBuilder()
                        .append("<date>").append(year1).append("-");
                if (formatted.contains(buf1)
                        && year2 == year1 + 1
                        && cal2.get(Calendar.MONTH) == Calendar.JANUARY
                        && cal2.get(Calendar.DAY_OF_MONTH) == 1) {
                    // Oh! The year just switched in the midst of the test...
                    System.out.println("Happy new year!");
                } else {
                    throw new Error("Expected year " + year2
                            + " not found in log:\n" + formatted);
                }
            }
        } finally {
            Locale.setDefault(locale);
        }
    }

}

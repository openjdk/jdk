/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test International Number Format API
 * @modules jdk.localedata
 * @run junit IntlTestNumberFormatAPI
 */
/*
(C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
(C) Copyright IBM Corp. 1996, 1997 - All Rights Reserved

  The original version of this source code and documentation is copyrighted and
owned by Taligent, Inc., a wholly-owned subsidiary of IBM. These materials are
provided under terms of a License Agreement between Taligent and Sun. This
technology is protected by multiple US and International patents. This notice and
attribution to Taligent may not be removed.
  Taligent is a registered trademark of Taligent, Inc.
*/

import java.text.*;
import java.util.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class IntlTestNumberFormatAPI
{
    // This test checks various generic API methods in DecimalFormat to achieve 100% API coverage.
    @Test
    public void TestAPI()
    {
        Locale reservedLocale = Locale.getDefault();
        try {
            System.out.println("NumberFormat API test---"); System.out.println("");
            Locale.setDefault(Locale.ENGLISH);

            // ======= Test constructors

            System.out.println("Testing NumberFormat constructors");

            NumberFormat def = NumberFormat.getInstance();

            NumberFormat fr = NumberFormat.getInstance(Locale.FRENCH);

            NumberFormat cur = NumberFormat.getCurrencyInstance();

            NumberFormat cur_fr =
                    NumberFormat.getCurrencyInstance(Locale.FRENCH);

            NumberFormat per = NumberFormat.getPercentInstance();

            NumberFormat per_fr =
                    NumberFormat.getPercentInstance(Locale.FRENCH);

            // ======= Test equality

            System.out.println("Testing equality operator");

            if( per_fr.equals(cur_fr) ) {
                fail("ERROR: == failed");
            }

            // ======= Test various format() methods

            System.out.println("Testing various format() methods");

//          final double d = -10456.0037; // this appears as
                                          // -10456.003700000001 on NT
//          final double d = -1.04560037e-4; // this appears as
                                             // -1.0456003700000002E-4 on NT
            final double d = -10456.00370000000000; // this works!
            final long l = 100000000;

            String res1 = new String();
            String res2 = new String();
            StringBuffer res3 = new StringBuffer();
            StringBuffer res4 = new StringBuffer();
            StringBuffer res5 = new StringBuffer();
            StringBuffer res6 = new StringBuffer();
            FieldPosition pos1 = new FieldPosition(0);
            FieldPosition pos2 = new FieldPosition(0);
            FieldPosition pos3 = new FieldPosition(0);
            FieldPosition pos4 = new FieldPosition(0);

            res1 = cur_fr.format(d);
            System.out.println( "" + d + " formatted to " + res1);

            res2 = cur_fr.format(l);
            System.out.println("" + l + " formatted to " + res2);

            res3 = cur_fr.format(d, res3, pos1);
            System.out.println( "" + d + " formatted to " + res3);

            res4 = cur_fr.format(l, res4, pos2);
            System.out.println("" + l + " formatted to " + res4);

            res5 = cur_fr.format(d, res5, pos3);
            System.out.println("" + d + " formatted to " + res5);

            res6 = cur_fr.format(l, res6, pos4);
            System.out.println("" + l + " formatted to " + res6);


            // ======= Test parse()

            System.out.println("Testing parse()");

//          String text = new String("-10,456.0037");
            String text = new String("-10456,0037");
            ParsePosition pos = new ParsePosition(0);
            ParsePosition pos01 = new ParsePosition(0);
            double d1 = ((Number)fr.parseObject(text, pos)).doubleValue();
            if(d1 != d) {
                fail("ERROR: Roundtrip failed (via parse()) for " + text);
            }
            System.out.println(text + " parsed into " + d1);

            double d2 = fr.parse(text, pos01).doubleValue();
            if(d2 != d) {
                fail("ERROR: Roundtrip failed (via parse()) for " + text);
            }
            System.out.println(text + " parsed into " + d2);

            double d3 = 0;
            try {
                d3 = fr.parse(text).doubleValue();
            }
            catch (ParseException e) {
                fail("ERROR: parse() failed");
            }
            if(d3 != d) {
                fail("ERROR: Roundtrip failed (via parse()) for " + text);
            }
            System.out.println(text + " parsed into " + d3);


            // ======= Test getters and setters

            System.out.println("Testing getters and setters");

            final Locale[] locales = NumberFormat.getAvailableLocales();
            long count = locales.length;
            System.out.println("Got " + count + " locales" );
            for(int i = 0; i < count; i++) {
                String name;
                name = locales[i].getDisplayName();
                System.out.println(name);
            }

            fr.setParseIntegerOnly( def.isParseIntegerOnly() );
            if(fr.isParseIntegerOnly() != def.isParseIntegerOnly() ) {
                    fail("ERROR: setParseIntegerOnly() failed");
            }

            fr.setGroupingUsed( def.isGroupingUsed() );
            if(fr.isGroupingUsed() != def.isGroupingUsed() ) {
                    fail("ERROR: setGroupingUsed() failed");
            }

            fr.setMaximumIntegerDigits( def.getMaximumIntegerDigits() );
            if(fr.getMaximumIntegerDigits() != def.getMaximumIntegerDigits() ) {
                    fail("ERROR: setMaximumIntegerDigits() failed");
            }

            fr.setMinimumIntegerDigits( def.getMinimumIntegerDigits() );
            if(fr.getMinimumIntegerDigits() != def.getMinimumIntegerDigits() ) {
                    fail("ERROR: setMinimumIntegerDigits() failed");
            }

            fr.setMaximumFractionDigits( def.getMaximumFractionDigits() );
            if(fr.getMaximumFractionDigits() != def.getMaximumFractionDigits() ) {
                    fail("ERROR: setMaximumFractionDigits() failed");
            }

            fr.setMinimumFractionDigits( def.getMinimumFractionDigits() );
            if(fr.getMinimumFractionDigits() != def.getMinimumFractionDigits() ) {
                    fail("ERROR: setMinimumFractionDigits() failed");
            }

            // ======= Test getStaticClassID()

//          logln("Testing instanceof()");

//          try {
//              NumberFormat test = new DecimalFormat();

//              if (! (test instanceof DecimalFormat)) {
//                  errln("ERROR: instanceof failed");
//              }
//          }
//          catch (Exception e) {
//              errln("ERROR: Couldn't create a DecimalFormat");
//          }
        } finally {
            // restore the reserved locale
            Locale.setDefault(reservedLocale);
        }
    }
}

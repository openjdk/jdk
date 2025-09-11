/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282625 8366517
 * @summary test International Decimal Format Symbols
 * @run junit IntlTestDecimalFormatSymbols
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class IntlTestDecimalFormatSymbols
{
    // Test the API of DecimalFormatSymbols; primarily a simple get/set set.
    @Test
    public void TestSymbols()
    {
        DecimalFormatSymbols fr = new DecimalFormatSymbols(Locale.FRENCH);

        DecimalFormatSymbols en = new DecimalFormatSymbols(Locale.ENGLISH);

        if(en.equals(fr)) {
            fail("ERROR: English DecimalFormatSymbols equal to French");
        }

        // just do some VERY basic tests to make sure that get/set work

        if (!fr.getLocale().equals(Locale.FRENCH)) {
            fail("ERROR: French DecimalFormatSymbols not Locale.FRENCH");
        }

        if (!en.getLocale().equals(Locale.ENGLISH)) {
            fail("ERROR: English DecimalFormatSymbols not Locale.ENGLISH");
        }

        char zero = en.getZeroDigit();
        fr.setZeroDigit(zero);
        if(fr.getZeroDigit() != en.getZeroDigit()) {
            fail("ERROR: get/set ZeroDigit failed");
        }

        char group = en.getGroupingSeparator();
        fr.setGroupingSeparator(group);
        if(fr.getGroupingSeparator() != en.getGroupingSeparator()) {
            fail("ERROR: get/set GroupingSeparator failed");
        }

        char decimal = en.getDecimalSeparator();
        fr.setDecimalSeparator(decimal);
        if(fr.getDecimalSeparator() != en.getDecimalSeparator()) {
            fail("ERROR: get/set DecimalSeparator failed");
        }

        char perMill = en.getPerMill();
        fr.setPerMill(perMill);
        if(fr.getPerMill() != en.getPerMill()) {
            fail("ERROR: get/set PerMill failed");
        }

        char percent = en.getPercent();
        fr.setPercent(percent);
        if(fr.getPercent() != en.getPercent()) {
            fail("ERROR: get/set Percent failed");
        }

        char digit = en.getDigit();
        fr.setDigit(digit);
        if(fr.getPercent() != en.getPercent()) {
            fail("ERROR: get/set Percent failed");
        }

        char patternSeparator = en.getPatternSeparator();
        fr.setPatternSeparator(patternSeparator);
        if(fr.getPatternSeparator() != en.getPatternSeparator()) {
            fail("ERROR: get/set PatternSeparator failed");
        }

        String infinity = en.getInfinity();
        fr.setInfinity(infinity);
        String infinity2 = fr.getInfinity();
        if(! infinity.equals(infinity2)) {
            fail("ERROR: get/set Infinity failed");
        }

        String nan = en.getNaN();
        fr.setNaN(nan);
        String nan2 = fr.getNaN();
        if(! nan.equals(nan2)) {
            fail("ERROR: get/set NaN failed");
        }

        char minusSign = en.getMinusSign();
        fr.setMinusSign(minusSign);
        if(fr.getMinusSign() != en.getMinusSign()) {
            fail("ERROR: get/set MinusSign failed");
        }

//        char exponential = en.getExponentialSymbol();
//        fr.setExponentialSymbol(exponential);
//        if(fr.getExponentialSymbol() != en.getExponentialSymbol()) {
//            errln("ERROR: get/set Exponential failed");
//        }

        DecimalFormatSymbols foo = new DecimalFormatSymbols();

        en = (DecimalFormatSymbols) fr.clone();

        if(! en.equals(fr)) {
            fail("ERROR: Clone failed");
        }
    }

    @Test
    void nullLocaleTest() {
        assertThrows(NullPointerException.class, () -> new DecimalFormatSymbols(null));
        assertThrows(NullPointerException.class, () -> DecimalFormatSymbols.getInstance(null));
    }
}

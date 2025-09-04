/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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
(C) Copyright Taligent, Inc. 1996 - All Rights Reserved
(C) Copyright IBM Corp. 1996 - All Rights Reserved

  The original version of this source code and documentation is copyrighted and
owned by Taligent, Inc., a wholly-owned subsidiary of IBM. These materials are
provided under terms of a License Agreement between Taligent and Sun. This
technology is protected by multiple US and International patents. This notice and
attribution to Taligent may not be removed.
  Taligent is a registered trademark of Taligent, Inc.
*/

/*
 * @test
 * @bug 4109023 4153060 4153061 8366400
 * @summary test ParsePosition and FieldPosition
 * @run junit PositionTest
 */

import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class PositionTest {

    // Parsing text which contains un-parseable data, but the index
    // begins at the valid portion. Ensure PP is properly updated.
    @Test
    public void modifiedPositionTest() {
        var df = new DecimalFormat("YY#");
        df.setStrict(false); // Lenient by default, set for test explicitness
        var pp = new ParsePosition(9);
        assertEquals(123L, assertDoesNotThrow(() -> df.parse("FOOBARBAZYY123", pp)));
        assertEquals(-1, pp.getErrorIndex());
        assertEquals(14, pp.getIndex());
    }

    // Clearly invalid index value that could not work under any scenarios
    // Specifically, ensuring no SIOOBE during affix matching
    @Test
    public void invalidPositionParseTest() {
        var df = new DecimalFormat();
        df.setStrict(false); // Lenient by default, set for test explicitness
        assertNull(assertDoesNotThrow(() -> df.parse("1", new ParsePosition(-1))));
        assertNull(assertDoesNotThrow(() -> df.parse("1", new ParsePosition(Integer.MAX_VALUE))));
    }

    // When prefix matching, position + affix length is greater than parsed String length
    // Ensure we do not index out of bounds of the length of the parsed String
    @Test
    public void prefixMatchingTest() {
        var df = new DecimalFormat("ZZZ#;YYY#");
        df.setStrict(false); // Lenient by default, set for test explicitness
        // 0 + 3 > 2 = (pos + prefix > text)
        assertNull(assertDoesNotThrow(() -> df.parse("Z1", new ParsePosition(0))));
        assertNull(assertDoesNotThrow(() -> df.parse("Y1", new ParsePosition(0))));
    }

    // When suffix matching, position + affix length is greater than parsed String length
    // Ensure we do not index out of bounds of the length of the parsed String
    @Test
    public void suffixMatchingTest() {
        var df = new DecimalFormat("#ZZ;#YY");
        df.setStrict(false); // Lenient by default, set for test explicitness
        // Matches prefix properly first. Then 3 + 2 > 4 = (pos + suffix > text)
        assertNull(assertDoesNotThrow(() -> df.parse("123Z", new ParsePosition(0))));
        assertNull(assertDoesNotThrow(() -> df.parse("123Y", new ParsePosition(0))));
    }

    @Test
    public void TestParsePosition() {
        ParsePosition pp1 = new ParsePosition(0);
        if (pp1.getIndex() == 0) {
            System.out.println("PP constructor() tested.");
        }else{
            fail("*** PP getIndex or constructor() result");
        }

        {
            int to = 5;
            ParsePosition pp2 = new ParsePosition ( to );
            if (pp2.getIndex() == 5) {
                System.out.println("PP getIndex and constructor(TextOffset) tested.");
            }else{
                fail("*** PP getIndex or constructor(TextOffset) result");
            }
            pp2.setIndex( 3 );
            if (pp2.getIndex() == 3) {
                System.out.println("PP setIndex tested.");
            }else{
                fail("*** PP getIndex or setIndex result");
            }
        }

        ParsePosition pp2, pp3;
        pp2 = new ParsePosition( 3 );
        pp3 = new ParsePosition( 5 );
        ParsePosition pp4 = new ParsePosition(5);
        if (! pp2.equals(pp3)) {
            System.out.println("PP not equals tested.");
        }else{
            fail("*** PP not equals fails");
        }
        if (pp3.equals(pp4)) {
            System.out.println("PP equals tested.");
        }else{
            fail("*** PP equals fails (" + pp3.getIndex() + " != " + pp4.getIndex() + ")");
        }

        ParsePosition pp5;
        pp5 = pp4;
        if (pp4.equals(pp5)) {
            System.out.println("PP operator= tested.");
        }else{
            fail("*** PP operator= operator== or operator != result");
        }

    }

    @Test
    public void TestFieldPosition() {
        FieldPosition fp = new FieldPosition( 7 );

        if (fp.getField() == 7) {
            System.out.println("FP constructor(int) and getField tested.");
        }else{
            fail("*** FP constructor(int) or getField");
        }

        FieldPosition fph = new FieldPosition( 3 );
        if ( fph.getField() != 3) fail("*** FP getField or heap constr.");

        boolean err1 = false;
        boolean err2 = false;
        boolean err3 = false;
//        for (long i = -50; i < 50; i++ ) {
//            fp.setField( i+8 );
//            fp.setBeginIndex( i+6 );
//            fp.setEndIndex( i+7 );
//            if (fp.getField() != i+8)  err1 = true;
//            if (fp.getBeginIndex() != i+6) err2 = true;
//            if (fp.getEndIndex() != i+7) err3 = true;
//        }
        if (!err1) {
            System.out.println("FP setField and getField tested.");
        }else{
            fail("*** FP setField or getField");
        }
        if (!err2) {
            System.out.println("FP setBeginIndex and getBeginIndex tested.");
        }else{
            fail("*** FP setBeginIndex or getBeginIndex");
        }
        if (!err3) {
            System.out.println("FP setEndIndex and getEndIndex tested.");
        }else{
            fail("*** FP setEndIndex or getEndIndex");
        }

        System.out.println("");
    }

    @Test
    public void TestFieldPosition_example() {
        //***** no error detection yet !!!!!!!
        //***** this test is for compiler checks and visual verification only.
        double doubleNum[] = { 123456789.0, -12345678.9, 1234567.89, -123456.789,
            12345.6789, -1234.56789, 123.456789, -12.3456789, 1.23456789};
        int dNumSize = doubleNum.length;

        DecimalFormat fmt = (DecimalFormat) NumberFormat.getInstance();
        fmt.setDecimalSeparatorAlwaysShown(true);

        final int tempLen = 20;
        StringBuffer temp;

        for (int i=0; i<dNumSize; i++) {
            temp = new StringBuffer(); // Get new buffer

            FieldPosition pos = new FieldPosition(NumberFormat.INTEGER_FIELD);
            StringBuffer buf = new StringBuffer();
            //char fmtText[tempLen];
            //ToCharString(fmt->format(doubleNum[i], buf, pos), fmtText);
            StringBuffer res = fmt.format(doubleNum[i], buf, pos);
            int tempOffset = (tempLen <= (tempLen - pos.getEndIndex())) ?
                tempLen : (tempLen - pos.getEndIndex());
            for (int j=0; j<tempOffset; j++) temp.append('='); // initialize
            //cout << temp << fmtText   << endl;
            System.out.println("FP " + temp + res);
        }

        System.out.println("");
    }
    /* @bug 4109023
     * Need to override ParsePosition.equals and FieldPosition.equals.
     */
    @Test
    public void Test4109023()
    {

        ParsePosition p = new ParsePosition(3);
        ParsePosition p2 = new ParsePosition(3);
        if (!p.equals(p2))
            fail("Error : ParsePosition.equals() failed");
        FieldPosition fp = new FieldPosition(2);
        FieldPosition fp2 = new FieldPosition(2);
        if (!fp.equals(fp2))
            fail("Error : FieldPosition.equals() failed");
    }

    /**
     * @bug 4153060
     * ParsePosition.hashCode() returns different values on equal objects.
     */
    @Test
    public void Test4153060() {
        ParsePosition p = new ParsePosition(53);
        ParsePosition q = new ParsePosition(53);
        if (!p.equals(q)) {
            fail("" + p + " and " + q + " are not equal and should be");
        }
        if (p.hashCode() != q.hashCode()) {
            fail("ParsePosition.hashCode() different for equal objects");
        } else {
            System.out.println("hashCode(" + p + ") = " + p.hashCode());
        }
    }

    /**
     * @bug 4153061
     * FieldPosition.hashCode() returns different values on equal objects.
     */
    @Test
    public void Test4153061() {
        FieldPosition p = new FieldPosition(53);
        FieldPosition q = new FieldPosition(53);
        if (!p.equals(q)) {
            fail("" + p + " and " + q + " are not equal and should be");
        }
        if (p.hashCode() != q.hashCode()) {
            fail("FieldPosition.hashCode() different for equal objects");
        } else {
            System.out.println("hashCode(" + p + ") = " + p.hashCode());
        }
    }
}

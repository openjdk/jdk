/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4401726
 * @author John O'Conner
 * @library /java/text/testlib
 * @summary Regression tests for Collation and associated classes
 * @run junit Test4401726
 */


import java.text.*;
import java.util.Locale;
import java.util.Vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class Test4401726 {

    @Test
   public void TestSetOffSet() {

       int[] expected = {0, -1, 65536};
       int[] actual = new int[expected.length];

        try {
            String rule = "< a, A < d; D";

            RuleBasedCollator rbc = new RuleBasedCollator(rule);
            String str = "aD";
            CollationElementIterator iterator =
                rbc.getCollationElementIterator(str);

            iterator.setOffset(0);
            actual[0] = iterator.getOffset();
            actual[1] = iterator.previous();
            iterator.setOffset(0);
            actual[2] = iterator.next();

            if (compareArray(expected, actual) == false) {
                fail("Failed.");
            }

            str = "a";
            iterator = rbc.getCollationElementIterator(str);
            iterator.setOffset(0);
            actual[0] = iterator.getOffset();
            actual[1] = iterator.previous();
            iterator.setOffset(0);
            actual[2] = iterator.next();

            if (compareArray(expected, actual) == false) {
                fail("Failed.");
            }

        } catch (ParseException e) {
            fail("Unexpected ParseException: " + e);
        }


    }

    boolean compareArray(int[] expected, int[] actual) {
        boolean retVal = false;
        if (expected.length == actual.length) {
            int errors = 0;
            for(int x=0; x< expected.length; ++x) {
                if (expected[x] != actual[x]) {
                    ++errors;
                }
            }
            if (errors == 0) retVal = true;
        }
        return retVal;
    }
}

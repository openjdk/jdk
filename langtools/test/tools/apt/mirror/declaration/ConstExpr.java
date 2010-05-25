/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5027675 5048535
 * @summary Tests FieldDeclaration.getConstantExpression method
 * @library ../../lib
 * @compile -source 1.5 ConstExpr.java
 * @run main/othervm ConstExpr
 */


import java.math.RoundingMode;
import java.util.*;

import com.sun.mirror.declaration.*;
import com.sun.mirror.type.*;
import com.sun.mirror.util.*;

import static java.math.RoundingMode.UP;

import static com.sun.mirror.util.DeclarationVisitors.*;


public class ConstExpr extends Tester {

    public static void main(String[] args) {
        (new ConstExpr()).run();
    }


    // Declarations used by tests

    public static final byte B = (byte) 0xBE;
    public static final short S = (short) 32767;
    public static final int I = -4;
    public static final long l = 4294967296L;
    public static final float f = 3.5f;
    public static final double PI = Math.PI;
    public static final char C = 'C';
    public static final String STR = "cheese";

    public static final char SMILEY = '\u263A';
    public static final String TWOLINES = "ab\ncd";

    public static final double D1 = Double.POSITIVE_INFINITY;
    public static final double D2 = Double.NEGATIVE_INFINITY;
    public static final double D3 = Double.NaN;
    public static final float  F1 = Float.POSITIVE_INFINITY;
    public static final float  F2 = Float.NEGATIVE_INFINITY;
    public static final float  F3 = Float.NaN;

    public static final String NOSTR = null;    // not a compile-time constant
    public static final RoundingMode R = UP;    // not a compile-time constant


    @Test(result={
              "0xbe",
              "32767",
              "-4",
              "4294967296L",
              "3.5f",
              "3.141592653589793",
              "'C'",
              "\"cheese\"",

              "'\\u263a'",
              "\"ab\\ncd\"",

              "1.0/0.0",
              "-1.0/0.0",
              "0.0/0.0",
              "1.0f/0.0f",
              "-1.0f/0.0f",
              "0.0f/0.0f",

              "null",
              "null"
          },
          ordered=true)
    Collection<String> getConstantExpression() {
        final Collection<String> res = new ArrayList<String>();

        thisClassDecl.accept(
            DeclarationVisitors.getSourceOrderDeclarationScanner(
                NO_OP,
                new SimpleDeclarationVisitor() {
                    public void visitFieldDeclaration(FieldDeclaration f) {
                        res.add(f.getConstantExpression());
                    }
                }));
        return res;
    }
}

/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8282252
 * @summary Test constructors of BigDecimal to replace BigInteger subclasses
 */

import java.math.*;

public class ConstructorUnscaledValue {
    public static void main(String... args) {
        TestBigInteger tbi = new TestBigInteger(BigInteger.ONE);
        // Create BigDecimal's using each of the three constructors
        // with guards on the class of unscaledValue
        BigDecimal[] values = {
            new BigDecimal(tbi),
            new BigDecimal(tbi, 2),
            new BigDecimal(tbi, 3, MathContext.DECIMAL32),
        };

        for (var bd : values) {
            BigInteger unscaledValue = bd.unscaledValue();
            if (unscaledValue.getClass() != BigInteger.class) {
                throw new RuntimeException("Bad class for unscaledValue");
            }
            if (!unscaledValue.equals(BigInteger.ONE)) {
                throw new RuntimeException("Bad value for unscaledValue");
            }
        }
    }

    private static class TestBigInteger extends BigInteger {
        public TestBigInteger(BigInteger bi) {
            super(bi.toByteArray());
        }

        @Override
        public String toString() {
            return java.util.Arrays.toString(toByteArray());
        }
    }
}

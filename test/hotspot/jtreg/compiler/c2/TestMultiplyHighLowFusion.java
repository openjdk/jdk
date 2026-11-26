/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8379327
 * @summary Verify correctness for combined low/high 64-bit multiplication patterns.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

package compiler.c2;

import compiler.lib.generators.Generator;
import compiler.lib.generators.Generators;
import compiler.lib.ir_framework.*;
import java.math.BigInteger;

public class TestMultiplyHighLowFusion {
  private static final BigInteger MASK_64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
  private static final Generator<Long> LONG_GEN = Generators.G.longs();

  public static void main(String[] args) {
    TestFramework.run();
  }

  @Test
  @IR(applyIfPlatform = {"x64", "true"}, phase = CompilePhase.PRINT_IDEAL, counts = {"\\bMulHiLoL\\b", "1"})
  public static long doMath(long a, long b) {
    long low = a * b;
    long high = Math.multiplyHigh(a, b);
    return low + high;
  }

  @Test
  @IR(applyIfPlatform = {"x64", "true"}, phase = CompilePhase.PRINT_IDEAL, counts = {"\\bMulHiLoL\\b", "1"})
  public static long doMathSwapped(long a, long b) {
    long low = b * a;
    long high = Math.multiplyHigh(b, a);
    return low + high;
  }

  @Test
  @IR(applyIfPlatform = {"x64", "true"}, phase = CompilePhase.PRINT_IDEAL, counts = {"\\bUMulHiLoL\\b", "1"})
  public static long doUnsignedMath(long a, long b) {
    long low = a * b;
    long high = Math.unsignedMultiplyHigh(a, b);
    return low + high;
  }

  @Test
  @IR(applyIfPlatform = {"x64", "true"}, phase = CompilePhase.PRINT_IDEAL, counts = {"\\bUMulHiLoL\\b", "1"})
  public static long doUnsignedMathSwapped(long a, long b) {
    long low = b * a;
    long high = Math.unsignedMultiplyHigh(b, a);
    return low + high;
  }

  @Run(test = {"doMath", "doMathSwapped", "doUnsignedMath", "doUnsignedMathSwapped"})
  public void runTests() {
    verifyPair(LONG_GEN.next(), LONG_GEN.next());
  }

  private void verifyPair(long a, long b) {
    long expectedSigned = expectedSigned(a, b);
    long expectedUnsigned = expectedUnsigned(a, b);

    if (doMath(a, b) != expectedSigned) {
      throw new RuntimeException("Signed mismatch for a=" + a + ", b=" + b);
    }
    if (doMathSwapped(a, b) != expectedSigned) {
      throw new RuntimeException("Signed swapped mismatch for a=" + a + ", b=" + b);
    }
    if (doUnsignedMath(a, b) != expectedUnsigned) {
      throw new RuntimeException("Unsigned mismatch for a=" + a + ", b=" + b);
    }
    if (doUnsignedMathSwapped(a, b) != expectedUnsigned) {
      throw new RuntimeException("Unsigned swapped mismatch for a=" + a + ", b=" + b);
    }
  }

  private static long expectedSigned(long a, long b) {
    BigInteger product = BigInteger.valueOf(a).multiply(BigInteger.valueOf(b));
    long low = product.longValue();
    long high = product.shiftRight(64).longValue();
    return low + high;
  }

  private static long expectedUnsigned(long a, long b) {
    BigInteger ua = BigInteger.valueOf(a).and(MASK_64);
    BigInteger ub = BigInteger.valueOf(b).and(MASK_64);
    BigInteger product = ua.multiply(ub);
    long low = product.longValue();
    long high = product.shiftRight(64).longValue();
    return low + high;
  }
}

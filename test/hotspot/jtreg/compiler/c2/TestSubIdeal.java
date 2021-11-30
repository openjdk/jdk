/*
 * @test
 * @bug 8277882
 * @summary New subnode ideal optimization: converting "c0 - (x + c1)" into "(c0 - c1) - x"
 * @library /test/lib
 * @run main/othervm -XX:-TieredCompilation -Xbatch
 *                   -XX:CompileCommand=dontinline,compiler.c2.TestSubIdeal::test*
 *                   -XX:CompileCommand=compileonly,compiler.c2.TestSubIdeal::test*
 *                   compiler.c2.TestSubIdeal
 */
package compiler.c2;

import jdk.test.lib.Asserts;

public class TestSubIdeal {

    private static final int C0_0 = 1234;
    private static final int C1 = 1234;
    private static final int C0_1 = 4321;

    public static int testC0EqualsC1(int x) {
        return C0_0 - (x + C1);
    }

    public static int testC0NotEqualsC1(int x) {
        return C0_1 - (x + C1);
    }

    public static int testXPlusC1IsOverflow(int x) {
        return Integer.MAX_VALUE - (x + Integer.MAX_VALUE);
    }

    public static int testXPlusC1IsUnderflow(int x) {
        return Integer.MIN_VALUE - (x + Integer.MIN_VALUE);
    }

    public static int testC0MinusC1IsOverflow(int x) {
        return Integer.MAX_VALUE - (x + Integer.MIN_VALUE);
    }

    public static int testC0MinusC1IsUnderflow(int x) {
        return Integer.MIN_VALUE - (x + Integer.MAX_VALUE);
    }

    public static int testResultIsOverflow(int x) {
        return 2147483637 - (x + 10); // Integer.MAX_VALUE == 2147483647
    }

    public static int testResultIsUnderflow(int x) {
        return -2147483637 - (x + 10); // Integer.MIN_VALUE == -2147483648
    }

    public static void main(String... args) {
        for (int i = 0; i < 50_000; i++) {
            Asserts.assertTrue(testC0EqualsC1(10) == -10);
            Asserts.assertTrue(testC0NotEqualsC1(100) == 2987);
            Asserts.assertTrue(testXPlusC1IsOverflow(10) == -10);
            Asserts.assertTrue(testXPlusC1IsUnderflow(-10) == 10);
            Asserts.assertTrue(testC0MinusC1IsOverflow(10) == -11);
            Asserts.assertTrue(testC0MinusC1IsUnderflow(10) == -9);
            Asserts.assertTrue(testResultIsOverflow(-21) == Integer.MIN_VALUE);
            Asserts.assertTrue(testResultIsUnderflow(2) == Integer.MAX_VALUE);
        }
    }
}

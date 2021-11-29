/*
 * @test
 * @bug 8277882
 * @summary New subnode ideal optimization: converting "c0 - (x + c1)" into "(c0 - c1) - x"
 * @library /test/lib
 * @run main/othervm -XX:-TieredCompilation
 *                   -XX:CompileCommand=dontinline,compiler.c2.TestSubIdeal::test*
 *                   compiler.c2.TestSubIdeal
 */
package compiler.c2;

import jdk.test.lib.Asserts;

public class TestSubIdeal {

    private static final int C0 = 1234;
    private static final int C1 = 1234;

    public static int test1(int x) {
        return C0 - (x + C1);
    }

    public static void main(String... args) {
        for (int i = 0; i < 50_000; i++) {
            Asserts.assertTrue(test1(10) == -10);
        }
    }
}

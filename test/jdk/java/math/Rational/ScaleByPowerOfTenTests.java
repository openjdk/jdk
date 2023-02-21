/*
 * @test
 * @summary Basic tests of scaleByPowerOfTen
 */

import java.math.*;

import static java.math.Rational.*;

public class ScaleByPowerOfTenTests {

    public static void main(String argv[]) {
        for (int i = -10; i < 10; i++) {
            Rational r = ONE.scaleByPowerOfTen(i);
            Rational expected;

            if (!r.equals(expected = TEN.pow(i))) {
                throw new RuntimeException("Unexpected result " + r.toString() + "; expected " + expected.toString());
            }

            r = ONE.negate().scaleByPowerOfTen(i);
            if (!r.equals(expected = TEN.pow(i).negate())) {
                throw new RuntimeException("Unexpected result " + r.toString() + "; expected " + expected.toString());
            }

        }
    }
}

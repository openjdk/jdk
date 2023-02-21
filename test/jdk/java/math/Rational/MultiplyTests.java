/*
 * @test
 * @summary Test Rational.multiply(Rational)
 * @author xlu
 */

import java.math.*;

public class MultiplyTests {

    private static int multiplyTests() {
        int failures = 0;

        Rational[] r1 = {
            new Rational(123456789),
            new Rational(1234567898),
            new Rational(12345678987L)
        };

        Rational[] r2 = {
            new Rational(987654321),
            new Rational(8987654321L),
            new Rational(78987654321L)
        };

        // Two dimensonal array recording bd1[i] * bd2[j] &&
        // 0 <= i <= 2 && 0 <= j <= 2;
        Rational[][] expectedResults = {
            {new Rational(121932631112635269L),
             new Rational(1109586943112635269L),
             new Rational("9751562173112635269")
            },
            { new Rational(1219326319027587258L),
              new Rational("11095869503027587258"),
              new Rational("97515622363027587258")
            },
            { new Rational("12193263197189452827"),
              new Rational("110958695093189452827"),
              new Rational("975156224183189452827")
            }
        };

        for (int i = 0; i < r1.length; i++) {
            for (int j = 0; j < r2.length; j++) {
                if (!r1[i].multiply(r2[j]).equals(expectedResults[i][j])) {
                    failures++;
                }
            }
        }

        Rational x = new Rational("0.8");
        Rational xPower = new Rational(-1L);
        try {
            for (int i = 0; i < 100; i++) {
                xPower = xPower.multiply(x);
            }
        } catch (Exception ex) {
            failures++;
        }
        return failures;
    }

    public static void main(String[] args) {
        int failures = 0;

        failures += multiplyTests();

        if (failures > 0) {
            throw new RuntimeException("Incurred " + failures +
                                       " failures while testing multiply.");
        }
    }
}

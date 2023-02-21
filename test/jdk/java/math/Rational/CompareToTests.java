/*
 * @test
 * @summary Tests of Rational.compareTo
 * @author Fabio Romano
 */
import static java.math.Rational.ONE;
import static java.math.Rational.ZERO;

import java.math.Rational;

public class CompareToTests {
	private static int compareToTests() {
		int failures = 0;

		final Rational MINUS_ONE = Rational.ONE.negate();

		// First operand, second operand, expected compareTo result
		Rational [][] testCases = {
				// Basics
				{new Rational(0),      new Rational(0),     ZERO},
				{new Rational(0),      new Rational(1),     MINUS_ONE},
				{new Rational(1),      new Rational(2),     MINUS_ONE},
				{new Rational(2),      new Rational(1),     ONE},
				{new Rational(10),     new Rational(10),    ZERO},

				// Significands would compare differently than scaled value
				{new Rational("0.2"),  new Rational(2),     MINUS_ONE},
				{new Rational(20),     new Rational(2),     ONE},
				{new Rational("0.1"),  new Rational(2),     MINUS_ONE},
				{new Rational(10),     new Rational(2),     ONE},
				{new Rational(50),     new Rational(2),     ONE},

				// Boundary and near boundary values
				{new Rational(Long.MAX_VALUE),            new Rational(Long.MAX_VALUE), ZERO},
				{new Rational(Long.MAX_VALUE).negate(),   new Rational(Long.MAX_VALUE), MINUS_ONE},

				{new Rational(Long.MAX_VALUE-1),          new Rational(Long.MAX_VALUE), MINUS_ONE},
				{new Rational(Long.MAX_VALUE-1).negate(), new Rational(Long.MAX_VALUE), MINUS_ONE},

				{new Rational(Long.MIN_VALUE),            new Rational(Long.MAX_VALUE), MINUS_ONE},
				{new Rational(Long.MIN_VALUE).negate(),   new Rational(Long.MAX_VALUE), ONE},

				{new Rational(Long.MIN_VALUE+1),          new Rational(Long.MAX_VALUE), MINUS_ONE},
				{new Rational(Long.MIN_VALUE+1).negate(), new Rational(Long.MAX_VALUE), ZERO},

				{new Rational(Long.MAX_VALUE),            new Rational(Long.MIN_VALUE), ONE},
				{new Rational(Long.MAX_VALUE).negate(),   new Rational(Long.MIN_VALUE), ONE},

				{new Rational(Long.MAX_VALUE-1),          new Rational(Long.MIN_VALUE), ONE},
				{new Rational(Long.MAX_VALUE-1).negate(), new Rational(Long.MIN_VALUE), ONE},

				{new Rational(Long.MIN_VALUE),            new Rational(Long.MIN_VALUE), ZERO},
				{new Rational(Long.MIN_VALUE).negate(),   new Rational(Long.MIN_VALUE), ONE},

				{new Rational(Long.MIN_VALUE+1),          new Rational(Long.MIN_VALUE), ONE},
				{new Rational(Long.MIN_VALUE+1).negate(), new Rational(Long.MIN_VALUE), ONE},
		};

		for (Rational[] testCase : testCases) {
			Rational a = testCase[0];
			Rational a_negate = a.negate();
			Rational b = testCase[1];
			Rational b_negate = b.negate();
			int expected = testCase[2].intValue();

			failures += compareToTest(a,        b,         expected);
			failures += compareToTest(a_negate, b_negate, -expected);
		}


		return failures;
	}

	private static int compareToTest(Rational a, Rational b, int expected) {
		int result = a.compareTo(b);
		int failed = (result==expected) ? 0 : 1;
		if (failed == 1) {
			System.err.println("(" + a + ").compareTo(" + b + ") => " + result +
					"\n\tExpected " + expected);
		}
		return failed;
	}

	public static void main(String argv[]) {
		int failures = 0;

		failures += compareToTests();

		if (failures > 0) {
			throw new RuntimeException("Incurred " + failures +
					" failures while testing compareTo.");
		}
	}
}
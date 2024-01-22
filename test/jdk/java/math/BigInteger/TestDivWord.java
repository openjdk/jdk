import java.math.Accessor;
import java.time.Duration;
import java.util.Random;

public class TestDivWord {

    private static void checkResult(long n, int d, long res) {
        final long LONG_MASK = 0xffffffffL;
        final long dLong = d & LONG_MASK;
        final long q = res & LONG_MASK, r = res >>> 32;

        if (!(0 <= r && r < dLong && n == q * dLong + r))
            System.err.println("Incorrect result with n = " + Long.toUnsignedString(n) + ", d = " + dLong + ": q = " + q + ", r = " + r);
    }

    private static void randomCaseTests() {
        final int MAX_DIVISIONS = 1 << 30;
        Random rnd = new Random();

        for (int nDivs = 1; nDivs <= MAX_DIVISIONS; nDivs <<= 1) {
            Duration elapsed = Duration.ZERO;

            for (int i = 0; i < nDivs; i++) {
                // divWord(n, d) is used when n < 0
                long n = rnd.nextLong() & (1L << 63);
                int d = rnd.nextInt();
                // Avoid d == 0
                if (d == 0)
                    d = 1;

                long t0 = System.nanoTime();
                long res = Accessor.divWord(n, d);
                long t1 = System.nanoTime();

                checkResult(n, d, res);
                elapsed = elapsed.plusNanos(t1 - t0);
            }

            System.out.println("Time to do " + nDivs + " divisions: " + elapsed);
            System.out.println("Average time for division: " + elapsed.dividedBy(nDivs) + "\n");
        }
    }

    private static void oldImplementationWorstCaseTest() {
        long n = 0xfffffffffffffffeL;
        int d = 3;
        System.out.println(Long.toUnsignedString(n) + " / " + d + " = " + Long.divideUnsigned(n, d));
        long res = Accessor.divWord(n, d);
        checkResult(n, d, res);
    }

    public static void main(String[] args) {
        randomCaseTests();
        //oldImplementationWorstCaseTest();
    }
}

import java.math.Accessor;
import java.time.Duration;
import java.util.Random;

public class TestDivWord {

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
                Accessor.divWord(n, d);
                long t1 = System.nanoTime();

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
        Accessor.divWord(n, d);
    }

    public static void main(String[] args) {
        randomCaseTests();
        //oldImplementationWorstCaseTest();
    }
}
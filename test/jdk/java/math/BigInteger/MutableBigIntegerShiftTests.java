import java.math.BigInteger;
import java.math.MutableBigIntegerBox;
import java.util.Random;
import jdk.test.lib.RandomFactory;

import static java.math.MutableBigIntegerBox.*;

public class MutableBigIntegerShiftTests {

    static final int ORDER_SMALL = 60;
    static final int ORDER_MEDIUM = 100;

    private static Random random = RandomFactory.getRandom();

    static boolean failure = false;

    public static void shift(int order) {
        int failCount1 = 0;
        int failCount2 = 0;

        for (int i=0; i<100; i++) {
            MutableBigIntegerBox x = fetchNumber(order);
            int n = Math.abs(random.nextInt()%200);

            if (x.shiftLeft(n).compare
                (x.multiply(new MutableBigIntegerBox(BigInteger.TWO.pow(n)))) != 0) {
                failCount1++;
            }

            if (x.shiftLeft(n).shiftRight(n).compare(x) != 0)
                failCount2++;
        }
        report("baz shiftLeft for " + order + " bits", failCount1);
        report("baz shiftRight for " + order + " bits", failCount2);
    }

    /**
     * Main to interpret arguments and run several tests.
     *
     * Up to three arguments may be given to specify the SIZE of BigIntegers
     * used for call parameters 1, 2, and 3. The SIZE is interpreted as
     * the maximum number of decimal digits that the parameters will have.
     *
     */
    public static void main(String[] args) {
        // Some variables for sizing test numbers in bits
        int order1 = ORDER_MEDIUM;
        int order2 = ORDER_SMALL;

        if (args.length >0)
            order1 = (int)((Integer.parseInt(args[0]))* 3.333);
        if (args.length >1)
            order2 = (int)((Integer.parseInt(args[1]))* 3.333);

        shift(order1);
        shift(order2);

        if (failure)
            throw new RuntimeException("Failure in MutableBigIntegerShiftTests.");
    }

    /*
     * Get a random or boundary-case number. This is designed to provide
     * a lot of numbers that will find failure points, such as max sized
     * numbers, empty MutableBigIntegers, etc.
     *
     * If order is less than 2, order is changed to 2.
     */
    private static MutableBigIntegerBox fetchNumber(int order) {
        int numType = random.nextInt(7);
        MutableBigIntegerBox result = null;
        if (order < 2) order = 2;

        switch (numType) {
            case 0: // Empty
                result = MutableBigIntegerBox.ZERO;
                break;

            case 1: // One
                result = MutableBigIntegerBox.ONE;
                break;

            case 2: // All bits set in number
                int numInts = (order + 31) >> 5;
                int[] fullBits = new int[numInts];
                for(int i = 0; i < numInts; i++)
                    fullBits[i] = -1;

                fullBits[0] &= -1 >>> -order;
                result = new MutableBigIntegerBox(fullBits);
                break;

            case 3: // One bit in number
                result = MutableBigIntegerBox.ONE.shiftLeft(random.nextInt(order));
                break;

            case 4: // Random bit density
                int[] val = new int[(order + 31) >> 5];
                int iterations = random.nextInt(order);
                for (int i = 0; i < iterations; i++) {
                    int bitIdx = random.nextInt(order);
                    val[bitIdx >> 5] |= 1 << bitIdx;
                }
                result = new MutableBigIntegerBox(val);
                break;
            case 5: // Runs of consecutive ones and zeros
                result = ZERO;
                int remaining = order;
                int bit = random.nextInt(2);
                while (remaining > 0) {
                    int runLength = Math.min(remaining, random.nextInt(order));
                    result = result.shiftLeft(runLength);
                    if (bit > 0)
                        result = result.add(ONE.shiftLeft(runLength).subtract(ONE));
                    remaining -= runLength;
                    bit = 1 - bit;
                }
                break;

            default: // random bits
                result = new MutableBigIntegerBox(new BigInteger(order, random));
        }

        return result;
    }

    static void report(String testName, int failCount) {
        System.err.println(testName+": " +
                           (failCount==0 ? "Passed":"Failed("+failCount+")"));
        if (failCount > 0)
            failure = true;
    }
}

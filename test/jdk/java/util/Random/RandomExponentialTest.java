import jdk.test.lib.RandomFactory;

/**
 * @test
 * @summary Check that nextExponential() returns non-negative outcomes
 * @bug 8284866
 *
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run main RandomExponentialTest
 */

public class RandomExponentialTest {

    private static final int SAMPLES = 1_000_000_000;

    public static void main(String[] args) throws Exception {
        var errCount = 0;
        var errSample = Double.NaN;
        var random = RandomFactory.getRandom();
        for (int i = 0; i < SAMPLES; i++) {
            var expVal = random.nextExponential();
            if (!(expVal >= 0.0)) {
                errCount += 1;
                errSample = expVal;
            }
        }
        if (errCount > 0) {
            throw new RuntimeException("%d errors out of %d samples: e.g., %f"
                            .formatted(errCount, SAMPLES, errSample));
        }
    }

}

import java.security.SecureRandom;
import java.util.Arrays;

/*
 * @test
 * @bug 8364657
 * @summary verify the behaviour of SecureRandom instance returned by
 *          SecureRandom.getInstanceStrong()
 * @run main TestStrong
 */
public class TestStrong {

    public static void main(String[] args) throws Exception {

        final SecureRandom random = SecureRandom.getInstanceStrong();
        System.out.println("going to generate random seed using " + random);
        final byte[] seed = random.generateSeed(0);
        System.out.println("random seed generated");
        System.out.println("seed: " + Arrays.toString(seed));
    }
}

/**
 * @test
 * @bug 8300487
 * @run main/othervm -Xms250m HugeToString
 */

import java.util.NaturalsBitSet;

public final class HugeToString {

    public static void main(String[] args) {
        NaturalsBitSet bs = new NaturalsBitSet(500_000_000);
        bs.flip(0, 500_000_000);
        try {
            bs.toString();
        } catch (OutOfMemoryError expected) {
        } catch (Throwable t) {
            throw new AssertionError("Unexpected exception", t);
        }
    }
}

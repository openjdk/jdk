/**
 * @test
 * @bug 8300487
 * @summary test the NaturalsBitSet.and() method
 */
import java.util.NaturalsBitSet;

public final class And {
    public static void main(String[] args) throws Exception {
        NaturalsBitSet a = new NaturalsBitSet();
        NaturalsBitSet b = new NaturalsBitSet();

        a.set(0);
        a.set(70);
        b.set(40);
        a.and(b);
        if (a.length() != 0)
            throw new RuntimeException("Incorrect length after and().");
    }
}

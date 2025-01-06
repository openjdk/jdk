package compiler.lib.generators;

import java.util.random.RandomGenerator;

public class RandomnessSourceAdapter implements RandomnessSource {
    private final RandomGenerator rand;

    RandomnessSourceAdapter(RandomGenerator rand) {
        this.rand = rand;
    }

    @Override
    public long nextLong() {
        return rand.nextLong();
    }

    @Override
    public long nextLong(long lo, long hi) {
        return rand.nextLong(lo, hi);
    }

    @Override
    public int nextInt() {
        return rand.nextInt();
    }

    @Override
    public int nextInt(int lo, int hi) {
        return rand.nextInt(lo, hi);
    }

    @Override
    public double nextDouble(double lo, double hi) {
        return rand.nextDouble(lo, hi);
    }

    @Override
    public float nextFloat(float lo, float hi) {
        return rand.nextFloat(lo, hi);
    }
}

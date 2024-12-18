package compiler.lib.generators;

/**
 * Mixed results between two different generators with configurable weights.
 */
public class MixedGenerator<T> implements Generator<T> {
    private final Generator<T> a;
    private final Generator<T> b;
    private final int weightUniform;
    private final int weightSpecial;

    /**
     * Creates a new {@link MixedGenerator}, which samples from two generators A and B,
     * according to specified weights.
     *
     * @param weightA Weight for the distribution for a.
     * @param weightB Weight for the distribution for b.
     */
    public MixedGenerator(Generator<T> a, Generator<T> b, int weightA, int weightB) {
        this.a = a;
        this.b = b;
        this.weightUniform = weightA;
        this.weightSpecial = weightB;
    }

    @Override
    public T next() {
        int r = Generators.RANDOM.nextInt(weightUniform + weightSpecial);
        if (r < weightUniform) {
            return a.next();
        } else {
            return b.next();
        }
    }
}

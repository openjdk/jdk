package compiler.lib.generators;

/**
 * Mixed results between two different generators with configurable weights.
 */
class MixedGenerator<T> extends GeneratorBase<T> {
    private final Generator<T> a;
    private final Generator<T> b;
    private final int weightA;
    private final int weightB;

    /**
     * Creates a new {@link MixedGenerator}, which samples from two generators A and B,
     * according to specified weights.
     *
     * @param weightA Weight for the distribution for a.
     * @param weightB Weight for the distribution for b.
     */
    MixedGenerator(Generators g, Generator<T> a, Generator<T> b, int weightA, int weightB) {
        super(g);
        this.a = a;
        this.b = b;
        this.weightA = weightA;
        this.weightB = weightB;
    }

    @Override
    public T next() {
        int r = g.random.nextInt(0, weightA + weightB);
        if (r < weightA) {
            return a.next();
        } else {
            return b.next();
        }
    }
}

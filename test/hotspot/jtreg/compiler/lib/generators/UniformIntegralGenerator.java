package compiler.lib.generators;

public abstract class UniformIntegralGenerator<T> implements Generator<T> {
    private final T lo;
    private final T hi;

    public UniformIntegralGenerator(T lo, T hi) {
        this.lo = lo;
        this.hi = hi;
    }

    T hi() { return hi; }
    T lo() { return lo; }
}

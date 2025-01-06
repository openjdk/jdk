package compiler.lib.generators;

/**
 * A generators whose outputs is restricted to a certain range.
 */
abstract class RestrictableGeneratorBase<T extends Comparable<T>> extends GeneratorBase<T> implements RestrictableGenerator<T> {
    private final T lo;
    private final T hi;

    public RestrictableGeneratorBase(Generators g, T lo, T hi) {
        super(g);
        if (lo.compareTo(hi) > 0) throw new EmptyGeneratorException();
        this.lo = lo;
        this.hi = hi;
    }

    /**
     * Creates a new generator by further restricting the range of values. The range of values will be the
     * intersection of the previous values and the values in the provided range.
     * The probability of each element occurring in the new generator stay the same relative to each other.
     */
    @Override
    public RestrictableGenerator<T> restricted(T newLo, T newHi) {
        if (newLo.compareTo(newHi) > 0) throw new EmptyGeneratorException();
        if (newHi.compareTo(lo()) <= 0) {  // new interval is to the left
            if (newHi.compareTo(lo()) < 0) throw new EmptyGeneratorException();
            return doRestrictionFromIntersection(newLo, hi());
        } else if (hi().compareTo(newLo) <= 0) {  // new interval is to the right
            if (hi().compareTo(newLo) < 0) throw new EmptyGeneratorException();
            return doRestrictionFromIntersection(lo(), newHi);
        } else {  // old and new interval intersect
            return doRestrictionFromIntersection(max(newLo, lo()), min(newHi, hi()));
        }
    }

    private T max(T a, T b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    private T min(T a, T b) {
        return a.compareTo(b) < 0 ? a : b;
    }

    /**
     * Your subclass can just override this method which will received the computed intersection between the old and
     * new interval. It is guaranteed that the interval is non-empty.
     */
    protected abstract RestrictableGenerator<T> doRestrictionFromIntersection(T lo, T hi);

    T hi() { return hi; }
    T lo() { return lo; }
}

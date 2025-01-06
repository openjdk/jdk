package compiler.lib.generators;

/**
 * A restrictable generator allows the creation of a new generator by restricting the range of its output values.
 * The exact semantics of this restriction depend on the concrete implementation, but it usually means taking the
 * intersection of the old range and the newly requested range of values.
 */
public interface RestrictableGenerator<T> extends Generator<T> {
    /**
     * Returns a new generator where the range of this generator has been restricted to the range of newLo and newHi.
     * Whether newHi is inclusive or exclusive depends on the concrete implementation.
     * @throws EmptyGeneratorException if this restriction would result in an empty generator.
     */
    RestrictableGenerator<T> restricted(T newLo, T newHi);
}

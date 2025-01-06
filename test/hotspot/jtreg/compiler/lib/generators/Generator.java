package compiler.lib.generators;

/**
 * A stream of values according to a specific distribution.
 */
public interface Generator<T> {
    /**
     * Returns the next value from the stream.
     */
    T next();
}

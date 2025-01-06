package compiler.lib.generators;

/**
 * A generator which always returns the same value.
 */
class SingleValueGenerator<T> implements Generator<T> {
    private final T value;

    SingleValueGenerator(T value) {
        this.value = value;
    }

    @Override
    public T next() {
        return this.value;
    }
}

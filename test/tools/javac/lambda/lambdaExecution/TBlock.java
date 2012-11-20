/**
 * Performs operations upon an input object which may modify that object and/or
 * external state (other objects).
 *
 * <p>All block implementations are expected to:
 * <ul>
 * <li>When used for aggregate operations upon many elements blocks
 * should not assume that the {@code apply} operation will be called upon
 * elements in any specific order.</li>
 * </ul>
 *
 * @param <T> The type of input objects to {@code apply}.
 */
public interface TBlock<T> {

    /**
     * Performs operations upon the provided object which may modify that object
     * and/or external state.
     *
     * @param t an input object
     */
    void apply(T t);

    /**
     * Returns a Block which performs in sequence the {@code apply} methods of
     * multiple Blocks. This Block's {@code apply} method is performed followed
     * by the {@code apply} method of the specified Block operation.
     *
     * @param other an additional Block which will be chained after this Block
     * @return a Block which performs in sequence the {@code apply} method of
     * this Block and the {@code apply} method of the specified Block operation
     */
    public default TBlock<T> chain(TBlock<? super T> other) {
        return (T t) -> { apply(t); other.apply(t); };
    }
}
